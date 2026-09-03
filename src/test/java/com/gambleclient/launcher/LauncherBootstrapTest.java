package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherBootstrapTest {
    @Test
    void linuxDefaultsToTheSingleToolkitCompatibilityUi() {
        assertTrue(LauncherBootstrap.shouldLaunchSwing("Linux", new String[0]));
        assertFalse(LauncherBootstrap.shouldLaunchSwing("Linux", new String[]{"--javafx"}));
        assertFalse(LauncherBootstrap.shouldLaunchSwing("Linux", new String[]{"--self-test"}));
        assertFalse(LauncherBootstrap.shouldLaunchSwing("Linux", new String[]{"--diagnostics"}));
    }

    @Test
    void otherPlatformsKeepJavaFxUnlessSwingIsExplicitlyRequested() {
        assertFalse(LauncherBootstrap.shouldLaunchSwing("Windows 11", new String[0]));
        assertFalse(LauncherBootstrap.shouldLaunchSwing("Mac OS X", new String[0]));
        assertTrue(LauncherBootstrap.shouldLaunchSwing("Windows 11", new String[]{"--swing"}));
    }
}
