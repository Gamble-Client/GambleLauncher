package com.gambleclient.launcher;

import javax.swing.JOptionPane;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

public final class LauncherBootstrap {
    private LauncherBootstrap() {
    }

    public static void main(String[] args) {
        if (Arrays.asList(args).contains("--swing")) {
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
            cause.printStackTrace(System.err);
            showFallbackNotice(cause);
            launchSwing(args);
        }
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

    private static void showFallbackNotice(Throwable cause) {
        try {
            JOptionPane.showMessageDialog(
                null,
                "The modern launcher UI could not start on this system, so Gamble Client will open the compatibility launcher instead.\n\n"
                    + rootMessage(cause),
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
            throw new RuntimeException("Could not start Gamble Client Launcher.", error);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
