package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DisplayNameTest {
    @Test
    void launcherDisplayNamesAreBoundedAndFormattingFree() throws Exception {
        Method method = Main.class.getDeclaredMethod("sanitizeDisplayName", String.class, String.class);
        method.setAccessible(true);
        assertEquals("My Client", method.invoke(null, "  My\n Client§c  ", "Fallback"));
        assertEquals("Fallback", method.invoke(null, "\n§c", "Fallback"));
        assertEquals(40, ((String) method.invoke(null, "x".repeat(60), "Fallback")).length());
    }
}
