package com.gambleclient.launcher;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Desktop;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.io.IOException;

public class FxMain extends Application {
    private static final String[] PROFILE_LABELS = {"With Gamble Client", "Vanilla", "Fabric"};
    private static final String[] BUILD_LABELS = {"Release", "Beta++", "Media", "Ad Tier"};
    private static final String[] MEMORY_LABELS = {"2", "3", "4", "5", "6", "7", "8", "10", "12", "16"};
    private static final String[] SLOT_SYMBOLS = {"🍒", "💎", "7", "🔔", "CG", "💰"};
    private static final String MINECRAFT_DISCLAIMER = "NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.";
    private Main backend;
    private Stage stage;
    private StackPane appRoot;
    private StackPane contentHost;
    private BorderPane launchPane;
    private VBox logLines;
    private ScrollPane logScroll;
    private ProgressBar progress;
    private Label progressText;
    private Label accountNameLabel;
    private Label accountStatusLabel;
    private Label launcherInstalled;
    private Label launcherReleased;
    private Label clientInstalled;
    private Label clientReleased;
    private Label brandTitle;
    private ComboBox<String> profileBox;
    private ComboBox<String> buildBox;
    private ComboBox<String> memoryBox;
    private ComboBox<String> graphicsMode;
    private TextField username;
    private VBox usernameBox;
    private Label buildLockedLabel;
    private TextField javaArgs;
    private TextField gpuSelector;
    private Button launchButton;
    private Button updateButton;
    private Button signInButton;
    private Button antiScreenshareButton;
    private Button autoCheck;
    private VBox sidebarAd;
    private Label sidebarAdCopy;
    private Button sidebarAdButton;
    private Timeline hudInfoRefresh;
    private MediaPlayer sponsorMediaPlayer;
    private WebView sponsorWebView;
    private String lastLog = "";
    private boolean syncingFromBackend;
    private boolean slotSoundsEnabled = true;
    private boolean slotWinSoundsEnabled = true;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        backend = new Main();
        runSwing(() -> {
            call("loadDisplayNames");
            call("createRoot");
        });
        slotSoundsEnabled = backendBoolean("readSlotSoundsEnabled");
        slotWinSoundsEnabled = backendBoolean("readSlotWinSoundsEnabled");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app");
        root.setLeft(sidebar());
        contentHost = new StackPane();
        contentHost.getStyleClass().add("content-host");
        launchPane = mainPane();
        contentHost.getChildren().setAll(launchPane);
        root.setCenter(contentHost);

        appRoot = new StackPane(root);
        Scene scene = new Scene(appRoot, 1180, 760);
        scene.getStylesheets().add(getClass().getResource("/launcher-fx.css").toExternalForm());
        stage.setTitle(swingText("launcherDisplayName"));
        Image icon = resourceImage("/assets/cg-mod-icon.png");
        if (icon != null) stage.getIcons().add(icon);
        stage.setMinWidth(1080);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.show();

