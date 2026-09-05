package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class JavaLaunchArgumentsTest {
    private static final String FAKE_TOKEN = "FAKE_ACCESS_TOKEN_ARGV_SENTINEL_9283";
    private static final Charset NATIVE_ENCODING = Charset.forName(System.getProperty("sun.jnu.encoding"));

    @TempDir
    Path temporaryDirectory;

    @Test
    void realJavaPreservesJvmAndGameArgumentsAndCleansUpAfterExit() throws Exception {
        roundTrip(List.of(
            "", " ", "  leading and trailing  ", "double\"quote", "single'quote", "both'\"quotes",
            "\\", "ends-in-backslash\\", "C:\\Program Files\\Java\\", "\\\"quoted\\\"",
            "line one\n  line two\r\nline three\rend", "\t\f", "literal\\n\\r\\t\\f\\u1234",
            "#comment", "one # two", "@", "@missing-argument-file", "@@literal", "--disable-@files",
            "*.jar", "${literal}", "--accessToken", FAKE_TOKEN,
            "x".repeat(4090) + "\\\"\n" + "y".repeat(5000), ""
        ));
    }

    @Test
    void realJavaPreservesUnicodeIncludingSupplementaryAndCombiningCharacters() throws Exception {
        List<String> values = List.of("caf\u00e9", "\u4e2d\u6587", "\u041f\u0440\u0438\u0432\u0435\u0442",
            "emoji \ud83c\udfb2", "e\u0301", "\ufeff", "\u8868\\\"\n\u8868");
        assumeTrue(values.stream().allMatch(value -> NATIVE_ENCODING.newEncoder().canEncode(value)),
            "The native Java launcher encoding must support the Unicode fixture");
        roundTrip(values, "private caf\u00e9 \u8868 \ud83c\udfb2");
    }

    @Test
    void realJavaPreservesRepresentableNativeMultibyteCharacters() throws Exception {
        List<String> values = List.of("\u8868", "\u30bd", "\u00e9", "\u00a3", "\u4e2d").stream()
            .filter(value -> NATIVE_ENCODING.newEncoder().canEncode(value)).toList();
        assumeTrue(!values.isEmpty(), "Native encoding has no representable fixture characters");
        roundTrip(values);
    }

    @Test
    void privateFileExistsBeforeSpawnAndAbandonedLaunchDeletesIt() throws Exception {
        JavaLaunchArguments arguments = JavaLaunchArguments.create(temporaryDirectory, probeCommand(List.of(FAKE_TOKEN)));
        Path file = argumentFile(arguments);
        try (arguments) {
            assertEquals(2, arguments.command().size());
            assertTrue(file.isAbsolute());
            assertEquals(javaExecutable(), arguments.command().getFirst());
            assertTrue(Files.readString(file, NATIVE_ENCODING).contains(FAKE_TOKEN));
            assertOwnerOnly(file);
        }
        assertFalse(Files.exists(file));
        assertFalse(Files.exists(file.resolveSibling(file.getFileName() + ".owner")));
        arguments.close();
        assertThrows(IllegalStateException.class, () -> arguments.start(new ProcessBuilder(arguments.command())));
    }

    @Test
    void nextStartupRemovesOnlyMarkedArgumentsForAnExitedChild() throws Exception {
        Process child = new ProcessBuilder(javaExecutable(), "-version").redirectErrorStream(true).start();
        child.getInputStream().readAllBytes();
        assertEquals(0, child.waitFor());
        Path file = markedArguments(child.pid(), -1);
        Path unrelated = Files.writeString(temporaryDirectory.resolve("personal-notes.part"), "preserve");
        JavaLaunchArguments.cleanupOrphans(temporaryDirectory);
        assertFalse(Files.exists(file));
        assertFalse(Files.exists(file.resolveSibling(file.getFileName() + ".owner")));
        assertEquals("preserve", Files.readString(unrelated));
    }

    @Test
    void postSpawnRecordFailureAbortsChildAndRemovesSecretArguments() throws Exception {
        try (JavaLaunchArguments arguments = JavaLaunchArguments.create(temporaryDirectory, probeCommand(List.of(FAKE_TOKEN)))) {
            Path file = argumentFile(arguments);
            Path marker = file.resolveSibling(file.getFileName() + ".owner");
            Files.delete(marker);
            Files.createDirectory(marker);
            Files.writeString(marker.resolve("keep"), "preserve");
            IOException error = assertThrows(IOException.class, () -> arguments.start(builder(arguments)));
            assertFalse(error.getMessage().contains(FAKE_TOKEN));
            assertFalse(Files.exists(file));
            assertEquals("preserve", Files.readString(marker.resolve("keep")));
            // Restore the intentionally blocked marker fixture for close().
            Files.delete(marker.resolve("keep"));
            Files.deleteIfExists(marker);
        }
    }

    @Test
    void cleanupRetainsLiveReusedAndUnknownProcessIdentities() throws Exception {
        Path live = markedArguments(ProcessHandle.current().pid(), 1);
        Path unknown = markedArguments(0, -1);
        JavaLaunchArguments.cleanupOrphans(temporaryDirectory);
        assertTrue(Files.exists(live), "A reused/live PID must never lose its argument file");
        assertTrue(Files.exists(unknown), "Incomplete spawn records are not permission to delete");
    }

    @Test
    void cleanupRejectsMalformedRecordsAndNeverFollowsSymlinks() throws Exception {
        Path file = markedArguments(0, -1);
        Path owner = file.resolveSibling(file.getFileName() + ".owner");
        Files.writeString(owner, "../../../outside\n" + "x".repeat(300));
        JavaLaunchArguments.cleanupOrphans(temporaryDirectory);
        assertTrue(Files.exists(file));
        if (temporaryDirectory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Path other = Files.writeString(temporaryDirectory.resolve("outside"), "preserve");
            Files.delete(file);
            Files.createSymbolicLink(file, other);
            Files.writeString(owner, "gamble-java-arguments-v1\n2147483647\n-1\n");
            JavaLaunchArguments.cleanupOrphans(temporaryDirectory);
            assertTrue(Files.isSymbolicLink(file));
            assertEquals("preserve", Files.readString(other));
        }
    }

    private Path markedArguments(long pid, long start) throws Exception {
        Path file = PrivateFileSecurity.createPrivateTempFile(temporaryDirectory, ".java-launch-");
        Files.writeString(file, FAKE_TOKEN);
        PrivateFileSecurity.writePrivate(file.resolveSibling(file.getFileName() + ".owner"),
            ("gamble-java-arguments-v1\n" + pid + "\n" + start + "\n")
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return file;
    }

    @Test
    void realJavaProcessCmdlineContainsOnlyExecutableAndArgfile() throws Exception {
        assumeTrue(Files.isReadable(Path.of("/proc/self/cmdline")), "procfs cmdline is unavailable");
        try (JavaLaunchArguments arguments = JavaLaunchArguments.create(temporaryDirectory, probeCommand(List.of(FAKE_TOKEN)))) {
            Process process = arguments.start(builder(arguments));
            try {
                assertResponse(process, List.of(FAKE_TOKEN));
                Path cmdline = Path.of("/proc", Long.toString(process.pid()), "cmdline");
                assumeTrue(Files.isReadable(cmdline), "Child cmdline is not exposed on this system");
                String contents = new String(Files.readAllBytes(cmdline), NATIVE_ENCODING);
                assertFalse(contents.contains(FAKE_TOKEN));
                assertEquals(arguments.command(), List.of(contents.split("\u0000")));
                assertTrue(Files.exists(argumentFile(arguments)));
            } finally {
                stop(process);
            }
        }
    }

    @Test
    void failedSpawnDeletesArgumentFileAndRetainsOriginalError() throws Exception {
        List<String> command = new ArrayList<>(probeCommand(List.of(FAKE_TOKEN)));
        command.set(0, temporaryDirectory.resolve("missing-java-executable").toString());
        JavaLaunchArguments arguments = JavaLaunchArguments.create(temporaryDirectory, command);
        Path file = argumentFile(arguments);
        assertTrue(Files.exists(file));
        IOException error = assertThrows(IOException.class, () -> arguments.start(builder(arguments)));
        assertFalse(error.getMessage().contains(FAKE_TOKEN));
        assertFalse(Files.exists(file), "Failed spawn must clean up without requiring caller close()");
        arguments.close();
    }

    @Test
    void mismatchedBuilderIsRejectedAndCleansUpWithoutSpawning() throws Exception {
        JavaLaunchArguments arguments = JavaLaunchArguments.create(temporaryDirectory, probeCommand(List.of(FAKE_TOKEN)));
        Path file = argumentFile(arguments);
        assertThrows(IllegalArgumentException.class,
            () -> arguments.start(new ProcessBuilder(probeCommand(List.of(FAKE_TOKEN)))));
        assertFalse(Files.exists(file));
    }

    @Test
    void forcedChildExitAlsoDeletesArgumentFileWithoutCallerClose() throws Exception {
        try (JavaLaunchArguments arguments = JavaLaunchArguments.create(temporaryDirectory, probeCommand(List.of()))) {
            Path file = argumentFile(arguments);
            Process process = arguments.start(builder(arguments));
            try {
                assertResponse(process, List.of());
                assertTrue(Files.exists(file));
                assertThrows(IllegalStateException.class, () -> arguments.start(builder(arguments)));
                assertTrue(Files.exists(file), "A second start must not delete a running child's arguments");
                stop(process);
                awaitDeletion(file);
            } finally {
                stop(process);
            }
        }
    }

    @Test
    void invalidArgumentsDoNotCreateFilesOrExposeTheirValues() throws Exception {
        Path directory = temporaryDirectory.resolve("not-created");
        assertThrows(IllegalArgumentException.class, () -> JavaLaunchArguments.create(directory, List.of()));
        assertThrows(IllegalArgumentException.class, () -> JavaLaunchArguments.create(directory, List.of("", "arg")));
        IllegalArgumentException nulError = assertThrows(IllegalArgumentException.class,
            () -> JavaLaunchArguments.create(directory, List.of(javaExecutable(), FAKE_TOKEN + "\0")));
        IOException encodingError = assertThrows(IOException.class,
            () -> JavaLaunchArguments.create(directory, List.of(javaExecutable(), FAKE_TOKEN + "\ud800")));
        assertFalse(nulError.getMessage().contains(FAKE_TOKEN));
        assertFalse(encodingError.getMessage().contains(FAKE_TOKEN));
        assertFalse(Files.exists(directory));
    }

    @Test
    void windows1252UsesNativeBytesRatherThanUtf8() throws Exception {
        try (JavaLaunchArguments arguments = JavaLaunchArguments.create(temporaryDirectory,
            List.of(javaExecutable(), "caf\u00e9 \u00a3"), Charset.forName("windows-1252"))) {
            assertArrayEquals(new byte[]{'"', 'c', 'a', 'f', (byte) 0xe9, ' ', (byte) 0xa3, '"', '\n'},
                Files.readAllBytes(argumentFile(arguments)));
        }
    }

    @Test
    void windows31jEscapesBackslashBytesInsideMultibyteCharacters() throws Exception {
        try (JavaLaunchArguments arguments = JavaLaunchArguments.create(temporaryDirectory,
            List.of(javaExecutable(), "\u8868\u30bd"), Charset.forName("windows-31j"))) {
            // 表 = 95 5c and ソ = 83 5c. libjli consumes one slash from each pair
            // before LauncherHelper decodes the original Windows-31J bytes.
            assertArrayEquals(new byte[]{'"', (byte) 0x95, '\\', '\\', (byte) 0x83, '\\', '\\', '"', '\n'},
                Files.readAllBytes(argumentFile(arguments)));
        }
    }

    @Test
    void unsupportedWindowsTextAndArgfilePathsFailWithoutReplacementOrFiles() throws Exception {
        Path directory = temporaryDirectory.resolve("not-created");
        for (String charset : List.of("windows-1252", "windows-31j")) {
            Charset encoding = Charset.forName(charset);
            IOException error = assertThrows(IOException.class, () -> JavaLaunchArguments.create(directory,
                List.of(javaExecutable(), FAKE_TOKEN + "\ud83c\udfb2"), encoding));
            assertTrue(error.getMessage().contains(encoding.name()));
            assertTrue(error.getMessage().contains("UTF-8 system locale"));
            assertFalse(error.getMessage().contains(FAKE_TOKEN));
            assertThrows(IOException.class, () -> JavaLaunchArguments.create(directory.resolve("\ud83c\udfb2"),
                List.of(javaExecutable(), "-version"), encoding));
        }
        assertFalse(Files.exists(directory));
    }

    @Test
    void windows31jRejectsLossyAliasesEvenWhenEncoderReportsThemAsSupported() {
        Charset encoding = Charset.forName("windows-31j");
        Path directory = temporaryDirectory.resolve("not-created");
        for (String value : List.of("\u00a5", "\u203e")) {
            assertTrue(encoding.newEncoder().canEncode(value));
            assertThrows(IOException.class,
                () -> JavaLaunchArguments.create(directory, List.of(javaExecutable(), value), encoding));
        }
        assertFalse(Files.exists(directory));
    }

    private void roundTrip(List<String> values) throws Exception {
        roundTrip(values, "private arguments # folder");
    }

    private void roundTrip(List<String> values, String directoryName) throws Exception {
        // Use a relative input when the workspace and temp directory share a
        // drive; Windows runners put them on D: and C:, where no relative path
        // exists. Both cases still require an absolute @file for the child cwd.
        Path directory = temporaryDirectory.resolve(directoryName);
        Path workspace = Path.of("").toAbsolutePath();
        Path inputDirectory = workspace.getRoot().equals(directory.toAbsolutePath().getRoot())
            ? workspace.relativize(directory.toAbsolutePath()) : directory.toAbsolutePath();
        Path workingDirectory = Files.createDirectory(temporaryDirectory.resolve("game " + directoryName));
        try (JavaLaunchArguments arguments = JavaLaunchArguments.create(inputDirectory, probeCommand(values))) {
            Path file = argumentFile(arguments);
            assertOwnerOnly(file);
            Process process = arguments.start(builder(arguments).directory(workingDirectory.toFile()));
            try {
                // Closing immediately after spawn must not race the Java launcher's read.
                arguments.close();
                assertResponse(process, values);
                assertTrue(process.isAlive());
                assertTrue(Files.exists(file), "The argument file must remain until the child exits");
                process.getOutputStream().close();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS));
                assertEquals(0, process.exitValue());
                awaitDeletion(file);
            } finally {
                stop(process);
            }
        }
    }

    private static List<String> probeCommand(List<String> values) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-Dlaunch.probe.count=" + values.size());
        for (int i = 0; i < values.size(); i++) command.add("-Dlaunch.probe." + i + "=" + values.get(i));
        command.add("-cp");
        command.add(Path.of(Probe.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        command.add(Probe.class.getName());
        command.addAll(values);
        return command;
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static ProcessBuilder builder(JavaLaunchArguments arguments) {
        ProcessBuilder builder = new ProcessBuilder(arguments.command());
        // Keep the probe deterministic without changing the launcher's environment policy.
        for (String key : List.of("JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS")) builder.environment().remove(key);
        return builder.redirectError(ProcessBuilder.Redirect.INHERIT);
    }

    private static Path argumentFile(JavaLaunchArguments arguments) {
        return Path.of(arguments.command().get(1).substring(1));
    }

    private static void assertOwnerOnly(Path file) throws IOException {
        if (Files.getFileAttributeView(file, PosixFileAttributeView.class) != null) {
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(file));
        } else {
            AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
            assertNotNull(acl, "Sensitive launch arguments require OS file permissions");
            assertEquals(1, acl.getAcl().size());
            assertEquals(AclEntryType.ALLOW, acl.getAcl().getFirst().type());
            assertEquals(acl.getOwner(), acl.getAcl().getFirst().principal());
        }
    }

    private static void assertResponse(Process process, List<String> expected) throws Exception {
        CompletableFuture<List<List<String>>> response = CompletableFuture.supplyAsync(() -> {
            try {
                DataInputStream input = new DataInputStream(process.getInputStream());
                assertEquals(21, input.readInt(), "This regression test must exercise a real JDK 21 launcher");
                return List.of(readValues(input), readValues(input));
            } catch (IOException error) {
                throw new java.io.UncheckedIOException(error);
            }
        });
        assertEquals(List.of(expected, expected), response.get(15, TimeUnit.SECONDS),
            "Both JVM properties and application arguments must round-trip exactly");
    }

    private static List<String> readValues(DataInputStream input) throws IOException {
        int count = input.readInt();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++) values.add(input.readUTF());
        return values;
    }

    private static void awaitDeletion(Path file) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (Files.exists(file) && System.nanoTime() < deadline) Thread.sleep(10);
        assertFalse(Files.exists(file), "Process exit must remove the private argument file");
    }

    private static void stop(Process process) throws InterruptedException {
        if (process.isAlive()) process.destroyForcibly();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Probe child must not be left running");
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            DataOutputStream output = new DataOutputStream(System.out);
            output.writeInt(Runtime.version().feature());
            int count = Integer.parseInt(System.getProperty("launch.probe.count"));
            output.writeInt(count);
            for (int i = 0; i < count; i++) output.writeUTF(System.getProperty("launch.probe." + i));
            output.writeInt(args.length);
            for (String arg : args) output.writeUTF(arg);
            output.flush();
            // A pipe handshake keeps the child alive while the parent checks /proc
            // and permissions, without races against sleeps or premature child exit.
            System.in.read();
        }
    }
}
