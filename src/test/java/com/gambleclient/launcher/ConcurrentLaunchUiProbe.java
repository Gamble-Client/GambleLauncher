package com.gambleclient.launcher;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit local display smoke run, not part of headless CI. No account/network/game access. */
public final class ConcurrentLaunchUiProbe {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("gamble-java-multi-ui-");
        System.setProperty("user.home", root.toString());
        System.setProperty("gamble.gameDir", root.resolve("minecraft").toString());
        AtomicReference<Main> backend = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                Main main = new Main();
                backend.set(main);
                set(main, "launcherToken", "fixture-only");
                set(main, "launcherUser", call(main, "parseLauncherUser", new Class<?>[]{Object.class},
                    Map.of("ownerAccess", true, "accessStatus", "owned")));
                JPanel actions = (JPanel) call(main, "createActionsPanel", new Class<?>[0]);
                MinecraftChildren children = (MinecraftChildren) get(main, "minecraftChildren");
                // The UI fixture tracks this live probe, but must never stop it.
                Process dummy = new UiProcess();
                var a = children.add(dummy, root.toFile(), root.resolve("a.log").toFile());
                var b = children.add(dummy, root.toFile(), root.resolve("b.log").toFile());
                call(main, "setBusy", new Class<?>[]{boolean.class}, false);
                JButton another = (JButton) get(main, "launchAnotherButton");
                JButton play = (JButton) get(main, "launchButton");
                check(another.isVisible() && another.isEnabled(), "Owner sees Launch another");
                check("Stop all (2)".equals(play.getText()), "Stop all includes count");
                for (int width : new int[]{650, 740, 960}) {
                    actions.setSize(width, 58);
                    layout(actions);
                    check(another.getBounds().width >= another.getPreferredSize().width, "No clipped Swing secondary button");
                    BufferedImage image = new BufferedImage(width, 58, BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics();
                    actions.printAll(graphics);
                    graphics.dispose();
                    ImageIO.write(image, "png", root.resolve("swing-actions-" + width + ".png").toFile());
                }
                for (String role : new String[]{"media", "beta_plus", "ad_tier", "owner"}) {
                    set(main, "launcherUser", call(main, "parseLauncherUser", new Class<?>[]{Object.class},
                        Map.of("selectedPlan", role, "accessStatus", role, "ownerAccess", "true")));
                    call(main, "setBusy", new Class<?>[]{boolean.class}, false);
                    check(!another.isVisible(), "Label/string boolean cannot show secondary launch");
                }
                set(main, "launcherUser", call(main, "parseLauncherUser", new Class<?>[]{Object.class},
                    Map.of("devAccess", true, "accessStatus", "owned")));
                call(main, "setBusy", new Class<?>[]{boolean.class}, false);
                children.remove(a);
                children.remove(b);
                set(main, "launchPreparing", true);
                call(main, "switchLauncherAccount", new Class<?>[0]);
                check("fixture-only".equals(get(main, "launcherToken")), "Account cannot change during pending launch");
                set(main, "launchPreparing", false);
                // Keep the displayed Stop label stale to reproduce the exit/click race.
                call(main, "launch", new Class<?>[0]);
                check(!(boolean) get(main, "launchPreparing"), "Stale Stop never prepares a launch");
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                Main main = backend.get();
                FxMain fx = new FxMain();
                set(fx, "backend", main);
                BorderPane pane = (BorderPane) call(fx, "mainPane", new Class<?>[0]);
                pane.getStyleClass().add("app");
                // Deliberately use real reflected bridge fields with fixture control states.
                JButton another = (JButton) get(main, "launchAnotherButton");
                another.setVisible(true);
                another.setEnabled(true);
                ((JButton) get(main, "launchButton")).setText("Stop all (2)");
                call(fx, "syncLaunchButtons", new Class<?>[0]);
                Button extra = (Button) get(fx, "launchAnotherButton");
                check(extra.isVisible() && extra.isManaged() && !extra.isDisabled(), "FX mirrors secondary control");
                Scene scene = new Scene(pane, 740, 650);
                scene.getStylesheets().add(FxMain.class.getResource("/launcher-fx.css").toExternalForm());
                for (int width : new int[]{650, 740, 960}) {
                    pane.resize(width, 650);
                    pane.applyCss();
                    pane.layout();
                    check(extra.getWidth() >= extra.minWidth(-1), "FX button isn't clipped");
                    WritableImage shot = pane.snapshot(null, null);
                    BufferedImage image = new BufferedImage((int) shot.getWidth(), (int) shot.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, shot.getPixelReader().getArgb(x, y));
                    ImageIO.write(image, "png", root.resolve("fx-actions-" + width + ".png").toFile());
                }
                another.setVisible(false);
                call(fx, "syncLaunchButtons", new Class<?>[0]);
                check(!extra.isVisible() && !extra.isManaged(), "FX hides unprivileged action without leaving a gap");
                Button displayedStop = (Button) get(fx, "launchButton");
                displayedStop.setDisable(false);
                displayedStop.setText("Stop all (2)");
                ((JButton) get(main, "launchButton")).setText("Play");
                displayedStop.fire();
                check(!(boolean) get(main, "launchPreparing"), "Stale FX Stop cannot launch after backend switched to Play");
            } catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        });
        check(done.await(30, TimeUnit.SECONDS), "FX render deadline");
        SwingUtilities.invokeAndWait(() -> {
            try { ((JFrame) get(backend.get(), "frame")).dispose(); }
            catch (Exception error) { failure.set(error); }
        });
        Platform.exit();
        if (failure.get() != null) throw new AssertionError(failure.get());
        System.out.println("PASS Swing/FX role visibility, sizes, Stop race; captures: " + root);
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static void layout(java.awt.Container container) {
        container.doLayout();
        for (var child : container.getComponents()) if (child instanceof java.awt.Container nested) layout(nested);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static final class UiProcess extends Process {
        public java.io.OutputStream getOutputStream() { return java.io.OutputStream.nullOutputStream(); }
        public java.io.InputStream getInputStream() { return java.io.InputStream.nullInputStream(); }
        public java.io.InputStream getErrorStream() { return java.io.InputStream.nullInputStream(); }
        public int waitFor() { return 0; }
        public int exitValue() { throw new IllegalThreadStateException(); }
        public boolean isAlive() { return true; }
        public void destroy() { throw new AssertionError("UI fixture must not be stopped"); }
    }
}
