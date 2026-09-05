package com.gambleclient.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
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

    static Path createPrivateTempFile(Path parent, String prefix) throws IOException {
        Files.createDirectories(parent);
        FileAttribute<?> permissions;
        if (Files.getFileAttributeView(parent, PosixFileAttributeView.class) != null) {
            permissions = PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE);
        } else if (Files.getFileAttributeView(parent, AclFileAttributeView.class) != null) {
            var owner = parent.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
            var entries = List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                .setPrincipal(owner).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build());
            permissions = new FileAttribute<List<AclEntry>>() {
                public String name() { return "acl:acl"; }
                public List<AclEntry> value() { return entries; }
            };
        } else {
            throw new IOException("This folder cannot protect account credentials. Choose a local filesystem with file permissions.");
        }
        // Set permissions at creation, before credentials or enrollment bytes
        // exist. chmod after writing leaves a readable first-creation window.
        Path file = Files.createTempFile(parent, prefix, ".part", permissions);
        try {
            // Windows can combine a creation ACL with inheritable parent ACEs.
            // Tighten and read it back while the file is still empty; never
            // accept the best-effort path used for ordinary diagnostic files.
            var acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
            if (acl != null) {
                // Elevated Windows tokens can default new-file ownership to the
                // Administrators group. Private files must belong to this user,
                // not that group, including for later orphan ownership checks.
                var owner = file.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
                if (!acl.getOwner().equals(owner)) acl.setOwner(owner);
                var expected = List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                    .setPrincipal(owner).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build());
                acl.setAcl(expected);
                if (!acl.getOwner().equals(owner) || !acl.getAcl().equals(expected)) {
                    throw new IOException("Could not restrict the private file's permissions.");
                }
            }
            return file;
        } catch (IOException | RuntimeException error) {
            Files.deleteIfExists(file);
            throw error;
        }
    }

    static void writePrivate(Path target, byte[] contents) throws IOException {
        Path staging = createPrivateTempFile(target.toAbsolutePath().getParent(), ".launcher-private-");
        try {
            Files.write(staging, contents, java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.LinkOption.NOFOLLOW_LINKS);
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(staging); }
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
