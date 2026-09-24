package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

final class MinecraftChildrenTest {
    @TempDir Path directory;

    @Test
    void secondaryProfilesAreDistinctAndCopyNothing() throws Exception {
        Path primary = directory.resolve("profiles/gamble-client");
        Files.createDirectories(primary.resolve("cg-mod"));
        Files.writeString(primary.resolve("cg-mod/client-session.txt"), "fixture-session");
        Files.writeString(primary.resolve("options.txt"), "fixture-settings");
        Files.createDirectory(primary.resolve("saves"));
        Path first = MinecraftChildren.newProfile(directory, "gamble-client");
        Path second = MinecraftChildren.newProfile(directory, "gamble-client");
        assertNotEquals(first, second);
        assertEquals(primary.getParent(), first.getParent());
        assertTrue(first.getFileName().toString().startsWith("gamble-client-session-"));
        try (var files = Files.list(first)) { assertEquals(0, files.count()); }
        try (var files = Files.list(second)) { assertEquals(0, files.count()); }
        assertEquals("fixture-session", Files.readString(primary.resolve("cg-mod/client-session.txt")));
        assertThrows(java.io.IOException.class, () -> MinecraftChildren.newProfile(directory, "../other"));
    }

    @Test
    void twoRealJavaChildrenKeepIndependentStateWhenOneExits() throws Exception {
        MinecraftChildren children = new MinecraftChildren();
        Process first = startChild("first");
        Process second = startChild("second");
        try {
            var a = children.add(first, directory.resolve("first").toFile(), directory.resolve("first.log").toFile());
            var b = children.add(second, directory.resolve("second").toFile(), directory.resolve("second.log").toFile());
            assertEquals(2, children.live().size());
            a.startupComplete = true;
            a.fatalDetected = true;
            a.detectedFailure = "fixture failure";
            a.record("first log");
            b.record("second log");
            assertFalse(b.startupComplete);
            assertFalse(b.fatalDetected);
            assertEquals("", b.detectedFailure);
            assertEquals(List.of("second log"), b.lastLines(100));
            first.getOutputStream().write('x');
            first.getOutputStream().flush();
            assertTrue(first.waitFor(10, TimeUnit.SECONDS));
            assertEquals(73, first.exitValue());
            children.remove(a);
            assertEquals(List.of(b), children.live());
            children.remove(a); // A delayed/double watcher cannot remove its sibling.
            assertEquals(List.of(b), children.live());
            assertTrue(second.isAlive());
            assertFalse(b.stopRequested);
        } finally {
            first.destroyForcibly();
            second.destroyForcibly();
            first.waitFor(10, TimeUnit.SECONDS);
            second.waitFor(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void stopAllMarksAndStopsBothRealChildren() throws Exception {
        MinecraftChildren children = new MinecraftChildren();
        Process first = startChild("first");
        Process second = startChild("second");
        try {
            var a = children.add(first, directory.toFile(), directory.resolve("a.log").toFile());
            var b = children.add(second, directory.toFile(), directory.resolve("b.log").toFile());
            var snapshot = children.requestStopAll();
            assertEquals(2, snapshot.size());
            assertTrue(a.stopRequested);
            assertTrue(b.stopRequested);
            MinecraftChildren.stop(snapshot);
            assertFalse(first.isAlive());
            assertFalse(second.isAlive());
            assertTrue(children.live().isEmpty());
        } finally {
            first.destroyForcibly();
            second.destroyForcibly();
        }
    }

    @Test
    void logsAreBoundedPerChild() throws Exception {
        Process child = startChild("bounded");
        try {
            var state = new MinecraftChildren.Child(child, directory.toFile(), directory.resolve("a.log").toFile());
            for (int i = 0; i < 400; i++) state.record("line " + i);
            assertEquals(300, state.lastLines(500).size());
            assertEquals(List.of("line 398", "line 399"), state.lastLines(2));
        } finally { child.destroyForcibly(); }
    }

    private Process startChild(String name) throws Exception {
        Path profile = Files.createDirectories(directory.resolve("profile space " + name));
        String exe = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        List<String> command = List.of(Path.of(System.getProperty("java.home"), "bin", exe).toString(),
            "-cp", Path.of(Probe.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
            Probe.class.getName(), name);
        try (JavaLaunchArguments args = JavaLaunchArguments.create(directory, command)) {
            ProcessBuilder builder = new ProcessBuilder(args.command()).directory(profile.toFile());
            for (String key : List.of("JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS")) builder.environment().remove(key);
            Process process = args.start(builder);
            try {
                String ready = CompletableFuture.supplyAsync(() -> {
                    try { return new BufferedReader(new InputStreamReader(process.getInputStream())).readLine(); }
                    catch (Exception error) { throw new RuntimeException(error); }
                }).get(10, TimeUnit.SECONDS);
                assertEquals("READY " + name, ready);
                assertTrue(process.isAlive());
                return process;
            } catch (Throwable error) {
                process.destroyForcibly();
                throw error;
            }
        }
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            System.out.println("READY " + args[0]);
            System.out.flush();
            System.in.read();
            System.exit(73);
        }
    }
}
