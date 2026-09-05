package com.gambleclient.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.zip.ZipFile;

final class NativeArchive {
    private NativeArchive() {}

    static void extract(Path archive, Path target, long maxBytes, int maxEntries) throws IOException {
        if (maxBytes < 0 || maxEntries < 0) throw new IOException("Native archive limits are invalid.");
        Files.createDirectories(target);
        Path staging = Files.createTempDirectory(target, ".native-extract-");
        var outputs = new ArrayList<Path>();
        try (var zip = new ZipFile(archive.toFile())) {
            if (zip.size() > maxEntries) throw new IOException("Native archive contains too many files.");
            long written = 0;
            var names = new HashSet<String>();
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                // ZipFile skips unneeded members without inflating them.
                if (entry.isDirectory() || name.startsWith("META-INF/") || name.contains("..")) continue;
                String filename = Path.of(name.replace('\\', '/')).getFileName().toString();
                if (filename.isBlank() || !names.add(filename.toLowerCase(java.util.Locale.ROOT))) {
                    throw new IOException("Native archive contains an invalid or duplicate filename.");
                }
                Path output = staging.resolve(filename);
                outputs.add(output);
                var checksum = new java.util.zip.CRC32();
                long entryBytes = 0;
                try (var input = zip.getInputStream(entry); var stream = Files.newOutputStream(output)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer, 0, (int) Math.min(buffer.length, maxBytes - written + 1))) != -1) {
                        if (count > maxBytes - written) throw new IOException("Native archive exceeds the expanded-size safety limit.");
                        stream.write(buffer, 0, count);
                        checksum.update(buffer, 0, count);
                        entryBytes += count;
                        written += count;
                    }
                }
                if (entryBytes != entry.getSize() || checksum.getValue() != entry.getCrc()) {
                    throw new IOException("Native archive entry failed its size or checksum check.");
                }
            }
            // Only verified, complete extraction replaces active native files.
            for (Path output : outputs) Files.move(output, target.resolve(output.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            for (Path output : outputs) Files.deleteIfExists(output);
            Files.deleteIfExists(staging);
        }
    }
}
