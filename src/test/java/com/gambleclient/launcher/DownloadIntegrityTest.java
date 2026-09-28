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

    /** Real published files: Mojang's SHA-1 names and Modrinth's SHA-512 match what the class computes. */
    @Test
    void realMojangAndModrinthFilesVerify() throws Exception {
        java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
        DownloadIntegrity.Downloader fetch = (url, target) -> {
            try {
                var response = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofFile(target.toPath()));
                if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        };
        DownloadIntegrity integrity = new DownloadIntegrity(null);
        String asset = "5ff04807c356f1beed0b86ccf659b44b9983e3fa";
        File assetFile = temp.resolve(asset).toFile();
        integrity.ensureFile("https://resources.download.minecraft.net/5f/" + asset, assetFile,
            DownloadIntegrity.Expected.of("SHA-1", asset, 781), "asset", fetch);
        assertEquals(781, assetFile.length());

        String versions = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%221.21.11%22%5D&loaders=%5B%22fabric%22%5D")).build(),
            java.net.http.HttpResponse.BodyHandlers.ofString()).body();
        var matcher = java.util.regex.Pattern.compile(
            "\\{[^{}]*\"sha512\":\"([0-9a-f]{128})\"[^{}]*\\},\"url\":\"([^\"]+)\",\"filename\":\"([^\"]+)\"[^{}]*\"primary\":true")
            .matcher(versions);
        assertTrue(matcher.find(), "Modrinth listing parsed");
        File fabric = temp.resolve(matcher.group(3)).toFile();
        integrity.ensureFile(matcher.group(2), fabric, DownloadIntegrity.Expected.of("SHA-512", matcher.group(1), 0), "Fabric API", fetch);
        assertTrue(fabric.length() > 100_000);
    }

    @Test
    void oldLoadersAreRefused() throws IOException {
        assertThrows(IOException.class, () -> Main.requireSupportedLoader("1.4.28", "1.4.29"));
        Main.requireSupportedLoader("1.4.29", "1.4.29");
        Main.requireSupportedLoader("1.4.30", "1.4.29");
    }
}
