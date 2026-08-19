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

final class PrivateFileSecurityTest {
    @TempDir
    Path temporaryDirectory;

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
