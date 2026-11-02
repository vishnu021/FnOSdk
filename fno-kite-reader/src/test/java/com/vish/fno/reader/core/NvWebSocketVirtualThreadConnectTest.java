package com.vish.fno.reader.core;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the premises of the bounded connect in {@link KiteWebSocket} (ADR 0078 amendment 5) on the real library path,
 * nv-websocket-client 2.3 as used by Kite SDK 4.0.0. That socket has no connect or read timeout and its
 * {@code disconnect()} is a no-op while CONNECTING, so the only way to end a handshake read the server never answers is
 * to interrupt the connecting thread, and Java 21 makes a blocking socket read interruptible on a virtual thread only.
 * The connect therefore runs on a virtual thread, so that thread must also hand nv's own reader thread a working socket.
 * These tests pin JDK + library behaviour: run them on the production JDK version after any JDK or Kite SDK upgrade.
 */
class NvWebSocketVirtualThreadConnectTest {

    private static final long BLOCKED_PROBE_MS = 500;
    private static final long RELEASE_MS = 2_000;
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final String GREETING = "hello";

    private ServerSocket server;
    private final AtomicReference<Socket> accepted = new AtomicReference<>();
    private final CountDownLatch acceptedLatch = new CountDownLatch(1);

    @AfterEach
    void stopServer() throws IOException {
        Socket socket = accepted.get();
        if (socket != null) {
            socket.close();
        }
        if (server != null) {
            server.close();
        }
    }

    /** Accepts one connection, then runs {@code afterAccept} on it (or does nothing: a silent server). */
    private int startServer(SocketHandler afterAccept) throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(() -> {
            try {
                Socket socket = server.accept();
                accepted.set(socket);
                acceptedLatch.countDown();
                afterAccept.handle(socket);
            } catch (IOException | NoSuchAlgorithmException e) {
                // server or peer closed by the test teardown
            }
        }, "test-server");
        acceptor.setDaemon(true);
        acceptor.start();
        return server.getLocalPort();
    }

    @FunctionalInterface
    private interface SocketHandler {
        void handle(Socket socket) throws IOException, NoSuchAlgorithmException;
    }

    /** A minimal RFC 6455 server side: answer the upgrade with 101, then send one unmasked text frame. */
    private static void upgradeAndGreet(Socket socket) throws IOException, NoSuchAlgorithmException {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
        String key = null;
        for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
            if (line.regionMatches(true, 0, "Sec-WebSocket-Key:", 0, 18)) {
                key = line.substring(18).trim();
            }
        }
        byte[] digest = MessageDigest.getInstance("SHA-1").digest((key + WS_GUID).getBytes(StandardCharsets.ISO_8859_1));
        String response = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + Base64.getEncoder().encodeToString(digest) + "\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(response.getBytes(StandardCharsets.ISO_8859_1));
        byte[] payload = GREETING.getBytes(StandardCharsets.UTF_8);
        out.write(new byte[]{(byte) 0x81, (byte) payload.length});
        out.write(payload);
        out.flush();
    }

    private static Runnable connect(WebSocket ws, AtomicReference<Throwable> outcome, CountDownLatch done) {
        return () -> {
            try {
                ws.connect();
            } catch (WebSocketException e) {
                outcome.set(e);
            } finally {
                done.countDown();
            }
        };
    }

    @Test
    @DisplayName("an interrupt releases a virtual thread blocked in the wss handshake of a silent server")
    void interruptReleasesVirtualThreadBlockedInHandshake() throws Exception {
        int port = startServer(socket -> { });
        WebSocket ws = new WebSocketFactory().createSocket("wss://127.0.0.1:" + port + "/");
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread connecting = Thread.ofVirtual().start(connect(ws, outcome, done));
        assertFalse(done.await(BLOCKED_PROBE_MS, TimeUnit.MILLISECONDS), "connect() must block on the silent server");

        connecting.interrupt();

        assertTrue(done.await(RELEASE_MS, TimeUnit.MILLISECONDS), "interrupt did not release the virtual thread");
        assertInstanceOf(WebSocketException.class, outcome.get());
    }

    @Test
    @DisplayName("an interrupt does not release a platform thread blocked the same way (why the connect runs on a virtual thread)")
    void interruptDoesNotReleasePlatformThreadBlockedInHandshake() throws Exception {
        int port = startServer(socket -> { });
        WebSocket ws = new WebSocketFactory().createSocket("wss://127.0.0.1:" + port + "/");
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Thread connecting = Thread.ofPlatform().daemon().start(connect(ws, outcome, done));
        assertFalse(done.await(BLOCKED_PROBE_MS, TimeUnit.MILLISECONDS), "connect() must block on the silent server");

        connecting.interrupt();

        assertFalse(done.await(BLOCKED_PROBE_MS, TimeUnit.MILLISECONDS), "a platform-thread socket read ignored the interrupt");
        assertTrue(acceptedLatch.await(RELEASE_MS, TimeUnit.MILLISECONDS), "server never accepted");
        accepted.get().close(); // only the peer closing ends it
        assertTrue(done.await(RELEASE_MS, TimeUnit.MILLISECONDS), "closing the peer did not release the platform thread");
    }

    @Test
    @DisplayName("a connect on a virtual thread hands nv's platform reader thread a working socket (the healthy path)")
    void virtualThreadConnectDeliversFramesToTheReaderThread() throws Exception {
        int port = startServer(NvWebSocketVirtualThreadConnectTest::upgradeAndGreet);
        WebSocket ws = new WebSocketFactory().createSocket("ws://127.0.0.1:" + port + "/");
        AtomicReference<String> received = new AtomicReference<>();
        AtomicBoolean receivedOnVirtualThread = new AtomicBoolean(true);
        CountDownLatch message = new CountDownLatch(1);
        ws.addListener(new WebSocketAdapter() {
            @Override
            public void onTextMessage(WebSocket websocket, String text) {
                received.set(text);
                receivedOnVirtualThread.set(Thread.currentThread().isVirtual());
                message.countDown();
            }
        });
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Thread.ofVirtual().start(connect(ws, outcome, done));

        try {
            assertTrue(done.await(RELEASE_MS, TimeUnit.MILLISECONDS), "connect() did not return");
            assertNull(outcome.get(), "connect() failed");
            assertTrue(message.await(RELEASE_MS, TimeUnit.MILLISECONDS), "no frame reached the reader thread");
            assertEquals(GREETING, received.get());
            assertFalse(receivedOnVirtualThread.get(), "frames must be read on nv's own (platform) reading thread");
        } finally {
            ws.disconnect();
        }
    }
}
