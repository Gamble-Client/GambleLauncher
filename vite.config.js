import { defineConfig } from "vite";

export default defineConfig({
  server: {
    watch: {
      // Flatpak-builder creates OSTree and /var/run symlink forests inside
      // these generated folders. Watching them can hit ELOOP and kill the
      // launcher preview server after a local package build.
      ignored: [
        "**/.flatpak-builder/**",
        "**/flatpak-build/**",
        "**/flatpak-repo/**",
        "**/build/**",
        "**/src-tauri/target/**"
      ]
    }
  }
});
