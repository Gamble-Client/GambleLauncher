package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LaunchFailureMessageTest {
    @Test
    void failuresAfterTheOneUseEnrollmentAskForAFreshPlay() {
        assertEquals("Asset download failed.", Main.freshLaunchMessage("Asset download failed.", false));
        String shown = Main.freshLaunchMessage("Asset download failed.", true);
        assertTrue(shown.startsWith("Press Play again."), shown);
        assertTrue(shown.endsWith("What failed: Asset download failed."), shown);
        assertTrue(Main.freshLaunchMessage("", true).endsWith("What failed: Minecraft could not launch."));
    }
}
