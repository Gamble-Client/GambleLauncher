package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ArchiveDisplayNameTest {
    @TempDir Path directory;

    private java.io.File archive(String metadata) throws Exception {
        Path file = directory.resolve("pack.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(file))) {
            output.putNextEntry(new ZipEntry("fabric.mod.json"));
            output.write(metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return file.toFile();
    }

    @Test void readsSmallModMetadata() throws Exception {
        assertEquals("My mod", ArchiveDisplayName.read(archive("{\"name\":\"My mod\"}"), true));
    }

    @Test void resourcePacksNeverReadModMetadata() throws Exception {
        assertEquals("pack.zip", ArchiveDisplayName.read(archive("{\"name\":\"not a pack name\"}"), false));
    }

    @Test void oversizedCompressedModMetadataFallsBackToFilename() throws Exception {
        assertEquals("pack.zip", ArchiveDisplayName.read(archive("{\"name\":\"" + "a".repeat(1024*1024) + "\"}"), true));
    }

    @Test void corruptArchiveStaysManageable() throws Exception {
        Path file = Files.writeString(directory.resolve("broken.jar"), "not a zip");
        assertEquals("broken.jar", ArchiveDisplayName.read(file.toFile(), true));
    }
}
