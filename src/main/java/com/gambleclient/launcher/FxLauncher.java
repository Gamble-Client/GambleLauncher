package com.gambleclient.launcher;

import javafx.application.Application;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FxLauncher {
    private FxLauncher() {
    }

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--self-test") || Arrays.asList(args).contains("--diagnostics")) {
            runSelfTest();
            return;
        }

        relaunchOnX11IfNeeded(args);
        Logger.getLogger("com.sun.javafx.application.PlatformImpl").setLevel(Level.SEVERE);
        Logger.getLogger("javafx").setLevel(Level.SEVERE);
        Application.launch(FxMain.class, args);
    }

    private static void relaunchOnX11IfNeeded(String[] args) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("linux")) return;
        Map<String, String> env = System.getenv();
        if ("1".equals(env.get("GAMBLE_LAUNCHER_X11"))) return;
        if (blank(env.get("WAYLAND_DISPLAY")) || blank(env.get("DISPLAY"))) return;
        if (env.getOrDefault("GDK_BACKEND", "").toLowerCase().contains("x11")) return;

        try {
            File self = new File(FxLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!self.isFile() || !self.getName().toLowerCase().endsWith(".jar")) return;

            String java = new File(new File(System.getProperty("java.home"), "bin"), "java").getAbsolutePath();
            List<String> command = new ArrayList<>();
            command.add(java);
            command.add("--enable-native-access=ALL-UNNAMED");
            command.add("-Dglass.platform=gtk");
            command.add("-jar");
            command.add(self.getAbsolutePath());
            command.addAll(Arrays.asList(args));

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.inheritIO();
            builder.environment().put("GAMBLE_LAUNCHER_X11", "1");
            builder.environment().put("GDK_BACKEND", "x11");
            builder.environment().remove("WAYLAND_DISPLAY");
            System.exit(builder.start().waitFor());
        } catch (Exception ignored) {
            // Continue normally if the compatibility relaunch is not available.
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void runSelfTest() {
        println("Gamble Client Launcher self-test");
        check("Java runtime", Runtime.version().feature() >= 21, "Java " + Runtime.version());
        check("Operating system", true, System.getProperty("os.name") + " " + System.getProperty("os.arch"));

        File root = managedMinecraftRoot();
        File data = new File(root, "cg-mod");
        check("Managed Minecraft root", root.isDirectory(), root.getAbsolutePath());
        check("Launcher data folder", data.isDirectory(), data.getAbsolutePath());
        check("Launcher account session", new File(data, "launcher-session.txt").isFile(), "launcher-session.txt");
        check("Microsoft account cache", new File(data, "microsoft-account.json").isFile(), "microsoft-account.json");

        File profiles = new File(root, "profiles");
        check("Profiles folder", profiles.isDirectory(), profiles.getAbsolutePath());
        if (profiles.isDirectory()) {
            File[] children = profiles.listFiles(File::isDirectory);
            int count = children == null ? 0 : children.length;
            check("Profile count", count > 0, String.valueOf(count));
            if (children != null) {
                for (File profile : children) {
                    check("Profile " + profile.getName() + " mods", new File(profile, "mods").isDirectory(), new File(profile, "mods").getAbsolutePath());
                    check("Profile " + profile.getName() + " versions", new File(profile, "versions").isDirectory(), new File(profile, "versions").getAbsolutePath());
                    check("Profile " + profile.getName() + " libraries", new File(profile, "libraries").isDirectory(), new File(profile, "libraries").getAbsolutePath());
                    check("Profile " + profile.getName() + " assets", new File(profile, "assets/indexes").isDirectory(), new File(profile, "assets/indexes").getAbsolutePath());
                }
            }
        }

        http("Launcher release metadata", "https://gamble-client.store/api/launcher/version");
        http("Mojang version manifest", "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json");
        http("Fabric loader metadata", "https://meta.fabricmc.net/v2/versions/loader/1.21.11/0.19.3/profile/json");
        http("Minecraft asset CDN", "https://resources.download.minecraft.net/", true);

        File latestLaunch = new File(data, "latest-launch.log");
        check("Latest launch log", latestLaunch.isFile(), latestLaunch.getAbsolutePath());
        if (latestLaunch.isFile()) {
            try {
                long lines = Files.lines(latestLaunch.toPath(), StandardCharsets.UTF_8).count();
                check("Latest launch log readable", true, lines + " lines");
            } catch (IOException e) {
                check("Latest launch log readable", false, e.getMessage());
            }
        }
    }

    private static void http(String label, String url) {
        http(label, url, false);
    }

    private static void http(String label, String url, boolean hostReachabilityOnly) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "GambleClientLauncher/SelfTest");
            int status = connection.getResponseCode();
            boolean ok = hostReachabilityOnly ? status < 500 : status >= 200 && status < 400;
            check(label, ok, "HTTP " + status);
        } catch (Exception e) {
            check(label, false, e.getMessage());
        }
    }

    private static void check(String label, boolean ok, String detail) {
        println((ok ? "OK   " : "WARN ") + label + " - " + detail);
    }

    private static void println(String value) {
        System.out.println(value);
    }

    private static File managedMinecraftRoot() {
        String configured = System.getProperty("gamble.gameDir", "").trim();
        if (configured.isEmpty()) configured = System.getenv("GAMBLE_CLIENT_GAME_DIR");
        if (configured != null && !configured.trim().isEmpty()) return new File(configured.trim());
        return new File(appDataFolder(), "minecraft");
    }

    private static File appDataFolder() {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return new File(appData != null && !appData.isEmpty() ? appData : userHome, "Gamble Client");
        }
        if (os.contains("mac")) return new File(userHome, "Library/Application Support/Gamble Client");

        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        File dataHome = xdgDataHome != null && !xdgDataHome.trim().isEmpty()
            ? new File(xdgDataHome.trim())
            : new File(userHome, ".local/share");
        return new File(dataHome, "gamble-client");
    }
}
