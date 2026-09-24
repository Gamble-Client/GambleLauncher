package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLHandshakeException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NetworkFailoverTest {
    @Test
    void backendRequestsKeepPrivatePathAndQueryAcrossIndependentGateways() throws Exception {
        var urls = Main.firstPartyApiUrls(
            "https://gambleclient.org/api/launcher/poll?code=private-code&state=one"
        );

        assertEquals(3, urls.size());
        assertEquals("gambleclient.org", URI.create(urls.get(0)).getHost());
        assertEquals("dash.gambleclient.org", URI.create(urls.get(1)).getHost());
        assertEquals("gamble-client-b67.pages.dev", URI.create(urls.get(2)).getHost());
        assertEquals(URI.create(urls.get(0)).getRawPath(), URI.create(urls.get(2)).getRawPath());
        assertEquals(URI.create(urls.get(0)).getRawQuery(), URI.create(urls.get(2)).getRawQuery());
    }

    @Test
    void backendFailoverRejectsUntrustedOrMalformedOrigins() {
        assertThrows(IOException.class, () -> Main.firstPartyApiUrls(
            "https://evil.example/api/launcher/poll?code=private-code"
        ));
        assertThrows(IOException.class, () -> Main.firstPartyApiUrls(
            "http://gambleclient.org/api/launcher/poll"
        ));
        assertThrows(IOException.class, () -> Main.firstPartyApiUrls(
            "https://user:password@gambleclient.org/api/launcher/poll"
        ));
    }

    @Test
    void onlyConnectionClassFailuresTriggerGatewayFailover() {
        assertTrue(Main.isRetryableTransport(
            new IOException("wrapper", new NoRouteToHostException("No route to host"))
        ));
        assertFalse(Main.isRetryableTransport(new IOException("Malformed response")));
    }

    @Test
    void postIsResentOnlyWhenTheFailureProvesNothingWasSent() {
        var readTimeout = new SocketTimeoutException("Read timed out");
        assertTrue(Main.isRetryableTransport("GET", readTimeout));
        assertFalse(Main.isRetryableTransport("POST", readTimeout));
        assertFalse(Main.isRetryableTransport("POST", new IOException("wrapper", new javax.net.ssl.SSLException("Connection reset"))));
        assertTrue(Main.isRetryableTransport("POST", new Main.RequestNotSentException(new ConnectException("Connection refused"))));
        assertTrue(Main.isRetryableTransport("POST", new Main.RequestNotSentException(new SSLHandshakeException("handshake"))));
        assertTrue(Main.isRetryableTransport("post", new Main.RequestNotSentException(new java.net.UnknownHostException("dns"))));
        assertFalse(Main.isRetryableTransport("POST", new Main.RequestNotSentException(new IOException("Malformed"))));
        IOException wrapped = Main.notResentIfMaybeDelivered(readTimeout);
        assertTrue(wrapped.getMessage().contains("did not send it again"));
        assertEquals(readTimeout, wrapped.getCause());
    }

    @Test
    void realPostTimeoutAfterSendingIsNotRetryableButRefusedConnectIs() throws Exception {
        AtomicInteger accepted = new AtomicInteger();
        try (ServerSocket server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
            Thread thread = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    accepted.incrementAndGet();
                    socket.getInputStream().read(new byte[4096]);
                    Thread.sleep(3000); // Received, never answers.
                } catch (Exception ignored) {
                }
            });
            thread.setDaemon(true);
            thread.start();
            HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + server.getLocalPort() + "/api/standalone/loader").toURL().openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(300);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            Main.connectBeforeSending(connection);
            try (OutputStream output = connection.getOutputStream()) {
                output.write("{}".getBytes(StandardCharsets.UTF_8));
            }
            IOException failure = assertThrows(IOException.class, connection::getResponseCode);
            connection.disconnect();
            assertTrue(Main.isRetryableTransport(failure), "classified as a transport failure");
            assertFalse(Main.isRetryableTransport("POST", failure), "must not resend a POST that reached the server");
            assertEquals(1, accepted.get());
        }

        int closedPort;
        try (ServerSocket closed = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = closed.getLocalPort();
        }
        HttpURLConnection refused = (HttpURLConnection) URI.create("http://127.0.0.1:" + closedPort + "/").toURL().openConnection();
        refused.setConnectTimeout(1000);
        refused.setRequestMethod("POST");
        refused.setDoOutput(true);
        IOException notSent = assertThrows(IOException.class, () -> Main.connectBeforeSending(refused));
        assertTrue(Main.isRetryableTransport("POST", notSent));
    }
}
