package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.NoRouteToHostException;
import java.net.URI;

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
}
