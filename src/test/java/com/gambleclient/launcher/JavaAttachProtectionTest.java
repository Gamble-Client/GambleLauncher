package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class JavaAttachProtectionTest {
    @Test
    void generatedSwingJvmCommandEnforcesAttachProtectionUnlessDeveloperOverrides() throws Exception {
        Method helper = Arrays.stream(Main.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("enforceAttachProtection"))
            .findFirst().orElse(null);
        assertNotNull(helper, "Swing launch command needs an attach-protection helper");
        helper.setAccessible(true);

        List<String> normalCommand = new ArrayList<>(List.of(
            "java", "-Xmx4G", "-XX:-DisableAttachMechanism", "-cp", "libraries", "Main"));
        helper.invoke(null, normalCommand, false);
        assertEquals(1, normalCommand.stream().filter("-XX:+DisableAttachMechanism"::equals).count());
        assertFalse(normalCommand.contains("-XX:-DisableAttachMechanism"));
        assertTrue(normalCommand.indexOf("-XX:+DisableAttachMechanism") < normalCommand.indexOf("-cp"));

        List<String> developerCommand = new ArrayList<>(List.of(
            "java", "-Xmx4G", "-XX:+DisableAttachMechanism", "-cp", "libraries", "Main"));
        helper.invoke(null, developerCommand, true);
        assertFalse(developerCommand.contains("-XX:+DisableAttachMechanism"));
        assertFalse(developerCommand.contains("-XX:-DisableAttachMechanism"));

        Method override = Main.class.getDeclaredMethod("developerAttachOverrideEnabled", String.class);
        override.setAccessible(true);
        assertFalse((boolean) override.invoke(null, "true"));
        assertFalse((boolean) override.invoke(null, "0"));
        assertTrue((boolean) override.invoke(null, "1"));
    }

    @Test
    void Java21StartsWithTheAttachDisableFlag() throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Process process = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", executable).toString(),
            "-XX:+DisableAttachMechanism", "-version")
            .redirectErrorStream(true)
            .start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);
        assertTrue(output.contains("version \"21."), output);
    }
}
