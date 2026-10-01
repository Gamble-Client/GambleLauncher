package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

final class LauncherMinimumVersionTest {
    @Test
    void installedLauncherBelowMinimumIsForcedToUpdate() throws Exception {
        String url = "https://example.invalid/launcher-0.1.150";
        String status = status("0.1.149", "0.1.150", "0.1.150", url);
        assertTrue(status.contains("update required"), status);
        assertTrue(status.contains(url), status);
    }

    @Test
    void swingNeverOffersAReleaseBelowMinimum() throws Exception {
        String status = status("0.1.149", "0.1.150", "0.1.151", "https://example.invalid/launcher-0.1.150");
        assertTrue(status.contains("update required"), status);
        assertFalse(status.contains("https://example.invalid/launcher-0.1.150"), status);
    }

    private static String status(String installed, String latest, String minimum, String url) throws Exception {
        Method policy = Arrays.stream(Main.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("launcherVersionStatus"))
            .findFirst().orElse(null);
        if (policy != null) {
            policy.setAccessible(true);
            return (String) policy.invoke(null, installed, latest, minimum, url);
        }

        // Reproduce the baseline status gate: it compares latest to installed and
        // appends any non-empty URL, without reading minVersion.
        if (!Main.isLauncherVersionNewer(installed, latest)) return "Launcher latest: " + installed + ".";
        return "Launcher update available: " + latest + ". Download: " + url;
    }
}
