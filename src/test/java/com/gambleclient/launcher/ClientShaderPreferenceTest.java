package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ClientShaderPreferenceTest {
    @Test
    void preferenceOverridesDuplicatesWithoutTouchingOtherOptions() throws Exception {
        Method method = Main.class.getDeclaredMethod("applyClientShaderPreference", List.class, boolean.class);
        method.setAccessible(true);
        for (boolean disabled : new boolean[] {false, true}) {
            List<String> command = new ArrayList<>(List.of("java", "-Xmx4G",
                "-Dgamble.shaders.disabled=false", "-Dgamble.shaders.disabled=true",
                "-Dgamble.shaders.disabled", "-Dgamble.shaders.disabled.other=keep"));
            method.invoke(null, command, disabled);
            List<String> expected = new ArrayList<>(List.of("java", "-Xmx4G", "-Dgamble.shaders.disabled.other=keep"));
            if (disabled) expected.add("-Dgamble.shaders.disabled=true");
            assertEquals(expected, command);
            method.invoke(null, command, disabled);
            assertEquals(expected, command);
        }
    }
}
