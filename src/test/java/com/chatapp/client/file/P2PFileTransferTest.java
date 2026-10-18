package com.chatapp.client.file;

import com.chatapp.common.model.FileTransferRequest;
import com.chatapp.common.security.HashUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class P2PFileTransferTest {
    @TempDir Path dir;
    static class Result implements FileTransferListener {
        volatile String completed;
        volatile String error;
        public void onProgress(long b, long t, double s, int p) { }
        public void onCompleted(String path) { completed = path; }
        public void onError(String message) { error = message; }
    }

    private void transfer(byte[] data, int partialBytes, boolean collision, boolean corrupt) throws Exception {
        Path source = dir.resolve("source.bin");
        Files.write(source, data);
        Path destination = Files.createDirectory(dir.resolve("received"));
        String checksum = HashUtil.hashFile(source.toFile());
        FileTransferRequest request = new FileTransferRequest("a", "b", "file.bin", data.length, checksum);
        request.setReceiverIp("127.0.0.1");
        Path part = destination.resolve("file.bin." + checksum + ".part");
        if (partialBytes > 0) {
            byte[] prefix = Arrays.copyOf(data, partialBytes);
            if (corrupt) prefix[0] ^= 1;
            Files.write(part, prefix);
        }
        if (collision) Files.write(destination.resolve("file.bin"), new byte[] {9});
        Result received = new Result();
        Result sent = new Result();
        P2PFileReceiver receiver = new P2PFileReceiver(request, destination.toFile(), received);
        P2PFileSender sender = new P2PFileSender(request, source.toFile(), sent);
        Thread r = new Thread(receiver);
        Thread s = new Thread(sender);
        try {
            r.start(); s.start();
            r.join(10000); s.join(10000);
            assertFalse(r.isAlive(), "Receiver must finish");
            assertFalse(s.isAlive(), "Sender must wait for receiver verification then finish");
            if (corrupt) {
                assertNotNull(received.error);
                assertNotNull(sent.error);
                assertNull(received.completed);
                assertFalse(Files.exists(part));
            } else {
                assertNull(received.error);
                assertNull(sent.error);
                assertNotNull(sent.completed);
                assertArrayEquals(data, Files.readAllBytes(Path.of(received.completed)));
                assertEquals(partialBytes, request.getResumeOffset());
                if (collision) assertArrayEquals(new byte[] {9}, Files.readAllBytes(destination.resolve("file.bin")));
            }
        } finally { sender.cancel(); receiver.cancel(); r.join(1000); s.join(1000); }
    }

    @Test void encryptedChunksResumeAndPreserveExistingFile() throws Exception {
        byte[] data = new byte[200000];
        new Random(42).nextBytes(data);
        transfer(data, 71001, true, false);
    }
    @Test void rejectCorruptPartialFile() throws Exception {
        transfer(new byte[100000], 50000, false, true);
    }
    @Test void emptyFileCompletes() throws Exception { transfer(new byte[0], 0, false, false); }
    @Test void freshTransfer() throws Exception { transfer(new byte[150000], 0, false, false); }

    @Test void sameSourceTransfersToTwoRecipientsIndependently() throws Exception {
        byte[] data = new byte[200000];
        new Random(17).nextBytes(data);
        Path source = Files.write(dir.resolve("group.bin"), data);
        List<P2PFileSender> senders = new ArrayList<>();
        List<P2PFileReceiver> receivers = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        List<Result> results = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                FileTransferRequest request = new FileTransferRequest("sender", "member" + i, "group.bin", data.length, HashUtil.hashFile(source.toFile()));
                request.setRoomId("lobby");
                request.setReceiverIp("127.0.0.1");
                Result received = new Result(), sent = new Result();
                results.add(received); results.add(sent);
                P2PFileReceiver receiver = new P2PFileReceiver(request, Files.createDirectory(dir.resolve("member" + i)).toFile(), received);
                P2PFileSender sender = new P2PFileSender(request, source.toFile(), sent);
                receivers.add(receiver); senders.add(sender);
                threads.add(new Thread(receiver)); threads.add(new Thread(sender));
            }
            threads.forEach(Thread::start);
            for (Thread thread : threads) { thread.join(10000); assertFalse(thread.isAlive()); }
            for (Result result : results) { assertNull(result.error); assertNotNull(result.completed); }
            for (int i = 0; i < 2; i++) assertArrayEquals(data, Files.readAllBytes(dir.resolve("member" + i).resolve("group.bin")));
        } finally {
            senders.forEach(P2PFileSender::cancel);
            receivers.forEach(P2PFileReceiver::cancel);
            for (Thread thread : threads) thread.join(1000);
        }
    }
}
