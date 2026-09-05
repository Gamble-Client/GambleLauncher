package com.gambleclient.launcher;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;

/** Optional UI metadata must never expand a whole user-supplied archive member. */
final class ArchiveDisplayName {
    static final int MAX_METADATA_BYTES = 64 * 1024;

    private ArchiveDisplayName() {}

    static String read(File file, boolean modMetadata) {
        if (!modMetadata) return file.getName();
        try (var zip = new ZipFile(file)) {
            var entry = zip.getEntry("fabric.mod.json");
            if (entry == null || entry.getSize() > MAX_METADATA_BYTES) return file.getName();
            byte[] bytes;
            try (var input = zip.getInputStream(entry)) {
                bytes = input.readNBytes(MAX_METADATA_BYTES + 1);
            }
            if (bytes.length > MAX_METADATA_BYTES) return file.getName();
            var matcher = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(new String(bytes, StandardCharsets.UTF_8));
            if (matcher.find()) {
                String name = matcher.group(1).replaceAll("[\\p{Cntrl}]", " ").strip();
                if (!name.isBlank()) return name.substring(0, Math.min(name.length(), 100));
            }
        } catch (Exception ignored) {
            // A corrupt or non-Fabric archive remains manageable by filename.
        }
        return file.getName();
    }
}
