package com.gambleclient.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

final class PrivateFileSecurity {
    enum Mode { POSIX, WINDOWS_ACL, BEST_EFFORT }

    private static final Set<PosixFilePermission> OWNER_READ_WRITE = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE
    );
    private static final AtomicBoolean FALLBACK_WARNING_REPORTED = new AtomicBoolean();

    private PrivateFileSecurity() {
    }

    static Mode harden(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return Mode.BEST_EFFORT;

        PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) {
            posix.setPermissions(OWNER_READ_WRITE);
            return Mode.POSIX;
        }

        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl != null) {
            try {
                AclEntry ownerOnly = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(acl.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .build();
                acl.setAcl(List.of(ownerOnly));
                return Mode.WINDOWS_ACL;
            } catch (UnsupportedOperationException | IOException | SecurityException ignored) {
                // Removable and network-backed Windows filesystems may expose an ACL
                // view without permitting ACL replacement. Fall through to a
                // non-fatal best-effort mode so an optional hardening step cannot
                // prevent Minecraft from launching.
            }
        }

        java.io.File file = path.toFile();
        file.setExecutable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
        if (FALLBACK_WARNING_REPORTED.compareAndSet(false, true)) {
            System.err.println("Launcher warning: owner-only file permissions are unavailable on this filesystem.");
        }
        return Mode.BEST_EFFORT;
    }
}
