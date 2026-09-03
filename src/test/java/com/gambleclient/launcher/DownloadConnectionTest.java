package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DownloadConnectionTest {
    @Test
    void downloadConnectionIsFullyConfiguredBeforeItConnects() throws Exception {
        TrackingConnection connection = new TrackingConnection();

        int status = Main.connectDownloadConnection(connection);

        assertEquals(200, status);
        assertEquals(15_000, connection.getConnectTimeout());
        assertEquals(30_000, connection.getReadTimeout());
        assertEquals("GambleClientLauncher/0.1.132", connection.userAgent);
        assertTrue(connection.connectedForResponse);
    }

    private static final class TrackingConnection extends HttpURLConnection {
        boolean connectedForResponse;
        String userAgent;

        TrackingConnection() throws Exception {
            super(new URL("https://example.invalid/test"));
        }

        @Override
        public int getResponseCode() {
            connected = true;
            connectedForResponse = true;
            return 200;
        }

        @Override
        public void setRequestProperty(String key, String value) {
            super.setRequestProperty(key, value);
            if ("User-Agent".equalsIgnoreCase(key)) userAgent = value;
        }

        @Override
        public void disconnect() {
            connected = false;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() throws IOException {
            connected = true;
        }
    }
}
