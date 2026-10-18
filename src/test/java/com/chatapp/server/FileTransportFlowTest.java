package com.chatapp.server;

import com.chatapp.client.*;
import com.chatapp.client.file.*;
import com.chatapp.common.model.*;
import com.chatapp.common.security.*;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FileTransportFlowTest {
    @TempDir Path directory;

    private void probe(String mode) throws Exception {
        Path log = directory.resolve(mode + ".log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dchatapp.textKey=" + TextEncryptionFixture.KEY,
                "-DCHATAPP_FILE_MODE=" + mode,
                "-cp", System.getProperty("java.class.path"), Probe.class.getName(), mode)
                .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "File transport probe must finish");
            assertEquals(0, process.exitValue(), new String(Files.readAllBytes(log), java.nio.charset.StandardCharsets.UTF_8));
        } finally { process.destroyForcibly(); }
    }

    @Test void p2pChatSignalingAndFileTransferBothDirections() throws Exception { probe("p2p"); }
    @Test void relayChatSignalingAndFileTransferBothDirections() throws Exception { probe("relay"); }

    public static class Probe {
        static class Peer implements AutoCloseable {
            final ChatClient client = new ChatClient();
            final CountDownLatch login = new CountDownLatch(1);
            final BlockingQueue<FileTransferRequest> offered = new LinkedBlockingQueue<>();
            final BlockingQueue<FileTransferRequest> accepted = new LinkedBlockingQueue<>();
            final BlockingQueue<ChatMessage> messages = new LinkedBlockingQueue<>();
            Peer(int port, String username) throws Exception {
                client.setListener((ClientListener) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{ClientListener.class}, (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "onLoginSuccess": login.countDown(); break;
                                case "onFileTransferRequested": offered.add((FileTransferRequest) args[0]); break;
                                case "onFileTransferAccepted": accepted.add((FileTransferRequest) args[0]); break;
                                case "onMessageReceived": messages.add((ChatMessage) args[0]); break;
                                default: break;
                            }
                            return null;
                        }));
                client.connect("127.0.0.1", port);
                client.login(username, "123456");
                if (!login.await(5, TimeUnit.SECONDS)) throw new AssertionError("Login failed");
            }
            public void close() { client.disconnect(); }
        }

        static class Result implements FileTransferListener {
            final CompletableFuture<String> completion = new CompletableFuture<>();
            public void onProgress(long b, long t, double s, int p) { }
            public void onCompleted(String path) { completion.complete(path); }
            public void onError(String error) { completion.completeExceptionally(new AssertionError(error)); }
        }

        static void send(Peer from, Peer to, boolean relay, String name) throws Exception {
            byte[] bytes = new byte[200000];
            new Random(17).nextBytes(bytes);
            Path source = Files.write(Path.of(name + ".bin"), bytes);
            FileTransferRequest outgoing = from.client.prepareFileRequests(to.client.getCurrentUser().getUsername(), false, source.toFile()).get(0);
            outgoing.setRelayed(!relay);
            outgoing.setRelayToken("forged-token");
            outgoing.setReceiverIp("192.0.2.1");
            outgoing.setReceiverPort(1);
            from.client.sendFileRequest(outgoing);
            FileTransferRequest offered = to.offered.poll(5, TimeUnit.SECONDS);
            if (offered == null || offered.isRelayed() != relay) throw new AssertionError("Wrong transport signaling");
            if (relay ? "forged-token".equals(offered.getRelayToken()) : offered.getRelayToken() != null)
                throw new AssertionError("Client transport credentials trusted");
            Result received = new Result(), sent = new Result();
            P2PFileReceiver receiver = new P2PFileReceiver(offered, Files.createDirectory(Path.of(name + "-received")).toFile(), received);
            P2PFileSender sender = null;
            Thread receiverThread = new Thread(receiver);
            Thread senderThread = null;
            try {
                receiverThread.start();
                FileTransferRequest reply = offered;
                if (relay) {
                    reply = new FileTransferRequest();
                    reply.setTransferId(offered.getTransferId());
                    reply.setSender(offered.getSender());
                    reply.setReceiverIp("192.0.2.1");
                    reply.setRelayToken("forged-token");
                }
                to.client.acceptFileRequest(reply, relay ? 0 : receiver.getListeningPort());
                FileTransferRequest accepted = from.accepted.poll(5, TimeUnit.SECONDS);
                if (accepted == null || accepted.isRelayed() != relay || accepted.getReceiverIp() == null)
                    throw new AssertionError("Missing reachable endpoint");
                if (relay && (accepted.getReceiverPort() != receiver.getListeningPort()
                        || !offered.getRelayToken().equals(accepted.getRelayToken())))
                    throw new AssertionError("Receiver overrode relay endpoint or token");
                sender = new P2PFileSender(accepted, source.toFile(), sent);
                senderThread = new Thread(sender);
                senderThread.start();
                from.client.sendDirectMessage(to.client.getCurrentUser().getUsername(), "Chat during file transfer");
                if (to.messages.poll(5, TimeUnit.SECONDS) == null) throw new AssertionError("Chat blocked by file transfer");
                Path destination = Path.of(received.completion.get(10, TimeUnit.SECONDS));
                sent.completion.get(10, TimeUnit.SECONDS);
                if (!Arrays.equals(bytes, Files.readAllBytes(destination))) throw new AssertionError("File corrupted");
            } finally {
                receiver.cancel();
                if (sender != null) sender.cancel();
                receiverThread.join(2000);
                if (senderThread != null) senderThread.join(2000);
            }
        }

        public static void main(String[] args) throws Exception {
            if (args[0].equals("relay")) {
                try (ServerSocket occupied = new ServerSocket(0)) {
                    System.setProperty("CHATAPP_FILE_RELAY_PORT", String.valueOf(occupied.getLocalPort()));
                    ChatServer failed = new ChatServer(0);
                    try {
                        failed.start();
                        throw new AssertionError("An occupied relay port must prevent startup");
                    } catch (java.net.BindException expected) { }
                    try (ServerSocket released = new ServerSocket(failed.getPort())) {
                        if (!released.isBound()) throw new AssertionError("Failed startup leaked the chat socket");
                    }
                }
            }
            try (ServerSocket freePort = new ServerSocket(0)) {
                System.setProperty("CHATAPP_FILE_RELAY_PORT", String.valueOf(freePort.getLocalPort()));
            }
            ChatServer server = new ChatServer(0);
            server.start();
            try (Peer a = new Peer(server.getPort(), "giahuy"); Peer b = new Peer(server.getPort(), "sonlam")) {
                send(a, b, args[0].equals("relay"), "forward");
                send(b, a, args[0].equals("relay"), "reverse");
            } finally { server.stop(); }
        }
    }
}
