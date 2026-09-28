package com.gambleclient.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Download integrity for files the launcher installs, matching the native launcher.
 *
 * <p>Every managed file is checked against a hash published by its source (Mojang
 * and Fabric metadata, Modrinth). Results are cached by path, size and modification
 * time so unchanged files are not hashed on every launch. The cache is a performance
 * aid, not a security boundary.</p>
 */
final class DownloadIntegrity {
    interface Downloader {
        void download(String url, File target) throws IOException;
    }

    static final class Expected {
        final String algorithm;
        final String digest;
        final long size;

        private Expected(String algorithm, String digest, long size) {
            this.algorithm = algorithm;
            this.digest = digest;
            this.size = size;
        }

        /** Null when the metadata has no usable hash of this kind. */
        static Expected of(String algorithm, String digest, long size) {
            int length;
            switch (algorithm) {
                case "SHA-1": length = 40; break;
                case "SHA-256": length = 64; break;
                case "SHA-512": length = 128; break;
                default: return null;
            }
            String value = digest == null ? "" : digest.trim().toLowerCase(Locale.ROOT);
            if (value.length() != length || !value.chars().allMatch(c -> Character.digit(c, 16) >= 0)) return null;
            return new Expected(algorithm, value, size > 0 ? size : -1L);
        }
    }

    private final File store;
    private final Map<String, String> entries = new HashMap<>();
    private boolean dirty;

    DownloadIntegrity(File store) {
        this.store = store;
        if (store != null && store.isFile()) {
            try {
                for (String line : Files.readAllLines(store.toPath(), StandardCharsets.UTF_8)) {
                    int tab = line.indexOf('\t');
                    if (tab > 0) entries.put(line.substring(0, tab), line.substring(tab + 1));
                }
            } catch (IOException ignored) {
                // A missing or unreadable cache only means files are hashed again.
            }
        }
    }

    static String digest(File file, String algorithm) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(algorithm + " is unavailable.", e);
        }
        byte[] buffer = new byte[65536];
        try (InputStream in = Files.newInputStream(file.toPath())) {
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format("%02x", value));
        return hex.toString();
    }

    private static String state(File file) {
        if (!file.isFile()) return null;
        return file.length() + "\t" + file.lastModified();
    }

    private static String key(File file) {
        return file.getAbsolutePath().replace('\t', ' ').replace('\n', ' ');
    }

    synchronized boolean vouched(File file, Expected expected) {
        String state = state(file);
        return state != null && (state + "\t" + expected.algorithm + "\t" + expected.digest).equals(entries.get(key(file)));
    }

    synchronized boolean unchangedSinceVerified(File file) {
        String state = state(file);
        String entry = entries.get(key(file));
        return state != null && entry != null && entry.startsWith(state + "\t");
    }

    private synchronized void remember(File file, String state, Expected expected) {
        entries.put(key(file), state + "\t" + expected.algorithm + "\t" + expected.digest);
        dirty = true;
    }

    private synchronized void forget(File file) {
        if (entries.remove(key(file)) != null) dirty = true;
    }

    /** Whether the file exists and matches; hashes only when the cache cannot vouch. */
    boolean matches(File file, Expected expected) throws IOException {
        String state = state(file);
        if (state == null) return false;
        if (expected.size > 0 && file.length() != expected.size) return false;
        if (vouched(file, expected)) return true;
        if (!digest(file, expected.algorithm).equals(expected.digest)) return false;
        if (!state.equals(state(file))) return false;
        remember(file, state, expected);
        return true;
    }

    /**
     * Keeps a matching file, otherwise removes it and downloads again. Without a
     * published hash an existing file is kept. A download that does not match is
     * deleted and fails.
     */
    void ensureFile(String url, File file, Expected expected, String label, Downloader downloader) throws IOException {
        if (file.isFile()) {
            if (expected == null) return;
            if (matches(file, expected)) return;
            Files.deleteIfExists(file.toPath());
            forget(file);
        }
        if (url == null || url.trim().isEmpty()) throw new IOException("No download URL for " + label + ".");
        downloader.download(url, file);
        if (expected != null && !matches(file, expected)) {
            Files.deleteIfExists(file.toPath());
            throw new IOException(label + " did not match its published " + expected.algorithm
                + " hash after download, so it was deleted. Try again; if this repeats, your connection may be altering downloads.");
        }
    }

    synchronized void save() {
        if (store == null || !dirty) return;
        List<String> lines = new ArrayList<>(entries.size());
        for (Map.Entry<String, String> entry : entries.entrySet()) lines.add(entry.getKey() + "\t" + entry.getValue());
        File staging = new File(store.getAbsolutePath() + ".part");
        try {
            File parent = store.getParentFile();
            if (parent != null) Files.createDirectories(parent.toPath());
            Files.write(staging.toPath(), lines, StandardCharsets.UTF_8);
            Files.move(staging.toPath(), store.toPath(), StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException ignored) {
            try {
                Files.deleteIfExists(staging.toPath());
            } catch (IOException ignoredAgain) {
                // Nothing else to clean up.
            }
        }
    }

    /** Metadata paths must stay inside the folder they are joined to. */
    static String safeRelativePath(String value) throws IOException {
        if (value == null || value.trim().isEmpty() || value.startsWith("/") || value.contains("\\")
            || value.contains(":") || value.contains("\0")) {
            throw new IOException("Refusing an unsafe path from version metadata: " + value);
        }
        for (String part : value.split("/")) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IOException("Refusing an unsafe path from version metadata: " + value);
            }
        }
        return value;
    }
}
