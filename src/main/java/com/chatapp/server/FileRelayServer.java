package com.chatapp.server;

import com.chatapp.common.config.AppConfig;
import com.chatapp.common.model.FileTransferRequest;
import com.chatapp.common.security.AESUtil;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Bounded relay: only pairs issued by chat signaling may exchange encrypted bytes. */
public final class FileRelayServer implements AutoCloseable {
    private final ServerSocket listener;
    private final Map<String, Transfer> transfers = new ConcurrentHashMap<>();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final ExecutorService workers = new ThreadPoolExecutor(0, 32, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>());
    private final ScheduledExecutorService expiry = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean closed;

    public FileRelayServer(int port, Consumer<String> onExpired) throws IOException {
        listener = new ServerSocket(port);
        Thread acceptor = new Thread(this::accept, "FileRelayAccept");
        acceptor.start();
        expiry.scheduleAtFixedRate(() -> {
            for (Transfer transfer : transfers.values()) {
                if (transfer.expire()) {
                    remove(transfer.request.getTransferId());
                    onExpired.accept(transfer.request.getTransferId());
                }
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    public int getPort() { return listener.getLocalPort(); }

    public synchronized boolean register(FileTransferRequest request) {
        if (closed || transfers.size() >= 128) return false;
        request.setRelayed(true);
        request.setRelayToken(UUID.randomUUID().toString());
        request.setReceiverIp(null);
        request.setReceiverPort(getPort());
        return transfers.putIfAbsent(request.getTransferId(), new Transfer(request)) == null;
    }

    public void remove(String id) {
        Transfer transfer = transfers.remove(id);
        if (transfer != null) transfer.close();
    }

    public void removeUser(String username) {
        for (Transfer transfer : transfers.values()) {
            if (username.equals(transfer.request.getSender()) || username.equals(transfer.request.getReceiver()))
                remove(transfer.request.getTransferId());
        }
    }

    private void accept() {
        while (!closed) {
            try {
                Socket socket = listener.accept();
                sockets.add(socket);
                try { workers.execute(() -> forward(socket)); }
                catch (RejectedExecutionException e) { sockets.remove(socket); closeSocket(socket); }
            } catch (IOException e) {
                if (!closed) close();
            }
        }
    }

    private void forward(Socket socket) {
        Transfer transfer = null;
        boolean attached = false;
        try (Socket connection = socket) {
            connection.setSoTimeout(AppConfig.fileTimeout());
            String[] credentials = AESUtil.decrypt(new DataInputStream(connection.getInputStream()).readUTF()).split("\n", -1);
            transfer = credentials.length == 3 ? transfers.get(credentials[0]) : null;
            boolean sender = credentials.length == 3 && credentials[2].equals("sender");
            if (transfer == null || (!sender && !credentials[2].equals("receiver"))
                    || !transfer.request.getRelayToken().equals(credentials[1])
                    || !(attached = transfer.attach(connection, sender))) {
                new DataOutputStream(connection.getOutputStream()).writeBoolean(false);
                return;
            }
            Socket peer = transfer.awaitPeer(sender);
            connection.getInputStream().transferTo(peer.getOutputStream());
            peer.shutdownOutput();
            transfer.finished();
        } catch (IOException | SecurityException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            if (attached) transfer.close();
        } finally {
            sockets.remove(socket);
            if (attached && transfer.isClosed()) transfers.remove(transfer.request.getTransferId(), transfer);
        }
    }

    @Override public synchronized void close() {
        closed = true;
        try { listener.close(); } catch (IOException ignored) { }
        transfers.values().forEach(Transfer::close);
        transfers.clear();
        sockets.forEach(FileRelayServer::closeSocket);
        workers.shutdownNow();
        expiry.shutdownNow();
    }

    private static void closeSocket(Socket socket) {
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
    }

    private static final class Transfer {
        final FileTransferRequest request;
        final long created = System.nanoTime();
        Socket sender;
        Socket receiver;
        boolean closed;
        int finished;

        Transfer(FileTransferRequest request) { this.request = request; }

        synchronized boolean attach(Socket socket, boolean sending) throws IOException {
            if (closed || (sending ? sender != null : receiver != null)) return false;
            DataOutputStream reply = new DataOutputStream(socket.getOutputStream());
            reply.writeBoolean(true);
            reply.flush();
            if (sending) sender = socket; else receiver = socket;
            notifyAll();
            return true;
        }

        synchronized Socket awaitPeer(boolean sending) throws IOException, InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AppConfig.fileTimeout());
            while (!closed && (sending ? receiver : sender) == null) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new SocketTimeoutException("File relay peer did not connect");
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }
            if (closed) throw new IOException("File relay closed");
            return sending ? receiver : sender;
        }

        synchronized void finished() throws IOException, InterruptedException {
            // Keep both sockets alive until the reverse stream has delivered the checksum acknowledgement.
            if (++finished == 2) close();
            while (!closed) wait();
        }

        synchronized boolean expire() {
            if (sender != null && receiver != null) return false;
            if (System.nanoTime() - created < TimeUnit.MINUTES.toNanos(10)) return false;
            close();
            return true;
        }

        synchronized boolean isClosed() { return closed; }
        synchronized void close() {
            closed = true;
            closeSocket(sender);
            closeSocket(receiver);
            notifyAll();
        }
    }
}
