package com.gambleclient.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

/** Keeps Java 21 launch arguments out of the operating system process command line. */
final class JavaLaunchArguments implements AutoCloseable {
    private final Path argumentFile;
    private final Path ownerFile;
    private final List<String> command;
    private Process process;
    private boolean closed;

    private JavaLaunchArguments(Path argumentFile, String executable) {
        this.argumentFile = argumentFile;
        this.ownerFile = argumentFile.resolveSibling(argumentFile.getFileName() + ".owner");
        this.command = List.of(executable, "@" + argumentFile);
    }

    /**
     * Accepts the complete existing command, including the Java executable at index zero.
     * The directory should be application-owned. No argument values are included in errors.
     */
    static JavaLaunchArguments create(Path directory, List<String> originalCommand) throws IOException {
        // libjli's LauncherHelper.makePlatformString uses sun.jnu.encoding, even on
        // JDK 21 where file.encoding defaults to UTF-8. On Windows this is GetACP(),
        // which can also differ from native.encoding (the user's format locale).
        Charset encoding = Charset.forName(System.getProperty("sun.jnu.encoding",
            System.getProperty("native.encoding", "UTF-8")));
        return create(directory, originalCommand, encoding);
    }

    /** Explicit encoding seam for regression tests of other platforms' byte formats. */
    static JavaLaunchArguments create(Path directory, List<String> originalCommand, Charset encoding)
        throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(originalCommand, "originalCommand");
        Objects.requireNonNull(encoding, "encoding");
        if (originalCommand.size() < 2) {
            throw new IllegalArgumentException("A Java executable and arguments are required.");
        }
        List<String> arguments = List.copyOf(originalCommand);
        for (String argument : arguments) {
            if (argument.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Java launch arguments cannot contain NUL characters.");
            }
        }
        if (arguments.getFirst().isEmpty()) {
            throw new IllegalArgumentException("A Java executable is required.");
        }

        // The Java launcher must also be able to open the @file path in this encoding.
        Path absoluteDirectory = directory.toAbsolutePath().normalize();
        cleanupOrphans(absoluteDirectory);
        encode(absoluteDirectory.toString(), encoding);
        ByteArrayOutputStream contents = new ByteArrayOutputStream();
        for (int i = 1; i < arguments.size(); i++) {
            appendArgument(contents, arguments.get(i), encoding);
        }

