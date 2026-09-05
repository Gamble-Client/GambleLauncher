package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PrivateFileSecurityTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void temporaryFileIsPrivateBeforeAnySecretIsWritten() throws Exception {
        Path file = PrivateFileSecurity.createPrivateTempFile(temporaryDirectory, ".test-private-");
        assertEquals(0, Files.size(file));
        assertPrivate(file);
    }

    @Test
    void replacingCredentialsLeavesOnlyPrivateNewContents() throws Exception {
        Path file = Files.writeString(temporaryDirectory.resolve("account.json"), "old sentinel");
        PrivateFileSecurity.writePrivate(file, "new sentinel".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("new sentinel", Files.readString(file));
        assertPrivate(file);
        try (var files = Files.list(temporaryDirectory)) { assertEquals(1, files.count()); }
    }

    @Test
    void failedReplacementCleansStagingAndPreservesDestination() throws Exception {
        Path destination = Files.createDirectory(temporaryDirectory.resolve("occupied"));
        Files.writeString(destination.resolve("keep"), "preserved");
        assertThrows(java.io.IOException.class,
            () -> PrivateFileSecurity.writePrivate(destination, new byte[] {1}));
        assertEquals("preserved", Files.readString(destination.resolve("keep")));
        try (var files = Files.list(temporaryDirectory)) { assertEquals(1, files.count()); }
    }

    @Test
    void replacementDoesNotFollowExistingPosixSymlink() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(temporaryDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path other = Files.writeString(temporaryDirectory.resolve("other"), "untouched");
        Path file = Files.createSymbolicLink(temporaryDirectory.resolve("account"), other);
        PrivateFileSecurity.writePrivate(file, new byte[] {42});
        assertEquals("untouched", Files.readString(other));
        assertFalse(Files.isSymbolicLink(file));
        assertPrivate(file);
    }

    private static void assertPrivate(Path file) throws Exception {
        if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(file));
        } else {
            var acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
            assertEquals(1, acl.getAcl().size());
            assertEquals(acl.getOwner(), acl.getAcl().getFirst().principal());
        }
    }

    @Test
    void privateFilesRemainUsableAfterPlatformHardening() throws Exception {
        Path file = Files.writeString(temporaryDirectory.resolve("launcher-state.txt"), "private");

        PrivateFileSecurity.Mode mode = PrivateFileSecurity.harden(file);

        assertEquals("private", Files.readString(file));
        assertTrue(Files.isWritable(file));
        assertNotEquals(PrivateFileSecurity.Mode.BEST_EFFORT, mode,
            "The default OS filesystem should support owner-only permissions");

        if (mode == PrivateFileSecurity.Mode.POSIX) {
            assertEquals(Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            ), Files.getPosixFilePermissions(file));
        } else {
            AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
            assertEquals(1, acl.getAcl().size());
            assertEquals(acl.getOwner(), acl.getAcl().getFirst().principal());
        }
    }
}
