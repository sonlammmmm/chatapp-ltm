package com.chatapp.server;

import com.chatapp.common.model.FileTransferRequest;
import com.chatapp.common.protocol.FileRelayProtocol;
import com.chatapp.common.security.AESUtil;
import java.io.*;
import java.net.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class FileRelayServerTest {
    private Socket connect(FileRelayServer relay) throws IOException {
        Socket socket = new Socket("127.0.0.1", relay.getPort());
        socket.setSoTimeout(3000);
        return socket;
    }

    @Test void wrongTokenDuplicateRoleAndConsumedSessionCannotHijackTransfer() throws Exception {
        FileTransferRequest request = new FileTransferRequest("a", "b", "x", 0, "0".repeat(64));
        try (FileRelayServer relay = new FileRelayServer(0, id -> {})) {
            assertTrue(relay.register(request));
            try (Socket bad = connect(relay)) {
                DataOutputStream output = new DataOutputStream(bad.getOutputStream());
                output.writeUTF(AESUtil.encrypt(request.getTransferId() + "\nwrong-token\nreceiver"));
                output.flush();
                assertFalse(new DataInputStream(bad.getInputStream()).readBoolean());
            }
            try (Socket receiver = connect(relay); Socket sender = connect(relay)) {
                FileRelayProtocol.join(receiver, request, false);
                try (Socket duplicate = connect(relay)) {
                    assertThrows(IOException.class, () -> FileRelayProtocol.join(duplicate, request, false));
                }
                FileRelayProtocol.join(sender, request, true);
                new DataOutputStream(receiver.getOutputStream()).writeLong(71);
                assertEquals(71, new DataInputStream(sender.getInputStream()).readLong());
                sender.getOutputStream().write(new byte[]{1, 2, 3});
                assertArrayEquals(new byte[]{1, 2, 3}, receiver.getInputStream().readNBytes(3));
                relay.remove(request.getTransferId());
            }
            try (Socket replay = connect(relay)) {
                assertThrows(IOException.class, () -> FileRelayProtocol.join(replay, request, true));
            }
        }
    }

    @Test void disconnectAndStopCloseWaitingAndUnauthenticatedSockets() throws Exception {
        FileTransferRequest request = new FileTransferRequest("a", "b", "x", 0, "0".repeat(64));
        try (FileRelayServer relay = new FileRelayServer(0, id -> {})) {
            assertTrue(relay.register(request));
            try (Socket waiting = connect(relay)) {
                FileRelayProtocol.join(waiting, request, false);
                relay.removeUser("b");
                assertEquals(-1, waiting.getInputStream().read());
            }
            try (Socket idle = connect(relay)) {
                // An invalid handshake reply proves this socket was accepted before shutdown.
                new DataOutputStream(idle.getOutputStream()).writeUTF(AESUtil.encrypt("unknown\nx\nsender"));
                assertFalse(new DataInputStream(idle.getInputStream()).readBoolean());
            }
            try (Socket paired = connect(relay)) {
                assertTrue(relay.register(request));
                FileRelayProtocol.join(paired, request, false);
                relay.close();
                assertEquals(-1, paired.getInputStream().read());
            }
        }
    }

    @Test void missingPeerTimesOutAndInvalidatesToken() throws Exception {
        String previous = System.getProperty("CHATAPP_P2P_TIMEOUT_MS");
        System.setProperty("CHATAPP_P2P_TIMEOUT_MS", "250");
        FileTransferRequest request = new FileTransferRequest("a", "b", "x", 0, "0".repeat(64));
        try (FileRelayServer relay = new FileRelayServer(0, id -> {})) {
            assertTrue(relay.register(request));
            try (Socket waiting = connect(relay)) {
                FileRelayProtocol.join(waiting, request, false);
                assertEquals(-1, waiting.getInputStream().read());
            }
            try (Socket late = connect(relay)) {
                assertThrows(IOException.class, () -> FileRelayProtocol.join(late, request, true));
            }
        } finally {
            if (previous == null) System.clearProperty("CHATAPP_P2P_TIMEOUT_MS");
            else System.setProperty("CHATAPP_P2P_TIMEOUT_MS", previous);
        }
    }
}
