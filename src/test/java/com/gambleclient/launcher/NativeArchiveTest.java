package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

final class NativeArchiveTest {
    @TempDir Path directory;

    private Path zip(String[] names, byte[][] bytes) throws IOException {
        Path archive = directory.resolve("natives.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (int i = 0; i < names.length; i++) {
                output.putNextEntry(new ZipEntry(names[i]));
                output.write(bytes[i]);
                output.closeEntry();
            }
        }
        return archive;
    }

    @Test void extractsNestedNativesAtExactExpandedBudget() throws Exception {
        Path archive = zip(new String[]{"windows/native.dll", "linux/lib.so"}, new byte[][]{{1,2},{3,4}});
        Path target = directory.resolve("target");
        NativeArchive.extract(archive, target, 4, 2);
        assertArrayEquals(new byte[]{1,2}, Files.readAllBytes(target.resolve("native.dll")));
        try (var files = Files.list(target)) { assertEquals(2, files.count()); }
    }

    @Test void oversizedExpansionPreservesExistingNativesAndCleansStaging() throws Exception {
        Path target = Files.createDirectory(directory.resolve("target"));
        Files.writeString(target.resolve("native.dll"), "original");
        Path archive = zip(new String[]{"native.dll", "large.so"}, new byte[][]{{1}, new byte[1024*1024]});
        assertThrows(IOException.class, () -> NativeArchive.extract(archive, target, 32, 2));
        assertEquals("original", Files.readString(target.resolve("native.dll")));
        try (var files = Files.list(target)) { assertEquals(1, files.count()); }
    }

    @Test void rejectsExcessiveEntriesBeforeWriting() throws Exception {
        Path archive = zip(new String[]{"a", "b"}, new byte[][]{{1},{2}});
        Path target = directory.resolve("target");
        assertThrows(IOException.class, () -> NativeArchive.extract(archive, target, 32, 1));
        try (var files = Files.list(target)) { assertEquals(0, files.count()); }
    }

    @Test void enforcesActualBudgetEvenWhenCentralDirectoryLies() throws Exception {
        Path archive = zip(new String[]{"native.dll"}, new byte[][]{new byte[1024*1024]});
        byte[] bytes = Files.readAllBytes(archive);
        for (int i = 0; i + 28 < bytes.length; i++) {
            if (bytes[i] == 0x50 && bytes[i+1] == 0x4b && bytes[i+2] == 1 && bytes[i+3] == 2) {
                java.nio.ByteBuffer.wrap(bytes, i+24, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(1);
                break;
            }
        }
        Files.write(archive, bytes);
        Path target = directory.resolve("target");
        assertThrows(IOException.class, () -> NativeArchive.extract(archive, target, 32, 1));
        try (var files = Files.list(target)) { assertEquals(0, files.count()); }
    }

    @Test void corruptChecksumCannotReplaceExistingNative() throws Exception {
        Path archive = zip(new String[]{"native.dll"}, new byte[][]{{1,2,3}});
        byte[] bytes = Files.readAllBytes(archive);
        for (int i = 0; i + 20 < bytes.length; i++) {
            if (bytes[i] == 0x50 && bytes[i+1] == 0x4b && bytes[i+2] == 1 && bytes[i+3] == 2) {
                bytes[i+16] ^= 1;
                break;
            }
        }
        Files.write(archive, bytes);
        Path target = Files.createDirectory(directory.resolve("target"));
        Files.writeString(target.resolve("native.dll"), "original");
        assertThrows(IOException.class, () -> NativeArchive.extract(archive, target, 32, 1));
        assertEquals("original", Files.readString(target.resolve("native.dll")));
    }

    @Test void rejectsFlattenedCaseInsensitiveCollisionsWithoutPublishing() throws Exception {
        Path archive = zip(new String[]{"x/a.dll", "y/A.dll"}, new byte[][]{{1},{2}});
        Path target = directory.resolve("target");
        assertThrows(IOException.class, () -> NativeArchive.extract(archive, target, 32, 2));
        try (var files = Files.list(target)) { assertEquals(0, files.count()); }
    }

    @Test void skipsMetadataAndTraversalWithoutInflatingThem() throws Exception {
        Path archive = zip(new String[]{"META-INF/large", "../escape", "x/native.dll"},
            new byte[][]{new byte[1024*1024], {2}, {3}});
        Path target = directory.resolve("target");
        NativeArchive.extract(archive, target, 1, 3);
        assertFalse(Files.exists(directory.resolve("escape")));
        assertArrayEquals(new byte[]{3}, Files.readAllBytes(target.resolve("native.dll")));
    }
}