        Path file = PrivateFileSecurity.createPrivateTempFile(
            absoluteDirectory, ".java-launch-");
        try {
            // Permissions were installed at creation, before the first sensitive byte.
            // Do not recreate the file if it disappears between creation and writing.
            Files.write(file, contents.toByteArray(), StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS);
            JavaLaunchArguments result = new JavaLaunchArguments(file, arguments.getFirst());
            PrivateFileSecurity.writePrivate(result.ownerFile, ownerRecord(0, -1));
            return result;
        } catch (IOException | RuntimeException | Error failure) {
            deleteAfterFailure(file, failure);
            deleteAfterFailure(file.resolveSibling(file.getFileName() + ".owner"), failure);
            throw failure;
        }
    }

    private static ByteBuffer encode(String argument, Charset encoding) throws IOException {
        try {
            ByteBuffer bytes = encoding.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(argument));
            // Some legacy encoders accept lossy aliases (for example a yen sign
            // encoded as a backslash in Windows-31J) even with REPORT enabled.
            if (!encoding.newDecoder().decode(bytes.asReadOnlyBuffer()).toString().equals(argument)) {
                throw new CharacterCodingException();
            }
            return bytes;
        } catch (CharacterCodingException invalid) {
            throw new IOException("Java launch text cannot be represented in " + encoding.name()
                + ". Use a UTF-8 system locale or remove unsupported characters from launch settings and paths.", invalid);
        }
    }

    private static void appendArgument(ByteArrayOutputStream output, String argument, Charset encoding)
        throws IOException {
        ByteBuffer bytes = encode(argument, encoding);
        output.write('"');
        while (bytes.hasRemaining()) {
            // Escape bytes, not Java chars: some native multibyte encodings contain
            // an ASCII backslash as a trailing byte. libjli also parses byte by byte.
            int value = Byte.toUnsignedInt(bytes.get());
            switch (value) {
                case '\\', '"' -> { output.write('\\'); output.write(value); }
                case '\n' -> { output.write('\\'); output.write('n'); }
                case '\r' -> { output.write('\\'); output.write('r'); }
                case '\t' -> { output.write('\\'); output.write('t'); }
                case '\f' -> { output.write('\\'); output.write('f'); }
                default -> output.write(value);
            }
        }
        output.write('"');
        output.write('\n');
    }

    /** The only two OS arguments: the executable and an absolute @file reference. */
    List<String> command() {
        return command;
    }

    /**
     * Start a configured builder created from {@link #command()}. This transfers file
     * cleanup to process exit, so closing this helper immediately after start is safe.
     * A failed spawn deletes the file before propagating the original failure.
     */
    synchronized Process start(ProcessBuilder builder) throws IOException {
        if (closed || process != null) {
            throw new IllegalStateException("These Java launch arguments have already been used or closed.");
        }
        try {
            if (!command.equals(builder.command())) {
                throw new IllegalArgumentException("The process builder must use the private Java command.");
            }
            process = builder.start();
            try {
                attachExitCleanup();
                PrivateFileSecurity.writePrivate(ownerFile, ownerRecord(process.pid(),
                    process.info().startInstant().map(java.time.Instant::toEpochMilli).orElse(-1L)));
            } catch (IOException | RuntimeException | Error failure) {
                // Do not return a running launch we cannot identify for cleanup.
                try {
                    process.destroyForcibly();
                    if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                        failure.addSuppressed(new IOException("The child has not exited; private arguments were retained safely."));
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    failure.addSuppressed(interrupted);
                } catch (RuntimeException terminationFailure) {
                    failure.addSuppressed(terminationFailure);
                }
                throw failure;
            }
        } catch (IOException | RuntimeException | Error failure) {
            closed = true;
            if (process == null || !process.isAlive()) {
                deleteAfterFailure(argumentFile, failure);
                deleteAfterFailure(ownerFile, failure);
            }
            throw failure;
        }
        return process;
    }

    private void attachExitCleanup() {
        process.onExit().thenRun(() -> {
            try {
                close();
            } catch (IOException | SecurityException failure) {
                // Never log file contents or register deleteOnExit: the launcher can
                // exit before the child, when the child may still need to read the file.
                System.err.println("Launcher warning: could not remove the private Java argument file.");
            }
        });
    }

    /** Deletes abandoned arguments, or defers deletion until an already-started child exits. */
    @Override
    public synchronized void close() throws IOException {
        closed = true;
        if (process == null || !process.isAlive()) {
            Files.deleteIfExists(argumentFile);
            Files.deleteIfExists(ownerFile);
        }
    }

    private static byte[] ownerRecord(long pid, long startedAt) {
        return ("gamble-java-arguments-v1\n" + pid + "\n" + startedAt + "\n")
            .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    /** Clean only identified dead children; a live/reused/unknown PID is retained. */
    static void cleanupOrphans(Path directory) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return;
        try (var markers = Files.newDirectoryStream(directory, ".java-launch-*.part.owner")) {
            var currentOwner = directory.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
            int examined = 0;
            for (Path marker : markers) {
                if (++examined > 512) break;
                String name = marker.getFileName().toString();
                Path arguments = marker.resolveSibling(name.substring(0, name.length() - ".owner".length()));
                try {
                    if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                        || !Files.isRegularFile(arguments, LinkOption.NOFOLLOW_LINKS)
                        || !Files.getOwner(marker, LinkOption.NOFOLLOW_LINKS).equals(currentOwner)
                        || !Files.getOwner(arguments, LinkOption.NOFOLLOW_LINKS).equals(currentOwner)) continue;
                    String record;
                    try (var input = Files.newInputStream(marker, LinkOption.NOFOLLOW_LINKS)) {
                        byte[] bytes = input.readNBytes(257);
                        if (bytes.length > 256) continue;
                        record = new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
                    }
                    String[] fields = record.split("\n");
                    if (fields.length != 3 || !fields[0].equals("gamble-java-arguments-v1")) continue;
                    long pid = Long.parseLong(fields[1]);
                    if (pid <= 0) continue; // Crash between spawn and recording: identity unknown.
                    long startedAt = Long.parseLong(fields[2]);
                    var child = ProcessHandle.of(pid);
                    if (child.isPresent() && child.get().isAlive()) {
                        // Reused PID is deliberately retained too: no assumptions
                        // about process metadata visibility or clock precision.
                        continue;
                    }
                    if (startedAt < -1) continue;
                    Files.delete(arguments); // Exact marked sibling, never a recorded path.
                    Files.delete(marker);
                } catch (IOException | RuntimeException ignored) {
                    // Inconclusive ownership/identity or failed cleanup is not launch failure.
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Best-effort cleanup; credentials themselves are still written fail-closed.
        }
    }

    private static void deleteAfterFailure(Path file, Throwable failure) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException | RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}
