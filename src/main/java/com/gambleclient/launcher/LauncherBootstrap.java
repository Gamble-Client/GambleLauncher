package com.gambleclient.launcher;

import javax.swing.JOptionPane;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

public final class LauncherBootstrap {
    private static boolean diagnostics;

    private LauncherBootstrap() {
    }

    public static void main(String[] args) {
        diagnostics = Arrays.asList(args).contains("--diagnostics") || Arrays.asList(args).contains("--self-test");
        if (shouldLaunchSwing(System.getProperty("os.name", ""), args)) {
            launchSwing(args);
            return;
        }

        try {
            Class<?> launcher = Class.forName("com.gambleclient.launcher.FxLauncher");
            Method main = launcher.getMethod("main", String[].class);
            main.invoke(null, (Object) args);
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            System.err.println("JavaFX launcher failed, falling back to Swing launcher.");
            if (diagnostics) cause.printStackTrace(System.err);
            showFallbackNotice();
            launchSwing(args);
        }
    }

    static boolean shouldLaunchSwing(String operatingSystem, String[] args) {
        var arguments = Arrays.asList(args);
        if (arguments.contains("--self-test") || arguments.contains("--diagnostics")) return false;
        if (arguments.contains("--swing")) return true;
        if (arguments.contains("--javafx")) return false;
        return String.valueOf(operatingSystem).toLowerCase().contains("linux");
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            return unwrap(invocation.getCause());
        }
        if (error instanceof ExceptionInInitializerError initializer && initializer.getCause() != null) {
            return unwrap(initializer.getCause());
        }
        return error;
    }

    private static void showFallbackNotice() {
        try {
            JOptionPane.showMessageDialog(
                null,
                "The modern launcher UI could not start on this system, so Gamble Client will open the compatibility launcher instead.",
                "Gamble Client Launcher",
                JOptionPane.WARNING_MESSAGE
            );
        } catch (Throwable ignored) {
            // If even Swing dialogs are unavailable, continue to the Swing launcher attempt.
        }
    }

    private static void launchSwing(String[] args) {
        try {
            Main.main(args);
        } catch (Throwable error) {
            if (diagnostics) error.printStackTrace(System.err);
            throw new RuntimeException("Could not start Gamble Client Launcher. Run with --diagnostics for technical details.");
        }
    }
}
