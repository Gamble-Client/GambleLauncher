package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadIntegrityTest {
    @TempDir Path temp;

    private static String sha1(byte[] bytes) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-1").digest(bytes)) hex.append(String.format("%02x", value));
        return hex.toString();
    }

    @Test
    void expectedRejectsMalformedDigests() {
        assertNull(DownloadIntegrity.Expected.of("SHA-1", "abc", 0));
        assertNull(DownloadIntegrity.Expected.of("SHA-1", "z".repeat(40), 0));
        assertNull(DownloadIntegrity.Expected.of("MD5", "a".repeat(32), 0));
        assertEquals("a".repeat(128), DownloadIntegrity.Expected.of("SHA-512", "A".repeat(128), 0).digest);
    }

    @Test
    void keepsMatchingFilesAndReplacesTamperedOnes() throws Exception {
        byte[] good = "genuine library bytes".getBytes(StandardCharsets.UTF_8);
        File file = temp.resolve("lib.jar").toFile();
        DownloadIntegrity integrity = new DownloadIntegrity(temp.resolve("cache.tsv").toFile());
        DownloadIntegrity.Expected expected = DownloadIntegrity.Expected.of("SHA-1", sha1(good), good.length);
        AtomicInteger downloads = new AtomicInteger();
        DownloadIntegrity.Downloader fetch = (url, target) -> {
            downloads.incrementAndGet();
            Files.write(target.toPath(), good);
        };
        integrity.ensureFile("https://example.test/lib.jar", file, expected, "lib", fetch);
        integrity.ensureFile("https://example.test/lib.jar", file, expected, "lib", fetch);
        assertEquals(1, downloads.get(), "a verified file is kept");
        Files.writeString(file.toPath(), "tampered library bytes");
        integrity.ensureFile("https://example.test/lib.jar", file, expected, "lib", fetch);
        assertEquals(2, downloads.get(), "a changed file is fetched again");
        assertArrayEquals(good, Files.readAllBytes(file.toPath()));
    }

    @Test
    void aBadDownloadIsDeletedAndFails() throws Exception {
        File file = temp.resolve("asset").toFile();
        DownloadIntegrity integrity = new DownloadIntegrity(null);
        DownloadIntegrity.Expected expected = DownloadIntegrity.Expected.of("SHA-1", sha1("expected".getBytes()), 0);
        IOException error = assertThrows(IOException.class, () -> integrity.ensureFile("https://example.test/a", file, expected, "asset",
            (url, target) -> Files.writeString(target.toPath(), "something else")));
        assertTrue(error.getMessage().contains("did not match"), error.getMessage());
        assertFalse(file.exists());
    }

    @Test
    void cacheSurvivesReloadAndNoticesChanges() throws Exception {
        File store = temp.resolve("cache.tsv").toFile();
        File file = temp.resolve("file").toFile();
        Files.writeString(file.toPath(), "cached bytes");
        DownloadIntegrity.Expected expected = DownloadIntegrity.Expected.of("SHA-1", sha1("cached bytes".getBytes()), 0);
        DownloadIntegrity first = new DownloadIntegrity(store);
        assertTrue(first.matches(file, expected));
        first.save();
        DownloadIntegrity second = new DownloadIntegrity(store);
        assertTrue(second.vouched(file, expected));
        assertTrue(file.setLastModified(file.lastModified() + 5000));
        assertFalse(second.vouched(file, expected), "a changed timestamp means hashing again");
    }

    @Test
    void metadataPathsStayInsideTheirFolder() throws IOException {
        assertEquals("org/lwjgl/lwjgl.jar", DownloadIntegrity.safeRelativePath("org/lwjgl/lwjgl.jar"));
        for (String bad : List.of("../evil.jar", "org/../../evil.jar", "/etc/passwd", "", "C:\\evil.jar", "a//b.jar", "./a.jar")) {
            assertThrows(IOException.class, () -> DownloadIntegrity.safeRelativePath(bad), bad);
        }
    }

    @Test
    void credentialsNeverGoToTheFallbackOrigin() throws IOException {
        List<String> urls = Main.firstPartyApiUrls("https://gambleclient.org/api/standalone/loader");
        assertEquals(3, urls.size());
        List<String> safe = Main.credentialSafeUrls(urls, true);
        assertEquals(List.of("https://gambleclient.org/api/standalone/loader", "https://dash.gambleclient.org/api/standalone/loader"), safe);
        assertEquals(urls, Main.credentialSafeUrls(urls, false));
        assertTrue(Main.urlCarriesCredentials("https://gambleclient.org/api/download/x.jar?token=abc"));
        assertTrue(Main.urlCarriesCredentials("https://gambleclient.org/api/launcher/poll?code=abc"));
        assertFalse(Main.urlCarriesCredentials("https://gambleclient.org/api/launcher/version"));
    }

    @Test
    void oldLoadersAreRefused() throws IOException {
        assertThrows(IOException.class, () -> Main.requireSupportedLoader("1.4.28", "1.4.29"));
        Main.requireSupportedLoader("1.4.29", "1.4.29");
        Main.requireSupportedLoader("1.4.30", "1.4.29");
    }
}
