package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

final class LauncherVersionOrderTest {
    @Test
    void onlyStrictlyNewerSemanticLauncherVersionsAreUpdates() throws Exception {
        Method helper = Arrays.stream(Main.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("isLauncherVersionNewer"))
            .findFirst().orElse(null);
        assertNotNull(helper, "Swing launcher needs a semantic update comparison helper");
        helper.setAccessible(true);

        assertFalse(newer(helper, "0.1.149", "0.1.148"));
        assertFalse(newer(helper, "0.1.149", "0.1.149"));
        assertTrue(newer(helper, "0.1.149", "0.1.150"));
        assertTrue(newer(helper, "1.0.0-rc.1", "1.0.0"));
        assertFalse(newer(helper, "1.0.0", "1.0.0-rc.1"));
        assertTrue(newer(helper, "1.0.0-alpha.9", "1.0.0-alpha.10"));
        assertTrue(newer(helper, "1.0.0-alpha", "1.0.0-alpha.1"));
        assertFalse(newer(helper, "0.1.149+installed", "0.1.149+advertised"));
        assertFalse(newer(helper, "0.1.149", "not-a-version"));
        assertFalse(newer(helper, "0.1.149", " 0.1.150"));
    }

    private static boolean newer(Method helper, String installed, String advertised) throws Exception {
        return (boolean) helper.invoke(null, installed, advertised);
    }
}
