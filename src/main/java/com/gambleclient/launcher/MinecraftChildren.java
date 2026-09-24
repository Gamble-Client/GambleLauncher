package com.gambleclient.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Per-process lifecycle and diagnostics; never shares state with a sibling. */
final class MinecraftChildren {
    static final class Child {
        final Process process;
        final File gameDir;
        final File logFile;
        final long startedAt = System.currentTimeMillis();
        final Deque<String> lines = new ArrayDeque<>();
        volatile boolean startupComplete;
        volatile boolean fatalDetected;
        volatile boolean stopRequested;
        volatile boolean gambleClient;
        volatile String detectedFailure = "";

        Child(Process process, File gameDir, File logFile) {
            this.process = process;
            this.gameDir = gameDir;
            this.logFile = logFile;
        }

        synchronized void record(String line) {
            lines.addLast(line);
            while (lines.size() > 300) lines.removeFirst();
        }

        synchronized List<String> lastLines(int limit) {
            List<String> copy = new ArrayList<>(lines);
            return copy.subList(Math.max(0, copy.size() - limit), copy.size());
        }
    }

    private final List<Child> children = new ArrayList<>();

    synchronized Child add(Process process, File gameDir, File logFile) {
        Child child = new Child(process, gameDir, logFile);
        children.add(child);
        return child;
    }

    synchronized List<Child> live() {
        return children.stream().filter(child -> child.process.isAlive()).toList();
    }

    synchronized void remove(Child child) {
        children.remove(child);
    }

    List<Child> requestStopAll() {
        List<Child> snapshot = live();
        snapshot.forEach(child -> child.stopRequested = true);
        return snapshot;
    }

    static void stop(List<Child> snapshot) {
        snapshot.forEach(child -> child.process.destroy());
        for (Child child : snapshot) {
            try {
                if (!child.process.waitFor(2, TimeUnit.SECONDS)) {
                    child.process.destroyForcibly();
                    child.process.waitFor(4, TimeUnit.SECONDS);
                }
            } catch (InterruptedException interrupted) {
                snapshot.forEach(other -> other.process.destroyForcibly());
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    static Path newProfile(Path managedRoot, String kind) throws IOException {
        if (!List.of("gamble-client", "vanilla", "fabric").contains(kind)) {
            throw new IOException("Unknown launch profile.");
        }
        Path profiles = managedRoot.resolve("profiles");
        Files.createDirectories(profiles);
        // Atomic unique allocation, deliberately empty: no saves/config/credentials copied.
        return Files.createTempDirectory(profiles, kind + "-session-");
    }
}