        syncControlSelectionsFromBackend();
        syncToBackend();
        showLaunchScreen();
        Timeline poll = new Timeline(new KeyFrame(Duration.millis(300), event -> syncFromBackend()));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();
    }

    private VBox sidebar() {
        VBox side = new VBox(14);
        side.getStyleClass().add("sidebar");
        side.setPrefWidth(315);

        brandTitle = new Label(swingText("clientDisplayName"));
        brandTitle.getStyleClass().add("title");

        launcherInstalled = chipLine("Installed: " + backendString("launcherVersion"));
        launcherReleased = chipLine("Released: checking...");
        clientInstalled = chipLine("Installed: none");
        clientReleased = chipLine("Released: sign in to check");

        antiScreenshareButton = actionButton("AntiScreenshare", this::openAntiScreenshare);

        side.getChildren().addAll(
            brandTitle,
            versionChip("Launcher", launcherInstalled, launcherReleased),
            versionChip("Client", clientInstalled, clientReleased),
            slotMachine(),
            sidebarAdBox(),
            spacer(),
            antiScreenshareButton,
            actionButton("Mods", this::openModManager),
            actionButton("Resource Packs", this::openResourcePackManager)
        );
        return side;
    }

    private BorderPane mainPane() {
        BorderPane pane = new BorderPane();
        pane.getStyleClass().add("content");

        VBox top = new VBox(14);
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label heading = new Label("Launch Setup");
        heading.getStyleClass().add("heading");
        Region push = spacer();
        VBox account = new VBox(2);
        accountNameLabel = label("Not signed in", "account-name");
        accountStatusLabel = label("Launcher account required", "muted");
        account.getChildren().addAll(accountNameLabel, accountStatusLabel);
        signInButton = secondary("Sign In");
        signInButton.setOnAction(e -> runAccountButtonAction());
        Button settings = secondary("Settings");
        settings.setOnAction(e -> openSettings());
        header.getChildren().addAll(heading, push, account, signInButton, settings);
        HBox.setHgrow(push, Priority.ALWAYS);

        GridPane form = new GridPane();
        form.getStyleClass().add("card");
        form.setHgap(12);
        form.setVgap(10);

        profileBox = combo(PROFILE_LABELS);
        buildBox = combo(BUILD_LABELS);
        memoryBox = combo(MEMORY_LABELS);
        memoryBox.getSelectionModel().select("4");
        graphicsMode = combo(new String[] {"Automatic", "Safe graphics", "Software fallback"});
        gpuSelector = new TextField();
        gpuSelector.setPromptText("Optional DRI_PRIME selector");
        username = new TextField(defaultUsername());
        javaArgs = new TextField();
        javaArgs.setPromptText("Optional JVM arguments");

        buildBox.setCellFactory(view -> new BuildAccessCell());
        buildBox.setButtonCell(new BuildAccessCell());

        addField(form, "Profile", profileBox, 0, 0);
        VBox buildField = addField(form, "Build", buildBox, 1, 0);
        buildLockedLabel = label("Ad Tier", "selected-build-label");
        buildLockedLabel.setManaged(false);
        buildLockedLabel.setVisible(false);
        buildField.getChildren().add(buildLockedLabel);
        usernameBox = addField(form, "Username", username, 0, 1, 2);

        progress = new ProgressBar(0);
        progress.setMaxWidth(Double.MAX_VALUE);
        progressText = new Label("Idle");
        progressText.getStyleClass().add("muted");
        VBox progressBox = new VBox(6, progressText, progress);
        progressBox.getStyleClass().add("progress-box");

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        updateButton = secondary("Update");
        updateButton.setOnAction(e -> runBackend("installSelectedBuild", false));
        Button accounts = secondary("Accounts");
        accounts.setOnAction(e -> openAccountManager());
        launchButton = primary("Play");
        launchButton.setOnAction(e -> {
            syncToBackend();
            runBackend("launch");
        });
        actions.getChildren().addAll(updateButton, accounts, launchButton);

        top.getChildren().addAll(header, form, progressBox, actions);
        pane.setTop(top);

        VBox logBox = new VBox(8);
        logBox.getStyleClass().add("log-card");
        HBox logHead = new HBox(8);
        logHead.setAlignment(Pos.CENTER_LEFT);
        Label logTitle = new Label("Game Log");
        logTitle.getStyleClass().add("section-title");
        Region logPush = spacer();
        Button copy = secondary("Copy");
        copy.setOnAction(e -> runBackend("copyLauncherLog"));
        logHead.getChildren().addAll(logTitle, logPush, copy);
        HBox.setHgrow(logPush, Priority.ALWAYS);
        logLines = new VBox(3);
        logLines.getStyleClass().add("log-lines");
        logScroll = new ScrollPane(logLines);
        logScroll.setFitToWidth(true);
        logScroll.getStyleClass().add("log-scroll");
        VBox.setVgrow(logScroll, Priority.ALWAYS);
        logBox.getChildren().addAll(logHead, logScroll);
        BorderPane.setMargin(logBox, new Insets(18, 0, 0, 0));
        pane.setCenter(logBox);

        profileBox.setOnAction(e -> {
            syncToBackend();
            syncProfileControls();
        });
        buildBox.setOnAction(e -> syncToBackend());
        memoryBox.setOnAction(e -> syncToBackend());
        username.textProperty().addListener((obs, old, value) -> syncToBackend());
        javaArgs.textProperty().addListener((obs, old, value) -> syncToBackend());
        return pane;
    }

    private void openSettings() {
        VBox body = new VBox(14);
        body.getStyleClass().addAll("content", "screen");
        HBox header = screenHeader("Launcher Settings");
        boolean[] autoEnabled = { swingCheckBoxSelected("autoCheckUpdates") };
        boolean[] slotSounds = { backendBoolean("readSlotSoundsEnabled") };
        boolean[] slotWinSounds = { backendBoolean("readSlotWinSoundsEnabled") };
        autoCheck = stateToggle("Auto updates", autoEnabled[0]);
        autoCheck.setOnAction(e -> {
            autoEnabled[0] = !autoEnabled[0];
            setStateToggle(autoCheck, autoEnabled[0]);
            runBackend("saveAutoCheckUpdates", autoEnabled[0]);
        });
        Button slotSoundToggle = stateToggle("Slot sounds", slotSounds[0]);
        slotSoundToggle.setOnAction(e -> {
            slotSounds[0] = !slotSounds[0];
            slotSoundsEnabled = slotSounds[0];
            setStateToggle(slotSoundToggle, slotSounds[0]);
            runBackend("saveSlotSoundsEnabled", slotSounds[0]);
        });
        Button slotWinSoundToggle = stateToggle("Win sounds", slotWinSounds[0]);
        slotWinSoundToggle.setOnAction(e -> {
            slotWinSounds[0] = !slotWinSounds[0];
            slotWinSoundsEnabled = slotWinSounds[0];
            setStateToggle(slotWinSoundToggle, slotWinSounds[0]);
            runBackend("saveSlotWinSoundsEnabled", slotWinSounds[0]);
        });
        Button folder = secondary("Game Folder");
        folder.setOnAction(e -> openBackendFile("getMinecraftFolder"));
        Button review = secondary("Review");
        review.setOnAction(e -> openUrl("https://gambleclient.org/dashboard.html?section=community&tab=reviews"));
        Button website = secondary("Website");
        website.setOnAction(e -> openUrl("https://gambleclient.org"));
        Button credits = secondary("Credits");
        credits.setOnAction(e -> openUrl("https://gambleclient.org/credits"));

        TextField launcherName = new TextField(swingText("launcherDisplayName"));
        TextField clientName = new TextField(swingText("clientDisplayName"));
        Runnable saveNames = () -> {
            runSwing(() -> {
                ((JTextField) field("launcherDisplayName")).setText(launcherName.getText());
                ((JTextField) field("clientDisplayName")).setText(clientName.getText());
                call("saveDisplayNames");
            });
            launcherName.setText(swingText("launcherDisplayName"));
            clientName.setText(swingText("clientDisplayName"));
            stage.setTitle(launcherName.getText());
            if (brandTitle != null) brandTitle.setText(clientName.getText());
        };
        launcherName.setOnAction(e -> saveNames.run());
        clientName.setOnAction(e -> saveNames.run());
        launcherName.focusedProperty().addListener((obs, old, focused) -> { if (!focused) saveNames.run(); });
        clientName.focusedProperty().addListener((obs, old, focused) -> { if (!focused) saveNames.run(); });

        body.getChildren().addAll(
            header,
            section("Appearance",
                label("Visible names only; managed folders and update identifiers stay canonical.", "muted"),
                controlField("Launcher name", launcherName), controlField("Client name", clientName)),
            section("Versions", chip("Minecraft", "1.21.11"), chip("Fabric Loader", "0.19.3+ (profile selectable)")),
            section("Runtime", controlField("Memory", memoryBox), controlField("Java Args", javaArgs), controlField("Graphics mode", graphicsMode), controlField("GPU selector (DRI_PRIME)", gpuSelector)),
            section("Updates", label("Launcher and client update checks", "muted"), buttonRow(autoCheck, checkUpdatesButton())),
            section("Slots", label("Slot sounds are quiet reel ticks. Win sounds are separate.", "muted"), buttonRow(slotSoundToggle, slotWinSoundToggle)),
            section("Links", buttonRow(review, website, credits)),
            section("Folders", folder),
            section("Unofficial", label(MINECRAFT_DISCLAIMER, "disclaimer-copy"))
        );
        showScreen(scrollScreen(body));
    }

    private Button checkUpdatesButton() {
        Button button = secondary("Check for Updates");
        button.setOnAction(e -> {
            String result = backendString("checkForUpdatesNow");
            if (!result.isBlank()) appendLog(result);
        });
        return button;
    }

    private Button stateToggle(String text, boolean selected) {
        Button button = secondary(text);
        button.getStyleClass().add("state-toggle");
        setStateToggle(button, selected);
        return button;
    }

    private void setStateToggle(Button button, boolean selected) {
        button.getStyleClass().removeAll("selected");
        if (selected) button.getStyleClass().add("selected");
        ScaleTransition pulse = new ScaleTransition(Duration.millis(150), button);
        pulse.setFromX(.97);
        pulse.setFromY(.97);
        pulse.setToX(1);
        pulse.setToY(1);
        pulse.playFromStart();
    }

    private void openAntiScreenshare() {
        if (!canUseAntiScreenshare()) {
            VBox body = new VBox(14);
            body.getStyleClass().addAll("content", "screen");
            HBox header = screenHeader("AntiScreenshare");
            Label title = label("AntiScreenshare is locked.", "section-title");
            Label copy = label("Upgrade to Beta++ or Media to control the live client from the launcher.", "muted");
            copy.setWrapText(true);
            Button upgrade = primary("Upgrade Now");
            upgrade.setOnAction(e -> openUrl(backendString("siteUrl").replaceAll("/+$", "") + "/#pricing"));
            body.getChildren().setAll(header, section("Access Required", title, copy, upgrade));
            showScreen(scrollScreen(body));
            return;
        }
        VBox body = new VBox(14);
        body.getStyleClass().addAll("content", "screen");
        HBox header = screenHeader("AntiScreenshare");
        body.getChildren().setAll(header, section("Live Control", label("Connecting to 127.0.0.1:18765...", "muted")));
        showScreen(scrollScreen(body));

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            body.getChildren().setAll(header, section("Live Control", label("Checking live client bridge...", "muted")));
            Thread load = new Thread(() -> {
                List<Map<String, Object>> modules = backendModuleList();
                boolean liveConnected = !modules.isEmpty() && "Live client".equals(mapString(modules.get(0), "source"));
                boolean enabled = backendBoolean("antiScreenshareEnabled");
                String statusText = backendString("antiScreenshareStatus");
                String pathText = backendString("antiScreenshareConfigPath");
                Platform.runLater(() -> renderAntiScreenshare(body, header, refresh[0], modules, liveConnected, enabled, statusText, pathText));
            }, "antiscreenshare-ui-refresh");
            load.setDaemon(true);
            load.start();
        };
        refresh[0].run();
    }

    private void renderAntiScreenshare(VBox body, HBox header, Runnable refresh, List<Map<String, Object>> modules, boolean liveConnected, boolean enabled, String statusText, String pathText) {
        body.getChildren().setAll(header);
        if (!enabled && !liveConnected) {
            Button enable = primary("Enable");
            enable.setOnAction(e -> {
                String result = backendString("enableAntiScreenshare");
                if (!result.isBlank()) appendLog(result);
                refresh.run();
            });
            Label state = label(statusText, "muted");
            state.setWrapText(true);
            VBox enableOnly = new VBox(12, label("Live Client", "section-title"), state, enable);
            enableOnly.getStyleClass().add("card");
            body.getChildren().add(enableOnly);
            return;
        }

        Label status = label(antiScreenshareSummary(modules, liveConnected, enabled), "anti-status-title");
        Label hint = label(liveConnected ? "Changes apply to the open client. Save when your module setup feels right." : "Using saved module data until the client bridge reconnects.", "muted");
        hint.setWrapText(true);
        Button core = enabled ? secondary("Disable") : primary("Enable");
        core.setOnAction(e -> {
            String result = enabled
                ? backendString("toggleAntiScreenshareModule", "antiscreenshare", false)
                : backendString("enableAntiScreenshare");
            if (!result.isBlank()) appendLog(result);
            refresh.run();
        });
        Button save = secondary("Save");
        save.setOnAction(e -> {
            String result = backendString("saveAntiScreenshareConfig");
            if (!result.isBlank()) appendLog(result);
        });
        Button community = secondary("Configs");
        community.setOnAction(e -> openCommunityConfigsWindow());
        Button hud = secondary("HUD");
        hud.setOnAction(e -> openAntiScreenshareHudMenu());
        Button reload = secondary("Refresh");
        reload.setOnAction(e -> refresh.run());

        body.getChildren().addAll(
            section("Live Control", status, hint, buttonRow(core, save, community, hud, reload)),
            antiScreenshareModulePanel(modules, refresh)
        );
    }

    private String antiScreenshareSummary(List<Map<String, Object>> modules, boolean liveConnected, boolean enabled) {
        String bridge = liveConnected ? "Connected" : "Saved config";
        String state = enabled ? "enabled" : "ready";
        return bridge + " · " + modules.size() + " modules · AntiScreenshare " + state;
    }

    private void openAccountManager() {
        syncToBackend();

        VBox body = new VBox(14);
        body.getStyleClass().addAll("content", "screen");
        HBox header = screenHeader("Accounts");

        VBox launcher = new VBox(10);
        launcher.getStyleClass().add("card");
        launcher.getChildren().addAll(
            label("Launcher", "section-title"),
            label(accountNameLabel.getText(), "account-name"),
            label(accountStatusLabel.getText(), "muted")
        );
        Button launcherSignIn = secondary(field("launcherUser") == null ? "Sign In" : "Switch");
        launcherSignIn.setOnAction(e -> runAccountButtonAction());
        launcher.getChildren().add(launcherSignIn);

        Object microsoftAccount = field("microsoftAccount");
        boolean hasMicrosoft = microsoftAccount != null && !objectFieldString(microsoftAccount, "refreshToken").isBlank();
        boolean cracked = Boolean.TRUE.equals(field("crackedMode"));
        String crackedName = username.getText() == null || username.getText().isBlank() ? defaultUsername() : username.getText().trim();
        String microsoftName = hasMicrosoft ? objectFieldString(microsoftAccount, "name") : "";

        VBox game = new VBox(10);
        game.getStyleClass().add("card");
        game.getChildren().add(label("Game Account", "section-title"));

        HBox crackedRow = accountRow("Cracked", crackedName, cracked ? "Selected" : "Offline fallback");
        Button useCracked = secondary(cracked ? "Selected" : "Use");
        useCracked.setDisable(cracked);
        useCracked.setOnAction(e -> runBackend("selectCrackedAccount"));
        crackedRow.getChildren().add(useCracked);
        game.getChildren().add(crackedRow);

        if (hasMicrosoft) {
            HBox microsoftRow = accountRow("Microsoft", microsoftName.isBlank() ? "Linked account" : microsoftName, cracked ? "Available" : "Selected");
            Button useMicrosoft = secondary(cracked ? "Use" : "Selected");
            useMicrosoft.setDisable(!cracked);
            useMicrosoft.setOnAction(e -> runBackend("selectMicrosoftAccount"));
            Button remove = secondary("Remove");
            remove.setOnAction(e -> runBackend("signOutMicrosoft"));
            microsoftRow.getChildren().addAll(useMicrosoft, remove);
            game.getChildren().add(microsoftRow);
        } else {
            Label empty = new Label("No Microsoft account linked.");
            empty.getStyleClass().add("muted");
            game.getChildren().add(empty);
        }

        Button add = primary(hasMicrosoft ? "Switch Microsoft Account" : "Add Microsoft Account");
        add.setOnAction(e -> {
            if (backendBoolean("isGameRunning")) {
                showWarning("Minecraft is running", "Quit Minecraft before switching Microsoft accounts.");
                return;
            }
            runBackend("startMicrosoftSignIn", true);
            showLaunchScreen();
        });
        game.getChildren().add(add);

        body.getChildren().addAll(header, launcher, game);
        showScreen(body);
    }

    private void openModManager() {
        syncToBackend();
        runSwing(() -> call("ensureProfileFolders", selectedBackendProfile()));

        VBox body = new VBox(12);
        body.getStyleClass().addAll("content", "screen");
        HBox header = screenHeader(profileBox.getValue() + " Mods");
        Label summary = new Label();
        summary.getStyleClass().add("muted");
        summary.setWrapText(true);
        summary.setMaxWidth(Double.MAX_VALUE);

        ListView<ModFile> list = new ListView<>();
        list.getStyleClass().add("mod-list");
        list.setCellFactory(view -> new ModFileCell());
        VBox.setVgrow(list, Priority.ALWAYS);
        Runnable reloadMods = () -> {
            List<ModFile> mods = readMods();
            list.getItems().setAll(mods);
            long enabled = mods.stream().filter(mod -> mod.enabled).count();
            long locked = mods.stream().filter(mod -> mod.locked).count();
            summary.setText((mods.isEmpty()
                ? "No jar files are in this profile yet."
                : enabled + " enabled, " + (mods.size() - enabled) + " disabled, " + locked + " required.")
                + "  Folder: " + modsFolder().getAbsolutePath());
        };
        reloadMods.run();
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                toggleSelectedMod(list, summary, reloadMods);
            }
        });
        list.setOnDragOver(e -> {
            Dragboard board = e.getDragboard();
            if (board.hasFiles() && board.getFiles().stream().anyMatch(this::isJarLikeFile)) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        list.setOnDragDropped(e -> {
            Dragboard board = e.getDragboard();
            boolean ok = false;
            if (board.hasFiles()) {
                ok = copyDroppedMods(board.getFiles(), summary);
                reloadMods.run();
            }
            e.setDropCompleted(ok);
            e.consume();
        });

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Button refresh = secondary("Refresh");
        refresh.setOnAction(e -> reloadMods.run());
        Button toggle = secondary("Toggle");
        toggle.setOnAction(e -> toggleSelectedMod(list, summary, reloadMods));
        Button open = secondary("Open Folder");
        open.setOnAction(e -> openFile(modsFolder()));
        buttons.getChildren().addAll(refresh, toggle, open);

        body.getChildren().addAll(header, summary, list, buttons);
        showScreen(body);
    }

    private void openResourcePackManager() {
        syncToBackend();
        runSwing(() -> call("ensureProfileFolders", selectedBackendProfile()));

        File packsFolder = resourcePacksFolder();
        if (!packsFolder.exists() && !packsFolder.mkdirs()) {
            appendLog("Could not create resource packs folder.");
            return;
        }

        VBox body = new VBox(12);
        body.getStyleClass().addAll("content", "screen");
        HBox header = screenHeader(profileBox.getValue() + " Resource Packs");
        Label summary = new Label();
        summary.getStyleClass().add("muted");
        summary.setWrapText(true);
        summary.setMaxWidth(Double.MAX_VALUE);

        ListView<ModFile> list = new ListView<>();
        list.getStyleClass().add("mod-list");
        list.setCellFactory(view -> new ModFileCell());
        VBox.setVgrow(list, Priority.ALWAYS);
        Runnable reloadPacks = () -> {
            List<ModFile> packs = readResourcePacks();
            list.getItems().setAll(packs);
            long enabled = packs.stream().filter(pack -> pack.enabled).count();
            summary.setText((packs.isEmpty()
                ? "Drop zip resource packs here to add them to this profile."
                : enabled + " enabled, " + (packs.size() - enabled) + " disabled.")
                + "  Folder: " + packsFolder.getAbsolutePath());
        };
        reloadPacks.run();
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) toggleSelectedResourcePack(list, summary, reloadPacks);
        });
        list.setOnDragOver(e -> {
            Dragboard board = e.getDragboard();
            if (board.hasFiles() && board.getFiles().stream().anyMatch(this::isResourcePackLikeFile)) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        list.setOnDragDropped(e -> {
            Dragboard board = e.getDragboard();
            boolean ok = false;
            if (board.hasFiles()) {
                ok = copyDroppedResourcePacks(board.getFiles(), summary);
                reloadPacks.run();
            }
            e.setDropCompleted(ok);
            e.consume();
        });

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Button refresh = secondary("Refresh");
        refresh.setOnAction(e -> reloadPacks.run());
        Button toggle = secondary("Toggle");
        toggle.setOnAction(e -> toggleSelectedResourcePack(list, summary, reloadPacks));
        Button add = secondary("Add");
        add.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Add Resource Packs");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Resource packs", "*.zip"));
            List<File> files = chooser.showOpenMultipleDialog(stage);
            if (files != null && copyDroppedResourcePacks(files, summary)) reloadPacks.run();
        });
        Button open = secondary("Open Folder");
        open.setOnAction(e -> openFile(resourcePacksFolder()));
        buttons.getChildren().addAll(refresh, toggle, add, open);

        body.getChildren().addAll(header, summary, list, buttons);
        showScreen(body);
    }

    private HBox accountRow(String kind, String name, String status) {
        HBox row = new HBox(12);
        row.getStyleClass().add("account-row");
        row.setAlignment(Pos.CENTER_LEFT);
        VBox copy = new VBox(2, label(kind, "chip-name"), label(name, "account-name"), label(status, "muted"));
        HBox.setHgrow(copy, Priority.ALWAYS);
        row.getChildren().add(copy);
        return row;
    }

    private void toggleSelectedMod(ListView<ModFile> list, Label summary, Runnable reloadMods) {
        ModFile file = list.getSelectionModel().getSelectedItem();
        if (file == null) return;
        if (file.locked) {
            summary.setText(file.file.getName() + " is required for this profile.");
            return;
        }
        try {
            File target = file.toggleTarget();
            Files.move(file.file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            reloadMods.run();
        } catch (Exception ex) {
            appendLog("Mod toggle failed: " + rootMessage(ex));
        }
    }

    private boolean copyDroppedMods(List<File> files, Label summary) {
        File mods = modsFolder();
        if (!mods.exists() && !mods.mkdirs()) {
            summary.setText("Could not create mods folder.");
            return false;
        }

        int copied = 0;
        for (File file : files) {
            if (!isJarLikeFile(file)) continue;
            try {
                Files.copy(file.toPath(), new File(mods, file.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (Exception e) {
                appendLog("Mod copy failed: " + file.getName() + ": " + rootMessage(e));
            }
        }
        summary.setText(copied == 1 ? "Added 1 mod." : "Added " + copied + " mods.");
        return copied > 0;
    }

    private void toggleSelectedResourcePack(ListView<ModFile> list, Label summary, Runnable reloadPacks) {
        ModFile file = list.getSelectionModel().getSelectedItem();
        if (file == null) return;
        try {
            File target = file.toggleTarget();
            Files.move(file.file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            setResourcePackEnabled(target, !file.enabled);
            reloadPacks.run();
        } catch (Exception ex) {
            appendLog("Resource pack toggle failed: " + rootMessage(ex));
            summary.setText("Could not toggle resource pack.");
        }
    }

    private boolean copyDroppedResourcePacks(List<File> files, Label summary) {
        File packs = resourcePacksFolder();
        if (!packs.exists() && !packs.mkdirs()) {
            summary.setText("Could not create resource packs folder.");
            return false;
        }

        int copied = 0;
        for (File file : files) {
            if (!isResourcePackLikeFile(file)) continue;
            try {
                File target = new File(packs, file.getName());
                if (file.isDirectory()) copyDirectory(file.toPath(), target.toPath());
                else Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                setResourcePackEnabled(target, true);
                copied++;
            } catch (Exception e) {
                appendLog("Resource pack copy failed: " + file.getName() + ": " + rootMessage(e));
            }
        }
        summary.setText(copied == 1 ? "Added 1 resource pack." : "Added " + copied + " resource packs.");
        return copied > 0;
    }

    private void syncToBackend() {
        if (syncingFromBackend) return;
        runSwing(() -> {
            ((JComboBox<?>) field("profileBox")).setSelectedIndex(Math.max(0, profileBox.getSelectionModel().getSelectedIndex()));
            ((JComboBox<?>) field("buildBox")).setSelectedIndex(Math.max(0, buildBox.getSelectionModel().getSelectedIndex()));
            ((JComboBox<?>) field("memoryGb")).setSelectedItem(Integer.parseInt(memoryBox.getValue()));
            ((JTextField) field("username")).setText(username.getText());
            ((JTextField) field("javaArgs")).setText(javaArgs.getText());
            ((JComboBox<?>) field("graphicsMode")).setSelectedItem(graphicsMode.getValue());
            ((JTextField) field("gpuSelector")).setText(gpuSelector.getText());
        });
    }

    private void syncFromBackend() {
        String text = swingText("log");
        if (!text.equals(lastLog)) {
            lastLog = text;
            renderLogText(text);
        }

        JButton button = (JButton) field("launchButton");
        launchButton.setText(button.getText());
        launchButton.setDisable(!button.isEnabled());
        accountNameLabel.setText(labelText("accountName"));
        accountStatusLabel.setText(accountStatusWithMicrosoft());
        Object launcherUser = field("launcherUser");
        signInButton.setText(launcherUser == null ? ((JButton) field("signInButton")).getText() : "Switch");
        signInButton.setDisable(!((JButton) field("signInButton")).isEnabled() && launcherUser == null);
        progressText.setText(((JProgressBar) field("progress")).getString());
        progress.setProgress(Math.max(0, ((JProgressBar) field("progress")).getValue()) / 100.0);
        launcherInstalled.setText(labelText("launcherInstalledVersion"));
        launcherReleased.setText(labelText("launcherReleasedVersion"));
        clientInstalled.setText(labelText("clientInstalledVersion"));
        clientReleased.setText(labelText("clientReleasedVersion"));
        updateAntiScreenshareAccess();
        updateSidebarAd();
        syncControlSelectionsFromBackend();
        syncProfileControls();
        buildBox.requestLayout();
    }

    private void syncControlSelectionsFromBackend() {
        syncingFromBackend = true;
        try {
            int backendProfile = Math.max(0, ((JComboBox<?>) field("profileBox")).getSelectedIndex());
            int backendBuild = Math.max(0, ((JComboBox<?>) field("buildBox")).getSelectedIndex());
            Object backendMemory = ((JComboBox<?>) field("memoryGb")).getSelectedItem();
            String backendJavaArgs = ((JTextField) field("javaArgs")).getText();
            String backendUsername = ((JTextField) field("username")).getText();
            String backendGraphicsMode = String.valueOf(((JComboBox<?>) field("graphicsMode")).getSelectedItem());
            String backendGpuSelector = ((JTextField) field("gpuSelector")).getText();

            if (profileBox != null && profileBox.getSelectionModel().getSelectedIndex() != backendProfile && backendProfile < profileBox.getItems().size()) {
                profileBox.getSelectionModel().select(backendProfile);
            }
            if (buildBox != null && buildBox.getSelectionModel().getSelectedIndex() != backendBuild && backendBuild < buildBox.getItems().size()) {
                buildBox.getSelectionModel().select(backendBuild);
            }
            if (memoryBox != null && backendMemory != null && !String.valueOf(backendMemory).equals(memoryBox.getValue())) {
                memoryBox.getSelectionModel().select(String.valueOf(backendMemory));
            }
            if (javaArgs != null && backendJavaArgs != null && !backendJavaArgs.equals(javaArgs.getText())) {
                javaArgs.setText(backendJavaArgs);
            }
            if (username != null && backendUsername != null && !backendUsername.equals(username.getText())) {
                username.setText(backendUsername);
            }
            if (graphicsMode != null && backendGraphicsMode != null && !backendGraphicsMode.equals(graphicsMode.getValue())) {
                graphicsMode.getSelectionModel().select(backendGraphicsMode);
            }
            if (gpuSelector != null && backendGpuSelector != null && !backendGpuSelector.equals(gpuSelector.getText())) {
                gpuSelector.setText(backendGpuSelector);
            }
        } finally {
            syncingFromBackend = false;
        }
    }

    private void syncProfileControls() {
        boolean gamble = profileBox.getSelectionModel().getSelectedIndex() == 0;
        Object user = field("launcherUser");
        boolean limited = user != null && bestBuildId(user).equals("ad_tier");
        buildBox.setDisable(!gamble || limited);
        buildBox.setManaged(!limited);
        buildBox.setVisible(!limited);
        if (buildLockedLabel != null) {
            buildLockedLabel.setManaged(limited);
            buildLockedLabel.setVisible(limited);
            buildLockedLabel.setText("Ad Tier");
        }
        updateButton.setDisable(!gamble);
        updateAntiScreenshareAccess();
        boolean hasMicrosoft = field("microsoftAccount") != null;
        boolean cracked = Boolean.TRUE.equals(field("crackedMode"));
        boolean showUsername = !hasMicrosoft || cracked;
        usernameBox.setManaged(showUsername);
        usernameBox.setVisible(showUsername);
    }

    private void updateAntiScreenshareAccess() {
        if (antiScreenshareButton == null) return;
        boolean allowed = canUseAntiScreenshare();
        antiScreenshareButton.setDisable(false);
        antiScreenshareButton.setText(allowed ? "AntiScreenshare" : "AntiScreenshare Locked");
        antiScreenshareButton.setTooltip(new Tooltip(allowed ? "Open the live client control panel." : "AntiScreenshare is available for Beta++ and Media accounts."));
    }

    private void runAccountButtonAction() {
        if (Boolean.TRUE.equals(call("isLauncherSignInActive"))) {
            runBackend("cancelLauncherSignIn");
        } else if (field("launcherUser") != null) {
            if (backendBoolean("isGameRunning")) {
                showWarning("Minecraft is running", "You cannot switch accounts while Minecraft is running.");
                return;
            }
            runBackend("switchLauncherAccount");
        } else {
            runBackend("startSignIn");
        }
    }

    private void showLaunchScreen() {
        showScreen(launchPane);
    }

    private void showScreen(Region screen) {
        stopHudInfoRefresh();
        contentHost.getChildren().setAll(screen);
        screen.setOpacity(0);
        screen.setTranslateY(8);
        FadeTransition fade = new FadeTransition(Duration.millis(220), screen);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(220), screen);
        slide.setFromY(8);
        slide.setToY(0);
        new ParallelTransition(fade, slide).play();
    }

    private void stopHudInfoRefresh() {
        if (hudInfoRefresh != null) {
            hudInfoRefresh.stop();
            hudInfoRefresh = null;
        }
    }

    private ScrollPane scrollScreen(Region screen) {
        ScrollPane scroll = new ScrollPane(screen);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setFocusTraversable(false);
        scroll.getStyleClass().add("content-scroll");
        return scroll;
    }

    private HBox screenHeader(String title) {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Button back = secondary("Back");
        back.setOnAction(e -> showLaunchScreen());
        Label heading = new Label(title);
        heading.getStyleClass().add("heading");
        heading.setWrapText(true);
        HBox.setHgrow(heading, Priority.ALWAYS);
        header.getChildren().addAll(back, heading);
        return header;
    }

    private HBox submenuHeader(String title, Runnable backAction) {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Button back = secondary("Back");
        back.setOnAction(e -> backAction.run());
        Label heading = new Label(title);
        heading.getStyleClass().add("heading");
        heading.setWrapText(true);
        HBox.setHgrow(heading, Priority.ALWAYS);
        header.getChildren().addAll(back, heading);
        return header;
    }

    private VBox section(String title, Region... controls) {
        VBox section = new VBox(10);
        section.getStyleClass().add("card");
        section.getChildren().add(label(title, "section-title"));
        section.getChildren().addAll(controls);
        return section;
    }

    private FlowPane buttonRow(Button... buttons) {
        FlowPane row = new FlowPane(10, 10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getChildren().addAll(buttons);
        return row;
    }

    private void openCommunityConfigsWindow() {
        Stage stage = new Stage();
        stage.setTitle("Browse Configs");

        VBox body = new VBox(12);
        body.getStyleClass().addAll("content", "screen");
        body.getChildren().add(label("Browse Configs", "heading"));
        Label note = label("Approved configs from gambleclient.org.", "muted");
        note.setWrapText(true);
        body.getChildren().add(note);

        List<Map<String, Object>> configs = backendCommunityConfigs();
        VBox list = new VBox(10);
        list.getStyleClass().add("card");
        list.getChildren().add(label("Available", "section-title"));
        if (configs.isEmpty()) {
            list.getChildren().add(label("No approved configs are available yet.", "muted"));
        } else {
            for (Map<String, Object> config : configs) list.getChildren().add(communityConfigRow(config));
        }

        Button dashboard = secondary("Open Dashboard");
        dashboard.setOnAction(e -> openUrl("https://gambleclient.org/dashboard"));
        body.getChildren().addAll(list, buttonRow(dashboard));

        ScrollPane scroll = scrollScreen(body);
        Scene scene = new Scene(scroll, 620, 620);
        scene.getStylesheets().add(getClass().getResource("/launcher-fx.css").toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(460);
        stage.setMinHeight(420);
        stage.show();
    }

    private VBox communityConfigRow(Map<String, Object> config) {
        String title = mapString(config, "title");
        if (title.isBlank()) title = mapString(config, "name");
        if (title.isBlank()) title = "Untitled config";
        String description = mapString(config, "description");
        String author = mapString(config, "author_label");
        if (author.isBlank()) author = mapString(config, "author");
        String uses = mapString(config, "uses");
        String favorites = mapString(config, "favorites");

        Label name = label(title, "module-title");
        name.setWrapText(true);
        Label detail = label(description.isBlank() ? "No description provided." : description, "module-description");
        detail.setWrapText(true);
        String metaText = (author.isBlank() ? "Community" : author)
            + (uses.isBlank() ? "" : " | " + uses + " uses")
            + (favorites.isBlank() ? "" : " | " + favorites + " favorites");
        VBox row = new VBox(5, name, detail, label(metaText, "module-meta"));
        row.getStyleClass().add("account-row");
        return row;
    }

    private VBox slotMachine() {
        VBox slot = new VBox(10);
        slot.getStyleClass().add("slot-card");

        Label title = label("Gamble Slots", "chip-name");
        HBox reels = new HBox(8);
        reels.setAlignment(Pos.CENTER);
        reels.getStyleClass().add("slot-reels");
        Label[][] reelLabels = new Label[3][3];
        String[] initial = {"🍒", "7", "💎"};
        for (int i = 0; i < 3; i++) {
            VBox reel = new VBox(2);
            reel.setAlignment(Pos.CENTER);
            reel.getStyleClass().add("slot-reel");
            reelLabels[i][0] = label(slotNeighbor(initial[i], -1), "slot-ghost");
            reelLabels[i][1] = label(initial[i], "slot-symbol");
            reelLabels[i][2] = label(slotNeighbor(initial[i], 1), "slot-ghost");
            reel.getChildren().addAll(reelLabels[i][0], reelLabels[i][1], reelLabels[i][2]);
            reels.getChildren().add(reel);
        }
        Label payout = label("", "slot-payout");
        payout.setMinHeight(16);
        Button spin = secondary("Spin");
        spin.setMaxWidth(Double.MAX_VALUE);
        spin.setOnAction(e -> {
            spin.setDisable(true);
            slot.getStyleClass().removeAll("slot-win", "slot-loss");
            payout.getStyleClass().removeAll("slot-win-text", "slot-loss-text");
            payout.setText("");
            Timeline roll = new Timeline();
            double at = 0;
            int frames = 64;
            for (int frame = 0; frame < frames; frame++) {
                double progress = frame / (double) Math.max(1, frames - 1);
                at += 18 + Math.pow(progress, 2.65) * 96;
                final int frameIndex = frame;
                roll.getKeyFrames().add(new KeyFrame(Duration.millis(at), event -> {
                    rollSlotLabels(reelLabels, randomSlotSymbols());
                    if (frameIndex % 3 == 0) playSlotTick();
                }));
            }
            roll.setOnFinished(event -> {
                String[] finalSymbols = Math.random() < 0.006 ? new String[] {"7", "7", "7"} : randomSlotSymbols();
                rollSlotLabels(reelLabels, finalSymbols);
                boolean jackpot = finalSymbols[0].equals(finalSymbols[1]) && finalSymbols[1].equals(finalSymbols[2]);
                boolean pair = finalSymbols[0].equals(finalSymbols[1]) || finalSymbols[1].equals(finalSymbols[2]) || finalSymbols[0].equals(finalSymbols[2]);
                boolean win = jackpot || pair;
                slot.getStyleClass().add(win ? "slot-win" : "slot-loss");
                payout.getStyleClass().add(win ? "slot-win-text" : "slot-loss-text");
                payout.setText("");
                playSlotFinish(jackpot, pair);
                PauseTransition hold = new PauseTransition(Duration.millis(win ? 1100 : 260));
                hold.setOnFinished(done -> spin.setDisable(false));
                hold.play();
            });
            roll.play();
        });

        slot.getChildren().addAll(title, reels, payout, spin);
        return slot;
    }

    private VBox sidebarAdBox() {
        VBox box = new VBox(6);
        box.getStyleClass().add("ad-card");
        Label title = label("Ad Space", "chip-name");
        Label copy = label("Watch a sponsor break to refresh Ad Tier access.", "ad-copy");
        copy.setWrapText(true);
        Button watch = secondary("Watch Ad");
        watch.setMaxWidth(Double.MAX_VALUE);
        watch.setOnAction(event -> openSponsorBreak());
        sidebarAd = box;
        sidebarAdCopy = copy;
        sidebarAdButton = watch;
        box.getChildren().addAll(title, copy, watch);
        box.setManaged(false);
        box.setVisible(false);
        return box;
    }

    private void updateSidebarAd() {
        if (sidebarAd == null) return;
        Object ads = field("launcherAds");
        boolean required = objectFieldBoolean(ads, "required");
        boolean canWatch = objectFieldBoolean(ads, "canWatch");
        boolean active = objectFieldBoolean(ads, "active");
        long remaining = objectFieldLong(ads, "remainingSeconds");
        String message = objectFieldString(ads, "message");
        sidebarAd.setManaged(required);
        sidebarAd.setVisible(required);
        sidebarAdButton.setDisable(!canWatch);
        sidebarAdButton.setText(canWatch ? "Watch Ad" : "Ad Capped");
        if (active && remaining > 0) {
            sidebarAdCopy.setText("Sponsored access active for " + compactDuration(remaining) + ".");
        } else if (!message.isBlank()) {
            sidebarAdCopy.setText(message);
        } else {
            sidebarAdCopy.setText("Watch a sponsor break to refresh Ad Tier access.");
        }
    }

    private void openSponsorBreak() {
        Object ads = field("launcherAds");
        if (ads == null || !objectFieldBoolean(ads, "required")) return;
        if (!objectFieldBoolean(ads, "canWatch")) {
            appendLog("Sponsor break is not available for this account right now.");
            return;
        }

        Map<String, Object> start = backendMap("beginSponsorBreakForOverlay");
        if (start.isEmpty()) return;
        int seconds = Math.max(5, mapInt(start, "adSeconds"));
        String adUrl = mapString(start, "adUrl");
        if (adUrl.isBlank()) adUrl = "/assets/placeholder-ad.mp4";
        showSponsorOverlay(resolveAdUrl(adUrl), seconds);
    }

    private void showSponsorOverlay(String adUrl, int seconds) {
        if (appRoot == null) return;
        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("ad-overlay");
        VBox modal = new VBox(14);
        modal.getStyleClass().add("ad-modal");
        modal.setMaxWidth(720);
        Label title = label("Sponsor Break", "heading");
        Label copy = label("Keep the launcher open while the ad finishes.", "muted");
        copy.setWrapText(true);
        StackPane mediaBox = new StackPane();
        mediaBox.getStyleClass().add("ad-player");
        Label fallback = label("Loading sponsor media...", "ad-player-fallback");
        mediaBox.getChildren().add(fallback);
        ProgressBar bar = new ProgressBar(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        Label countdown = label(seconds + "s left", "ad-copy");
        Button leave = secondary("Leave");
        FlowPane controls = buttonRow(leave);
        HBox confirmLeave = new HBox(10);
        confirmLeave.setAlignment(Pos.CENTER_LEFT);
        confirmLeave.getStyleClass().add("ad-leave-confirm");
        Label confirmCopy = label("Leaving now forfeits the reward.", "muted");
        Button keepWatching = secondary("Keep Watching");
        Button leaveAnyway = secondary("Leave Anyway");
        confirmLeave.getChildren().addAll(confirmCopy, keepWatching, leaveAnyway);
        confirmLeave.setManaged(false);
        confirmLeave.setVisible(false);

        loadSponsorMedia(mediaBox, fallback, adUrl);

        final Timeline[] timerRef = new Timeline[1];
        leave.setOnAction(event -> {
            confirmLeave.setManaged(true);
            confirmLeave.setVisible(true);
            controls.setManaged(false);
            controls.setVisible(false);
        });
        keepWatching.setOnAction(event -> {
            confirmLeave.setManaged(false);
            confirmLeave.setVisible(false);
            controls.setManaged(true);
            controls.setVisible(true);
        });
        leaveAnyway.setOnAction(event -> {
            if (timerRef[0] != null) timerRef[0].stop();
            stopSponsorMedia();
            appRoot.getChildren().remove(overlay);
            appendLog("Sponsor break closed before completion. No reward was granted.");
        });

        modal.getChildren().addAll(title, copy, mediaBox, countdown, bar, controls, confirmLeave);
        overlay.getChildren().add(modal);
        appRoot.getChildren().add(overlay);

        final int[] watchedSeconds = new int[] {0};
        final int[] waitingSeconds = new int[] {0};
        Timeline timer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            SponsorPlaybackState playback = sponsorPlaybackState();
            if (playback != SponsorPlaybackState.PLAYING) {
                waitingSeconds[0]++;
                if (playback == SponsorPlaybackState.FAILED || waitingSeconds[0] >= 15) {
                    timerRef[0].stop();
                    stopSponsorMedia();
                    fallback.setText("Sponsor media could not play on this device. Update the launcher or try again.");
                    mediaBox.getChildren().setAll(fallback);
                    countdown.setText("Media unavailable");
                    appendLog("Sponsor media did not begin playback. No reward was granted.");
                }
                return;
            }
            waitingSeconds[0] = 0;
            int tick = Math.min(seconds, ++watchedSeconds[0]);
            int remaining = Math.max(0, seconds - tick);
            countdown.setText(remaining == 0 ? "Finishing..." : remaining + "s left");
            bar.setProgress(Math.min(1.0, tick / (double) Math.max(1, seconds)));
            if (tick >= seconds) {
                timerRef[0].stop();
                stopSponsorMedia();
                appRoot.getChildren().remove(overlay);
                String result = backendString("completeSponsorBreakForOverlay");
                if (!result.isBlank()) appendLog(result);
            }
        }));
        timerRef[0] = timer;
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    private void stopSponsorMedia() {
        if (sponsorMediaPlayer != null) {
            try {
                sponsorMediaPlayer.stop();
                sponsorMediaPlayer.dispose();
            } catch (RuntimeException ignored) {
            } finally {
                sponsorMediaPlayer = null;
            }
        }
        sponsorWebView = null;
    }

    private void loadSponsorMedia(StackPane mediaBox, Label fallback, String adUrl) {
        if (!isDirectMediaUrl(adUrl)) {
            fallback.setText("Sponsor media is not playable. Ask staff to upload an MP4 or WebM ad.");
            return;
        }

        stopSponsorMedia();
        Thread loader = new Thread(() -> {
            try {
                String mediaSource = cacheSponsorMedia(adUrl);
                Platform.runLater(() -> playSponsorMedia(mediaBox, fallback, adUrl, mediaSource));
            } catch (Exception e) {
                Platform.runLater(() -> appendLog("Sponsor media download failed: " + e.getMessage()));
                Platform.runLater(() -> showSponsorFallback(mediaBox, fallback));
            }
        }, "Sponsor media loader");
        loader.setDaemon(true);
        loader.start();
    }

    private String cacheSponsorMedia(String adUrl) throws IOException {
        String extension = sponsorMediaExtension(adUrl);
        java.nio.file.Path temp = Files.createTempFile("gamble-sponsor-", extension);
        temp.toFile().deleteOnExit();
        HttpURLConnection connection = (HttpURLConnection) URI.create(adUrl).toURL().openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent", "GambleClientLauncher/" + backendString("launcherVersion"));
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Sponsor media returned HTTP " + status + ".");
            long declaredSize = connection.getContentLengthLong();
            long maxBytes = 64L * 1024L * 1024L;
            if (declaredSize > maxBytes) throw new IOException("Sponsor media exceeds the 64 MiB limit.");
            try (InputStream input = connection.getInputStream(); java.io.OutputStream output = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[16384];
                long total = 0;
                for (int read; (read = input.read(buffer)) >= 0; ) {
                    total += read;
                    if (total > maxBytes) throw new IOException("Sponsor media exceeds the 64 MiB limit.");
                    output.write(buffer, 0, read);
                }
            }
        } finally {
            connection.disconnect();
        }
        if (Files.size(temp) <= 0) throw new IOException("Sponsor media download was empty.");
        return temp.toUri().toString();
    }

    private void playSponsorMedia(StackPane mediaBox, Label fallback, String adUrl, String mediaSource) {
        try {
            stopSponsorMedia();
            Media media = new Media(mediaSource);
            MediaPlayer player = new MediaPlayer(media);
            MediaView view = new MediaView(player);
            view.setPreserveRatio(true);
            view.setFitWidth(650);
            view.setFitHeight(330);
            player.setMute(true);
            player.setCycleCount(MediaPlayer.INDEFINITE);
            player.setOnReady(() -> {
                mediaBox.getChildren().setAll(view);
                player.play();
            });
            player.setOnError(() -> showSponsorWebFallback(mediaBox, fallback, adUrl, mediaSource, player.getError()));
            media.setOnError(() -> showSponsorWebFallback(mediaBox, fallback, adUrl, mediaSource, media.getError()));
            sponsorMediaPlayer = player;
        } catch (RuntimeException e) {
            showSponsorWebFallback(mediaBox, fallback, adUrl, mediaSource, e);
        }
    }

    private void showSponsorWebFallback(StackPane mediaBox, Label fallback, String adUrl, String mediaSource, Throwable error) {
        stopSponsorMedia();
        if (error != null && error.getMessage() != null && !error.getMessage().isBlank()) {
            appendLog("JavaFX media playback failed: " + error.getMessage());
        } else {
            appendLog("JavaFX media playback failed; trying embedded web playback.");
        }

        try {
            WebView webView = new WebView();
            webView.setContextMenuEnabled(false);
            webView.setPrefSize(650, 330);
            String source = mediaSource == null || mediaSource.isBlank() ? resolveAdUrl(adUrl) : mediaSource;
            String tag = isAudioMediaUrl(adUrl)
                ? "<audio controls autoplay loop muted style=\"width:100%;height:100%;background:#050408\" src=\"" + htmlAttr(source) + "\"></audio>"
                : "<video controls autoplay loop muted playsinline style=\"width:100%;height:100%;object-fit:contain;background:#050408\" src=\"" + htmlAttr(source) + "\"></video>";
            webView.getEngine().loadContent("<!doctype html><html><body style=\"margin:0;background:#050408;overflow:hidden\">" + tag + "</body></html>");
            sponsorWebView = webView;
            mediaBox.getChildren().setAll(webView);
        } catch (RuntimeException webError) {
            appendLog("Embedded sponsor media playback failed: " + webError.getMessage());
            showSponsorFallback(mediaBox, fallback);
        }
    }

    private void showSponsorFallback(StackPane mediaBox, Label fallback) {
        stopSponsorMedia();
        fallback.setText("Sponsor media could not be displayed on this device. Try uploading an H.264 MP4 ad.");
        mediaBox.getChildren().setAll(fallback);
    }

    private SponsorPlaybackState sponsorPlaybackState() {
        if (sponsorMediaPlayer != null) {
            if (sponsorMediaPlayer.getError() != null || sponsorMediaPlayer.getStatus() == MediaPlayer.Status.HALTED) {
                return SponsorPlaybackState.FAILED;
            }
            return sponsorMediaPlayer.getStatus() == MediaPlayer.Status.PLAYING
                ? SponsorPlaybackState.PLAYING
                : SponsorPlaybackState.WAITING;
        }
        if (sponsorWebView == null) return SponsorPlaybackState.WAITING;
        try {
            Object result = sponsorWebView.getEngine().executeScript("""
                (() => {
                    const media = document.querySelector('video, audio');
                    if (!media || media.error) return 'failed';
                    if (!media.paused && !media.ended && media.readyState >= 2) return 'playing';
                    return 'waiting';
                })()
                """);
            if ("playing".equals(result)) return SponsorPlaybackState.PLAYING;
            if ("failed".equals(result)) return SponsorPlaybackState.FAILED;
        } catch (RuntimeException ignored) {
            return SponsorPlaybackState.FAILED;
        }
        return SponsorPlaybackState.WAITING;
    }

    private enum SponsorPlaybackState {
        PLAYING,
        WAITING,
        FAILED
    }

    private boolean isDirectMediaUrl(String url) {
        String lower = mediaUrlPath(url);
        return lower.endsWith(".mp4") || lower.endsWith(".m4v") || lower.endsWith(".mov") || lower.endsWith(".webm") || lower.endsWith(".m3u8") || lower.endsWith(".mp3") || lower.endsWith(".wav");
    }

    private boolean isAudioMediaUrl(String url) {
        String lower = mediaUrlPath(url);
        return lower.endsWith(".mp3") || lower.endsWith(".wav");
    }

    private String sponsorMediaExtension(String url) {
        String lower = mediaUrlPath(url);
        if (lower.endsWith(".m4v")) return ".m4v";
        if (lower.endsWith(".mov")) return ".mov";
        if (lower.endsWith(".webm")) return ".webm";
        if (lower.endsWith(".m3u8")) return ".m3u8";
        if (lower.endsWith(".mp3")) return ".mp3";
        if (lower.endsWith(".wav")) return ".wav";
        return ".mp4";
    }

    private String mediaUrlPath(String url) {
        String lower = String.valueOf(url).toLowerCase(Locale.ROOT);
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        int hash = lower.indexOf('#');
        if (hash >= 0) lower = lower.substring(0, hash);
        return lower;
    }

    private String accountStatusWithMicrosoft() {
        String status = labelText("accountStatus");
        Object microsoft = field("microsoftAccount");
        if (microsoft == null) return status;
        String name = objectFieldString(microsoft, "name");
        if (name.isBlank()) return status;
        return status + " | MC: " + name;
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.initOwner(stage);
        alert.showAndWait();
    }

    private String htmlAttr(String value) {
        return String.valueOf(value == null ? "" : value)
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private String resolveAdUrl(String value) {
        String text = value == null ? "" : value.trim();
        String base = backendString("siteUrl");
        if (base.isBlank()) base = "https://gambleclient.org";
        try {
            URI uri = URI.create(text.startsWith("http://") || text.startsWith("https://") ? text : base.replaceAll("/+$", "") + (text.startsWith("/") ? text : "/" + text));
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                || (!host.equals("gambleclient.org") && !host.endsWith(".gambleclient.org")
                    && !host.equals("gamble-client.store") && !host.endsWith(".gamble-client.store"))
                || uri.getUserInfo() != null || uri.getPort() != -1) {
                throw new IllegalArgumentException("Sponsor media host is not allowed.");
            }
            return uri.toString();
        } catch (RuntimeException error) {
            appendLog("Rejected sponsor media URL: " + error.getMessage());
            return "";
        }
    }

    private String compactDuration(long seconds) {
        long safe = Math.max(0, seconds);
        long minutes = safe / 60;
        long remainder = safe % 60;
        if (minutes <= 0) return remainder + "s";
        if (minutes < 60) return minutes + "m " + remainder + "s";
        long hours = minutes / 60;
        return hours + "h " + (minutes % 60) + "m";
    }

    private void playSlotTick() {
        if (!slotSoundsEnabled) return;
        playTone(540, 22, 0.035);
    }

    private void playSlotFinish(boolean win) {
        if (!win || !slotWinSoundsEnabled) return;
        boolean jackpot = false;
        playTone(jackpot ? 880 : 660, 95, 0.08);
        Timeline winSound = new Timeline(
            new KeyFrame(Duration.millis(115), e -> playTone(jackpot ? 1100 : 780, 100, 0.075)),
            new KeyFrame(Duration.millis(250), e -> playTone(jackpot ? 1320 : 920, 125, 0.07))
        );
        winSound.play();
    }

    private void playSlotFinish(boolean jackpot, boolean pair) {
        if (!slotWinSoundsEnabled || (!jackpot && !pair)) return;
        if (jackpot) {
            playTone(740, 110, 0.08);
            Timeline sound = new Timeline(
                new KeyFrame(Duration.millis(120), e -> playTone(980, 120, 0.08)),
                new KeyFrame(Duration.millis(260), e -> playTone(1240, 170, 0.075))
            );
            sound.play();
        } else {
            playTone(620, 90, 0.065);
            Timeline sound = new Timeline(new KeyFrame(Duration.millis(120), e -> playTone(780, 120, 0.06)));
            sound.play();
        }
    }

    private void playTone(double hz, int millis, double volume) {
        Thread thread = new Thread(() -> {
            try {
                float sampleRate = 44100f;
                AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
                try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                    line.open(format);
                    line.start();
                    int samples = Math.max(1, (int) (sampleRate * millis / 1000.0));
                    byte[] buffer = new byte[samples * 2];
                    for (int i = 0; i < samples; i++) {
                        double fade = Math.min(1.0, Math.min(i / 80.0, (samples - i) / 120.0));
                        short value = (short) (Math.sin(2.0 * Math.PI * i * hz / sampleRate) * 32767.0 * volume * fade);
                        buffer[i * 2] = (byte) (value & 0xff);
                        buffer[i * 2 + 1] = (byte) ((value >> 8) & 0xff);
                    }
                    line.write(buffer, 0, buffer.length);
                    line.drain();
                }
            } catch (Exception ignored) {
            }
        }, "Gamble slot sound");
        thread.setDaemon(true);
        thread.start();
    }

    private String randomSlotSymbol() {
        return SLOT_SYMBOLS[(int) (Math.random() * SLOT_SYMBOLS.length)];
    }

    private String[] randomSlotSymbols() {
        return new String[] {randomSlotSymbol(), randomSlotSymbol(), randomSlotSymbol()};
    }

    private void rollSlotLabels(Label[][] labels, String[] symbols) {
        for (int i = 0; i < labels.length; i++) {
            labels[i][0].setText(slotNeighbor(symbols[i], -1));
            labels[i][1].setText(symbols[i]);
            labels[i][2].setText(slotNeighbor(symbols[i], 1));
        }
    }

    private String slotNeighbor(String symbol, int offset) {
        int index = 0;
        for (int i = 0; i < SLOT_SYMBOLS.length; i++) {
            if (SLOT_SYMBOLS[i].equals(symbol)) {
                index = i;
                break;
            }
        }
        return SLOT_SYMBOLS[Math.floorMod(index + offset, SLOT_SYMBOLS.length)];
    }

    private void openAntiScreenshareHudMenu() {
        VBox body = new VBox(12);
        body.getStyleClass().addAll("content", "screen");
        HBox header = submenuHeader("HUD Menu", this::openAntiScreenshare);
        Label state = label(backendString("antiScreenshareStatus"), "muted");
        state.setWrapText(true);
        Map<String, Object> hudInfo = backendMap("antiScreenshareHudInfo");
        boolean[] hudEnabled = { mapBoolean(hudInfo, "hudActive") };

        boolean[] includeActive = { true };
        boolean[] includeDisabled = { false };
        boolean[] includeSummary = { true };
        Button active = stateToggle("Active modules", includeActive[0]);
        Button disabled = stateToggle("Disabled modules", includeDisabled[0]);
        Button summary = stateToggle("Summary", includeSummary[0]);

        VBox preview = new VBox(8);
        preview.getStyleClass().add("card");
        Runnable updatePreview = () -> preview.getChildren().setAll(hudPreviewRows(includeActive[0], includeDisabled[0], includeSummary[0]));

        active.setOnAction(e -> {
            includeActive[0] = !includeActive[0];
            setStateToggle(active, includeActive[0]);
            updatePreview.run();
        });
        disabled.setOnAction(e -> {
            includeDisabled[0] = !includeDisabled[0];
            setStateToggle(disabled, includeDisabled[0]);
            updatePreview.run();
        });
        summary.setOnAction(e -> {
            includeSummary[0] = !includeSummary[0];
            setStateToggle(summary, includeSummary[0]);
            updatePreview.run();
        });

        Button hudToggle = stateToggle(hudEnabled[0] ? "HUD Enabled" : "HUD Disabled", hudEnabled[0]);
        hudToggle.setOnAction(e -> {
            boolean next = !hudEnabled[0];
            String result = backendString(next ? "enableAntiScreenshareHud" : "disableAntiScreenshareHud");
            if (!result.isBlank()) appendLog(result);
            hudEnabled[0] = backendBoolean("antiScreenshareHudEnabled");
            hudToggle.setText(hudEnabled[0] ? "HUD Enabled" : "HUD Disabled");
            setStateToggle(hudToggle, hudEnabled[0]);
            state.setText(backendString("antiScreenshareStatus"));
            updatePreview.run();
        });
        Button open = secondary("Open HUD Menu");
        open.setOnAction(e -> openAntiScreenshareHudInfoMenu(includeActive[0], includeDisabled[0], includeSummary[0]));

        VBox options = new VBox(10);
        options.getStyleClass().add("card");
        options.getChildren().addAll(label("HUD Display", "section-title"), label("Choose what the HUD menu shows.", "muted"), buttonRow(active, disabled, summary));

        updatePreview.run();
        body.getChildren().addAll(header, state, options, preview, buttonRow(hudToggle, open));
        showScreen(scrollScreen(body));
    }

    private List<Region> hudPreviewRows(boolean includeActive, boolean includeDisabled, boolean includeSummary) {
        List<Region> rows = new ArrayList<>();
        List<Map<String, Object>> modules = backendModuleList();
        long activeCount = modules.stream().filter(m -> mapBoolean(m, "active")).count();
        rows.add(label("Preview", "section-title"));
        if (includeSummary) rows.add(label(activeCount + " active modules, " + Math.max(0, modules.size() - activeCount) + " disabled modules.", "muted"));
        int shown = 0;
        for (Map<String, Object> module : modules) {
            boolean active = mapBoolean(module, "active");
            if (active && !includeActive) continue;
            if (!active && !includeDisabled) continue;
            rows.add(hudInfoRow(module));
            shown++;
            if (shown >= 3) {
                rows.add(label("Open HUD Menu for the full live view.", "muted"));
                break;
            }
        }
        if (shown == 0) rows.add(label("No modules match this view.", "muted"));
        return rows;
    }

    private void openAntiScreenshareHudInfoMenu(boolean includeActive, boolean includeDisabled, boolean includeSummary) {
        VBox body = new VBox(12);
        body.getStyleClass().addAll("content", "screen");
        body.getChildren().add(submenuHeader("HUD Info", this::openAntiScreenshareHudMenu));

        Label hudValue = label("-", "chip-value");
        Label fpsValue = label("-", "chip-value");
        Label serverValue = label("-", "chip-value");
        Label coordsValue = label("-", "chip-value");
        Label playersValue = label("-", "chip-value");
        Label timeValue = label("-", "chip-value");

        TilePane liveGrid = new TilePane();
        liveGrid.setHgap(10);
        liveGrid.setVgap(10);
        liveGrid.setPrefColumns(3);
        liveGrid.getChildren().addAll(
            chip("HUD", hudValue),
            chip("FPS / Ping", fpsValue),
            chip("Server", serverValue),
            chip("Coords", coordsValue),
            chip("Players", playersValue),
            chip("Time", timeValue)
        );

        VBox liveCard = new VBox(10, label("Live HUD", "section-title"), liveGrid);
        liveCard.getStyleClass().add("card");

        VBox modules = new VBox(8);
        modules.getStyleClass().add("card");
        modules.setPrefWidth(360);
        modules.getChildren().add(label("Active Modules", "section-title"));
        Label moduleSummary = label("", "muted");
        if (includeSummary) modules.getChildren().add(moduleSummary);
        VBox moduleRows = new VBox(6);
        ScrollPane moduleScroll = new ScrollPane(moduleRows);
        moduleScroll.setFitToWidth(true);
        moduleScroll.setPrefViewportHeight(420);
        moduleScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        moduleScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        moduleScroll.getStyleClass().add("module-scroll");
        modules.getChildren().add(moduleScroll);

        VBox itemRows = new VBox(6);
        ScrollPane itemScroll = new ScrollPane(itemRows);
        itemScroll.setFitToWidth(true);
        itemScroll.setPrefViewportHeight(420);
        itemScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        itemScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        itemScroll.getStyleClass().add("module-scroll");

        VBox signals = new VBox(8);
        signals.getStyleClass().add("card");
        signals.setPrefWidth(320);
        Label itemSummary = label("", "muted");
        signals.getChildren().addAll(label("HUD Items", "section-title"), itemSummary, itemScroll);

        HBox layout = new HBox(12, modules, signals);
        layout.setAlignment(Pos.TOP_LEFT);
        body.getChildren().addAll(liveCard, layout);
        showScreen(scrollScreen(body));

        Runnable refreshHudInfo = () -> {
            Map<String, Object> hudInfo = backendMap("antiScreenshareHudInfo");
            Map<String, Object> live = mapObject(hudInfo, "live");
            List<Map<String, Object>> items = mapList(hudInfo, "items");
            List<Map<String, Object>> all = backendModuleList();

            hudValue.setText(mapBoolean(hudInfo, "hudActive") ? "Enabled" : "Disabled");
            fpsValue.setText(valueText(live, "fps") + " / " + valueText(live, "ping") + "ms");
            serverValue.setText(valueText(live, "server"));
            coordsValue.setText(valueText(live, "coords"));
            playersValue.setText(valueText(live, "players"));
            timeValue.setText(valueText(live, "time"));

            long activeCount = all.stream().filter(m -> mapBoolean(m, "active")).count();
            moduleSummary.setText(activeCount + " active modules, " + Math.max(0, all.size() - activeCount) + " disabled modules.");
            moduleRows.getChildren().clear();
            int shown = 0;
            for (Map<String, Object> module : all) {
                boolean moduleActive = mapBoolean(module, "active");
                if (moduleActive && !includeActive) continue;
                if (!moduleActive && !includeDisabled) continue;
                moduleRows.getChildren().add(hudInfoRow(module));
                shown++;
                if (shown >= 18) break;
            }
            if (shown == 0) moduleRows.getChildren().add(label("No modules match this view.", "muted"));

            long activeItems = items.stream().filter(item -> mapBoolean(item, "active")).count();
            itemSummary.setText(activeItems + " active of " + items.size());
            itemRows.getChildren().clear();
            for (Map<String, Object> item : items) {
                if (!mapBoolean(item, "active")) continue;
                itemRows.getChildren().add(hudItemRow(item));
            }
            if (itemRows.getChildren().isEmpty()) itemRows.getChildren().add(label("No HUD items are active.", "muted"));
        };

        refreshHudInfo.run();
        hudInfoRefresh = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshHudInfo.run()));
        hudInfoRefresh.setCycleCount(Timeline.INDEFINITE);
        hudInfoRefresh.play();
    }

    private HBox hudInfoRow(Map<String, Object> module) {
        String name = mapString(module, "name");
        String title = mapString(module, "title").isBlank() ? name : mapString(module, "title");
        String state = mapBoolean(module, "active") ? "Enabled" : "Disabled";
        HBox row = new HBox(10);
        row.getStyleClass().add("account-row");
        row.setAlignment(Pos.CENTER_LEFT);
        VBox text = new VBox(2, label(title, "module-title"), label(name, "module-description"));
        HBox.setHgrow(text, Priority.ALWAYS);
        row.getChildren().addAll(text, label(state, "module-meta"));
        return row;
    }

    private HBox hudItemRow(Map<String, Object> item) {
        String name = mapString(item, "name");
        String title = mapString(item, "title").isBlank() ? name : mapString(item, "title");
        String group = mapString(item, "group").isBlank() ? "HUD" : mapString(item, "group");
        String pos = valueText(item, "x") + ", " + valueText(item, "y");
        HBox row = new HBox(10);
        row.getStyleClass().add("account-row");
        row.setAlignment(Pos.CENTER_LEFT);
        VBox text = new VBox(2, label(title, "module-title"), label(group + " · " + pos, "module-description"));
        HBox.setHgrow(text, Priority.ALWAYS);
        row.getChildren().addAll(text, label("Active", "module-meta"));
        return row;
    }

    private VBox antiScreenshareModulePanel(List<Map<String, Object>> modules, Runnable refresh) {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("card");

        TextField search = new TextField();
        search.setPromptText("Search modules");
        search.setMaxWidth(Double.MAX_VALUE);
        boolean[] activeOnly = { false };
        String[] selectedCategory = { "All" };
        Button activeOnlyButton = stateToggle("Active only", activeOnly[0]);
        Label count = label("", "muted");

        LinkedHashSet<String> categories = new LinkedHashSet<>();
        categories.add("All");
        for (Map<String, Object> module : modules) {
            String value = mapString(module, "category");
            if (!value.isBlank()) categories.add(value);
        }

        HBox categoryTabs = new HBox(0);
        categoryTabs.getStyleClass().add("folder-tabs");
        categoryTabs.setAlignment(Pos.CENTER_LEFT);
        List<Button> categoryButtons = new ArrayList<>();
        Runnable[] applyFilterRef = new Runnable[1];
        for (String value : categories) {
            Button tab = stateToggle(value, value.equals(selectedCategory[0]));
            tab.getStyleClass().add("folder-tab");
            tab.setOnAction(e -> {
                selectedCategory[0] = value;
                for (Button button : categoryButtons) setStateToggle(button, button.getText().equals(selectedCategory[0]));
                if (applyFilterRef[0] != null) applyFilterRef[0].run();
            });
            categoryButtons.add(tab);
            categoryTabs.getChildren().add(tab);
        }

        HBox filters = new HBox(10, search, activeOnlyButton);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);

        TilePane grid = new TilePane();
        grid.getStyleClass().add("module-grid");
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPrefColumns(3);
        grid.setTileAlignment(Pos.TOP_LEFT);

        Runnable applyFilter = () -> {
            String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
            grid.getChildren().clear();

            int shown = 0;
            for (Map<String, Object> module : modules) {
                boolean active = mapBoolean(module, "active");
                if (activeOnly[0] && !active) continue;
                if (!"All".equals(selectedCategory[0]) && !selectedCategory[0].equals(mapString(module, "category"))) continue;

                String haystack = (mapString(module, "title") + " " + mapString(module, "name") + " "
                    + mapString(module, "description") + " " + mapString(module, "info")).toLowerCase(Locale.ROOT);
                if (!query.isBlank() && !haystack.contains(query)) continue;

                grid.getChildren().add(antiScreenshareModuleTile(module, refresh));
                shown++;
            }
            count.setText(shown == modules.size() ? shown + " modules" : shown + " of " + modules.size() + " modules");
        };
        applyFilterRef[0] = applyFilter;

        search.textProperty().addListener((obs, old, value) -> applyFilter.run());
        activeOnlyButton.setOnAction(e -> {
            activeOnly[0] = !activeOnly[0];
            setStateToggle(activeOnlyButton, activeOnly[0]);
            applyFilter.run();
        });
        applyFilter.run();

        ScrollPane moduleScroll = new ScrollPane(grid);
        moduleScroll.setFitToWidth(true);
        moduleScroll.setPrefViewportHeight(520);
        moduleScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        moduleScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        moduleScroll.getStyleClass().add("module-scroll");

        panel.getChildren().addAll(label("Modules", "section-title"), filters, categoryTabs, count, moduleScroll);
        return panel;
    }

    private VBox antiScreenshareModuleTile(Map<String, Object> module, Runnable refresh) {
        String name = mapString(module, "name");
        String title = mapString(module, "title").isBlank() ? name : mapString(module, "title");
        String category = mapString(module, "category");
        String description = mapString(module, "description");
        if (description.isBlank()) description = mapString(module, "info");

        VBox tile = new VBox(7);
        tile.getStyleClass().add("module-tile");
        if (mapBoolean(module, "active")) tile.getStyleClass().add("active");

        Label titleLabel = label(title, "module-title");
        titleLabel.setWrapText(true);
        Label meta = label(category.isBlank() ? "Client" : category, "module-meta");
        Label detail = label(antiScreenshareDescription(description.isBlank() ? name : description), "module-description");
        detail.setWrapText(true);

        boolean active = mapBoolean(module, "active");
        Button toggle = stateToggle(active ? "Enabled" : "Disabled", active);
        toggle.setOnAction(e -> {
            toggle.setDisable(true);
            String result = backendString("toggleAntiScreenshareModule", name, !active);
            if (!result.isBlank()) appendLog(result);
            refresh.run();
        });

        VBox copy = new VBox(2, titleLabel, meta);
        HBox.setHgrow(copy, Priority.ALWAYS);
        HBox top = new HBox(10, copy, toggle);
        top.setAlignment(Pos.CENTER_LEFT);
        tile.getChildren().addAll(top, detail);
        tile.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                openAntiScreenshareModuleSettingsMenu(module);
                e.consume();
            }
        });
        return tile;
    }

    private String antiScreenshareDescription(String text) {
        String clean = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (clean.length() <= 86) return clean;
        return clean.substring(0, 83).trim() + "...";
    }

    private void openAntiScreenshareModuleSettingsMenu(Map<String, Object> module) {
        String name = mapString(module, "name");
        String title = mapString(module, "title").isBlank() ? name : mapString(module, "title");
        String category = mapString(module, "category").isBlank() ? "Client" : mapString(module, "category");
        String description = mapString(module, "description");
        if (description.isBlank()) description = mapString(module, "info");

        VBox body = new VBox(12);
        body.getStyleClass().addAll("content", "screen");
        body.getChildren().add(submenuHeader(title + " Settings", this::openAntiScreenshare));

        VBox info = new VBox(8);
        info.getStyleClass().add("card");
        Label detail = label(description.isBlank() ? "No settings description is available for this module." : description, "muted");
        detail.setWrapText(true);
        info.getChildren().addAll(
            label("Module", "section-title"),
            detail,
            chip("Name", name),
            chip("Category", category),
            chip("State", mapBoolean(module, "active") ? "Enabled" : "Disabled")
        );

        VBox metadata = new VBox(8);
        metadata.getStyleClass().add("card");
        metadata.getChildren().add(label("Available Metadata", "section-title"));
        boolean anyMetadata = false;
        for (Map.Entry<String, Object> entry : module.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if ("name".equals(key) || "title".equals(key) || "description".equals(key) || "category".equals(key) || "active".equals(key)) continue;
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            if (value.isBlank()) continue;
            metadata.getChildren().add(chip(key, value));
            anyMetadata = true;
        }
        if (!anyMetadata) metadata.getChildren().add(label("The live bridge is not exporting per-setting controls for this module yet.", "muted"));

        VBox settingsPanel = moduleSettingsPanel(name, module);

        Button enable = primary("Enable");
        enable.setOnAction(e -> {
            String result = backendString("toggleAntiScreenshareModule", name, true);
            if (!result.isBlank()) appendLog(result);
            openAntiScreenshare();
        });
        Button disable = secondary("Disable");
        disable.setOnAction(e -> {
            String result = backendString("toggleAntiScreenshareModule", name, false);
            if (!result.isBlank()) appendLog(result);
            openAntiScreenshare();
        });
        Button save = secondary("Save Config");
        save.setOnAction(e -> {
            String result = backendString("saveAntiScreenshareConfig");
            if (!result.isBlank()) appendLog(result);
        });
        body.getChildren().addAll(info, settingsPanel, metadata, buttonRow(enable, disable, save));
        showScreen(scrollScreen(body));
    }

    private VBox moduleSettingsPanel(String moduleName, Map<String, Object> module) {
        VBox panel = new VBox(8);
        panel.getStyleClass().add("card");
        panel.getChildren().add(label("Settings", "section-title"));

        List<Map<String, Object>> settings = mapListOfMaps(module, "settings");
        if (settings.isEmpty()) {
            panel.getChildren().add(label("No simple live settings are exported for this module yet.", "muted"));
            return panel;
        }

        for (Map<String, Object> setting : settings) {
            panel.getChildren().add(moduleSettingRow(moduleName, setting));
        }
        return panel;
    }

    private Region moduleSettingRow(String moduleName, Map<String, Object> setting) {
        String settingName = mapString(setting, "name");
        String title = mapString(setting, "title").isBlank() ? settingName : mapString(setting, "title");
        String type = mapString(setting, "type").isBlank() ? "setting" : mapString(setting, "type");
        String group = mapString(setting, "group").isBlank() ? "General" : mapString(setting, "group");
        String value = mapString(setting, "value");
        boolean editable = mapBoolean(setting, "editable");

        VBox row = new VBox(6);
        row.getStyleClass().add("account-row");

        Label titleLabel = label(title, "module-title");
        titleLabel.setWrapText(true);
        row.getChildren().addAll(titleLabel, label(group + " | " + type + (mapBoolean(setting, "changed") ? " | changed" : ""), "module-meta"));

        String description = mapString(setting, "description");
        if (!description.isBlank()) {
            Label detail = label(description, "module-description");
            detail.setWrapText(true);
            row.getChildren().add(detail);
        }

        if (!editable) {
            row.getChildren().add(chip("Current", value));
            return row;
        }

        if ("bool".equals(type)) {
            boolean[] current = { "true".equalsIgnoreCase(value) || "1".equals(value) || "on".equalsIgnoreCase(value) };
            Button toggle = stateToggle(current[0] ? "Enabled" : "Disabled", current[0]);
            toggle.setOnAction(e -> {
                boolean next = !current[0];
                String result = backendString("setAntiScreenshareModuleSetting", moduleName, settingName, Boolean.toString(next));
                if (!result.isBlank()) appendLog(result);
                if (result.startsWith(settingName + " ")) {
                    current[0] = next;
                    toggle.setText(current[0] ? "Enabled" : "Disabled");
                    setStateToggle(toggle, current[0]);
                }
            });
            row.getChildren().add(toggle);
            return row;
        }

        HBox controls = new HBox(8);
        controls.setAlignment(Pos.CENTER_LEFT);
        Region input;
        List<String> suggestions = mapStringList(setting, "suggestions");
        if (!suggestions.isEmpty()) {
            ComboBox<String> combo = new ComboBox<>();
            combo.getItems().addAll(suggestions);
            combo.setValue(value.isBlank() ? suggestions.get(0) : value);
            combo.setMaxWidth(Double.MAX_VALUE);
            input = combo;
        }
        else {
            TextField field = new TextField(value);
            field.setMaxWidth(Double.MAX_VALUE);
            input = field;
        }

        Button apply = secondary("Apply");
        apply.setOnAction(e -> {
            String next = input instanceof ComboBox
                ? String.valueOf(((ComboBox<?>) input).getValue())
                : ((TextField) input).getText();
            String result = backendString("setAntiScreenshareModuleSetting", moduleName, settingName, next);
            if (!result.isBlank()) appendLog(result);
        });

        HBox.setHgrow(input, Priority.ALWAYS);
        controls.getChildren().addAll(input, apply);
        row.getChildren().add(controls);
        return row;
    }

    private String backendString(String name, Object... args) {
        final Object[] result = new Object[1];
        runSwing(() -> result[0] = call(name, args));
        return result[0] == null ? "" : String.valueOf(result[0]);
    }

    private boolean backendBoolean(String name, Object... args) {
        final Object[] result = new Object[1];
        runSwing(() -> result[0] = call(name, args));
        return Boolean.TRUE.equals(result[0]);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> backendMap(String name, Object... args) {
        final Object[] result = new Object[1];
        runSwing(() -> result[0] = call(name, args));
        if (result[0] instanceof Map) return (Map<String, Object>) result[0];
        return new java.util.LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> backendModuleList() {
        final Object[] result = new Object[1];
        runSwing(() -> result[0] = call("antiScreenshareModules"));
        if (result[0] instanceof List) return (List<Map<String, Object>>) result[0];
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> backendCommunityConfigs() {
        final Object[] result = new Object[1];
        runSwing(() -> result[0] = call("communityConfigs"));
        if (result[0] instanceof List) return (List<Map<String, Object>>) result[0];
        return new ArrayList<>();
    }

    private String mapString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private boolean mapBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private int mapInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapObject(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) return (Map<String, Object>) value;
        return new java.util.LinkedHashMap<>();
    }

    private List<Map<String, Object>> mapList(Map<String, Object> map, String key) {
        return mapListOfMaps(map, key);
    }

    private String valueText(Map<String, Object> map, String key) {
        String value = mapString(map, key);
        return value.isBlank() ? "-" : value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapListOfMaps(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof List)) return new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map) out.add((Map<String, Object>) item);
        }
        return out;
    }

    private List<String> mapStringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof List)) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (Object item : (List<?>) value) {
            String text = item == null ? "" : String.valueOf(item);
            if (!text.isBlank()) out.add(text);
        }
        return out;
    }

    private VBox addField(GridPane form, String title, Region input, int col, int row) {
        return addField(form, title, input, col, row, 1);
    }

    private VBox addField(GridPane form, String title, Region input, int col, int row, int span) {
        VBox box = new VBox(6);
        box.getChildren().addAll(label(title, "field-label"), input);
        input.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(box, Priority.ALWAYS);
        form.add(box, col, row, span, 1);
        return box;
    }

    private VBox controlField(String title, Region input) {
        VBox box = new VBox(6);
        box.getChildren().addAll(label(title, "field-label"), input);
        input.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private ComboBox<String> combo(String[] values) {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll(values);
        combo.getSelectionModel().select(0);
        combo.setMaxWidth(Double.MAX_VALUE);
        return combo;
    }

    private Button primary(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("primary");
        button.setMnemonicParsing(false);
        button.setWrapText(true);
        button.setMinHeight(40);
        return button;
    }

    private Button secondary(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary");
        button.setMnemonicParsing(false);
        button.setWrapText(true);
        button.setMinHeight(40);
        return button;
    }

    private Button actionButton(String text, Runnable action) {
        Button button = secondary(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(e -> action.run());
        return button;
    }

    private Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private VBox chip(String name, String value) {
        return chip(name, label(value, "chip-value"));
    }

    private VBox chip(String name, Region value) {
        VBox chip = new VBox(3, label(name, "chip-name"), value);
        chip.getStyleClass().add("chip");
        return chip;
    }

    private VBox versionChip(String name, Label installed, Label released) {
        VBox chip = new VBox(4, label(name, "chip-name"), installed, released);
        chip.getStyleClass().addAll("chip", "version-chip");
        return chip;
    }

    private Label chipLine(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("chip-line");
        return label;
    }

    private Region spacer() {
        Region region = new Region();
        VBox.setVgrow(region, Priority.ALWAYS);
        return region;
    }

    private void runBackend(String method, Object... args) {
        runSwing(() -> call(method, args));
    }

    private void runSwing(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                runnable.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Object call(String name, Object... args) {
        try {
            Method method = findMethod(name, args);
            method.setAccessible(true);
            return method.invoke(backend, args);
        } catch (Exception e) {
            appendLog(name + " failed: " + rootMessage(e));
            return null;
        }
    }

    private Method findMethod(String name, Object[] args) throws NoSuchMethodException {
        for (Method method : Main.class.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            return method;
        }
        throw new NoSuchMethodException(name);
    }

    private Object field(String name) {
        try {
            Field field = Main.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(backend);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String swingText(String field) {
        return ((javax.swing.text.JTextComponent) field(field)).getText();
    }

    private String labelText(String field) {
        return ((javax.swing.JLabel) field(field)).getText();
    }

    private boolean swingCheckBoxSelected(String field) {
        return ((javax.swing.JCheckBox) field(field)).isSelected();
    }

    private Object selectedBackendProfile() {
        JComboBox<?> box = (JComboBox<?>) field("profileBox");
        return box.getSelectedItem();
    }

    private File modsFolder() {
        syncToBackend();
        return backendFile("getModsFolder");
    }

    private File resourcePacksFolder() {
        syncToBackend();
        return backendFile("getResourcePacksFolder", selectedBackendProfile());
    }

    private File profileFolder() {
        syncToBackend();
        return backendFile("getMinecraftFolder");
    }

    private File backendFile(String method, Object... args) {
        Object value = call(method, args);
        if (value instanceof File file) return file;
        throw new IllegalStateException(method + " did not return a folder");
    }

    private java.util.List<ModFile> readMods() {
        File mods = modsFolder();
        File[] files = mods.listFiles();
        java.util.List<ModFile> result = new java.util.ArrayList<>();
        if (files == null) return result;
        int profile = profileBox.getSelectionModel().getSelectedIndex();
        for (File file : files) {
            String lower = file.getName().toLowerCase(Locale.ROOT);
            boolean enabled = lower.endsWith(".jar");
            boolean disabled = lower.endsWith(".jar.disabled");
            if (!file.isFile() || (!enabled && !disabled)) continue;
            boolean locked = (profile == 0 && (isGambleClientJar(lower) || isGambleClientLoaderJar(lower)))
                || ((profile == 0 || profile == 2) && (isFabricApiJar(lower) || isModMenuJar(lower)));
            result.add(new ModFile(file, enabled, locked));
        }
        result.sort(java.util.Comparator.comparing(value -> value.file.getName().toLowerCase(Locale.ROOT)));
        return result;
    }

    private java.util.List<ModFile> readResourcePacks() {
        File[] files = resourcePacksFolder().listFiles();
        java.util.List<ModFile> result = new java.util.ArrayList<>();
        if (files == null) return result;
        for (File file : files) {
            String lower = file.getName().toLowerCase(Locale.ROOT);
            boolean enabled = isEnabledResourcePack(file);
            boolean disabled = lower.endsWith(".zip.disabled") || lower.endsWith(".disabled");
            if (!isResourcePackLikeFile(file) && !disabled) continue;
            result.add(new ModFile(file, enabled, false));
        }
        result.sort(java.util.Comparator.comparing(value -> value.file.getName().toLowerCase(Locale.ROOT)));
        return result;
    }

    private boolean isGambleClientJar(String name) {
        return name.startsWith("cg-client") || name.startsWith("cg-mod");
    }

    private boolean isGambleClientLoaderJar(String name) {
        return name.equals("gamble-client-loader.jar") || name.equals("gamble-client-loader.jar.disabled");
    }

    private boolean isFabricApiJar(String name) {
        return name.startsWith("fabric-api-");
    }

    private boolean isModMenuJar(String name) {
        return name.startsWith("modmenu-");
    }

    private boolean isJarLikeFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return file.isFile() && (name.endsWith(".jar") || name.endsWith(".jar.disabled"));
    }

    private boolean isResourcePackLikeFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return (file.isFile() && (name.endsWith(".zip") || name.endsWith(".zip.disabled"))) || file.isDirectory();
    }

    private boolean isEnabledResourcePack(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return file.isDirectory() ? !name.endsWith(".disabled") : name.endsWith(".zip");
    }

    private void setResourcePackEnabled(File file, boolean enabled) throws IOException {
        File options = new File(profileFolder(), "options.txt");
        java.util.List<String> lines = options.isFile()
            ? Files.readAllLines(options.toPath(), java.nio.charset.StandardCharsets.UTF_8)
            : new java.util.ArrayList<>();
        String packName = enabledResourcePackName(file);
        String entry = "file/" + packName;
        boolean found = false;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).startsWith("resourcePacks:")) continue;
            java.util.List<String> packs = parseResourcePackList(lines.get(i).substring("resourcePacks:".length()));
            packs.removeIf(value -> value.equals(entry) || value.equals("file/" + disabledResourcePackName(file)));
            if (enabled) packs.add(entry);
            lines.set(i, "resourcePacks:" + encodeResourcePackList(packs));
            found = true;
            break;
        }
        if (!found && enabled) lines.add("resourcePacks:" + encodeResourcePackList(java.util.List.of(entry)));
        if (!lines.stream().anyMatch(line -> line.startsWith("incompatibleResourcePacks:"))) {
            lines.add("incompatibleResourcePacks:[]");
        }
        Files.write(options.toPath(), lines, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String enabledResourcePackName(File file) {
        String name = file.getName();
        return name.endsWith(".disabled") ? name.substring(0, name.length() - ".disabled".length()) : name;
    }

    private String disabledResourcePackName(File file) {
        String name = file.getName();
        return name.endsWith(".disabled") ? name : name + ".disabled";
    }

    private java.util.List<String> parseResourcePackList(String raw) {
        java.util.List<String> packs = new java.util.ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(raw);
        while (matcher.find()) packs.add(matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        return packs;
    }

    private String encodeResourcePackList(java.util.List<String> packs) {
        return packs.stream()
            .distinct()
            .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private void copyDirectory(java.nio.file.Path source, java.nio.file.Path target) throws IOException {
        try (java.util.stream.Stream<java.nio.file.Path> stream = Files.walk(source)) {
            for (java.nio.file.Path path : stream.toList()) {
                java.nio.file.Path relative = source.relativize(path);
                java.nio.file.Path dest = target.resolve(relative);
                if (Files.isDirectory(path)) Files.createDirectories(dest);
                else Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void openBackendFile(String method) {
        Object file = call(method);
        if (file instanceof File) openFile((File) file);
    }

    private void openFile(File file) {
        Thread thread = new Thread(() -> {
            try {
                if (!file.exists() && !file.mkdirs()) {
                    throw new IllegalStateException("Failed to create folder: " + file);
                }

                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(file);
                    return;
                }

                new ProcessBuilder("xdg-open", file.getAbsolutePath()).start();
            } catch (Throwable e) {
                appendLog("Open failed: " + rootMessage(e));
            }
        }, "open-folder");
        thread.setDaemon(true);
        thread.start();
    }

    private void openUrl(String url) {
        Thread thread = new Thread(() -> {
            try {
                if (!allowedBrowserUrl(url)) {
                    appendLog("Blocked an untrusted browser URL.");
                    return;
                }
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create(url));
                    return;
                }
                new ProcessBuilder("xdg-open", url).start();
            } catch (Throwable e) {
                appendLog("Open failed: " + rootMessage(e));
            }
        }, "open-url");
        thread.setDaemon(true);
        thread.start();
    }

    private boolean allowedBrowserUrl(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equals(scheme) || uri.getUserInfo() != null || host.isEmpty() || uri.getPort() != -1) return false;
            return Set.of(
                "gambleclient.org",
                "dash.gambleclient.org",
                "admin.gambleclient.org",
                "profile.gambleclient.org",
                "gamble-client.store",
                "dash.gamble-client.store",
                "admin.gamble-client.store",
                "profile.gamble-client.store",
                "login.microsoftonline.com",
                "microsoft.com",
                "www.microsoft.com",
                "discord.gg"
            ).contains(host);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void appendLog(String message) {
        String visible = sanitizeVisibleMessage(message);
        if (logLines == null) {
            System.err.println(visible);
            return;
        }
        Platform.runLater(() -> {
            lastLog = lastLog.isEmpty() ? visible : lastLog + "\n" + visible;
            renderLogText(lastLog);
        });
    }

    private void renderLogText(String text) {
        logLines.getChildren().clear();
        String[] lines = (text == null ? "" : text).split("\\R", -1);
        int start = Math.max(0, lines.length - 120);
        for (int i = start; i < lines.length; i++) {
            if (lines[i].isEmpty()) continue;
            Label line = new Label(lines[i]);
            line.setWrapText(true);
            line.setMaxWidth(Double.MAX_VALUE);
            line.getStyleClass().addAll("log-line", logSeverity(lines[i]));
            logLines.getChildren().add(line);
        }
        Platform.runLater(() -> logScroll.setVvalue(1.0));
    }

    private String logSeverity(String line) {
        String value = line == null ? "" : line.toLowerCase(Locale.ROOT);
        if (value.matches(".*\\b(error|failed|failure|fatal|exception|crash|broken|denied|invalid)\\b.*")) return "error";
        if (value.matches(".*\\b(warn|warning|retry|stale|missing|unavailable|offline)\\b.*")) return "warning";
        return "normal";
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return sanitizeVisibleMessage(current.getMessage() == null ? current.toString() : current.getMessage());
    }

    private String sanitizeVisibleMessage(String message) {
        String value = message == null ? "Launcher status updated." : message.replace('\0', ' ').trim();
        value = value
            .replaceAll("(?i)([?&](?:token|code|ticket|session|signature)=)[^&\\s]+", "$1[private]")
            .replaceAll("(?i)https?://[^\\s]+\\?[^\\s]+", "[secure link]")
            .replaceAll("[A-Za-z]:\\\\(?:[^\\r\\n:*?\"<>|]+\\\\)*[^\\r\\n:*?\"<>|]*", "[launcher files]")
            .replaceAll("(^|\\s)/(?:[^\\s/]+/)+[^\\s]*", "$1[launcher files]")
            .replaceAll("(?:[A-Za-z_$][\\w$]*\\.){2,}[A-Za-z_$][\\w$]*(?::\\d+)?", "launcher component")
            .replaceAll("(?i)\\bpid\\s+\\d+\\b", "game process")
            .replaceAll("\\s+", " ")
            .trim();
        if (value.isEmpty()) return "Launcher status updated.";
        return value.length() > 320 ? value.substring(0, 317).trim() + "..." : value;
    }

    private String objectFieldString(Object object, String name) {
        if (object == null) return "";
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(object);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception e) {
            return "";
        }
    }

    private boolean objectFieldBoolean(Object object, String name) {
        if (object == null) return false;
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(object);
            if (value instanceof Boolean bool) return bool;
            return "true".equalsIgnoreCase(String.valueOf(value));
        } catch (Exception e) {
            return false;
        }
    }

    private int objectFieldInt(Object object, String name) {
        return (int) objectFieldLong(object, name);
    }

    private long objectFieldLong(Object object, String name) {
        if (object == null) return 0;
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(object);
            if (value instanceof Number number) return number.longValue();
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private String buildIdForLabel(String label) {
        if (label == null) return "";
        if (label.startsWith("Media")) return "media";
        if (label.startsWith("Beta")) return "beta_plus";
        if (label.startsWith("Release")) return "release";
        if (label.startsWith("Ad")) return "ad_tier";
        return "";
    }

    private String bestBuildId(Object user) {
        if (canUseBuild(user, "media")) return "media";
        if (canUseBuild(user, "beta_plus")) return "beta_plus";
        if (canUseBuild(user, "release")) return "release";
        return "ad_tier";
    }

    private boolean canUseAntiScreenshare() {
        Object user = field("launcherUser");
        return canUseBuild(user, "beta_plus");
    }

    private boolean canUseBuild(Object user, String buildId) {
        if (user == null) return false;
        String status = objectFieldString(user, "accessStatus");
        String plan = objectFieldString(user, "selectedPlan");
        boolean owner = objectFieldBoolean(user, "ownerAccess") || "owner".equals(status) || "owner".equals(plan);
        boolean media = owner || objectFieldBoolean(user, "mediaAccess") || objectFieldBoolean(user, "testerAccess") || "media".equals(status) || "media".equals(plan) || "tester".equals(plan);
        boolean beta = media || objectFieldBoolean(user, "betaAccess") || "beta_plus".equals(status) || "beta_plus".equals(plan) || "lifetime_beta".equals(plan);
        boolean release = "owned".equals(status) || beta;
        boolean blocked = "banned".equals(status) || "revoked".equals(status);

        return switch (buildId) {
            case "media" -> media;
            case "beta_plus" -> beta;
            case "release" -> release;
            case "ad_tier" -> !blocked && !objectFieldString(user, "email").isBlank();
            default -> false;
        };
    }

    private final class BuildAccessCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("build-no-access", "build-best", "build-ad-tier");
            if (empty || item == null) {
                setText(null);
                return;
            }

            Object user = field("launcherUser");
            String buildId = buildIdForLabel(item);
            boolean allowed = user == null || canUseBuild(user, buildId);
            boolean best = user != null && buildId.equals(bestBuildId(user));

            setText("ad_tier".equals(buildId) ? "Ad Tier" : item);
            if ("ad_tier".equals(buildId)) getStyleClass().add("build-ad-tier");
            if (!allowed) getStyleClass().add("build-no-access");
            if (best) getStyleClass().add("build-best");
        }
    }

    private String defaultUsername() {
        return "Player";
    }

    private Image resourceImage(String path) {
        try {
            return new Image(getClass().getResourceAsStream(path));
        } catch (Exception e) {
            return null;
        }
    }

    private static final class ModFile {
        final File file;
        final String displayName;
        final boolean enabled;
        final boolean locked;

        ModFile(File file, boolean enabled, boolean locked) {
            this.file = file;
            this.displayName = displayName(file);
            this.enabled = enabled;
            this.locked = locked;
        }

        File toggleTarget() {
            if (enabled) return new File(file.getParentFile(), file.getName() + ".disabled");
            String name = file.getName();
            return new File(file.getParentFile(), name.substring(0, name.length() - ".disabled".length()));
        }

        @Override
        public String toString() {
            return (enabled ? "On  " : "Off ") + displayName + (locked ? "  (required)" : "");
        }

        private static String displayName(File file) {
            try (ZipFile zip = new ZipFile(file)) {
                ZipEntry entry = zip.getEntry("fabric.mod.json");
                if (entry == null) return file.getName();
                String json = new String(zip.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
                if (matcher.find()) return matcher.group(1);
            } catch (Exception ignored) {
                // Fall back to filename for non-Fabric jars or disabled files.
            }
            return file.getName();
        }
    }

    private static final class ModFileCell extends ListCell<ModFile> {
        @Override
        protected void updateItem(ModFile item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label state = new Label(item.enabled ? "ON" : "OFF");
            state.getStyleClass().addAll("mod-state", item.enabled ? "mod-state-on" : "mod-state-off");

            Label name = new Label(item.displayName);
            name.getStyleClass().add("mod-name");
            name.setMaxWidth(Double.MAX_VALUE);

            String detail = item.file.getName();
            if (item.locked && isGambleClientLoaderName(item.file.getName())) detail = "gamble-client-loader.jar is required";
            else if (item.locked) detail = item.file.getName() + " is required";
            Label path = new Label(detail);
            path.getStyleClass().add("mod-path");

            VBox copy = new VBox(2, name, path);
            HBox.setHgrow(copy, Priority.ALWAYS);

            HBox row = new HBox(12);
            row.getStyleClass().add("mod-row");
            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().addAll(state, copy);

            if (item.locked) {
                Label required = new Label("REQUIRED");
                required.getStyleClass().add("mod-required");
                row.getChildren().add(required);
            }

            setText(null);
            setGraphic(row);
        }

        private static boolean isGambleClientLoaderName(String name) {
            return name != null && name.equalsIgnoreCase("gamble-client-loader.jar");
        }
    }
}
