package com.gambleclient.launcher;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JScrollBar;
import javax.swing.ListSelectionModel;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.imageio.ImageIO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.awt.datatransfer.StringSelection;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Main {
    private static final Color BACKGROUND = new Color(18, 16, 22);
    private static final Color SURFACE = new Color(25, 22, 31);
    private static final Color SURFACE_2 = new Color(33, 29, 39);
    private static final Color FIELD = new Color(16, 17, 24);
    private static final Color LINE = new Color(62, 54, 70);
    private static final Color TEXT = new Color(255, 255, 255);
    private static final Color MUTED = new Color(150, 150, 150);
    private static final Color ACCENT = new Color(230, 146, 35);
    private static final Color ACCENT_DARK = new Color(155, 120, 95);
    private static final Color GOOD = new Color(45, 225, 45);
    private static final Color GOLD = new Color(255, 190, 80);
    private static final Color BLUE = new Color(104, 141, 187);
    private static final Color BABY_BLUE = new Color(45, 125, 245);
    private static final Color HOVER = new Color(38, 32, 42);
    private static final String SCREEN_LAUNCH = "launch";
    private static final String SCREEN_SETTINGS = "settings";
    private static final String LAUNCHER_VERSION = "0.1.96";
    private static final String LOADER_JAR_NAME = "gamble-client-loader.jar";
    private static final String COMPATIBILITY_DEFAULTS_MARKER_NAME = ".gamble-compat-disabled-by-default";
    private static final String[] ANTISCREENSHARE_CORE_ON = {"antiscreenshare"};
    private static final String[] ANTISCREENSHARE_SCOREBOARD_ON = {"hide-scoreboard"};
    private static final String[] ANTISCREENSHARE_SCOREBOARD_OFF = {"fake-scoreboard"};
    private static final String[] ANTISCREENSHARE_HUD_OFF = {"hud", "jamble-hud", "better-tab", "discord-presence", "big-spender-net-hud"};
    private static final String[] ANTISCREENSHARE_VISUAL_OFF = {
        "player-esp", "storage-esp", "block-esp", "item-esp", "trident-esp", "invis-esp",
        "chams", "nametags", "logout-spots", "trail", "tracers", "light-finder",
        "hole-tunnel-stair-esp", "tunnel-esp", "base-digger", "base-finder",
        "block-debug-finder", "block-update-finder"
    };

    private static final String DEFAULT_SITE_URL = "https://gamble-client.store";
    private static final String CAPE_OWNERS_PATH = "/api/capes/owners.txt";
    private static final String CAPES_PATH = "/api/capes/capes.txt";
    private static final String MINECRAFT_VERSION = "1.21.11";
    private static final String FABRIC_LOADER_VERSION = "0.18.4";
    private static final String MANAGED_CLIENT_MOD_ID = "cg-mod";
    private static final long MAX_DOWNLOAD_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_MANAGED_CLIENT_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_FABRIC_METADATA_BYTES = 1024L * 1024L;
    private static final String LICENSE_PLACEHOLDER = "paste-your-license-key-here";
    private static final String FABRIC_PROFILE_URL = "https://meta.fabricmc.net/v2/versions/loader/"
        + MINECRAFT_VERSION + "/" + FABRIC_LOADER_VERSION + "/profile/json";
    private static final String FABRIC_API_MODRINTH_URL = "https://api.modrinth.com/v2/project/fabric-api/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22"
        + MINECRAFT_VERSION + "%22%5D";
    private static final String MOD_MENU_MODRINTH_URL = "https://api.modrinth.com/v2/project/modmenu/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22"
        + MINECRAFT_VERSION + "%22%5D";
    private static final String CLOTH_CONFIG_MODRINTH_URL = "https://api.modrinth.com/v2/project/cloth-config/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22"
        + MINECRAFT_VERSION + "%22%5D";
    private static final String YACL_MODRINTH_URL = "https://api.modrinth.com/v2/project/yacl/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22"
        + MINECRAFT_VERSION + "%22%5D";
    private static final String FABRIC_LANGUAGE_KOTLIN_MODRINTH_URL = "https://api.modrinth.com/v2/project/fabric-language-kotlin/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22"
        + MINECRAFT_VERSION + "%22%5D";
    private static final String ARCHITECTURY_MODRINTH_URL = "https://api.modrinth.com/v2/project/architectury-api/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22"
        + MINECRAFT_VERSION + "%22%5D";
    private static final ManagedFabricMod[] COMPATIBILITY_MODS = {
        new ManagedFabricMod("Cloth Config", "cloth-config-", CLOTH_CONFIG_MODRINTH_URL, "cloth-config-",
            new String[] {"clothconfig", "cloth-config", "me/shedaniel/clothconfig", "me.shedaniel.clothconfig"}),
        new ManagedFabricMod("YetAnotherConfigLib", "yet_another_config_lib", YACL_MODRINTH_URL, "yacl-",
            new String[] {"yet_another_config_lib", "dev/isxander/yacl", "dev.isxander.yacl"}),
        new ManagedFabricMod("Fabric Language Kotlin", "fabric-language-kotlin-", FABRIC_LANGUAGE_KOTLIN_MODRINTH_URL, "fabric-language-kotlin-",
            new String[] {"fabric-language-kotlin", "kotlin/", "kotlin.", "kotlinx/", "kotlinx."}),
        new ManagedFabricMod("Architectury API", "architectury-", ARCHITECTURY_MODRINTH_URL, "architectury-",
            new String[] {"architectury", "dev/architectury", "dev.architectury"})
    };
    private static final String VERSION_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String ASSET_BASE_URL = "https://resources.download.minecraft.net/";
    private static final String MICROSOFT_DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String MICROSOFT_TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String MICROSOFT_AUTHORIZE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String MICROSOFT_SCOPE = "XboxLive.signin offline_access";
    private static final String DEFAULT_MICROSOFT_CLIENT_ID = "8eea0ae2-d0a9-4af1-88b9-f66bd96c94bd";
    private static final int AD_REWARD_SECONDS_FALLBACK = 30;
    private static final int MICROSOFT_REDIRECT_PORT = 39062;
    private static final String MICROSOFT_REDIRECT_URI = "http://localhost:" + MICROSOFT_REDIRECT_PORT + "/";
    private static final String XBOX_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MINECRAFT_LOGIN_URL = "https://api.minecraftservices.com/launcher/login";
    private static final String MINECRAFT_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final Build[] BUILDS = new Build[] {
        new Build("Release", "release"),
        new Build("Beta++", "beta_plus"),
        new Build("Media", "media"),
        new Build("Ad Tier", "ad_tier")
    };
    private static final LaunchProfile[] LAUNCH_PROFILES = new LaunchProfile[] {
        new LaunchProfile("gamble-client", "With Gamble Client", "Fabric profile with Gamble Client plus any compatible mods you add.", true, true, true),
        new LaunchProfile("vanilla", "Vanilla", "Clean Minecraft profile with no Fabric and no mods.", false, false, false),
        new LaunchProfile("fabric", "Fabric", "Fabric loader profile without Gamble Client. Fabric API is included for regular mods.", true, false, true)
    };

    private final JFrame frame = new JFrame("Gamble Client Launcher");
    private final CardLayout screenLayout = new CardLayout();
    private final JPanel screens = new JPanel(screenLayout);
    private final JComboBox<LaunchProfile> profileBox = new JComboBox<>(LAUNCH_PROFILES);
    private final JComboBox<Build> buildBox = new JComboBox<>(BUILDS);
    private final JTextField username = new JTextField(defaultUsername());
    private JPanel usernameBlock;
    private final JComboBox<Integer> memoryGb = new JComboBox<>(new Integer[] {2, 3, 4, 5, 6, 7, 8, 10, 12, 16});
    private final JTextField javaArgs = new JTextField("");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JTextPane log = new JTextPane();
    private final JTextArea runtimeInfo = new JTextArea();
    private final JLabel accountName = new JLabel("Not signed in");
    private final JLabel accountStatus = new JLabel("Launcher account required");
    private final JLabel adTitle = new JLabel("Sponsor Break");
    private final JLabel adStatus = new JLabel("Sign in to check access.");
    private final JLabel adMeta = new JLabel("Paid accounts skip launcher ads.");
    private final JButton adButton = new JButton("Check");
    private final JPanel signInPromptPanel = new RoundedPanel(new BorderLayout(14, 0), new Color(26, 25, 34, 235), new Color(255, 255, 255, 18), 8);
    private final JLabel signInPromptTitle = new JLabel("Sign in to continue");
    private final JLabel signInPromptText = new JLabel("Open the Gamble Client sign-in page, then return here to launch.");
    private final JButton promptSignInButton = new JButton("Sign In");
    private final JButton promptLaterButton = new JButton("Later");
    private final JButton signInButton = new JButton("Sign In");
    private final JButton signOutButton = new JButton("Sign Out");
    private final JButton copyLogButton = new JButton("Copy");
    private final JButton installButton = new JButton("Install / Update");
    private final JLabel updateStatus = new JLabel("Updates idle");
    private final JLabel launcherInstalledVersion = new JLabel("Installed: " + LAUNCHER_VERSION);
    private final JLabel launcherReleasedVersion = new JLabel("Released: checking...");
    private final JLabel clientInstalledVersion = new JLabel("Installed: none");
    private final JLabel clientReleasedVersion = new JLabel("Released: sign in to check");
    private final JCheckBox autoCheckUpdates = new JCheckBox("Check for launcher and client updates on launch");
    private final JButton accountManagerButton = new JButton("Accounts");
    private final JButton editProfileButton = new JButton("Edit Profile");
    private final JButton launchButton = new JButton("Launch");
    private final JButton settingsButton = new JButton();
    private final JButton settingsBackButton = new JButton("Back");
    private final JButton settingsGameFolderButton = new JButton("Game Folder");
    private final JButton settingsModsButton = new JButton("Manage Mods");
    private final JButton settingsResourcePacksButton = new JButton("Resource Packs");
    private final JButton settingsSiteButton = new JButton("Website");
    private final JLabel microsoftName = new JLabel("Offline session");
    private final JLabel microsoftStatus = new JLabel("Realms/profile auth disabled");
    private final JButton microsoftSignInButton = new JButton("Microsoft Sign In");
    private final JButton microsoftSignOutButton = new JButton("Sign Out");
    private final JButton modsButton = new JButton("Manage Mods");
    private final JButton resourcePacksButton = new JButton("Resource Packs");
    private final JButton siteButton = new JButton("Website");
    private String launcherToken = "";
    private MicrosoftAccount microsoftAccount;
    private volatile Process minecraftProcess;
    private volatile long minecraftProcessStartedAt;
    private volatile boolean minecraftStartupComplete;
    private volatile boolean minecraftFatalDetected;
    private volatile boolean minecraftStopRequested;
    private volatile String minecraftDetectedFailure = "";
    private volatile int minecraftOutputThreadsRunning;
    private volatile boolean captureLaunchLog;
    private final Object launchLogLock = new Object();
    private final Deque<String> recentLaunchLines = new ArrayDeque<>();
    private LauncherUser launcherUser;
    private LauncherAds launcherAds;
    private String sponsorChallenge = "";
    private SwingWorker<LauncherSession, Void> launcherSignInWorker;
    private SwingWorker<MicrosoftAccount, Void> microsoftSignInWorker;
    private volatile Runnable microsoftSignInCancel;
    private boolean crackedMode;
    private boolean startupPromptShown;
    private boolean signInPromptDismissed;
    private boolean startupUpdateCheckStarted;
    private boolean explicitBuildSelection;
    private boolean applyingAutomaticBuildSelection;

    public static void main(String[] args) throws UnsupportedLookAndFeelException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new Main().show();
            }
        });
    }

    private void show() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1100, 720));
        frame.setIconImages(appIconImages());
        frame.setContentPane(createRoot());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                maybePromptForSignIn();
            }
        });
    }

    private JPanel createRoot() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        screens.setOpaque(false);
        screens.add(createLaunchScreen(), SCREEN_LAUNCH);
        screens.add(createSettingsScreen(), SCREEN_SETTINGS);
        root.add(screens, BorderLayout.CENTER);

        styleControls();

        log("Ready. Sign in, then press Launch.");
        log("Managed game folder: " + getManagedMinecraftRoot().getAbsolutePath());
        log("Active profile folder: " + getMinecraftFolder().getAbsolutePath());
        refreshStoredLauncherSession();
        return root;
    }

    private JPanel createLaunchScreen() {
        JPanel launch = transparentPanel(new BorderLayout(20, 20));
        launch.add(createHeroPanel(), BorderLayout.WEST);
        launch.add(createMainPanel(), BorderLayout.CENTER);
        return launch;
    }

    private JPanel createHeroPanel() {
        JPanel hero = new GradientPanel();
        hero.setLayout(new BorderLayout());
        hero.setPreferredSize(new Dimension(300, 620));
        hero.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        JPanel copy = transparentPanel(new BorderLayout(0, 18));
        JLabel title = label("Gamble Client", 30, Font.BOLD, TEXT);
        JLabel subtitle = htmlLabel("Fast managed launches<br>with a clean game folder.", 14, MUTED);
        copy.add(title, BorderLayout.NORTH);
        copy.add(subtitle, BorderLayout.CENTER);

        JPanel chips = transparentPanel(new GridBagLayout());
        GridBagConstraints chip = new GridBagConstraints();
        chip.gridx = 0;
        chip.gridy = 0;
        chip.weightx = 1;
        chip.fill = GridBagConstraints.HORIZONTAL;
        chip.insets = new Insets(0, 0, 8, 0);
        chips.add(statChip("Minecraft", MINECRAFT_VERSION), chip);
        chip.gridy++;
        chips.add(statChip("Fabric Loader", FABRIC_LOADER_VERSION), chip);
        chip.gridy++;
        chips.add(statChip("Runtime", "Java " + Runtime.version().feature()), chip);
        chip.gridy++;
        chips.add(versionChip("Launcher", launcherInstalledVersion, launcherReleasedVersion), chip);
        chip.gridy++;
        chip.insets = new Insets(0, 0, 0, 0);
        chips.add(versionChip("Client", clientInstalledVersion, clientReleasedVersion), chip);

        JPanel sidebarBottom = transparentPanel(new BorderLayout(0, 14));
        sidebarBottom.add(createSponsorPanel(), BorderLayout.CENTER);
        sidebarBottom.add(createSidebarActionsPanel(), BorderLayout.SOUTH);

        hero.add(copy, BorderLayout.NORTH);
        hero.add(chips, BorderLayout.CENTER);
        hero.add(sidebarBottom, BorderLayout.SOUTH);
        return hero;
    }

    private JPanel createMainPanel() {
        JPanel main = transparentPanel(new GridBagLayout());
        main.setPreferredSize(new Dimension(740, 620));

        JPanel header = transparentPanel(new BorderLayout(14, 0));
        header.add(label("Launch Setup", 24, Font.BOLD, TEXT), BorderLayout.WEST);
        JPanel headerRight = transparentPanel(new BorderLayout(8, 0));
        headerRight.add(createAccountPanel(), BorderLayout.CENTER);
        headerRight.add(iconButton(settingsButton), BorderLayout.EAST);
        header.add(headerRight, BorderLayout.EAST);

        JPanel top = transparentPanel(new BorderLayout(0, 12));
        top.add(header, BorderLayout.NORTH);
        top.add(createSignInPromptPanel(), BorderLayout.CENTER);

        JPanel formCard = card(new BorderLayout(0, 16));
        formCard.setPreferredSize(new Dimension(360, 250));
        formCard.add(createFormPanel(), BorderLayout.CENTER);
        formCard.add(createActionsPanel(), BorderLayout.SOUTH);

        JPanel logCard = card(new BorderLayout(0, 10));
        logCard.setMinimumSize(new Dimension(360, 120));
        logCard.setPreferredSize(new Dimension(360, 210));
        JPanel logHead = transparentPanel(new BorderLayout(8, 0));
        JLabel logTitle = label("Game Log", 15, Font.BOLD, TEXT);
        logHead.add(logTitle, BorderLayout.WEST);
        logHead.add(copyLogButton, BorderLayout.EAST);
        logCard.add(logHead, BorderLayout.NORTH);

        log.setEditable(false);
        log.setPreferredSize(new Dimension(720, 150));
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        log.setForeground(new Color(214, 219, 229));
        log.setBackground(FIELD);
        log.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JScrollPane scrollPane = new JScrollPane(log);
        styleScrollPane(scrollPane);
        logCard.add(scrollPane, BorderLayout.CENTER);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 14, 0);
        main.add(top, gbc);

        gbc.gridy = 1;
        main.add(formCard, gbc);

        gbc.gridy = 2;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        main.add(logCard, gbc);

        return main;
    }

    private JPanel createSignInPromptPanel() {
        signInPromptPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(),
            BorderFactory.createEmptyBorder(14, 16, 14, 16)
        ));

        JPanel copy = transparentPanel(new BorderLayout(0, 4));
        signInPromptTitle.setForeground(TEXT);
        signInPromptTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        signInPromptText.setForeground(MUTED);
        signInPromptText.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        copy.add(signInPromptTitle, BorderLayout.NORTH);
        copy.add(signInPromptText, BorderLayout.SOUTH);

        JPanel actions = transparentPanel();
        promptSignInButton.setPreferredSize(new Dimension(96, 36));
        promptLaterButton.setPreferredSize(new Dimension(84, 36));
        actions.add(primaryButton(promptSignInButton));
        actions.add(secondaryButton(promptLaterButton));

        signInPromptPanel.add(copy, BorderLayout.CENTER);
        signInPromptPanel.add(actions, BorderLayout.EAST);
        return signInPromptPanel;
    }

    private JPanel createAccountPanel() {
        JPanel panel = transparentPanel(new BorderLayout(14, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        JPanel labels = transparentPanel(new BorderLayout(0, 2));
        accountName.setForeground(TEXT);
        accountName.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        accountStatus.setForeground(MUTED);
        accountStatus.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        labels.add(accountName, BorderLayout.NORTH);
        labels.add(accountStatus, BorderLayout.SOUTH);

        JPanel buttons = transparentPanel();
        JButton signIn = ghostButton(signInButton, true);
        JButton signOut = ghostButton(signOutButton, false);
        signIn.setPreferredSize(new Dimension(76, 32));
        signOut.setPreferredSize(new Dimension(82, 32));
        buttons.add(signIn);
        buttons.add(signOut);

        panel.add(labels, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.EAST);
        return panel;
    }

    private JPanel createSponsorPanel() {
        JPanel panel = new RoundedPanel(new BorderLayout(0, 10), new Color(26, 25, 34, 235), new Color(255, 255, 255, 18), 8);
        panel.setPreferredSize(new Dimension(252, 132));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(),
            BorderFactory.createEmptyBorder(14, 16, 14, 16)
        ));

        JPanel labels = transparentPanel(new BorderLayout(0, 5));
        adTitle.setForeground(TEXT);
        adTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        adStatus.setForeground(new Color(218, 223, 232));
        adStatus.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        adMeta.setForeground(MUTED);
        adMeta.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        labels.add(adTitle, BorderLayout.NORTH);
        labels.add(adStatus, BorderLayout.CENTER);
        labels.add(adMeta, BorderLayout.SOUTH);

        JButton action = secondaryButton(adButton);
        action.setPreferredSize(new Dimension(100, 36));
        panel.add(labels, BorderLayout.CENTER);
        panel.add(action, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createSidebarActionsPanel() {
        JPanel panel = transparentPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 8, 0);

        panel.add(label("Quick Links", 12, Font.BOLD, MUTED), gbc);
        gbc.gridy = 1;
        JButton antiScreenshare = secondaryButton(new JButton("AntiScreenshare"));
        antiScreenshare.setPreferredSize(new Dimension(100, 38));
        antiScreenshare.addActionListener(e -> showAntiScreenshareMenu());
        panel.add(antiScreenshare, gbc);
        gbc.gridy = 2;
        JButton mods = secondaryButton(modsButton);
        mods.setPreferredSize(new Dimension(100, 38));
        panel.add(mods, gbc);
        gbc.gridy = 3;
        JButton resourcePacks = secondaryButton(resourcePacksButton);
        resourcePacks.setPreferredSize(new Dimension(100, 38));
        panel.add(resourcePacks, gbc);
        gbc.gridy = 4;
        gbc.insets = new Insets(0, 0, 0, 0);
        JButton site = secondaryButton(siteButton);
        site.setPreferredSize(new Dimension(100, 38));
        panel.add(site, gbc);
        return panel;
    }

    private JPanel createFormPanel() {
        JPanel form = transparentPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 16, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        profileBox.setToolTipText("Switches between separate managed game folders and mods folders.");
        buildBox.setToolTipText("Tier selected for Gamble Client launches.");
        memoryGb.setToolTipText("Maximum Java memory in GB.");
        javaArgs.setToolTipText("Optional JVM arguments. Leave blank unless you know you need them.");

        username.setToolTipText("Player name for the local launch session.");
        addInputBlock(form, gbc, 0, 0, 1, "Profile", createProfileControl(), new Insets(0, 0, 10, 10));
        usernameBlock = addInputBlock(form, gbc, 1, 0, 1, "Username", username, new Insets(0, 0, 10, 0));

        JLabel profileNote = label("Gamble Client supports other Fabric mods in its profile mods folder.", 12, Font.PLAIN, MUTED);
        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(0, 0, 16, 0);
        form.add(profileNote, gbc);
        gbc.gridwidth = 1;

        progress.setStringPainted(true);
        progress.setString("Idle");
        progress.setPreferredSize(new Dimension(100, 30));
        addInputBlock(form, gbc, 0, 2, 2, "Progress", progress, new Insets(0, 0, 0, 0));

        return form;
    }

    private JPanel createProfileControl() {
        JPanel panel = transparentPanel(new BorderLayout(8, 0));
        panel.add(profileBox, BorderLayout.CENTER);
        JButton edit = secondaryButton(editProfileButton);
        edit.setPreferredSize(new Dimension(112, 44));
        panel.add(edit, BorderLayout.EAST);
        return panel;
    }

    private JPanel createActionsPanel() {
        JPanel buttons = transparentPanel(new BorderLayout(10, 0));
        JPanel right = transparentPanel();
        JPanel status = transparentPanel(new BorderLayout());

        updateStatus.setForeground(MUTED);
        updateStatus.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        status.add(updateStatus, BorderLayout.CENTER);

        right.add(secondaryButton(installButton));
        right.add(secondaryButton(accountManagerButton));
        right.add(primaryButton(launchButton));

        buttons.add(status, BorderLayout.CENTER);
        buttons.add(right, BorderLayout.EAST);
        return buttons;
    }

    private JPanel createSettingsScreen() {
        JPanel screen = transparentPanel(new BorderLayout(0, 18));

        JPanel header = transparentPanel(new BorderLayout(14, 0));
        JPanel titleCopy = transparentPanel(new BorderLayout(0, 4));
        titleCopy.add(label("Launcher Settings", 24, Font.BOLD, TEXT), BorderLayout.NORTH);
        titleCopy.add(label("Build, memory, launch arguments, and folders.", 13, Font.PLAIN, MUTED), BorderLayout.SOUTH);

        JButton back = secondaryButton(settingsBackButton);
        back.setPreferredSize(new Dimension(96, 42));
        header.add(back, BorderLayout.WEST);
        header.add(titleCopy, BorderLayout.CENTER);

        JPanel content = transparentPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 14, 0);
        content.add(settingsSection("Launch", createLaunchSettingsPanel()), gbc);

        gbc.gridy = 1;
        content.add(settingsSection("Updates", createUpdateSettingsPanel()), gbc);

        gbc.gridy = 2;
        content.add(settingsSection("Folders", createFolderSettingsPanel()), gbc);

        gbc.gridy = 3;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        content.add(settingsSection("Runtime", createRuntimeSettingsPanel()), gbc);

        screen.add(header, BorderLayout.NORTH);
        screen.add(content, BorderLayout.CENTER);
        return screen;
    }

    private JPanel createMicrosoftAccountPanel() {
        JPanel panel = transparentPanel(new BorderLayout(16, 0));

        JPanel labels = transparentPanel(new BorderLayout(0, 4));
        microsoftName.setForeground(TEXT);
        microsoftName.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        microsoftStatus.setForeground(MUTED);
        microsoftStatus.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        labels.add(microsoftName, BorderLayout.NORTH);
        labels.add(microsoftStatus, BorderLayout.SOUTH);

        JPanel actions = transparentPanel();
        JButton signIn = primaryButton(microsoftSignInButton);
        JButton signOut = secondaryButton(microsoftSignOutButton);
        signIn.setPreferredSize(new Dimension(164, 40));
        signOut.setPreferredSize(new Dimension(96, 40));
        actions.add(signIn);
        actions.add(signOut);

        panel.add(labels, BorderLayout.CENTER);
        panel.add(actions, BorderLayout.EAST);
        return panel;
    }

    private JPanel createLaunchSettingsPanel() {
        JPanel panel = transparentPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 12, 0);
        addSettingsField(panel, gbc, 0, "Build", buildBox);
        addSettingsField(panel, gbc, 1, "Memory (GB)", memoryGb);
        addSettingsField(panel, gbc, 2, "Java Args", javaArgs);
        return panel;
    }

    private JPanel createUpdateSettingsPanel() {
        JPanel panel = transparentPanel(new BorderLayout(0, 8));
        autoCheckUpdates.setToolTipText("Checks the launcher version endpoint and the selected Gamble Client build after the launcher opens.");
        panel.add(autoCheckUpdates, BorderLayout.NORTH);
        panel.add(label("Client checks use your launcher sign-in. Launcher self-update is reported, not auto-applied.", 12, Font.PLAIN, MUTED), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createFolderSettingsPanel() {
        JPanel panel = transparentPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 10);

        JButton gameFolder = secondaryButton(settingsGameFolderButton);
        JButton mods = secondaryButton(settingsModsButton);
        JButton resourcePacks = secondaryButton(settingsResourcePacksButton);
        JButton site = secondaryButton(settingsSiteButton);
        gameFolder.setPreferredSize(new Dimension(120, 40));
        mods.setPreferredSize(new Dimension(120, 40));
        resourcePacks.setPreferredSize(new Dimension(140, 40));
        site.setPreferredSize(new Dimension(120, 40));

        gbc.gridx = 0;
        panel.add(gameFolder, gbc);
        gbc.gridx = 1;
        panel.add(mods, gbc);
        gbc.gridx = 2;
        panel.add(resourcePacks, gbc);
        gbc.gridx = 3;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(site, gbc);
        return panel;
    }

    private JPanel createRuntimeSettingsPanel() {
        JPanel panel = transparentPanel(new BorderLayout(0, 10));
        runtimeInfo.setEditable(false);
        runtimeInfo.setLineWrap(true);
        runtimeInfo.setWrapStyleWord(true);
        runtimeInfo.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        runtimeInfo.setForeground(new Color(210, 195, 180));
        runtimeInfo.setBackground(FIELD);
        runtimeInfo.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        updateRuntimeInfo();
        panel.add(runtimeInfo, BorderLayout.CENTER);
        return panel;
    }

    private JPanel settingsSection(String title, JPanel body) {
        JPanel panel = card(new BorderLayout(0, 14));
        panel.add(label(title, 15, Font.BOLD, TEXT), BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private JPanel addInputBlock(JPanel form, GridBagConstraints gbc, int column, int row, int width, String title, java.awt.Component component, Insets insets) {
        JPanel block = transparentPanel(new BorderLayout(0, 8));
        block.add(label(title, 13, Font.BOLD, TEXT), BorderLayout.NORTH);
        component.setPreferredSize(new Dimension(140, 44));
        block.add(component, BorderLayout.CENTER);

        gbc.gridy = row;
        gbc.gridx = column;
        gbc.gridwidth = width;
        gbc.weightx = 1;
        gbc.insets = insets;
        form.add(block, gbc);
        gbc.gridwidth = 1;
        return block;
    }

    private void styleControls() {
        styleInput(profileBox);
        styleInput(buildBox);
        styleInput(username);
        styleInput(memoryGb);
        styleInput(javaArgs);
        autoCheckUpdates.setSelected(readAutoCheckUpdates());
        styleCheckBox(autoCheckUpdates);
        memoryGb.setSelectedItem(4);
        selectStoredProfile();
        selectStoredBuild();

        progress.setForeground(ACCENT);
        progress.setBackground(FIELD);
        progress.setBorder(BorderFactory.createLineBorder(LINE));
        progress.setUI(new RoundedProgressUi());
        styleButton(copyLogButton, SURFACE_2, TEXT);
        copyLogButton.setPreferredSize(new Dimension(78, 32));

        signInButton.addActionListener(e -> {
            if (launcherUser != null && !launcherToken.isEmpty()) switchLauncherAccount();
            else startSignIn();
        });
        signOutButton.addActionListener(e -> signOut());
        settingsButton.addActionListener(e -> showSettingsScreen());
        settingsBackButton.addActionListener(e -> showLaunchScreen());
        promptSignInButton.addActionListener(e -> toggleLauncherSignIn());
        promptLaterButton.addActionListener(e -> {
            signInPromptDismissed = true;
            updateAccountUi();
        });
        adButton.addActionListener(e -> startSponsorBreak());
        installButton.addActionListener(e -> installSelectedBuild(false));
        autoCheckUpdates.addActionListener(e -> saveAutoCheckUpdates(autoCheckUpdates.isSelected()));
        profileBox.addActionListener(e -> updateProfileUi(true));
        buildBox.addActionListener(e -> {
            if (!applyingAutomaticBuildSelection) {
                explicitBuildSelection = true;
                saveSelectedBuild((Build) buildBox.getSelectedItem());
            }
            refreshVersionPanel();
        });
        editProfileButton.addActionListener(e -> editSelectedProfile());
        accountManagerButton.addActionListener(e -> showAccountManagerMenu());
        launchButton.addActionListener(e -> launch());
        copyLogButton.addActionListener(e -> copyLauncherLog());
        modsButton.addActionListener(e -> showModsManager());
        resourcePacksButton.addActionListener(e -> showResourcePacksManager());
        siteButton.addActionListener(e -> open(siteUrl()));
        settingsGameFolderButton.addActionListener(e -> open(getMinecraftFolder()));
        settingsModsButton.addActionListener(e -> showModsManager());
        settingsResourcePacksButton.addActionListener(e -> showResourcePacksManager());
        settingsSiteButton.addActionListener(e -> open(siteUrl()));
        microsoftSignInButton.addActionListener(e -> startMicrosoftSignIn(true));
        microsoftSignOutButton.addActionListener(e -> signOutMicrosoft());
        microsoftAccount = readMicrosoftAccount();
        if (microsoftAccount != null && !microsoftAccount.name.isEmpty()) {
            username.setText(microsoftAccount.name);
            crackedMode = false;
        }
        updateMicrosoftUi();
        updateProfileUi(false);
        updateAccountUi();
        updateAdUi();
        refreshVersionPanel();
    }

    private void showSettingsScreen() {
        screenLayout.show(screens, SCREEN_SETTINGS);
        frame.revalidate();
        frame.repaint();
    }

    private void showLaunchScreen() {
        screenLayout.show(screens, SCREEN_LAUNCH);
        frame.revalidate();
        frame.repaint();
    }

    private LaunchProfile selectedProfile() {
        Object selected = profileBox.getSelectedItem();
        return selected instanceof LaunchProfile ? (LaunchProfile) selected : LAUNCH_PROFILES[0];
    }

    private void updateProfileUi(boolean persist) {
        LaunchProfile profile = selectedProfile();
        boolean gambleProfile = profile.includesGambleClient;
        boolean controlsEnabled = launchButton.isEnabled();
        profileBox.setToolTipText(profile.description);
        buildBox.setEnabled(gambleProfile && controlsEnabled);
        installButton.setEnabled(gambleProfile && controlsEnabled);
        buildBox.setToolTipText(gambleProfile ? "Tier selected for Gamble Client launches." : profile.description);
        installButton.setToolTipText(gambleProfile ? "Update the selected Gamble Client build." : profile.description);
        editProfileButton.setToolTipText(profile.fabric ? "Open the " + profile.label + " mods folder." : "Open the Vanilla profile folder.");
        modsButton.setToolTipText(profile.fabric ? "Enable or disable jars in the " + profile.label + " mods folder." : "Vanilla has no mods folder.");
        settingsModsButton.setToolTipText(profile.fabric ? "Enable or disable jars in the " + profile.label + " mods folder." : "Vanilla has no mods folder.");
        updateRuntimeInfo();
        refreshVersionPanel();
        if (persist) {
            saveSelectedProfile(profile);
            log("Selected profile: " + profile.label + ".");
            log("Profile folder: " + getMinecraftFolder(profile).getAbsolutePath());
        }
    }

    private void updateRuntimeInfo() {
        if (runtimeInfo == null) return;
        runtimeInfo.setText("Managed root: " + getManagedMinecraftRoot().getAbsolutePath() + "\n"
            + "Active profile: " + selectedProfile().label + "\n"
            + "Active profile folder: " + getMinecraftFolder().getAbsolutePath() + "\n"
            + "Active mods folder: " + getModsFolder().getAbsolutePath() + "\n"
            + "Minecraft: " + MINECRAFT_VERSION + "\n"
            + "Fabric Loader: " + FABRIC_LOADER_VERSION + "\n"
            + "Java: " + Runtime.version().feature());
    }

    private void editSelectedProfile() {
        LaunchProfile profile = selectedProfile();
        try {
            ensureProfileFolders(profile);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, e.getMessage(), "Profile", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (profile.fabric) {
            open(new File(getMinecraftFolder(profile), "mods"));
            log("Opened " + profile.label + " mods folder.");
            return;
        }

        JOptionPane.showMessageDialog(
            frame,
            "Vanilla launches without Fabric or mods. Opening the profile folder instead.",
            "Vanilla profile",
            JOptionPane.INFORMATION_MESSAGE
        );
        open(getMinecraftFolder(profile));
    }

    private void showModsManager() {
        LaunchProfile profile = selectedProfile();
        try {
            ensureProfileFolders(profile);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, e.getMessage(), "Mods", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!profile.fabric) {
            JOptionPane.showMessageDialog(frame, "Vanilla has no mods folder.", "Mods", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        File mods = new File(getMinecraftFolder(profile), "mods");
        if (!mods.exists() && !mods.mkdirs()) {
            JOptionPane.showMessageDialog(frame, "Failed to create mods folder: " + mods, "Mods", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(frame, profile.label + " Mods", true);
        JPanel root = card(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(LINE),
            BorderFactory.createEmptyBorder(16, 16, 16, 16)
        ));

        DefaultListModel<ModEntry> model = new DefaultListModel<>();
        JList<ModEntry> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(FIELD);
        list.setForeground(TEXT);
        list.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        list.setFixedCellHeight(32);
        JScrollPane scroll = new JScrollPane(list);
        styleScrollPane(scroll);
        scroll.setPreferredSize(new Dimension(520, 300));

        Runnable reload = () -> {
            model.clear();
            loadModEntries(profile, mods, model);
            if (!model.isEmpty()) list.setSelectedIndex(0);
        };
        reload.run();

        JPanel header = transparentPanel(new BorderLayout(0, 4));
        header.add(label("Mods", 18, Font.BOLD, TEXT), BorderLayout.NORTH);

        JPanel actions = transparentPanel();
        JButton toggle = secondaryButton(new JButton("Toggle"));
        JButton openFolder = secondaryButton(new JButton("Open Folder"));
        JButton close = primaryButton(new JButton("Done"));
        toggle.setPreferredSize(new Dimension(104, 38));
        openFolder.setPreferredSize(new Dimension(126, 38));
        close.setPreferredSize(new Dimension(92, 38));

        toggle.addActionListener(e -> {
            ModEntry entry = list.getSelectedValue();
            if (entry == null) return;
            if (entry.locked) {
                JOptionPane.showMessageDialog(dialog, entry.file.getName() + " is required for this profile.", "Required mod", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            try {
                toggleModEntry(entry);
                log((entry.enabled ? "Disabled " : "Enabled ") + entry.file.getName() + ".");
                reload.run();
                refreshVersionPanel();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Mods", JOptionPane.ERROR_MESSAGE);
            }
        });
        openFolder.addActionListener(e -> open(mods));
        close.addActionListener(e -> dialog.dispose());

        actions.add(toggle);
        actions.add(openFolder);
        actions.add(close);

        root.add(header, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void showResourcePacksManager() {
        LaunchProfile profile = selectedProfile();
        try {
            ensureProfileFolders(profile);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, e.getMessage(), "Resource Packs", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File packs = getResourcePacksFolder(profile);
        if (!packs.exists() && !packs.mkdirs()) {
            JOptionPane.showMessageDialog(frame, "Failed to create resource packs folder: " + packs, "Resource Packs", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(frame, profile.label + " Resource Packs", true);
        JPanel root = card(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(LINE),
            BorderFactory.createEmptyBorder(16, 16, 16, 16)
        ));

        DefaultListModel<ModEntry> model = new DefaultListModel<>();
        JList<ModEntry> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(FIELD);
        list.setForeground(TEXT);
        list.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        list.setFixedCellHeight(32);
        JScrollPane scroll = new JScrollPane(list);
        styleScrollPane(scroll);
        scroll.setPreferredSize(new Dimension(520, 300));

        Runnable reload = () -> {
            model.clear();
            loadResourcePackEntries(packs, model);
            if (!model.isEmpty()) list.setSelectedIndex(0);
        };
        reload.run();

        JPanel header = transparentPanel(new BorderLayout(0, 4));
        header.add(label("Resource Packs", 18, Font.BOLD, TEXT), BorderLayout.NORTH);

        JPanel actions = transparentPanel();
        JButton toggle = secondaryButton(new JButton("Toggle"));
        JButton add = secondaryButton(new JButton("Add"));
        JButton openFolder = secondaryButton(new JButton("Open Folder"));
        JButton close = primaryButton(new JButton("Done"));
        toggle.setPreferredSize(new Dimension(104, 38));
        add.setPreferredSize(new Dimension(92, 38));
        openFolder.setPreferredSize(new Dimension(126, 38));
        close.setPreferredSize(new Dimension(92, 38));

        toggle.addActionListener(e -> {
            ModEntry entry = list.getSelectedValue();
            if (entry == null) return;
            try {
                File target = toggleResourcePackEntry(profile, entry);
                log((entry.enabled ? "Disabled " : "Enabled ") + target.getName() + ".");
                reload.run();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Resource Packs", JOptionPane.ERROR_MESSAGE);
            }
        });
        add.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setMultiSelectionEnabled(true);
            chooser.setDialogTitle("Add Resource Packs");
            int result = chooser.showOpenDialog(dialog);
            if (result != JFileChooser.APPROVE_OPTION) return;
            int copied = 0;
            for (File file : chooser.getSelectedFiles()) {
                if (!isResourcePackLikeFile(file)) continue;
                try {
                    File target = new File(packs, file.getName());
                    Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    setResourcePackEnabled(profile, target, true);
                    copied++;
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Resource Packs", JOptionPane.ERROR_MESSAGE);
                }
            }
            if (copied > 0) {
                log("Added " + copied + " resource pack" + (copied == 1 ? "." : "s."));
                reload.run();
            }
        });
        openFolder.addActionListener(e -> open(packs));
        close.addActionListener(e -> dialog.dispose());

        actions.add(toggle);
        actions.add(add);
        actions.add(openFolder);
        actions.add(close);

        root.add(header, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void loadModEntries(LaunchProfile profile, File mods, DefaultListModel<ModEntry> model) {
        File[] files = mods.listFiles();
        if (files == null) return;

        for (File file : files) {
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (!file.isFile()) continue;
            boolean locked = (profile.includesGambleClient && (isGambleClientJar(file.getName()) || isGambleClientLoaderJar(file.getName())))
                || (profile.requiresFabricApi && isFabricApiJar(file.getName()))
                || (profile.fabric && isModMenuJar(file.getName()));
            if (lower.endsWith(".jar")) model.addElement(new ModEntry(file, true, locked));
            else if (lower.endsWith(".jar.disabled")) model.addElement(new ModEntry(file, false, locked));
        }
    }

    private void loadResourcePackEntries(File packs, DefaultListModel<ModEntry> model) {
        File[] files = packs.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (!isResourcePackLikeFile(file)) continue;
            model.addElement(new ModEntry(file, isEnabledResourcePack(file), false));
        }
    }

    private void toggleModEntry(ModEntry entry) throws IOException {
        File source = entry.file;
        File target;
        if (entry.enabled) {
            target = new File(source.getParentFile(), source.getName() + ".disabled");
        } else {
            String name = source.getName();
            target = new File(source.getParentFile(), name.substring(0, name.length() - ".disabled".length()));
        }

        if (target.exists()) throw new IOException("Target already exists: " + target.getName());
        Files.move(source.toPath(), target.toPath());
    }

    private File toggleResourcePackEntry(LaunchProfile profile, ModEntry entry) throws IOException {
        File source = entry.file;
        File target;
        if (entry.enabled) {
            target = new File(source.getParentFile(), source.getName() + ".disabled");
        } else {
            String name = source.getName();
            target = new File(source.getParentFile(), name.substring(0, name.length() - ".disabled".length()));
        }

        if (target.exists()) throw new IOException("Target already exists: " + target.getName());
        Files.move(source.toPath(), target.toPath());
        setResourcePackEnabled(profile, target, !entry.enabled);
        return target;
    }

    private void selectStoredProfile() {
        File file = getSelectedProfileFile();
        if (!file.isFile()) return;

        try {
            String id = readFile(file).trim();
            for (LaunchProfile profile : LAUNCH_PROFILES) {
                if (profile.id.equals(id)) {
                    profileBox.setSelectedItem(profile);
                    return;
                }
            }
        } catch (IOException ignored) {
            // Default profile is fine.
        }
    }

    private void saveSelectedProfile(LaunchProfile profile) {
        try {
            File folder = getLauncherDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                throw new IOException("Failed to create launcher data folder: " + folder);
            }
            Files.write(getSelectedProfileFile().toPath(), (profile.id + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log("Could not save selected profile: " + e.getMessage());
        }
    }

    private void selectStoredBuild() {
        String id = Json.string(readLauncherSettings().get("selectedBuild"));
        Build build = id.isEmpty() ? null : findBuild(id);
        if (build == null) return;
        applyingAutomaticBuildSelection = true;
        try {
            buildBox.setSelectedItem(build);
            explicitBuildSelection = true;
        } finally {
            applyingAutomaticBuildSelection = false;
        }
    }

    private void saveSelectedBuild(Build build) {
        if (build == null) return;
        Map<String, Object> settings = readLauncherSettings();
        settings.put("selectedBuild", build.id);
        saveLauncherSettings(settings, "Selected build: " + build.label + ".");
    }

    private boolean readAutoCheckUpdates() {
        return jsonBoolean(readLauncherSettings().get("autoCheckUpdates"));
    }

    private void saveAutoCheckUpdates(boolean enabled) {
        Map<String, Object> settings = readLauncherSettings();
        settings.put("autoCheckUpdates", enabled);
        saveLauncherSettings(settings, "Startup update checks " + (enabled ? "enabled" : "disabled") + ".");
    }

    private boolean readSlotSoundsEnabled() {
        Map<String, Object> settings = readLauncherSettings();
        if (!settings.containsKey("slotSoundsEnabled")) return true;
        return jsonBoolean(settings.get("slotSoundsEnabled"));
    }

    private void saveSlotSoundsEnabled(boolean enabled) {
        Map<String, Object> settings = readLauncherSettings();
        settings.put("slotSoundsEnabled", enabled);
        saveLauncherSettings(settings, "Slot sounds " + (enabled ? "enabled" : "disabled") + ".");
    }

    private boolean readSlotWinSoundsEnabled() {
        Map<String, Object> settings = readLauncherSettings();
        if (!settings.containsKey("slotWinSoundsEnabled")) return true;
        return jsonBoolean(settings.get("slotWinSoundsEnabled"));
    }

    private void saveSlotWinSoundsEnabled(boolean enabled) {
        Map<String, Object> settings = readLauncherSettings();
        settings.put("slotWinSoundsEnabled", enabled);
        saveLauncherSettings(settings, "Slot win sounds " + (enabled ? "enabled" : "disabled") + ".");
    }

    private Map<String, Object> readLauncherSettings() {
        File file = getLauncherSettingsFile();
        if (!file.isFile()) return new LinkedHashMap<>();

        try {
            return new LinkedHashMap<>(Json.asObject(Json.parse(readFile(file))));
        } catch (Exception e) {
            log("Could not read launcher settings: " + rootMessage(e));
            return new LinkedHashMap<>();
        }
    }

    private void saveLauncherSettings(Map<String, Object> settings, String successMessage) {
        try {
            File folder = getLauncherDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                throw new IOException("Failed to create launcher data folder: " + folder);
            }

            String json = "{"
                + "\"autoCheckUpdates\":" + jsonBoolean(settings.get("autoCheckUpdates")) + ","
                + "\"slotSoundsEnabled\":" + (settings.containsKey("slotSoundsEnabled") ? jsonBoolean(settings.get("slotSoundsEnabled")) : true) + ","
                + "\"slotWinSoundsEnabled\":" + (settings.containsKey("slotWinSoundsEnabled") ? jsonBoolean(settings.get("slotWinSoundsEnabled")) : true) + ","
                + "\"selectedBuild\":\"" + jsonEscape(Json.string(settings.get("selectedBuild"))) + "\""
                + "}" + System.lineSeparator();
            Files.write(getLauncherSettingsFile().toPath(), json.getBytes(StandardCharsets.UTF_8));
            log(successMessage);
        } catch (IOException e) {
            log("Could not save launcher settings: " + e.getMessage());
        }
    }

    private void showAntiScreenshareMenu() {
        String status = antiScreenshareStatus();
        Object[] options = {"Clean View", "Enable Core", "Close"};
        int choice = JOptionPane.showOptionDialog(
            frame,
            status,
            "AntiScreenshare",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            options,
            options[0]
        );
        String result = "";
        if (choice == 0) result = applyAntiScreenshareCleanView();
        else if (choice == 1) result = setAntiScreenshareModule("antiscreenshare", true);
        if (!result.isEmpty()) JOptionPane.showMessageDialog(frame, result, "AntiScreenshare", JOptionPane.INFORMATION_MESSAGE);
    }

    private String antiScreenshareStatus() {
        List<Map<String, Object>> liveModules = readAntiScreenshareBridgeModules();
        if (liveModules.isEmpty()) {
            if (antiScreenshareBridgeOnline()) {
                return "Live client bridge connected, but no modules were reported yet. Open the client once, then refresh.";
            }

            Process process = minecraftProcess;
            if (process != null && process.isAlive()) {
                return "Minecraft is open, waiting for the Gamble Client bridge. Open the client once or enable AntiScreenshare, then refresh.";
            }

            File modules = getAntiScreenshareModulesFile();
            if (modules.isFile()) {
                return "Client is not running. Enable prepares the selected profile, then launch the client to control live modules here.";
            }
            return "Client is not running and no profile config exists yet. Launch Gamble Client once, then come back here.";
        }

        return "Live client connected. " + liveModules.size() + " modules available for " + selectedProfile().label + ".";
    }

    private String antiScreenshareConfigPath() {
        return getAntiScreenshareModulesFile().getAbsolutePath();
    }

    private boolean antiScreenshareEnabled() {
        List<Map<String, Object>> liveModules = readAntiScreenshareBridgeModules();
        if (!liveModules.isEmpty()) {
            for (Map<String, Object> module : liveModules) {
                if ("antiscreenshare".equalsIgnoreCase(Json.string(module.get("name")))) {
                    return jsonBoolean(module.get("active"));
                }
            }
            return true;
        }
        return false;
    }

    private String enableAntiScreenshare() {
        String liveResult = toggleAntiScreenshareBridgeModule("antiscreenshare", true);
        if (!liveResult.isBlank()) return liveResult;
        return setAntiScreenshareModule("antiscreenshare", true);
    }

    private List<Map<String, Object>> antiScreenshareModules() {
        List<Map<String, Object>> liveModules = readAntiScreenshareBridgeModules();
        if (!liveModules.isEmpty()) return liveModules;
        return readSavedAntiScreenshareModules();
    }

    private List<Map<String, Object>> communityConfigs() {
        try {
            ApiResponse response = apiRequest("GET", "/api/community-configs", "", "", 200);
            List<Object> rows = Json.asArray(response.body.get("configs"));
            List<Map<String, Object>> configs = new ArrayList<>();
            for (Object row : rows) configs.add(Json.asObject(row));
            return configs;
        } catch (Exception e) {
            log("Could not load community configs: " + rootMessage(e));
            return Collections.emptyList();
        }
    }

    private String toggleAntiScreenshareModule(String module, boolean active) {
        if (module == null || module.isBlank()) return "Missing module name.";

        String liveResult = toggleAntiScreenshareBridgeModule(module, active);
        if (!liveResult.isBlank()) return liveResult;

        return "Client is not running. Launch the client first so AntiScreenshare can change live modules.";
    }

    private String applyAntiScreenshareCleanView() {
        LinkedHashMap<String, Boolean> changes = new LinkedHashMap<>();
        addModuleChanges(changes, ANTISCREENSHARE_CORE_ON, true);
        addModuleChanges(changes, ANTISCREENSHARE_SCOREBOARD_ON, true);
        addModuleChanges(changes, ANTISCREENSHARE_SCOREBOARD_OFF, false);
        addModuleChanges(changes, ANTISCREENSHARE_HUD_OFF, false);
        addModuleChanges(changes, ANTISCREENSHARE_VISUAL_OFF, false);
        return updateAntiScreenshareModules(changes, "Clean View applied");
    }

    private String applyAntiScreenshareHudClean() {
        LinkedHashMap<String, Boolean> changes = new LinkedHashMap<>();
        addModuleChanges(changes, ANTISCREENSHARE_CORE_ON, true);
        addModuleChanges(changes, ANTISCREENSHARE_HUD_OFF, false);
        return updateAntiScreenshareModules(changes, "HUD cleanup applied");
    }

    private String applyAntiScreenshareVisualClean() {
        LinkedHashMap<String, Boolean> changes = new LinkedHashMap<>();
        addModuleChanges(changes, ANTISCREENSHARE_CORE_ON, true);
        addModuleChanges(changes, ANTISCREENSHARE_VISUAL_OFF, false);
        return updateAntiScreenshareModules(changes, "Visual overlay cleanup applied");
    }

    private String applyAntiScreenshareScoreboardMask() {
        LinkedHashMap<String, Boolean> changes = new LinkedHashMap<>();
        addModuleChanges(changes, ANTISCREENSHARE_CORE_ON, true);
        addModuleChanges(changes, ANTISCREENSHARE_SCOREBOARD_ON, true);
        addModuleChanges(changes, ANTISCREENSHARE_SCOREBOARD_OFF, false);
        return updateAntiScreenshareModules(changes, "Scoreboard mask applied");
    }

    private String setAntiScreenshareModule(String module, boolean active) {
        LinkedHashMap<String, Boolean> changes = new LinkedHashMap<>();
        changes.put(module, active);
        return updateAntiScreenshareModules(changes, module + " " + (active ? "enabled" : "disabled"));
    }

    private String restoreAntiScreenshareBackup() {
        File modules = getAntiScreenshareModulesFile();
        File folder = modules.getParentFile();
        File[] backups = folder == null ? null : folder.listFiles((dir, name) -> name.startsWith("modules.txt.backup-antiscreenshare-") && name.endsWith(".txt"));
        if (backups == null || backups.length == 0) return "No AntiScreenshare backup found for this profile.";

        File latest = backups[0];
        for (File backup : backups) {
            if (backup.getName().compareTo(latest.getName()) > 0) latest = backup;
        }

        try {
            Files.copy(latest.toPath(), modules.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log("Restored AntiScreenshare module backup: " + latest.getName());
            return "Restored " + latest.getName() + ".";
        } catch (IOException e) {
            return "Could not restore backup: " + e.getMessage();
        }
    }

    private String saveAntiScreenshareConfig() {
        File modules = getAntiScreenshareModulesFile();
        if (!modules.isFile()) {
            return "No modules.txt found yet. Launch Gamble Client once first:\n" + modules.getAbsolutePath();
        }

        File folder = new File(getProfileDataFolder(), "saved-configs");
        if (!folder.isDirectory() && !folder.mkdirs()) {
            return "Could not create saved config folder:\n" + folder.getAbsolutePath();
        }

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        File saved = new File(folder, "antiscreenshare-" + selectedProfile().id + "-" + stamp + ".txt");
        try {
            Files.copy(modules.toPath(), saved.toPath(), StandardCopyOption.REPLACE_EXISTING);
            String message = "Saved AntiScreenshare config: " + saved.getAbsolutePath();
            log(message);
            return message;
        } catch (IOException e) {
            return "Could not save AntiScreenshare config: " + e.getMessage();
        }
    }

    private String openCommunityConfigs() {
        return postAntiScreenshareBridgeAction("/open-configs", "Opened Browse Configs in the live client.");
    }

    private String openObsAntiScreenshare() {
        try {
            readAntiScreenshareBridge("/health");
            open("http://127.0.0.1:18765/public");
            return "Opened OBS Browser Source view. Use this URL in OBS: http://127.0.0.1:18765/public";
        } catch (IOException e) {
            return "Client bridge is not running. Launch Gamble Client, then add this OBS Browser Source: http://127.0.0.1:18765/public";
        }
    }

    private boolean antiScreenshareBridgeOnline() {
        try {
            readAntiScreenshareBridge("/health");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String openAntiScreenshareModuleSettings(String module) {
        if (module == null || module.isBlank()) return "Missing module name.";
        String encoded = URLEncoder.encode(module, StandardCharsets.UTF_8);
        return postAntiScreenshareBridgeAction("/open-settings?name=" + encoded, "Opened " + displayModuleTitle(module) + " settings in the live client.");
    }

    private String setAntiScreenshareModuleSetting(String module, String setting, String value) {
        if (module == null || module.isBlank()) return "Missing module name.";
        if (setting == null || setting.isBlank()) return "Missing setting name.";
        try {
            String path = "/setting?module=" + URLEncoder.encode(module, StandardCharsets.UTF_8)
                + "&name=" + URLEncoder.encode(setting, StandardCharsets.UTF_8)
                + "&value=" + URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
            String body = readAntiScreenshareBridge(path, "POST");
            Map<String, Object> root = Json.asObject(Json.parse(body));
            if (jsonBoolean(root.get("ok"))) {
                String savedValue = Json.string(root.get("value"));
                return setting + (savedValue.isBlank() ? " saved." : " set to " + savedValue + ".");
            }
            String error = Json.string(root.get("error"));
            return error.isBlank() ? "The live client rejected that setting." : error;
        } catch (Exception e) {
            return "Client is not running. Launch Gamble Client first.";
        }
    }

    private String enableAntiScreenshareHud() {
        String result = toggleAntiScreenshareBridgeModule("hud", true);
        return result.isBlank() ? "Client is not running. Launch the client first so AntiScreenshare can change the HUD." : result;
    }

    private String disableAntiScreenshareHud() {
        String result = toggleAntiScreenshareBridgeModule("hud", false);
        return result.isBlank() ? "Client is not running. Launch the client first so AntiScreenshare can change the HUD." : result;
    }

    private boolean antiScreenshareHudEnabled() {
        Object active = antiScreenshareHudInfo().get("hudActive");
        if (active instanceof Boolean bool) return bool;
        return "on".equals(antiScreenshareModuleState("hud"));
    }

    private Map<String, Object> antiScreenshareHudInfo() {
        try {
            return Json.asObject(Json.parse(readAntiScreenshareBridge("/hud-info")));
        } catch (Exception e) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("ok", false);
            root.put("hudActive", "on".equals(antiScreenshareModuleState("hud")));
            Map<String, Object> live = new LinkedHashMap<>();
            live.put("fps", 0);
            live.put("ping", 0);
            live.put("server", "Client offline");
            live.put("world", "");
            live.put("coords", "-");
            live.put("dimension", "-");
            live.put("players", 0);
            live.put("time", "-");
            root.put("live", live);
            root.put("items", Collections.emptyList());
            return root;
        }
    }

    private String openAntiScreenshareHud() {
        return postAntiScreenshareBridgeAction("/open-hud", "Opened the HUD editor in the live client.");
    }

    private String enableKdeScreencastPrivacy() {
        String desktop = String.valueOf(System.getenv("XDG_CURRENT_DESKTOP")).toLowerCase(Locale.ROOT);
        String session = String.valueOf(System.getenv("XDG_SESSION_DESKTOP")).toLowerCase(Locale.ROOT);
        if (!desktop.contains("kde") && !desktop.contains("plasma") && !session.contains("kde") && !session.contains("plasma")) {
            return "KDE screencast privacy is only available on Plasma/KWin.";
        }

        try {
            runQuiet("kwriteconfig6", "--file", "kwinrc", "--group", "org.kde.kdecoration2", "--key", "AlwaysShowExcludeFromCapture", "true");
            runQuiet("busctl", "--user", "call", "org.kde.KWin", "/KWin", "org.kde.KWin", "reconfigure");
            new ProcessBuilder("systemsettings", "kcm_kwinrules").start();
            String message = "Enabled KDE's Hide from Screencast titlebar control and opened Window Rules. Use KDE's per-window rule for compositor-level capture hiding.";
            log(message);
            return message;
        } catch (Exception e) {
            return "Could not open KDE screencast privacy controls: " + rootMessage(e);
        }
    }

    private void runQuiet(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException(command[0] + " timed out");
        }
        if (process.exitValue() != 0) {
            throw new IOException(command[0] + " exited with code " + process.exitValue());
        }
    }

    private String antiScreenshareModuleState(String module) {
        File modules = getAntiScreenshareModulesFile();
        if (!modules.isFile()) return "missing config";
        try {
            String text = readFile(modules);
            Boolean active = moduleActiveState(text, module);
            if (active == null) return "missing";
            return active ? "on" : "off";
        } catch (IOException e) {
            return "unreadable";
        }
    }

    private void addModuleChanges(LinkedHashMap<String, Boolean> changes, String[] modules, boolean active) {
        for (String module : modules) changes.put(module, active);
    }

    private String updateAntiScreenshareModules(LinkedHashMap<String, Boolean> changes, String message) {
        File modules = getAntiScreenshareModulesFile();
        if (!modules.isFile()) {
            return "No modules.txt found yet. Launch Gamble Client once first:\n" + modules.getAbsolutePath();
        }

        try {
            String text = readFile(modules);
            File backup = backupAntiScreenshareModules(modules);
            int touched = 0;
            List<String> missing = new ArrayList<>();
            for (Map.Entry<String, Boolean> entry : changes.entrySet()) {
                String updated = setModuleActiveText(text, entry.getKey(), entry.getValue());
                if (updated.equals(text)) {
                    missing.add(entry.getKey());
                } else {
                    touched++;
                    text = updated;
                }
            }
            Files.write(modules.toPath(), text.getBytes(StandardCharsets.UTF_8));
            String result = message + " for " + touched + " modules. Backup: " + backup.getName() + ".";
            if (!missing.isEmpty()) result += " Missing in this build: " + String.join(", ", missing) + ".";
            log(result);
            return result;
        } catch (IOException e) {
            return "Could not update AntiScreenshare config: " + e.getMessage();
        }
    }

    private File backupAntiScreenshareModules(File modules) throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        File backup = new File(modules.getParentFile(), "modules.txt.backup-antiscreenshare-" + stamp + ".txt");
        Files.copy(modules.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return backup;
    }

    private String setModuleActiveText(String text, String module, boolean active) {
        Pattern pattern = modulePattern(module);
        return pattern.matcher(text).replaceFirst("{active:" + (active ? "1" : "0") + "b$1");
    }

    private Boolean moduleActiveState(String text, String module) {
        Pattern pattern = Pattern.compile("\\{active:([01])b(?:(?!\\},\\{active:).)*?name:\"" + Pattern.quote(module) + "\"", Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return null;
        return "1".equals(matcher.group(1));
    }

    private List<Map<String, Object>> readAntiScreenshareBridgeModules() {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                String json = readAntiScreenshareBridge("/modules");
                Map<String, Object> root = Json.asObject(Json.parse(json));
                List<Object> modules = Json.asArray(root.get("modules"));
                List<Map<String, Object>> out = new ArrayList<>();
                for (Object item : modules) {
                    Map<String, Object> source = Json.asObject(item);
                    String name = Json.string(source.get("name"));
                    if (name.isBlank()) continue;

                    Map<String, Object> module = new LinkedHashMap<>();
                    module.put("name", name);
                    module.put("title", fallback(Json.string(source.get("title")), displayModuleTitle(name)));
                    module.put("category", fallback(Json.string(source.get("category")), "Client"));
                    module.put("active", jsonBoolean(source.get("active")));
                    module.put("favorite", jsonBoolean(source.get("favorite")));
                    module.put("description", Json.string(source.get("description")));
                    module.put("info", Json.string(source.get("info")));
                    module.put("gamble", jsonBoolean(source.get("gambleClient")));
                    module.put("settings", Json.asArray(source.get("settings")));
                    module.put("source", "Live client");
                    out.add(module);
                }
                return out;
            } catch (Exception e) {
                if (attempt < 2) {
                    try {
                        Thread.sleep(120L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return Collections.emptyList();
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    private List<Map<String, Object>> readSavedAntiScreenshareModules() {
        File modules = getAntiScreenshareModulesFile();
        if (!modules.isFile()) return Collections.emptyList();

        try {
            return readSavedAntiScreenshareModules(readFile(modules));
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> readSavedAntiScreenshareModules(String text) {
        Pattern pattern = Pattern.compile("\\{active:([01])b(?:(?!\\},\\{active:).)*?name:\"([^\"]+)\"(?:(?!\\},\\{active:).)*?(?=\\},\\{active:|\\]\\})", Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(text);
        List<Map<String, Object>> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        while (matcher.find()) {
            String name = matcher.group(2);
            if (name == null || name.isBlank() || !seen.add(name)) continue;

            Map<String, Object> module = new LinkedHashMap<>();
            module.put("name", name);
            module.put("title", displayModuleTitle(name));
            module.put("category", "Saved Config");
            module.put("active", "1".equals(matcher.group(1)));
            module.put("favorite", false);
            module.put("description", "Saved module state from the selected launcher profile.");
            module.put("info", "");
            module.put("gamble", true);
            module.put("source", "Saved config");
            out.add(module);
        }
        return out;
    }

    private String toggleAntiScreenshareBridgeModule(String module, boolean active) {
        try {
            String path = "/toggle?name=" + URLEncoder.encode(module, StandardCharsets.UTF_8) + "&state=" + (active ? "on" : "off");
            String body = readAntiScreenshareBridge(path, "POST");
            Map<String, Object> root = Json.asObject(Json.parse(body));
            if (!jsonBoolean(root.get("ok"))) return "";
            return module + " " + (active ? "enabled" : "disabled") + " in the live client.";
        } catch (Exception e) {
            return "";
        }
    }

    private String postAntiScreenshareBridgeAction(String path, String success) {
        try {
            String body = readAntiScreenshareBridge(path, "POST");
            Map<String, Object> root = Json.asObject(Json.parse(body));
            if (jsonBoolean(root.get("ok"))) return success;
            String error = Json.string(root.get("error"));
            return error.isBlank() ? "The live client rejected that action." : error;
        } catch (Exception e) {
            return "Client is not running. Launch Gamble Client first.";
        }
    }

    private String readAntiScreenshareBridge(String path) throws IOException {
        return readAntiScreenshareBridge(path, "GET");
    }

    private String readAntiScreenshareBridge(String path, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:18765" + path).toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(1500);
        connection.setReadTimeout(2200);
        if ("POST".equals(method)) connection.setDoOutput(true);

        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) throw new IOException("empty AntiScreenshare response");
        try (InputStream in = stream) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (code >= 400) throw new IOException(body);
            return body;
        } finally {
            connection.disconnect();
        }
    }

    private String displayModuleTitle(String name) {
        String[] parts = name.replace('_', '-').split("-");
        StringBuilder title = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (title.length() > 0) title.append(' ');
            title.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) title.append(part.substring(1));
        }
        return title.length() == 0 ? name : title.toString();
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Pattern modulePattern(String module) {
        return Pattern.compile("\\{active:[01]b((?:(?!\\},\\{active:).)*?name:\"" + Pattern.quote(module) + "\"(?:(?!\\},\\{active:).)*?)", Pattern.DOTALL);
    }

    private File getAntiScreenshareModulesFile() {
        return new File(getProfileDataFolder(), "modules.txt");
    }

    private void addSettingsField(JPanel form, GridBagConstraints gbc, int row, String title, java.awt.Component component) {
        JPanel block = transparentPanel(new BorderLayout(0, 6));
        block.add(label(title, 12, Font.BOLD, MUTED), BorderLayout.NORTH);
        component.setPreferredSize(new Dimension(240, 38));
        block.add(component, BorderLayout.CENTER);

        gbc.gridy = row;
        form.add(block, gbc);
    }

    private void styleInput(java.awt.Component component) {
        component.setBackground(FIELD);
        component.setForeground(TEXT);
        component.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        if (component instanceof JComboBox) {
            styleComboBox((JComboBox<?>) component);
            return;
        }
        if (component instanceof javax.swing.JComponent) {
            ((javax.swing.JComponent) component).setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
            ));
        }
    }

    private void styleCheckBox(JCheckBox checkBox) {
        checkBox.setOpaque(false);
        checkBox.setForeground(TEXT);
        checkBox.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        checkBox.setFocusPainted(false);
        checkBox.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        checkBox.setIconTextGap(10);
        checkBox.setIcon(new StyledCheckBoxIcon());
        checkBox.setSelectedIcon(new StyledCheckBoxIcon());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void styleComboBox(JComboBox<?> combo) {
        JComboBox raw = combo;
        combo.setOpaque(false);
        combo.setFocusable(false);
        combo.setMaximumRowCount(8);
        combo.setBackground(FIELD);
        combo.setForeground(TEXT);
        combo.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(255, 255, 255, 20)),
            BorderFactory.createEmptyBorder(0, 2, 0, 0)
        ));
        combo.setUI(new StyledComboBoxUi());
        raw.setRenderer(new StyledComboBoxRenderer());
    }

    private void styleScrollPane(JScrollPane scrollPane) {
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setBackground(FIELD);
        styleScrollBar(scrollPane.getVerticalScrollBar());
        styleScrollBar(scrollPane.getHorizontalScrollBar());
    }

    private void styleScrollBar(JScrollBar scrollBar) {
        scrollBar.setPreferredSize(new Dimension(7, 7));
        scrollBar.setUnitIncrement(16);
        scrollBar.setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                thumbColor = new Color(83, 96, 120);
                trackColor = FIELD;
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return zeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return zeroButton();
            }

            @Override
            protected void paintTrack(Graphics graphics, javax.swing.JComponent component, java.awt.Rectangle bounds) {
                graphics.setColor(FIELD);
                graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }

            @Override
            protected void paintThumb(Graphics graphics, javax.swing.JComponent component, java.awt.Rectangle bounds) {
                if (bounds.isEmpty() || !scrollBar.isEnabled()) return;
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(thumbColor);
                g.fillRoundRect(bounds.x + 2, bounds.y + 2, Math.max(3, bounds.width - 4), Math.max(3, bounds.height - 4), 8, 8);
                g.dispose();
            }
        });
    }

    private JButton zeroButton() {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(0, 0));
        button.setMinimumSize(new Dimension(0, 0));
        button.setMaximumSize(new Dimension(0, 0));
        return button;
    }

    private JButton primaryButton(JButton button) {
        styleButton(button, ACCENT, BACKGROUND);
        button.setPreferredSize(new Dimension(142, 40));
        return button;
    }

    private JButton secondaryButton(JButton button) {
        styleButton(button, SURFACE_2, TEXT);
        button.setPreferredSize(new Dimension(132, 40));
        return button;
    }

    private JButton ghostButton(JButton button, boolean accent) {
        styleButton(button, new Color(255, 255, 255, 0), accent ? ACCENT : new Color(214, 219, 229));
        button.putClientProperty("ghost", Boolean.TRUE);
        button.setPreferredSize(new Dimension(84, 32));
        return button;
    }

    private JButton iconButton(JButton button) {
        styleButton(button, SURFACE_2, TEXT);
        button.setIcon(resourceIcon("/assets/gear.png", 30));
        button.setText("");
        button.setHorizontalAlignment(JButton.CENTER);
        button.setVerticalAlignment(JButton.CENTER);
        button.setPreferredSize(new Dimension(48, 48));
        button.setToolTipText("Launch settings");
        return button;
    }

    private void styleButton(JButton button, Color background, Color foreground) {
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        button.setRolloverEnabled(true);
        button.setUI(new RoundedButtonUi());
    }

    private JPanel card(java.awt.LayoutManager layout) {
        JPanel panel = new RoundedPanel(layout, SURFACE, new Color(255, 255, 255, 16), 8);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(),
            BorderFactory.createEmptyBorder(16, 16, 16, 16)
        ));
        return panel;
    }

    private JPanel transparentPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        return panel;
    }

    private JPanel transparentPanel(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private JLabel label(String text, int size, int style, Color color) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(new Font(Font.SANS_SERIF, style, size));
        return label;
    }

    private ImageIcon resourceIcon(String resourcePath, int size) {
        try (InputStream stream = Main.class.getResourceAsStream(resourcePath)) {
            if (stream != null) {
                BufferedImage image = ImageIO.read(stream);
                if (image != null) {
                    return new ImageIcon(image.getScaledInstance(size, size, Image.SCALE_SMOOTH));
                }
            }
        } catch (IOException ignored) {
            // Text fallback below.
        }
        return null;
    }

    private JLabel htmlLabel(String text, int size, Color color) {
        JLabel label = label("<html>" + text + "</html>", size, Font.PLAIN, color);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        return label;
    }

    private JPanel statChip(String name, String value) {
        JPanel panel = new RoundedPanel(new BorderLayout(0, 3), new Color(33, 29, 39, 205), new Color(255, 255, 255, 14), 8);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        panel.add(label(name, 11, Font.BOLD, MUTED), BorderLayout.NORTH);
        panel.add(label(value, 15, Font.BOLD, TEXT), BorderLayout.CENTER);
        return panel;
    }

    private JPanel versionChip(String name, JLabel installed, JLabel released) {
        JPanel panel = new RoundedPanel(new BorderLayout(0, 5), new Color(33, 29, 39, 205), new Color(255, 255, 255, 14), 8);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        installed.setForeground(TEXT);
        installed.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        released.setForeground(MUTED);
        released.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        JPanel rows = transparentPanel(new BorderLayout(0, 2));
        rows.add(installed, BorderLayout.NORTH);
        rows.add(released, BorderLayout.SOUTH);

        panel.add(label(name, 11, Font.BOLD, MUTED), BorderLayout.NORTH);
        panel.add(rows, BorderLayout.CENTER);
        return panel;
    }

    private List<Image> appIconImages() {
        try (InputStream stream = Main.class.getResourceAsStream("/assets/cg-mod-icon.png")) {
            if (stream != null) {
                BufferedImage source = ImageIO.read(stream);
                if (source != null) {
                    List<Image> icons = new ArrayList<>();
                    int[] sizes = new int[] {16, 24, 32, 48, 64, 128, 256, 512};
                    for (int size : sizes) {
                        icons.add(source.getScaledInstance(size, size, Image.SCALE_SMOOTH));
                    }
                    icons.add(source);
                    return icons;
                }
            }
        } catch (IOException ignored) {
            // Fall back to the generated icon below.
        }

        List<Image> icons = new ArrayList<>();
        icons.add(appIconImage(32));
        icons.add(appIconImage(64));
        icons.add(appIconImage(128));
        return icons;
    }

    private Image appIconImage(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, ACCENT, size, size, BLUE));
        g.fillRoundRect(0, 0, size, size, Math.max(8, size / 5), Math.max(8, size / 5));
        g.setColor(new Color(255, 255, 255, 58));
        g.drawRoundRect(1, 1, size - 3, size - 3, Math.max(8, size / 5), Math.max(8, size / 5));
        g.setColor(BACKGROUND);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(13, size / 3)));
        String text = "GC";
        java.awt.FontMetrics metrics = g.getFontMetrics();
        int x = (size - metrics.stringWidth(text)) / 2;
        int y = ((size - metrics.getHeight()) / 2) + metrics.getAscent() + Math.max(1, size / 18);
        g.drawString(text, x, y);
        g.dispose();
        return image;
    }

    private static final class StyledCheckBoxIcon implements Icon {
        private static final int SIZE = 18;

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }

        @Override
        public void paintIcon(java.awt.Component component, Graphics graphics, int x, int y) {
            AbstractButton button = component instanceof AbstractButton ? (AbstractButton) component : null;
            boolean selected = button != null && button.isSelected();
            boolean enabled = component.isEnabled();

            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color border = selected ? ACCENT : new Color(255, 255, 255, 40);
            Color fill = selected ? new Color(230, 146, 35, enabled ? 230 : 120) : FIELD;

            g.setColor(fill);
            g.fillRoundRect(x, y, SIZE - 1, SIZE - 1, 6, 6);
            g.setColor(enabled ? border : new Color(border.getRed(), border.getGreen(), border.getBlue(), 80));
            g.drawRoundRect(x, y, SIZE - 1, SIZE - 1, 6, 6);

            if (selected) {
                g.setColor(BACKGROUND);
                g.setStroke(new java.awt.BasicStroke(2.2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g.drawLine(x + 5, y + 9, x + 8, y + 12);
                g.drawLine(x + 8, y + 12, x + 13, y + 6);
            }

            g.dispose();
        }
    }

    private static final class StyledComboBoxRenderer extends DefaultListCellRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String tooltip = null;
            if (value instanceof LaunchProfile) {
                LaunchProfile profile = (LaunchProfile) value;
                label.setText(profile.label);
                tooltip = profile.description;
            }

            label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            label.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            if (isSelected) {
                label.setBackground(new Color(48, 39, 48));
                label.setForeground(TEXT);
            } else {
                label.setBackground(index == -1 ? FIELD : SURFACE);
                label.setForeground(label.isEnabled() ? TEXT : MUTED);
            }
            if (list != null) {
                list.setBackground(SURFACE);
                list.setForeground(TEXT);
                list.setSelectionBackground(new Color(48, 39, 48));
                list.setSelectionForeground(TEXT);
                list.setToolTipText(tooltip);
            }
            label.setToolTipText(tooltip);
            return label;
        }
    }

    private static final class StyledComboBoxUi extends BasicComboBoxUI {
        @Override
        protected JButton createArrowButton() {
            JButton button = new JButton() {
                @Override
                protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(getModel().isPressed() ? new Color(255, 255, 255, 26) : new Color(255, 255, 255, 12));
                    g.fillRoundRect(2, 5, getWidth() - 7, getHeight() - 10, 6, 6);
                    g.setColor(isEnabled() ? ACCENT : MUTED);
                    int cx = getWidth() / 2;
                    int cy = getHeight() / 2 + 1;
                    g.drawLine(cx - 4, cy - 2, cx, cy + 2);
                    g.drawLine(cx, cy + 2, cx + 4, cy - 2);
                    g.dispose();
                }
            };
            button.setBorder(BorderFactory.createEmptyBorder());
            button.setContentAreaFilled(false);
            button.setFocusPainted(false);
            button.setOpaque(false);
            button.setPreferredSize(new Dimension(34, 34));
            return button;
        }

        @Override
        public void paintCurrentValueBackground(Graphics graphics, java.awt.Rectangle bounds, boolean hasFocus) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean enabled = comboBox == null || comboBox.isEnabled();
            g.setColor(enabled ? FIELD : new Color(24, 22, 28));
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);
            g.dispose();
        }
    }

    private static final class RoundedButtonUi extends BasicButtonUI {
        @Override
        public void paint(Graphics graphics, JComponent component) {
            AbstractButton button = (AbstractButton) component;
            ButtonModel model = button.getModel();
            boolean ghost = Boolean.TRUE.equals(button.getClientProperty("ghost"));
            Color fill = button.getBackground();
            if (!button.isEnabled()) fill = mix(fill, BACKGROUND, 0.45f);
            else if (model.isPressed()) fill = mix(fill, BACKGROUND, 0.18f);
            else if (model.isRollover()) fill = mix(fill, Color.WHITE, 0.08f);

            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (ghost) {
                if (model.isRollover() || model.isPressed()) {
                    g.setColor(model.isPressed() ? new Color(255, 255, 255, 24) : new Color(255, 255, 255, 12));
                    g.fillRoundRect(0, 0, component.getWidth() - 1, component.getHeight() - 1, 6, 6);
                }
            } else {
                g.setColor(fill);
                g.fillRoundRect(0, 0, component.getWidth() - 1, component.getHeight() - 1, 6, 6);
                g.setColor(new Color(255, 255, 255, button.isEnabled() ? 28 : 14));
                g.drawRoundRect(0, 0, component.getWidth() - 1, component.getHeight() - 1, 6, 6);
            }
            g.dispose();

            super.paint(graphics, component);
        }

        private static Color mix(Color from, Color to, float amount) {
            float clamped = Math.max(0f, Math.min(1f, amount));
            int red = Math.round(from.getRed() + (to.getRed() - from.getRed()) * clamped);
            int green = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * clamped);
            int blue = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * clamped);
            int alpha = Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * clamped);
            return new Color(red, green, blue, alpha);
        }
    }

    private static final class RoundedProgressUi extends BasicProgressBarUI {
        @Override
        protected void paintDeterminate(Graphics graphics, JComponent component) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = progressBar.getWidth();
            int height = progressBar.getHeight();
            int arc = 8;
            int inset = 2;
            Insets barInsets = progressBar.getInsets();
            int amount = Math.max(0, Math.min(width - inset * 2, getAmountFull(barInsets, width - inset * 2, height - inset * 2)));

            g.setColor(FIELD);
            g.fillRoundRect(0, 0, width - 1, height - 1, arc, arc);
            g.setColor(new Color(255, 255, 255, 22));
            g.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            if (amount > 0) {
                g.setColor(ACCENT);
                g.fillRoundRect(inset, inset, amount, height - inset * 2, arc, arc);
                g.setColor(new Color(255, 213, 114, 62));
                g.drawLine(inset + 3, inset + 2, Math.max(inset + 3, inset + amount - 4), inset + 2);
            }
            g.dispose();

            if (progressBar.isStringPainted()) paintString(graphics, 0, 0, width, height, amount, barInsets);
        }
    }

    private static final class RoundedPanel extends JPanel {
        private final Color fill;
        private final Color stroke;
        private final int radius;

        RoundedPanel(java.awt.LayoutManager layout, Color fill, Color stroke, int radius) {
            super(layout);
            this.fill = fill;
            this.stroke = stroke;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            if (stroke != null) {
                g.setColor(stroke);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class GradientPanel extends JPanel {
        GradientPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(28, 25, 34), getWidth(), getHeight(), BACKGROUND));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g.setColor(ACCENT);
            g.fillRoundRect(0, 0, 5, getHeight() - 1, 8, 8);
            g.setColor(new Color(104, 141, 187, 34));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g.dispose();
        }
    }

    private void startSignIn() {
        if (isLauncherSignInActive()) {
            cancelLauncherSignIn();
            return;
        }
        signInPromptDismissed = false;
        signInPromptTitle.setText("Waiting for browser sign-in");
        signInPromptText.setText("Finish sign-in on the Gamble Client site. Press Cancel here if you close the browser tab.");
        updateAccountUi();
        setProgress(0, "Sign in");
        log("Opening browser sign-in.");

        launcherSignInWorker = new SwingWorker<LauncherSession, Void>() {
            @Override
            protected LauncherSession doInBackground() throws Exception {
                ApiResponse start = apiRequest("POST", "/api/launcher/start", "{}", "", 200, 201);
                String code = Json.string(start.body.get("code"));
                String loginUrl = Json.string(start.body.get("loginUrl"));
                long expiresAt = jsonLong(start.body.get("expiresAt"));
                if (code.isEmpty() || loginUrl.isEmpty() || expiresAt <= 0) {
                    throw new IOException("Launcher sign-in did not return a usable browser link.");
                }

                log("Sign-in URL: " + loginUrl);
                if (!open(loginUrl)) {
                    log("Paste the sign-in URL above into your browser.");
                }
                Main.this.setProgress(12, "Browser");
                log("Waiting for browser sign-in to complete.");

                while (!isCancelled() && System.currentTimeMillis() / 1000L < expiresAt) {
                    Thread.sleep(2000L);
                    if (isCancelled()) throw new InterruptedException("Launcher sign-in cancelled.");
                    ApiResponse poll = apiRequest(
                        "POST",
                        "/api/launcher/poll",
                        "{\"code\":\"" + jsonEscape(code) + "\"}",
                        "",
                        200,
                        202
                    );
                    if (poll.status == 202) {
                        long remaining = Math.max(0L, expiresAt - (System.currentTimeMillis() / 1000L));
                        setProgressStatus("Waiting " + compactDuration(remaining));
                        continue;
                    }

                    String status = Json.string(poll.body.get("status"));
                    if ("ready".equals(status)) {
                        String token = Json.string(poll.body.get("token"));
                        if (token.isEmpty()) throw new IOException("Launcher session token was missing.");
                        LauncherAccount account = parseLauncherAccount(poll.body);
                        return new LauncherSession(token, account.user, account.ads);
                    }
                }

                throw new IOException("Launcher sign-in expired. Press Sign In and try again.");
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled()) {
                        setProgressStatus("Cancelled");
                        log("Launcher sign-in cancelled.");
                        signInPromptDismissed = false;
                        signInPromptTitle.setText("Sign in to continue");
                        signInPromptText.setText("Open the Gamble Client sign-in page, then return here to launch.");
                        updateAccountUi();
                        return;
                    }
                    LauncherSession session = get();
                    launcherToken = session.token;
                    launcherUser = session.user;
                    launcherAds = session.ads;
                    saveLauncherToken(session.token);
                    selectBestBuildForUser(session.user);
                    updateAccountUi();
                    updateAdUi();
                    refreshVersionPanel();
                    Main.this.setProgress(100, "Signed in");
                    log("Signed in as " + accountLabel(session.user) + ".");
                } catch (Exception e) {
                    setProgressStatus("Sign-in failed");
                    log("Sign-in failed: " + rootMessage(e));
                    signInPromptDismissed = false;
                    signInPromptTitle.setText("Sign-in failed");
                    signInPromptText.setText(rootMessage(e));
                    updateAccountUi();
                } finally {
                    launcherSignInWorker = null;
                    setBusy(false);
                }
            }
        };
        setBusy(true);
        launcherSignInWorker.execute();
    }

    private void toggleLauncherSignIn() {
        if (isLauncherSignInActive()) cancelLauncherSignIn();
        else startSignIn();
    }

    private boolean isLauncherSignInActive() {
        return launcherSignInWorker != null && !launcherSignInWorker.isDone();
    }

    private void cancelLauncherSignIn() {
        if (!isLauncherSignInActive()) return;
        launcherSignInWorker.cancel(true);
        setProgressStatus("Cancelled");
        setBusy(false);
    }

    private void refreshStoredLauncherSession() {
        final String storedToken = readLauncherToken();
        if (storedToken.isEmpty()) {
            updateAccountUi();
            maybeAutoCheckForUpdates();
            return;
        }

        launcherToken = storedToken;
        accountName.setText("Checking account...");
        accountStatus.setText("Stored launcher session");

        new SwingWorker<LauncherAccount, Void>() {
            @Override
            protected LauncherAccount doInBackground() throws Exception {
                ApiResponse response = apiRequest("GET", "/api/launcher/session", "", storedToken, 200);
                return parseLauncherAccount(response.body);
            }

            @Override
            protected void done() {
                try {
                    LauncherAccount account = get();
                    launcherUser = account.user;
                    launcherAds = account.ads;
                    selectBestBuildForUser(account.user);
                    updateAccountUi();
                    updateAdUi();
                    refreshVersionPanel();
                    log("Restored launcher account: " + accountLabel(launcherUser) + ".");
                    maybeAutoCheckForUpdates();
                } catch (Exception e) {
                    launcherUser = null;
                    launcherAds = null;
                    int status = httpStatus(e);
                    boolean rejected = status == 401 || status == 403;
                    if (rejected) {
                        signInPromptDismissed = false;
                    }
                    updateAccountUi();
                    updateAdUi();
                    refreshVersionPanel();
                    if (rejected) {
                        log("The server rejected the stored launcher sign-in, but its credential was preserved.");
                        maybePromptForSignIn();
                    } else {
                        log("Could not verify the stored sign-in yet; the saved session was kept: " + rootMessage(e));
                    }
                    maybeAutoCheckForUpdates();
                }
            }
        }.execute();
    }

    private void maybeAutoCheckForUpdates() {
        if (startupUpdateCheckStarted || !autoCheckUpdates.isSelected()) return;
        startupUpdateCheckStarted = true;
        checkForUpdatesOnStartup();
    }

    private String checkForUpdatesNow() {
        setUpdateStatus("Checking for updates...");
        log("Checking launcher and client updates.");
        String message = checkForUpdatesStatus();
        setUpdateStatus(message);
        log(message);
        refreshVersionPanel();
        return message;
    }

    private void checkForUpdatesOnStartup() {
        setUpdateStatus("Checking for updates...");
        log("Checking launcher and client updates.");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return checkForUpdatesStatus();
            }

            @Override
            protected void done() {
                try {
                    String message = get();
                    setUpdateStatus(message);
                    log(message);
                } catch (Exception e) {
                    setUpdateStatus("Could not check updates: " + rootMessage(e));
                    log("Update check failed: " + rootMessage(e));
                    showLauncherUpdateRequired(e);
                }
            }
        }.execute();
    }

    private String checkForUpdatesStatus() {
        Build build = (Build) buildBox.getSelectedItem();
        boolean profileUsesGambleClient = selectedProfile().includesGambleClient;
        List<String> results = new ArrayList<>();
        results.add(checkLauncherVersionStatus());

        if (build == null || !profileUsesGambleClient) {
            results.add("Client check skipped: selected profile does not use Gamble Client.");
        } else if (launcherToken == null || launcherToken.trim().isEmpty()) {
            results.add("Client check skipped: sign in to check the selected build.");
        } else {
            results.add(checkClientVersionStatus(build));
        }

        return String.join("  ", results);
    }

    private String checkLauncherVersionStatus() {
        try {
            LauncherVersion latest = fetchLauncherVersion();
            if (latest.version.isEmpty()) return "Launcher check unavailable.";
            if (LAUNCHER_VERSION.equals(latest.version)) return "Launcher latest: " + LAUNCHER_VERSION + ".";
            String suffix = latest.downloadUrl.isEmpty() ? "" : " Download: " + latest.downloadUrl;
            return "Launcher update available: " + latest.version + "." + suffix;
        } catch (Exception e) {
            return "Could not check launcher update: " + rootMessage(e);
        }
    }

    private boolean showLauncherUpdateRequired(Throwable error) {
        LauncherOutdatedException outdated = findLauncherOutdated(error);
        if (outdated == null) return false;

        String target = outdated.version.isEmpty() ? "the latest launcher" : "Gamble Client Launcher " + outdated.version;
        String download = outdated.downloadUrl.isEmpty() ? siteUrl() + "/download" : outdated.downloadUrl;
        String message = outdated.getMessage() + "\n\nDownload " + target + ":\n" + download;
        setUpdateStatus("Launcher update required: " + target + ".");
        log("Launcher update required: " + download);
        JOptionPane.showMessageDialog(frame, message, "Launcher update required", JOptionPane.WARNING_MESSAGE);
        return true;
    }

    private LauncherOutdatedException findLauncherOutdated(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof LauncherOutdatedException) return (LauncherOutdatedException) current;
            current = current.getCause();
        }
        return null;
    }

    private String checkClientVersionStatus(Build build) {
        try {
            LauncherManifest manifest = fetchLauncherManifest(build);
            File installed = new File(getModsFolder(), manifest.fileName);
            String version = displayManifestVersion(manifest);
            if (installed.isFile()) return build.label + " client latest: " + version + ".";
            return build.label + " client update available: " + version + ".";
        } catch (Exception e) {
            return "Could not check client update: " + rootMessage(e);
        }
    }

    private void refreshVersionPanel() {
        final Build build = (Build) buildBox.getSelectedItem();
        final LaunchProfile profile = selectedProfile();
        launcherInstalledVersion.setText("Installed: " + LAUNCHER_VERSION);
        launcherReleasedVersion.setText("Released: checking...");

        String installedClient = installedClientLabel(build);
        clientInstalledVersion.setText("Installed: " + installedClient);
        if (!profile.includesGambleClient) {
            clientReleasedVersion.setText("Released: profile has no client");
        } else if (launcherToken == null || launcherToken.trim().isEmpty()) {
            clientReleasedVersion.setText("Released: sign in to check");
        } else {
            clientReleasedVersion.setText("Released: checking...");
        }

        new SwingWorker<VersionPanelState, Void>() {
            @Override
            protected VersionPanelState doInBackground() {
                VersionPanelState state = new VersionPanelState();
                try {
                    LauncherVersion launcher = fetchLauncherVersion();
                    state.launcherReleased = launcher.version.isEmpty() ? "unknown" : launcher.version;
                } catch (Exception e) {
                    state.launcherReleased = "check failed";
                }

                if (build != null && profile.includesGambleClient && launcherToken != null && !launcherToken.trim().isEmpty()) {
                    try {
                        LauncherManifest manifest = fetchLauncherManifest(build);
                        state.clientReleased = displayManifestVersion(manifest);
                    } catch (Exception e) {
                        state.clientReleased = "check failed";
                    }
                }

                return state;
            }

            @Override
            protected void done() {
                try {
                    VersionPanelState state = get();
                    launcherReleasedVersion.setText("Released: " + state.launcherReleased);
                    if (state.clientReleased != null) clientReleasedVersion.setText("Released: " + state.clientReleased);
                } catch (Exception ignored) {
                    launcherReleasedVersion.setText("Released: check failed");
                }
            }
        }.execute();
    }

    private String installedClientLabel(Build build) {
        LaunchProfile profile = selectedProfile();
        if (build == null) return "none";
        if (!profile.includesGambleClient) {
            String scanned = findInstalledClientJarName(false);
            return scanned.isEmpty() ? "none" : displayArtifactName(scanned) + " in " + profile.label;
        }

        File loaderManifest = new File(getProfileDataFolder(), "loader-manifest.json");
        if (loaderManifest.isFile()) {
            try {
                Map<String, Object> body = Json.asObject(Json.parse(readFile(loaderManifest)));
                if (build.id.equals(Json.string(body.get("build")))) {
                    File installed = new File(Json.string(body.get("path")));
                    if (installed.isFile()) {
                        String version = Json.string(body.get("buildVersion"));
                        return version.isEmpty() ? displayArtifactName(installed.getName()) : displayClientVersion(version);
                    }
                }
            } catch (Exception ignored) {
                // Fall back to the legacy marker or a mods folder scan.
            }
        }

        File marker = new File(getProfileDataFolder(), "installed-build.txt");
        if (marker.isFile()) {
            try {
                String[] lines = readFile(marker).split("\\R");
                if (lines.length >= 2 && build.id.equals(lines[0].trim())) {
                    File installed = new File(getModsFolder(), lines[1].trim());
                    if (installed.isFile()) return displayArtifactName(installed.getName());
                }
            } catch (IOException ignored) {
                // Fall back to scanning the mods folder.
            }
        }

        String scanned = findInstalledClientJarName(false);
        return scanned.isEmpty() ? "none" : displayArtifactName(scanned);
    }

    private String displayArtifactName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return "unknown";
        String name = fileName.trim();
        String semantic = semanticClientVersion(name);
        if (!semantic.isEmpty()) return semantic;
        if (name.endsWith(".jar.disabled")) name = name.substring(0, name.length() - ".jar.disabled".length()) + " disabled";
        else if (name.endsWith(".zip")) name = name.substring(0, name.length() - 4);
        else if (name.endsWith(".jar")) name = name.substring(0, name.length() - 4);
        if (name.startsWith("cg-client-")) name = name.substring("cg-client-".length());
        if (name.startsWith("gamble-client-launcher-")) name = name.substring("gamble-client-launcher-".length());
        return name.length() > 34 ? name.substring(0, 31) + "..." : name;
    }

    private String semanticClientVersion(String fileName) {
        String digits = firstDigitRun(fileName, 14);
        if (digits.isEmpty()) return "";

        try {
            int year = Integer.parseInt(digits.substring(0, 4));
            int month = Integer.parseInt(digits.substring(4, 6));
            int day = Integer.parseInt(digits.substring(6, 8));
            int dayOfYear = dayOfYear(year, month, day);
            if (dayOfYear <= 0) return "";
            return "1." + dayOfYear;
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private String firstDigitRun(String value, int length) {
        int runStart = -1;
        int runLength = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                if (runStart < 0) runStart = i;
                runLength++;
                if (runLength >= length) return value.substring(runStart, runStart + length);
            } else {
                runStart = -1;
                runLength = 0;
            }
        }
        return "";
    }

    private int dayOfYear(int year, int month, int day) {
        if (month < 1 || month > 12) return -1;
        int[] days = new int[] {31, isLeapYear(year) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        if (day < 1 || day > days[month - 1]) return -1;
        int total = day;
        for (int i = 0; i < month - 1; i++) total += days[i];
        return total;
    }

    private boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }

    private void signOut() {
        clearLauncherSession();
        signInPromptDismissed = false;
        updateAccountUi();
        updateAdUi();
        setProgress(0, "Signed out");
        log("Signed out of the launcher.");
    }

    private void switchLauncherAccount() {
        if (isGameRunning()) {
            log("Switch account blocked: Minecraft is running.");
            return;
        }
        clearLauncherSession();
        microsoftAccount = null;
        crackedMode = true;
        deleteMicrosoftAccount();
        signInPromptDismissed = false;
        updateAccountUi();
        updateAdUi();
        setProgress(0, "Switch account");
        log("Cleared saved launcher and game accounts before switching.");
        signInPromptTitle.setText("Switch Gamble account");
        signInPromptText.setText("Saved accounts were removed. Starting a fresh launcher sign-in.");
        updateAccountUi();
        startSignIn();
    }

    private void clearLauncherSession() {
        launcherToken = "";
        launcherUser = null;
        launcherAds = null;
        deleteLauncherToken();
    }

    private void startMicrosoftSignIn() {
        startMicrosoftSignIn(false);
    }

    private void startMicrosoftSignIn(final boolean switchAccount) {
        if (isMicrosoftSignInActive()) {
            cancelMicrosoftSignIn();
            return;
        }

        final String clientId = microsoftClientId();
        if (clientId.isEmpty()) {
            String message = "Microsoft sign-in is not configured for this launcher build.";
            JOptionPane.showMessageDialog(frame, message, "Microsoft sign-in", JOptionPane.WARNING_MESSAGE);
            log(message);
            return;
        }

        setProgress(0, switchAccount ? "Switch account" : "Microsoft");
        log(switchAccount ? "Starting Microsoft account switch." : "Starting Microsoft browser sign-in.");

        microsoftSignInWorker = new SwingWorker<MicrosoftAccount, Void>() {
            @Override
            protected MicrosoftAccount doInBackground() throws Exception {
                Main.this.setProgress(10, "Browser");
                MicrosoftToken microsoftToken = requestMicrosoftBrowserToken(clientId, switchAccount);
                Main.this.setProgress(45, "Xbox");
                MinecraftAuth auth = exchangeMicrosoftForMinecraft(microsoftToken.accessToken);
                if (auth.name.isEmpty() || auth.uuid.isEmpty()) {
                    throw new IOException("Minecraft profile did not include a name and UUID.");
                }
                return new MicrosoftAccount(
                    auth.name,
                    auth.uuid,
                    auth.xuid,
                    microsoftToken.refreshToken,
                    System.currentTimeMillis() + (Math.max(300L, auth.expiresInSeconds) * 1000L)
                );
            }

            @Override
            protected void done() {
                try {
                    microsoftAccount = get();
                    saveMicrosoftAccount(microsoftAccount);
                    username.setText(microsoftAccount.name);
                    crackedMode = false;
                    updateMicrosoftUi();
                    Main.this.setProgress(100, "Microsoft");
                    log("Microsoft account linked: " + microsoftAccount.name + ".");
                    updateAccountUi();
                } catch (Exception e) {
                    String message = microsoftSignInMessage(e);
                    if (message.toLowerCase(Locale.ROOT).contains("canceled")) {
                        Main.this.setProgress(0, "MS canceled");
                        log(message);
                    } else {
                        setProgressStatus("MS failed");
                        log("Microsoft sign-in failed: " + message);
                        JOptionPane.showMessageDialog(frame, message, "Microsoft sign-in failed", JOptionPane.ERROR_MESSAGE);
                    }
                } finally {
                    microsoftSignInWorker = null;
                    setBusy(false);
                    updateMicrosoftUi();
                }
            }
        };
        setBusy(true);
        updateMicrosoftUi();
        microsoftSignInWorker.execute();
    }

    private boolean isMicrosoftSignInActive() {
        return microsoftSignInWorker != null && !microsoftSignInWorker.isDone();
    }

    private boolean isGameRunning() {
        Process process = minecraftProcess;
        return process != null && process.isAlive();
    }

    private void cancelMicrosoftSignIn() {
        Runnable cancel = microsoftSignInCancel;
        if (cancel != null) cancel.run();
    }

    private void signOutMicrosoft() {
        microsoftAccount = null;
        crackedMode = true;
        deleteMicrosoftAccount();
        updateMicrosoftUi();
        setProgress(0, "MS signed out");
        log("Signed out of Microsoft account.");
    }

    private void showAccountManagerMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(SURFACE);
        menu.setBorder(BorderFactory.createLineBorder(LINE));

        boolean hasMicrosoftAccount = microsoftAccount != null && !microsoftAccount.refreshToken.isEmpty();
        JMenuItem addAccount = accountMenuItem(hasMicrosoftAccount ? "Switch Microsoft Account" : "Add Microsoft Account");
        addAccount.addActionListener(e -> startMicrosoftSignIn(true));
        menu.add(addAccount);

        menu.addSeparator();
        JMenuItem cracked = accountMenuItem("Cracked: " + cleanUsername(username.getText()));
        cracked.addActionListener(e -> selectCrackedAccount());
        menu.add(cracked);

        if (!hasMicrosoftAccount) {
            JMenuItem empty = accountMenuItem("No Microsoft account linked");
            empty.setEnabled(false);
            menu.add(empty);
        } else {
            JMenuItem useAccount = accountMenuItem("Sign In: " + microsoftAccount.name);
            useAccount.addActionListener(e -> selectMicrosoftAccount());
            menu.add(useAccount);

            JMenuItem removeAccount = accountMenuItem("Remove: " + microsoftAccount.name);
            removeAccount.addActionListener(e -> signOutMicrosoft());
            menu.add(removeAccount);
        }

        menu.show(accountManagerButton, 0, accountManagerButton.getHeight() + 4);
    }

    private JMenuItem accountMenuItem(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setBackground(SURFACE);
        item.setForeground(TEXT);
        item.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        item.setOpaque(true);
        item.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        return item;
    }

    private void selectCrackedAccount() {
        crackedMode = true;
        updateMicrosoftUi();
        setProgress(0, "Cracked");
        log("Selected cracked/offline username: " + cleanUsername(username.getText()) + ".");
    }

    private void selectMicrosoftAccount() {
        if (microsoftAccount == null || microsoftAccount.refreshToken.isEmpty()) return;
        crackedMode = false;
        username.setText(microsoftAccount.name);
        updateMicrosoftUi();
        setProgress(0, "Account ready");
        log("Selected Microsoft account: " + microsoftAccount.name + ".");
    }

    private void updateMicrosoftUi() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    updateMicrosoftUi();
                }
            });
            return;
        }

        boolean signedIn = microsoftAccount != null && !microsoftAccount.refreshToken.isEmpty();
        boolean usingMicrosoft = signedIn && !crackedMode;
        boolean signingIn = isMicrosoftSignInActive();
        microsoftName.setText(usingMicrosoft ? microsoftAccount.name : "Cracked");
        microsoftStatus.setText(usingMicrosoft
            ? "Minecraft profile auth enabled"
            : signingIn ? "Waiting for browser sign-in"
            : "Offline username mode");
        microsoftSignInButton.setText(signingIn ? "Cancel Microsoft" : signedIn ? "Switch Microsoft" : "Microsoft Sign In");
        microsoftSignOutButton.setEnabled(!signingIn && signedIn);
        if (usernameBlock != null) {
            usernameBlock.setVisible(!usingMicrosoft);
            usernameBlock.getParent().revalidate();
            usernameBlock.getParent().repaint();
        }
    }

    private void maybePromptForSignIn() {
        if (startupPromptShown || launcherUser != null || !launcherToken.isEmpty()) return;
        startupPromptShown = true;
        signInPromptDismissed = false;
        signInPromptTitle.setText("Sign in to continue");
        signInPromptText.setText("Open the Gamble Client sign-in page, then return here to launch.");
        updateAccountUi();
        promptSignInButton.requestFocusInWindow();
    }

    private void refreshLauncherAccount() {
        if (launcherToken == null || launcherToken.trim().isEmpty()) return;

        new SwingWorker<LauncherAccount, Void>() {
            @Override
            protected LauncherAccount doInBackground() throws Exception {
                ApiResponse response = apiRequest("GET", "/api/launcher/account", "", launcherToken, 200);
                return parseLauncherAccount(response.body);
            }

            @Override
            protected void done() {
                try {
                    LauncherAccount account = get();
                    launcherUser = account.user;
                    launcherAds = account.ads;
                    selectBestBuildForUser(account.user);
                    updateAccountUi();
                    updateAdUi();
                } catch (Exception e) {
                    log("Account refresh failed: " + rootMessage(e));
                }
            }
        }.execute();
    }

    private void startSponsorBreak() {
        if (launcherToken == null || launcherToken.trim().isEmpty()) {
            startSignIn();
            return;
        }

        setBusy(true);
        adButton.setEnabled(false);
        setProgress(0, "Sponsor");

        new SwingWorker<LauncherAccount, Void>() {
            private String licenseKey = "";

            @Override
            protected LauncherAccount doInBackground() throws Exception {
                ApiResponse start = apiRequest("POST", "/api/launcher/ad-reward/start", "{}", launcherToken, 200);
                LauncherAds ads = parseLauncherAds(start.body.get("ads"));
                int seconds = ads.adSeconds > 0 ? ads.adSeconds : 30;
                if (ads.adUrl == null || ads.adUrl.isBlank()) {
                    throw new IOException("Sponsor media is unavailable, so no reward can be granted.");
                }
                throw new IOException("Sponsor rewards require the JavaFX or native launcher so playback can be verified.");
            }

            @Override
            protected void done() {
                try {
                    LauncherAccount account = get();
                    launcherUser = account.user;
                    launcherAds = account.ads;
                    selectBestBuildForUser(account.user);
                    if (!licenseKey.isEmpty()) writeLicenseKey(licenseKey);
                    updateAccountUi();
                    updateAdUi();
                    Main.this.setProgress(100, "Sponsored");
                    log("Sponsored access refreshed.");
                } catch (Exception e) {
                    setProgressStatus("Ad failed");
                    log("Sponsor break failed: " + rootMessage(e));
                    JOptionPane.showMessageDialog(frame, rootMessage(e), "Sponsor break failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private Map<String, Object> beginSponsorBreakForOverlay() throws IOException {
        ensureSignedIn();
        ApiResponse start = apiRequest("POST", "/api/launcher/ad-reward/start", "{}", launcherToken, 200);
        LauncherAds ads = parseLauncherAds(start.body.get("ads"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("adUrl", ads.adUrl);
        out.put("adSeconds", ads.adSeconds > 0 ? ads.adSeconds : AD_REWARD_SECONDS_FALLBACK);
        out.put("message", Json.string(start.body.get("message")));
        sponsorChallenge = Json.string(start.body.get("challenge"));
        log("Sponsor break started: " + out.get("adSeconds") + " seconds.");
        return out;
    }

    private String completeSponsorBreakForOverlay() throws IOException {
        ensureSignedIn();
        ApiResponse complete = apiRequest("POST", "/api/launcher/ad-reward/complete", "{\"challenge\":\"" + jsonEscape(sponsorChallenge) + "\"}", launcherToken, 200);
        sponsorChallenge = "";
        String licenseKey = Json.string(complete.body.get("licenseKey"));
        LauncherAccount account = parseLauncherAccount(complete.body);
        launcherUser = account.user;
        launcherAds = account.ads;
        selectBestBuildForUser(account.user);
        if (!licenseKey.isEmpty()) writeLicenseKey(licenseKey);
        updateAccountUi();
        updateAdUi();
        log("Sponsored access refreshed.");
        return Json.string(complete.body.get("message"));
    }

    private void updateAccountUi() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    updateAccountUi();
                }
            });
            return;
        }

        boolean signedIn = launcherUser != null && !launcherToken.isEmpty();
        accountName.setText(signedIn ? accountLabel(launcherUser) : "Not signed in");
        accountStatus.setText(signedIn ? accountStatusText(launcherUser) : "Launcher account required");
        signInButton.setText(signedIn ? "Switch" : "Sign In");
        signOutButton.setEnabled(signedIn);
        signInPromptPanel.setVisible(!signedIn && !signInPromptDismissed);
    }

    private void updateAdUi() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    updateAdUi();
                }
            });
            return;
        }

        if (launcherUser == null || launcherToken.isEmpty()) {
            adTitle.setText("Sponsor Break");
            adStatus.setText("Sign in to check access.");
            adMeta.setText("Paid accounts skip launcher ads.");
            adButton.setText("Sign In");
            adButton.setEnabled(true);
            adButton.setBackground(SURFACE_2);
            adButton.setForeground(TEXT);
            return;
        }

        if (launcherAds == null) {
            adTitle.setText("Access Check");
            adStatus.setText("Checking account status.");
            adMeta.setText("Backend account needed.");
            adButton.setText("Refresh");
            adButton.setEnabled(true);
            adButton.setBackground(SURFACE_2);
            adButton.setForeground(TEXT);
            return;
        }

        if (!launcherAds.required) {
            adTitle.setText("Paid Access");
            adStatus.setText("Ads are off for this account.");
            adMeta.setText(accountStatusText(launcherUser));
            adButton.setText("Ads Off");
            adButton.setEnabled(false);
            adButton.setBackground(new Color(22, 71, 52));
            adButton.setForeground(TEXT);
            return;
        }

        adTitle.setText("Sponsor Break");
        adStatus.setText(launcherAds.message.isEmpty() ? "Sponsored access available." : compactText(launcherAds.message, 34));
        adMeta.setText(launcherAds.remainingSeconds > 0
            ? "Remaining: " + compactDuration(launcherAds.remainingSeconds)
            : "Refresh Ad Tier here.");
        adButton.setText(launcherAds.canWatch ? "Watch" : "Capped");
        adButton.setEnabled(launcherAds.canWatch);
        adButton.setBackground(launcherAds.canWatch ? GOLD : SURFACE_2);
        adButton.setForeground(launcherAds.canWatch ? BACKGROUND : TEXT);
    }

    private String compactText(String value, int maxLength) {
        if (value == null) return "";
        String text = value.trim();
        if (text.length() <= maxLength) return text;
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private void ensureSignedIn() throws IOException {
        if (launcherToken == null || launcherToken.trim().isEmpty()) {
            throw new IOException("Sign in from the top bar before installing or launching.");
        }
    }

    private LauncherManifest fetchLauncherManifest(Build build) throws IOException {
        ApiResponse response = apiRequest(
            "POST",
            "/api/launcher/manifest",
            "{\"build\":\"" + jsonEscape(build.id) + "\"}",
            launcherToken,
            200
        );

        String fileName = Json.string(response.body.get("fileName"));
        String downloadUrl = Json.string(response.body.get("downloadUrl"));
        if (fileName.isEmpty() || downloadUrl.isEmpty()) {
            throw new IOException("Backend manifest did not include a jar download.");
        }
        if (!isSafeFileName(fileName)) throw new IOException("Backend manifest filename is unsafe.");
        String sha256 = Json.string(response.body.get("sha256"));
        long size = jsonLong(response.body.get("size"));
        if (size <= 0 || !sha256.matches("(?i)[0-9a-f]{64}")) {
            throw new IOException("Backend manifest is missing required size or SHA-256 integrity metadata.");
        }
        if (size > MAX_MANAGED_CLIENT_BYTES) {
            throw new IOException("Managed client artifact exceeds the 64 MiB safety limit.");
        }

        return new LauncherManifest(
            Json.string(response.body.get("build")),
            fileName,
            downloadUrl,
            Json.string(response.body.get("licenseKey")),
            sha256,
            size,
            Json.string(response.body.get("buildVersion"))
        );
    }

    private LaunchTicket fetchLaunchTicket(Build build) throws IOException {
        ensureSignedIn();
        ApiResponse response = apiRequest(
            "POST",
            "/api/launcher/launch-ticket",
            "{\"build\":\"" + jsonEscape(build.id) + "\"}",
            launcherToken,
            201
        );

        String ticket = Json.string(response.body.get("ticket"));
        if (ticket.isEmpty()) throw new IOException("Backend did not issue a launch ticket.");
        return new LaunchTicket(
            Json.string(response.body.get("build")),
            ticket,
            jsonLong(response.body.get("expiresAt"))
        );
    }

    private LauncherVersion fetchLauncherVersion() throws IOException {
        ApiResponse response = apiRequest("GET", "/api/launcher/version", "", "", 200);
        return new LauncherVersion(
            Json.string(response.body.get("version")),
            Json.string(response.body.get("minVersion")),
            Json.string(response.body.get("fileName")),
            Json.string(response.body.get("downloadUrl"))
        );
    }

    private void installSelectedBuild(final boolean launchAfterInstall) {
        final LaunchProfile profile = selectedProfile();
        if (!profile.includesGambleClient) {
            JOptionPane.showMessageDialog(frame, profile.label + " does not install the Gamble Client jar.", "Profile", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        final Build build = (Build) buildBox.getSelectedItem();
        if (build == null) return;

        setBusy(true);
        setProgress(0, "Checking");
        setUpdateStatus("Checking " + build.label + "...");
        log("Checking " + build.label + " for updates.");

        new SwingWorker<UpdateResult, Void>() {
            @Override
            protected UpdateResult doInBackground() throws Exception {
                return checkAndInstallBuild(build);
            }

            @Override
            protected void done() {
                try {
                    UpdateResult result = get();
                    Main.this.setProgress(100, result.updated ? "Updated" : "Current");
                    setUpdateStatus(result.message);
                    log(result.message);
                    log("Client jar: " + result.file.getAbsolutePath());
                    refreshVersionPanel();
                    if (launchAfterInstall) launch();
                    else if (result.updated) log("Press Launch to start Minecraft.");
                } catch (Exception e) {
                    setProgressStatus("Failed");
                    setUpdateStatus("Could not get update: " + rootMessage(e));
                    log("Install failed: " + rootMessage(e));
                    if (!showLauncherUpdateRequired(e)) {
                        JOptionPane.showMessageDialog(frame, rootMessage(e), "Install failed", JOptionPane.ERROR_MESSAGE);
                    }
                } finally {
                    if (!launchAfterInstall) setBusy(false);
                }
            }
        }.execute();
    }

    private UpdateResult checkAndInstallBuild(Build build) throws IOException {
        ensureSignedIn();
        LauncherManifest manifest = fetchLauncherManifest(build);
        ensureLocalLicenseKey(build, manifest);

        File installed = payloadJarFile(manifest);
        if (installed.isFile()) {
            if (!verifyManagedJar(installed, manifest)) {
                log("Cached client payload failed verification; replacing " + installed.getName() + ".");
            } else {
                hardenPrivateFile(installed);
                cleanupManagedClientJarsFromMods();
                ensureLoaderJar();
                writeInstallMarker(build.id, manifest, installed);
                return new UpdateResult(installed, false, "Latest managed client payload verified: " + displayManifestVersion(manifest));
            }
        }

        if (!installed.isFile()) {
            File stale = findManagedClientJar(getModsFolder(), build.id);
            if (stale != null) log("Replacing stale managed client: " + stale.getName());
        }

        File mods = getModsFolder();
        if (!mods.exists() && !mods.mkdirs()) {
            throw new IOException("Failed to create mods folder: " + mods);
        }

        setProgress(1, "Manifest");
        log("Updating to " + build.label + " " + displayManifestVersion(manifest) + ".");
        File temp = File.createTempFile("gamble-client-", ".jar");
        try {
            downloadFile(manifest.downloadUrl, temp, manifest.fileName, true);
            verifyDownloadedJar(temp, manifest);
            cleanupManagedClientJarsFromMods();
            ensureLoaderJar();
            File result = installPayloadJar(temp, manifest);
            writeInstallMarker(build.id, manifest, result);
            return new UpdateResult(result, true, "Updated managed client payload to: " + displayManifestVersion(manifest));
        } finally {
            if (temp.exists()) temp.delete();
        }
    }

    private File ensureBuildInstalled(Build build, boolean forceDownload) throws IOException {
        ensureSignedIn();
        LauncherManifest manifest = fetchLauncherManifest(build);

        ensureLocalLicenseKey(build, manifest);

        File installed = payloadJarFile(manifest);
        if (!forceDownload && installed.isFile()) {
            if (!verifyManagedJar(installed, manifest)) {
                log("Cached client payload failed verification; reinstalling " + installed.getName() + ".");
            } else {
                hardenPrivateFile(installed);
                cleanupManagedClientJarsFromMods();
                ensureLoaderJar();
                writeInstallMarker(build.id, manifest, installed);
                setProgress(6, "Client ready");
                log("Managed client payload verified: " + displayManifestVersion(manifest) + ".");
                return installed;
            }
        }

        File mods = getModsFolder();
        if (!mods.exists() && !mods.mkdirs()) {
            throw new IOException("Failed to create mods folder: " + mods);
        }

        setProgress(1, "Manifest");
        log("Installing managed " + build.label + " " + displayManifestVersion(manifest) + ".");
        File temp = File.createTempFile("gamble-client-", ".jar");
        try {
            downloadFile(manifest.downloadUrl, temp, manifest.fileName, true);
            verifyDownloadedJar(temp, manifest);
            cleanupManagedClientJarsFromMods();
            ensureLoaderJar();
            File result = installPayloadJar(temp, manifest);
            writeInstallMarker(build.id, manifest, result);
            return result;
        } finally {
            if (temp.exists()) temp.delete();
        }
    }

    private boolean verifyManagedJar(File file, LauncherManifest manifest) throws IOException {
        try {
            verifyDownloadedJar(file, manifest);
            return true;
        } catch (IOException e) {
            log("Client verification failed: " + e.getMessage());
            return false;
        }
    }

    private void verifyDownloadedJar(File file, LauncherManifest manifest) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("Managed client jar is missing.");
        }
        if (file.length() == 0) {
            throw new IOException("Managed client jar is empty.");
        }
        if (manifest.size <= 0 || file.length() != manifest.size) {
            throw new IOException("Expected " + manifest.size + " bytes but found " + file.length() + " bytes.");
        }

        String expectedHash = normalizeSha256(manifest.sha256);
        if (expectedHash.isEmpty()) {
            throw new IOException("Server manifest did not include a valid SHA-256 hash for " + manifest.fileName + ".");
        }

        String actualHash = sha256Hex(file);
        if (!expectedHash.equalsIgnoreCase(actualHash)) {
            throw new IOException("Expected SHA-256 " + expectedHash + " but found " + actualHash + ".");
        }
        verifyFabricModId(file, MANAGED_CLIENT_MOD_ID);
    }

    private void verifyFabricModId(File file, String expectedId) throws IOException {
        try (ZipFile jar = new ZipFile(file)) {
            long metadataEntries = jar.stream()
                .filter(entry -> "fabric.mod.json".equals(entry.getName()))
                .count();
            if (metadataEntries != 1) {
                throw new IOException("Managed client must contain exactly one top-level fabric.mod.json.");
            }
            ZipEntry metadata = jar.getEntry("fabric.mod.json");
            if (metadata == null || metadata.isDirectory()) {
                throw new IOException("Managed client is missing top-level fabric.mod.json.");
            }
            if (metadata.getSize() > MAX_FABRIC_METADATA_BYTES) {
                throw new IOException("Managed client fabric.mod.json exceeds the 1 MiB safety limit.");
            }
            byte[] bytes;
            try (InputStream input = jar.getInputStream(metadata);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_FABRIC_METADATA_BYTES) {
                        throw new IOException("Managed client fabric.mod.json exceeds the 1 MiB safety limit.");
                    }
                    output.write(buffer, 0, read);
                }
                bytes = output.toByteArray();
            }
            Map<String, Object> object;
            try {
                object = Json.asObject(Json.parse(new String(bytes, StandardCharsets.UTF_8)));
            } catch (RuntimeException error) {
                throw new IOException("Managed client fabric.mod.json is invalid.", error);
            }
            if (!expectedId.equals(Json.string(object.get("id")))) {
                throw new IOException("Managed client mod id must be " + expectedId + ".");
            }
        }
    }

    private boolean hasManagedClientIdentity(File file) {
        try {
            verifyFabricModId(file, MANAGED_CLIENT_MOD_ID);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private String normalizeSha256(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return text.matches("[0-9a-f]{64}") ? text : "";
    }

    private String displayManifestVersion(LauncherManifest manifest) {
        if (manifest != null && manifest.buildVersion != null && !manifest.buildVersion.trim().isEmpty()) {
            return displayClientVersion(manifest.buildVersion);
        }
        return manifest == null ? "unknown" : displayArtifactName(manifest.fileName);
    }

    private String displayClientVersion(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return "unknown";
        String[] parts = text.split("\\.");
        if (parts.length >= 3 && "1".equals(parts[0]) && "0".equals(parts[1]) && parts[2].matches("\\d+")) {
            return "1." + parts[2];
        }
        if (parts.length >= 4 && parts[0].matches("\\d+") && parts[1].matches("\\d+") && parts[2].matches("\\d+")) {
            return parts[0] + "." + parts[1] + "." + parts[2];
        }
        return text;
    }

    private File findManagedClientJar(File mods, String buildId) {
        File[] files = mods.listFiles();
        if (files == null) return null;

        String needle = buildId == null ? "" : buildId.toLowerCase(Locale.ROOT).replace("_", "-");
        File fallback = null;
        for (File file : files) {
            if (!file.isFile()) continue;
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".jar") && !lower.endsWith(".jar.disabled")) continue;
            if (!hasManagedClientIdentity(file)) continue;
            if (!needle.isEmpty() && lower.contains(needle)) return file;
            if (fallback == null) fallback = file;
        }
        return fallback;
    }

    private void launch() {
        final LaunchProfile launchProfile = selectedProfile();
        final Build build = (Build) buildBox.getSelectedItem();
        if (build == null) return;

        Process runningProcess = minecraftProcess;
        if (runningProcess != null && runningProcess.isAlive()) {
            stopMinecraftProcess(runningProcess);
            return;
        }

        if (launcherUser == null || launcherToken == null || launcherToken.trim().isEmpty()) {
            setProgressStatus("Sign in required");
            log("Launch blocked: sign in to the launcher first.");
            JOptionPane.showMessageDialog(frame, "Sign in to the launcher before launching Minecraft.", "Sign in required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final String requestedName = !crackedMode && microsoftAccount != null && !microsoftAccount.name.isEmpty()
            ? microsoftAccount.name
            : username.getText();
        final String name = cleanUsername(requestedName);
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Enter a username before launching.", "Username required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final Object selectedMemory = memoryGb.getSelectedItem();
        final int memory = selectedMemory instanceof Number ? ((Number) selectedMemory).intValue() : 4;
        final List<String> extraJavaArgs;
        try {
            extraJavaArgs = splitArgs(javaArgs.getText());
            validateExtraJavaArgs(extraJavaArgs);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(frame, e.getMessage(), "Java args", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (isOfflineLaunchSelected()) {
            JOptionPane.showMessageDialog(
                frame,
                "You are launching without a Microsoft account.\n\nOnline servers may reject this session. Add or select a Microsoft account from Accounts to launch with Microsoft authentication.",
                "Offline Minecraft session",
                JOptionPane.WARNING_MESSAGE
            );
        }

        clearLog();
        startLatestLaunchLog();
        setBusy(true);
        setProgress(0, "Preparing");
        log("Preparing Minecraft " + MINECRAFT_VERSION + " with Fabric Loader " + FABRIC_LOADER_VERSION + ".");

        new SwingWorker<Process, Void>() {
            @Override
            protected Process doInBackground() throws Exception {
                if (launchProfile.includesGambleClient) {
                    LauncherAccount account = refreshLauncherAccountBlocking();
                    if (!canUseBuild(account.user, build.id)) {
                        throw new IOException("This account no longer has access to " + build.label + ".");
                    }
                }
                File payloadJar = null;
                if (launchProfile.includesGambleClient) {
                    payloadJar = ensureBuildInstalled(build, false);
                } else {
                    ensureProfileFolders(launchProfile);
                    log("Using " + launchProfile.label + " profile without the Gamble Client jar.");
                }
                LaunchIdentity identity = resolveLaunchIdentity(name);
                LaunchTicket launchTicket = launchProfile.includesGambleClient ? fetchLaunchTicket(build) : null;
                return launchMinecraftProcess(launchProfile, identity, memory, extraJavaArgs, launchTicket, payloadJar);
            }

            @Override
            protected void done() {
                boolean started = false;
                try {
                    Process process = get();
                    minecraftProcess = process;
                    minecraftProcessStartedAt = System.currentTimeMillis();
                    minecraftStartupComplete = false;
                    minecraftFatalDetected = false;
                    minecraftStopRequested = false;
                    minecraftDetectedFailure = "";
                    Main.this.setProgress(100, "Running");
                    log("Minecraft process started.");
                    pipeProcessOutput(process);
                    monitorMinecraftProcess(process);
                    started = true;
                    setBusy(false);
                } catch (Exception e) {
                    setProgressStatus("Failed");
                    log("Launch failed: " + rootMessage(e));
                    JOptionPane.showMessageDialog(frame, rootMessage(e), "Launch failed", JOptionPane.ERROR_MESSAGE);
                } finally {
                    if (!started) setBusy(false);
                }
            }
        }.execute();
    }

    private LauncherAccount refreshLauncherAccountBlocking() throws IOException {
        ensureSignedIn();
        ApiResponse response = apiRequest("GET", "/api/launcher/account", "", launcherToken, 200);
        LauncherAccount account = parseLauncherAccount(response.body);
        launcherUser = account.user;
        launcherAds = account.ads;
        return account;
    }

    private void stopMinecraftProcess(Process process) {
        setProgressStatus("Stopping");
        log("Stopping Minecraft process.");
        minecraftStopRequested = true;
        setBusy(true);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                process.destroy();
                try {
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        process.waitFor(4, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
                return null;
            }

            @Override
            protected void done() {
                if (minecraftProcess == process) minecraftProcess = null;
                captureLaunchLog = false;
                Main.this.setProgress(0, process.isAlive() ? "Kill sent" : "Killed");
                if (process.isAlive()) log("Kill signal sent; Minecraft has not exited yet.");
                else log("Minecraft was stopped from the launcher.");
                setBusy(false);
            }
        }.execute();
    }

    private void ensureProfileFolders(LaunchProfile profile) throws IOException {
        File gameDir = getMinecraftFolder(profile);
        if (!gameDir.exists() && !gameDir.mkdirs()) {
            throw new IOException("Failed to create profile folder: " + gameDir);
        }
        if (!profile.fabric) return;

        File mods = new File(gameDir, "mods");
        if (!mods.exists() && !mods.mkdirs()) {
            throw new IOException("Failed to create mods folder: " + mods);
        }
        removeRetiredManagedMods(mods);
        if (profile.requiresFabricApi) ensureFabricApiInstalled(mods);
        ensureModMenuInstalled(mods);
        ensureCompatibilityModsInstalled(mods);
    }

    private void removeRetiredManagedMods(File mods) throws IOException {
        File[] files = mods.listFiles();
        if (files == null) return;
        for (File file : files) {
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (!file.isFile()) continue;
            if (!lower.startsWith("baritone-") || (!lower.endsWith(".jar") && !lower.endsWith(".jar.disabled"))) continue;
            Files.deleteIfExists(file.toPath());
            log("Removed retired Baritone compatibility jar: " + file.getName());
        }
    }

    private void ensureFabricApiInstalled(File mods) throws IOException {
        ensureManagedFabricModInstalled(mods, "Fabric API", "fabric-api-", FABRIC_API_MODRINTH_URL, "fabric-api-", true);
    }

    private void ensureModMenuInstalled(File mods) throws IOException {
        ensureManagedFabricModInstalled(mods, "Mod Menu", "modmenu-", MOD_MENU_MODRINTH_URL, "modmenu-", true);
    }

    private void ensureCompatibilityModsInstalled(File mods) throws IOException {
        for (ManagedFabricMod mod : COMPATIBILITY_MODS) {
            ensureManagedFabricModInstalled(mods, mod, false);
        }
        disableEnabledCompatibilityModsOnce(mods);
    }

    private void ensureManagedFabricModInstalled(File mods, ManagedFabricMod mod, boolean enableByDefault) throws IOException {
        ensureManagedFabricModInstalled(mods, mod.displayName, mod.filePrefix, mod.modrinthUrl, mod.tempPrefix, enableByDefault, mod.directUrl, mod.directFileName);
    }

    private void ensureManagedFabricModInstalled(File mods, String displayName, String filePrefix, String modrinthUrl, String tempPrefix, boolean enableByDefault) throws IOException {
        ensureManagedFabricModInstalled(mods, displayName, filePrefix, modrinthUrl, tempPrefix, enableByDefault, "", "");
    }

    private void ensureManagedFabricModInstalled(File mods, String displayName, String filePrefix, String modrinthUrl, String tempPrefix, boolean enableByDefault, String directUrl, String directFileName) throws IOException {
        File enabled = findManagedFabricModJar(mods, filePrefix, false);
        if (enabled != null) return;

        File disabled = findManagedFabricModJar(mods, filePrefix, true);
        if (disabled != null) {
            if (!enableByDefault) return;
            String name = disabled.getName();
            File target = new File(disabled.getParentFile(), name.substring(0, name.length() - ".disabled".length()));
            Files.move(disabled.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log("Re-enabled managed " + displayName + ": " + target.getName());
            return;
        }

        ModrinthRelease release = (directUrl == null || directUrl.trim().isEmpty())
            ? fetchModrinthRelease(modrinthUrl)
            : new ModrinthRelease(directFileName, resolveSiteUrl(directUrl));
        if (release.url.isEmpty() || release.fileName.isEmpty()) {
            throw new IOException("Could not find " + displayName + " for Minecraft " + MINECRAFT_VERSION + ".");
        }

        log("Installing managed " + displayName + ": " + release.fileName);
        File temp = File.createTempFile(tempPrefix, ".jar");
        downloadFile(release.url, temp, release.fileName, false);
        File target = new File(mods, enableByDefault ? release.fileName : release.fileName + ".disabled");
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        if (!enableByDefault) log("Left optional compatibility layer disabled: " + target.getName());
    }

    private void disableEnabledCompatibilityModsOnce(File mods) throws IOException {
        File marker = new File(mods, COMPATIBILITY_DEFAULTS_MARKER_NAME);
        if (marker.exists()) return;

        File[] files = mods.listFiles();
        if (files != null) {
            for (File file : files) {
                String lower = file.getName().toLowerCase(Locale.ROOT);
                if (!file.isFile() || !lower.endsWith(".jar") || !isManagedCompatibilityJar(lower)) continue;

                File target = new File(file.getParentFile(), file.getName() + ".disabled");
                Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log("Disabled optional compatibility layer by default: " + target.getName());
            }
        }

        Files.write(marker.toPath(), Collections.singletonList("Launcher " + LAUNCHER_VERSION + " disabled optional compatibility layers by default."), StandardCharsets.UTF_8);
    }

    private ModrinthRelease fetchModrinthRelease(String modrinthUrl) throws IOException {
        List<Object> versions = Json.asArray(Json.parse(readUrl(modrinthUrl)));
        if (versions.isEmpty()) return new ModrinthRelease("", "");

        Map<String, Object> version = Json.asObject(versions.get(0));
        List<Object> files = Json.asArray(version.get("files"));
        Map<String, Object> selected = Collections.emptyMap();
        for (Object value : files) {
            Map<String, Object> file = Json.asObject(value);
            if (Boolean.TRUE.equals(file.get("primary"))) {
                selected = file;
                break;
            }
            if (selected.isEmpty()) selected = file;
        }

        return new ModrinthRelease(Json.string(selected.get("filename")), Json.string(selected.get("url")));
    }

    private File findFabricApiJar(File mods, boolean includeDisabled) {
        return findManagedFabricModJar(mods, "fabric-api-", includeDisabled);
    }

    private File findModMenuJar(File mods, boolean includeDisabled) {
        return findManagedFabricModJar(mods, "modmenu-", includeDisabled);
    }

    private File findManagedFabricModJar(File mods, String filePrefix, boolean includeDisabled) {
        File[] files = mods.listFiles();
        if (files == null) return null;

        for (File file : files) {
            if (!file.isFile()) continue;
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (!lower.startsWith(filePrefix)) continue;
            if (lower.endsWith(".jar") || (includeDisabled && lower.endsWith(".jar.disabled"))) return file;
        }
        return null;
    }

    private void ensureLocalLicenseKey(Build build, LauncherManifest manifest) throws IOException {
        writeLicenseKey("");
    }

    private LauncherLicense requestLauncherLicense(Build build) throws IOException {
        ApiResponse response = apiRequest(
            "POST",
            "/api/launcher/license",
            "{\"build\":\"" + jsonEscape(build.id) + "\",\"reason\":\"missing_local_license\"}",
            launcherToken,
            200,
            201
        );
        return new LauncherLicense(Json.string(response.body.get("licenseKey")));
    }

    private LaunchIdentity resolveLaunchIdentity(String fallbackName) throws IOException {
        if (crackedMode || microsoftAccount == null || microsoftAccount.refreshToken.isEmpty()) {
            log("Launching without a Microsoft account as " + fallbackName + ". Online servers may reject this session.");
            return LaunchIdentity.offline(fallbackName);
        }

        String clientId = microsoftClientId();
        if (clientId.isEmpty()) {
            throw new IOException("Microsoft account is linked, but Microsoft sign-in is not configured for this launcher build.");
        }

        log("Refreshing Microsoft account: " + microsoftAccount.name + ".");
        MicrosoftToken microsoftToken = refreshMicrosoftToken(clientId, microsoftAccount.refreshToken);
        MinecraftAuth auth = exchangeMicrosoftForMinecraft(microsoftToken.accessToken);
        microsoftAccount = new MicrosoftAccount(
            auth.name,
            auth.uuid,
            auth.xuid,
            microsoftToken.refreshToken.isEmpty() ? microsoftAccount.refreshToken : microsoftToken.refreshToken,
            System.currentTimeMillis() + (Math.max(300L, auth.expiresInSeconds) * 1000L)
        );
        saveMicrosoftAccount(microsoftAccount);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                username.setText(microsoftAccount.name);
                updateMicrosoftUi();
            }
        });

        log("Launching with Microsoft profile: " + auth.name + ".");
        return LaunchIdentity.online(auth.name, auth.uuid, auth.accessToken, auth.xuid);
    }

    private Process launchMinecraftProcess(LaunchProfile launchProfile, LaunchIdentity identity, int memory, List<String> extraJavaArgs, LaunchTicket launchTicket, File payloadJar) throws IOException {
        File gameDir = getMinecraftFolder(launchProfile);
        File versionsDir = new File(gameDir, "versions");
        if (!versionsDir.exists() && !versionsDir.mkdirs()) {
            throw new IOException("Failed to create versions folder: " + versionsDir);
        }

        String versionId;
        if (launchProfile.fabric) {
            versionId = ensureFabricVersionJson(gameDir);
        } else {
            ensureVanillaVersionJson(gameDir, MINECRAFT_VERSION);
            versionId = MINECRAFT_VERSION;
        }
        VersionProfile profile = loadVersionProfile(gameDir, versionId);

        setProgress(8, "Libraries");
        List<File> classpath = ensureLibraries(gameDir, profile);

        setProgress(42, "Client jar");
        File clientJar = ensureClientJar(gameDir, profile);
        classpath.add(clientJar);

        setProgress(50, "Assets");
        ensureAssets(gameDir, profile);

        setProgress(70, "Natives");
        File nativesDir = extractNatives(gameDir, versionId, profile);

        setProgress(82, "Launching");
        File launchTicketFile = null;
        File livePayload = null;
        try {
            launchTicketFile = launchTicket != null ? writeLaunchTicketFile(launchTicket) : null;
            livePayload = payloadJar != null ? prepareLaunchPayload(payloadJar) : null;
            List<String> command = buildLaunchCommand(gameDir, profile, classpath, nativesDir, identity, memory, versionId, extraJavaArgs, launchProfile, launchTicketFile, launchTicket != null ? launchTicket.build : "", livePayload);
            LaunchValidation validation = validateLaunchSetup(gameDir, profile, classpath, nativesDir, versionId, launchProfile, identity);
            logValidationReport(validation);
            logLaunchCommandDetails(command, profile.mainClass, gameDir);
            log("Starting Java: " + command.get(0));
            log("Main class: " + profile.mainClass);

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(gameDir);
            builder.redirectErrorStream(false);
            Process process = builder.start();
            scheduleLaunchArtifactCleanup(process, launchTicketFile, livePayload);
            return process;
        } catch (IOException e) {
            deleteQuietly(launchTicketFile);
            deleteQuietly(livePayload);
            throw e;
        }
    }

    private LaunchValidation validateLaunchSetup(File gameDir, VersionProfile profile, List<File> classpath, File nativesDir, String versionId, LaunchProfile launchProfile, LaunchIdentity identity) {
        LaunchValidation validation = new LaunchValidation();

        File java = new File(javaExecutable());
        if (java.isFile() || javaExecutable().equals("java") || javaExecutable().endsWith(File.separator + "java")) {
            validation.ok("Java executable: " + javaExecutable());
        } else {
            validation.error("Java executable missing: " + javaExecutable());
        }

        int javaFeature = Runtime.version().feature();
        if (javaFeature >= 21) validation.ok("Java runtime feature version: " + javaFeature);
        else validation.error("Java 21+ is required, found Java " + javaFeature + ".");

        if (gameDir.isDirectory()) validation.ok("Working directory exists: " + gameDir.getAbsolutePath());
        else validation.error("Working directory is missing: " + gameDir.getAbsolutePath());

        File versionJson = new File(new File(new File(gameDir, "versions"), versionId), versionId + ".json");
        if (versionJson.isFile()) validation.ok("Version profile exists: " + versionJson.getAbsolutePath());
        else validation.error("Version profile missing: " + versionJson.getAbsolutePath());

        if (launchProfile.fabric) {
            if (profile.mainClass.contains("KnotClient")) validation.ok("Fabric main class: " + profile.mainClass);
            else validation.warn("Fabric profile main class is unexpected: " + profile.mainClass);
        } else {
            validation.ok("Vanilla profile selected.");
        }

        File assetIndex = new File(new File(new File(gameDir, "assets"), "indexes"), profile.assetIndexId + ".json");
        if (profile.assetIndexId.isEmpty()) validation.error("Asset index id is missing from the version profile.");
        else if (assetIndex.isFile()) validation.ok("Asset index exists: " + assetIndex.getAbsolutePath());
        else validation.error("Asset index missing: " + assetIndex.getAbsolutePath());

        int missingLibraries = 0;
        for (File entry : classpath) {
            if (!entry.isFile()) {
                missingLibraries++;
                validation.error("Classpath entry missing: " + entry.getAbsolutePath());
            }
        }
        if (missingLibraries == 0) validation.ok("Classpath entries present: " + classpath.size());

        if (nativesDir.isDirectory()) {
            File[] natives = nativesDir.listFiles();
            validation.ok("Native library folder exists: " + nativesDir.getAbsolutePath() + " (" + (natives == null ? 0 : natives.length) + " files)");
        } else {
            validation.error("Native library folder missing: " + nativesDir.getAbsolutePath());
        }

        File mods = new File(gameDir, "mods");
        if (launchProfile.fabric) {
            if (!mods.isDirectory()) {
                validation.warn("Mods folder is missing: " + mods.getAbsolutePath());
            } else {
                File[] files = mods.listFiles();
                int enabledJars = 0;
                boolean fabricApi = false;
                boolean modMenu = false;
                int compatibilityAvailable = 0;
                int compatibilityEnabled = 0;
                if (files != null) {
                    for (File file : files) {
                        String lower = file.getName().toLowerCase(Locale.ROOT);
                        if (file.isFile() && lower.endsWith(".jar")) {
                            enabledJars++;
                            if (isFabricApiJar(lower)) fabricApi = true;
                            if (isModMenuJar(lower)) modMenu = true;
                        }
                        if (file.isFile() && isManagedCompatibilityJar(lower)) {
                            compatibilityAvailable++;
                            if (lower.endsWith(".jar")) compatibilityEnabled++;
                        }
                    }
                }
                validation.ok("Enabled mod jars: " + enabledJars);
                if (launchProfile.requiresFabricApi && fabricApi) validation.ok("Required Fabric API jar is enabled.");
                else if (launchProfile.requiresFabricApi) validation.error("Fabric dependency missing: fabric-api");
                if (modMenu) validation.ok("Managed Mod Menu jar is enabled.");
                else validation.warn("Managed helper missing: Mod Menu");
                if (compatibilityAvailable >= COMPATIBILITY_MODS.length) {
                    validation.ok("Managed compatibility jars available: " + compatibilityAvailable + "/" + COMPATIBILITY_MODS.length + " (" + compatibilityEnabled + " enabled).");
                } else {
                    validation.warn("Managed compatibility jars available: " + compatibilityAvailable + "/" + COMPATIBILITY_MODS.length + " (" + compatibilityEnabled + " enabled).");
                }
            }
        }

        if (identity.playerName == null || identity.playerName.trim().isEmpty()) {
            validation.error("Launch account name is empty.");
        } else if ("msa".equals(identity.userType)) {
            if (identity.accessToken == null || identity.accessToken.trim().isEmpty() || "0".equals(identity.accessToken)) {
                validation.error("Microsoft account has no Minecraft access token.");
            } else {
                validation.ok("Microsoft account ready: " + identity.playerName);
            }
        } else {
            validation.warn("Launching without Microsoft account: " + identity.playerName + ". Online servers may reject this session.");
        }

        return validation;
    }

    private void logValidationReport(LaunchValidation validation) {
        log("Pre-launch validation:");
        for (String line : validation.ok) log("  OK: " + line);
        for (String line : validation.warnings) log("  WARN: " + line);
        for (String line : validation.errors) log("  ERROR: " + line);
        if (validation.errors.isEmpty()) log("Pre-launch validation passed.");
        else log("Pre-launch validation found " + validation.errors.size() + " issue(s).");
    }

    private void logLaunchCommandDetails(List<String> command, String mainClass, File gameDir) {
        int cpIndex = command.indexOf("-cp");
        if (cpIndex < 0) cpIndex = command.indexOf("-classpath");
        int mainIndex = command.indexOf(mainClass);

        String classpath = cpIndex >= 0 && cpIndex + 1 < command.size() ? command.get(cpIndex + 1) : "";
        List<String> jvmArgs = new ArrayList<>();
        if (mainIndex > 1) {
            for (int i = 1; i < mainIndex; i++) {
                if (i == cpIndex || i == cpIndex + 1) continue;
                jvmArgs.add(command.get(i));
            }
        }

        List<String> gameArgs = mainIndex >= 0 && mainIndex + 1 < command.size()
            ? new ArrayList<>(command.subList(mainIndex + 1, command.size()))
            : Collections.emptyList();

        log("Launch command diagnostics:");
        log("  Working directory: " + gameDir.getAbsolutePath());
        log("  Java executable: " + command.get(0));
        log("  JVM arguments: " + String.join(" ", redactLaunchSecrets(jvmArgs)));
        log("  Main class: " + mainClass);
        log("  Classpath entries: " + (classpath.isEmpty() ? 0 : classpath.split(Pattern.quote(File.pathSeparator), -1).length));
        log("  Classpath: " + classpath);
        log("  Minecraft arguments: " + String.join(" ", redactLaunchSecrets(gameArgs)));
        log("  Full command: " + String.join(" ", redactLaunchSecrets(command)));
    }

    private String ensureFabricVersionJson(File gameDir) throws IOException {
        String versionId = "fabric-loader-" + FABRIC_LOADER_VERSION + "-" + MINECRAFT_VERSION;
        File versionDir = new File(new File(gameDir, "versions"), versionId);
        File versionJson = new File(versionDir, versionId + ".json");

        if (!versionJson.exists()) {
            if (!versionDir.exists() && !versionDir.mkdirs()) {
                throw new IOException("Failed to create Fabric version folder: " + versionDir);
            }
            log("Downloading Fabric launch profile.");
            downloadFile(FABRIC_PROFILE_URL, versionJson, "Fabric profile", false);
        }

        ensureVanillaVersionJson(gameDir, MINECRAFT_VERSION);
        return versionId;
    }

    private void ensureVanillaVersionJson(File gameDir, String versionId) throws IOException {
        File versionDir = new File(new File(gameDir, "versions"), versionId);
        File versionJson = new File(versionDir, versionId + ".json");
        if (versionJson.exists()) return;

        if (!versionDir.exists() && !versionDir.mkdirs()) {
            throw new IOException("Failed to create Minecraft version folder: " + versionDir);
        }

        log("Downloading Mojang version manifest.");
        String manifestText = readUrl(VERSION_MANIFEST_URL);
        Map<String, Object> manifest = Json.asObject(Json.parse(manifestText));
        List<Object> versions = Json.asArray(manifest.get("versions"));
        String versionUrl = null;
        for (Object entry : versions) {
            Map<String, Object> version = Json.asObject(entry);
            if (versionId.equals(Json.string(version.get("id")))) {
                versionUrl = Json.string(version.get("url"));
                break;
            }
        }

        if (versionUrl == null || versionUrl.isEmpty()) {
            throw new IOException("Could not find Minecraft " + versionId + " in Mojang version manifest.");
        }

        log("Downloading Minecraft " + versionId + " profile.");
        downloadFile(versionUrl, versionJson, "Minecraft profile", false);
    }

    private VersionProfile loadVersionProfile(File gameDir, String versionId) throws IOException {
        File jsonFile = new File(new File(new File(gameDir, "versions"), versionId), versionId + ".json");
        if (!jsonFile.exists()) throw new IOException("Missing version profile: " + jsonFile);

        Map<String, Object> root = Json.asObject(Json.parse(readFile(jsonFile)));
        String inheritsFrom = Json.string(root.get("inheritsFrom"));
        VersionProfile profile = inheritsFrom.isEmpty() ? new VersionProfile() : loadVersionProfile(gameDir, inheritsFrom);

        profile.id = versionId;
        String mainClass = Json.string(root.get("mainClass"));
        if (!mainClass.isEmpty()) profile.mainClass = mainClass;

        Map<String, Object> assetIndex = Json.asObject(root.get("assetIndex"));
        if (!assetIndex.isEmpty()) {
            profile.assetIndexId = Json.string(assetIndex.get("id"));
            profile.assetIndexUrl = Json.string(assetIndex.get("url"));
        }

        Map<String, Object> downloads = Json.asObject(root.get("downloads"));
        Map<String, Object> clientDownload = Json.asObject(downloads.get("client"));
        if (!clientDownload.isEmpty()) {
            profile.clientVersionId = versionId;
            profile.clientJarUrl = Json.string(clientDownload.get("url"));
        }

        List<Object> libraries = Json.asArray(root.get("libraries"));
        for (Object item : libraries) {
            Library library = parseLibrary(item);
            if (library != null) profile.libraries.add(library);
        }

        Map<String, Object> arguments = Json.asObject(root.get("arguments"));
        if (!arguments.isEmpty()) {
            List<String> jvmArgs = parseArguments(Json.asArray(arguments.get("jvm")));
            if (!jvmArgs.isEmpty()) {
                profile.jvmArguments.addAll(jvmArgs);
            }

            List<String> gameArgs = parseArguments(Json.asArray(arguments.get("game")));
            if (!gameArgs.isEmpty()) {
                profile.gameArguments.clear();
                profile.gameArguments.addAll(gameArgs);
            }
        } else {
            String legacyArgs = Json.string(root.get("minecraftArguments"));
            if (!legacyArgs.isEmpty()) {
                profile.gameArguments.clear();
                Collections.addAll(profile.gameArguments, legacyArgs.split("\\s+"));
            }
        }

        return profile;
    }

    private Library parseLibrary(Object value) {
        Map<String, Object> object = Json.asObject(value);
        String name = Json.string(object.get("name"));
        if (name.isEmpty()) return null;

        Library library = new Library();
        library.name = name;
        library.rules = Json.asArray(object.get("rules"));

        Map<String, Object> downloads = Json.asObject(object.get("downloads"));
        Map<String, Object> artifact = Json.asObject(downloads.get("artifact"));
        library.artifactPath = Json.string(artifact.get("path"));
        library.artifactUrl = Json.string(artifact.get("url"));
        if (library.artifactPath.isEmpty()) {
            library.artifactPath = mavenArtifactPath(name);
            library.artifactUrl = mavenArtifactUrl(Json.string(object.get("url")), library.artifactPath);
        }

        library.natives = Json.asStringMap(Json.asObject(object.get("natives")));
        Map<String, Object> classifiers = Json.asObject(downloads.get("classifiers"));
        for (Map.Entry<String, Object> entry : classifiers.entrySet()) {
            Map<String, Object> classifier = Json.asObject(entry.getValue());
            library.classifierPaths.put(entry.getKey(), Json.string(classifier.get("path")));
            library.classifierUrls.put(entry.getKey(), Json.string(classifier.get("url")));
        }

        return library;
    }

    private String mavenArtifactPath(String name) {
        String extension = "jar";
        String coordinate = name;
        int extensionIndex = coordinate.indexOf('@');
        if (extensionIndex >= 0) {
            extension = coordinate.substring(extensionIndex + 1);
            coordinate = coordinate.substring(0, extensionIndex);
        }

        String[] parts = coordinate.split(":");
        if (parts.length < 3) return "";

        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length >= 4 && !parts[3].isEmpty() ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + "." + extension;
    }

    private String mavenArtifactUrl(String baseUrl, String artifactPath) {
        if (baseUrl == null || baseUrl.trim().isEmpty() || artifactPath.isEmpty()) return "";
        String normalized = baseUrl.trim();
        if (!normalized.endsWith("/")) normalized += "/";
        return normalized + artifactPath;
    }

    private List<String> parseArguments(List<Object> values) {
        List<String> out = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof String) {
                out.add((String) value);
                continue;
            }

            Map<String, Object> object = Json.asObject(value);
            if (!rulesAllow(Json.asArray(object.get("rules")))) continue;

            Object argumentValue = object.get("value");
            if (argumentValue instanceof String) {
                out.add((String) argumentValue);
            } else {
                List<Object> nested = Json.asArray(argumentValue);
                for (Object nestedValue : nested) {
                    if (nestedValue instanceof String) out.add((String) nestedValue);
                }
            }
        }
        return out;
    }

    private List<File> ensureLibraries(File gameDir, VersionProfile profile) throws IOException {
        List<File> classpath = new ArrayList<>();
        File librariesDir = new File(gameDir, "libraries");

        for (Library library : profile.libraries) {
            if (!rulesAllow(library.rules)) continue;

            if (!library.artifactPath.isEmpty()) {
                File file = new File(librariesDir, library.artifactPath);
                if (!file.exists()) {
                    if (library.artifactUrl.isEmpty()) throw new IOException("No download URL for library: " + library.name);
                    log("Downloading library: " + library.name);
                    downloadFile(library.artifactUrl, file, library.name, false);
                }
                classpath.add(file);
            }

            NativeArtifact nativeArtifact = nativeArtifact(library);
            if (nativeArtifact != null) {
                File file = new File(librariesDir, nativeArtifact.path);
                if (!file.exists()) {
                    if (nativeArtifact.url.isEmpty()) throw new IOException("No native download URL for library: " + library.name);
                    log("Downloading native: " + library.name);
                    downloadFile(nativeArtifact.url, file, library.name + " native", false);
                }
            }
        }

        return classpath;
    }

    private File ensureClientJar(File gameDir, VersionProfile profile) throws IOException {
        if (profile.clientVersionId.isEmpty() || profile.clientJarUrl.isEmpty()) {
            throw new IOException("Version profile does not include a Minecraft client jar URL.");
        }

        File clientJar = new File(new File(new File(gameDir, "versions"), profile.clientVersionId), profile.clientVersionId + ".jar");
        if (!clientJar.exists()) {
            log("Downloading Minecraft client jar: " + profile.clientVersionId);
            downloadFile(profile.clientJarUrl, clientJar, "Minecraft client jar", true);
        }
        return clientJar;
    }

    private void ensureAssets(File gameDir, VersionProfile profile) throws IOException {
        if (profile.assetIndexId.isEmpty() || profile.assetIndexUrl.isEmpty()) {
            throw new IOException("Version profile does not include an asset index.");
        }

        File assetsDir = new File(gameDir, "assets");
        File indexesDir = new File(assetsDir, "indexes");
        File indexFile = new File(indexesDir, profile.assetIndexId + ".json");
        if (!indexFile.exists()) {
            log("Downloading asset index: " + profile.assetIndexId);
            downloadFile(profile.assetIndexUrl, indexFile, "asset index", false);
        }

        Map<String, Object> index = Json.asObject(Json.parse(readFile(indexFile)));
        Map<String, Object> objects = Json.asObject(index.get("objects"));
        List<AssetDownload> missing = new ArrayList<>();
        for (Object value : objects.values()) {
            Map<String, Object> asset = Json.asObject(value);
            String hash = Json.string(asset.get("hash"));
            if (hash.length() < 2) continue;
            File objectFile = new File(new File(new File(assetsDir, "objects"), hash.substring(0, 2)), hash);
            if (!objectFile.exists()) {
                missing.add(new AssetDownload(hash, objectFile));
            }
        }

        if (missing.isEmpty()) {
            log("Assets ready.");
            return;
        }

        log("Downloading missing assets: " + missing.size() + " (parallel)");
        int workers = Math.max(2, Math.min(12, Runtime.getRuntime().availableProcessors() * 2));
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        AtomicInteger done = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (AssetDownload asset : missing) {
            futures.add(pool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!asset.file.exists()) {
                            downloadFile(ASSET_BASE_URL + asset.hash.substring(0, 2) + "/" + asset.hash, asset.file, "asset " + asset.hash, false);
                        }
                        int value = done.incrementAndGet();
                        if (value == missing.size() || value % 20 == 0) {
                            int valuePct = 50 + Math.min(18, (value * 18) / Math.max(1, missing.size()));
                            setProgress(valuePct, "Assets " + value + "/" + missing.size());
                        }
                    } catch (IOException e) {
                        throw new AssetDownloadException(asset.hash, e);
                    }
                }
            }));
        }

        pool.shutdown();
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Asset download interrupted.", e);
            } catch (Exception e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (cause instanceof AssetDownloadException) {
                    throw new IOException(cause.getMessage(), cause.getCause());
                }
                throw new IOException("Asset download failed: " + cause.getMessage(), cause);
            }
        }
    }

    private File extractNatives(File gameDir, String versionId, VersionProfile profile) throws IOException {
        File nativesDir = new File(new File(new File(gameDir, "versions"), versionId), "natives");
        if (!nativesDir.exists() && !nativesDir.mkdirs()) {
            throw new IOException("Failed to create natives folder: " + nativesDir);
        }

        File librariesDir = new File(gameDir, "libraries");
        for (Library library : profile.libraries) {
            if (!rulesAllow(library.rules)) continue;

            NativeArtifact artifact = nativeArtifact(library);
            if (artifact == null) continue;

            File file = new File(librariesDir, artifact.path);
            if (!file.exists()) continue;
            unzipNatives(file, nativesDir);
        }

        return nativesDir;
    }

    private void unzipNatives(File zip, File targetDir) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip.toPath())))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || name.startsWith("META-INF/") || name.contains("..")) continue;

                File out = new File(targetDir, new File(name).getName());
                try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out))) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        stream.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private List<String> buildLaunchCommand(File gameDir, VersionProfile profile, List<File> classpath, File nativesDir, LaunchIdentity identity, int memory, String versionId, List<String> extraJavaArgs, LaunchProfile launchProfile, File launchTicketFile, String launchBuild, File payloadJar) {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-Xmx" + memory + "G");
        if (Runtime.version().feature() >= 24) command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-Djava.library.path=" + nativesDir.getAbsolutePath());
        command.add("-Dminecraft.launcher.brand=GambleClientLauncher");
        command.add("-Dminecraft.launcher.version=" + LAUNCHER_VERSION);
        if (launchTicketFile != null) {
            command.add("-Dgamble.launchTicketFile=" + launchTicketFile.getAbsolutePath());
        }
        if (launchProfile.includesGambleClient && launchBuild != null && !launchBuild.isBlank()) {
            command.add("-Dgamble.launchBuild=" + launchBuild);
        }
        if (payloadJar != null) {
            command.add("-Dfabric.addMods=" + payloadJar.getAbsolutePath());
        }
        if (launchProfile.fabric && !profile.clientVersionId.isEmpty()) {
            File gameJar = new File(new File(new File(gameDir, "versions"), profile.clientVersionId), profile.clientVersionId + ".jar");
            command.add("-Dfabric.gameJarPath=" + gameJar.getAbsolutePath());
        }
        for (String arg : profile.jvmArguments) {
            if (isLauncherManagedJvmArg(arg)) continue;
            command.add(replaceJvmPlaceholders(arg, gameDir, profile, classpath, nativesDir));
        }
        addDefaultCapeProviderProperties(command);
        command.addAll(extraJavaArgs);
        command.add("-cp");
        command.add(joinClasspath(classpath));
        command.add(profile.mainClass);

        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("${auth_player_name}", identity.playerName);
        replacements.put("${version_name}", launchProfile.label + " " + MINECRAFT_VERSION);
        replacements.put("${game_directory}", gameDir.getAbsolutePath());
        replacements.put("${assets_root}", new File(gameDir, "assets").getAbsolutePath());
        replacements.put("${assets_index_name}", profile.assetIndexId);
        replacements.put("${auth_uuid}", identity.uuid);
        replacements.put("${auth_access_token}", identity.accessToken);
        replacements.put("${clientid}", microsoftClientId());
        replacements.put("${auth_xuid}", identity.xuid);
        replacements.put("${user_type}", identity.userType);
        replacements.put("${version_type}", "release");
        replacements.put("${classpath}", joinClasspath(classpath));
        replacements.put("${natives_directory}", nativesDir.getAbsolutePath());
        replacements.put("${launcher_name}", "GambleClientLauncher");
        replacements.put("${launcher_version}", LAUNCHER_VERSION);

        for (String arg : profile.gameArguments) {
            command.add(replacePlaceholders(arg, replacements));
        }

        return command;
    }

    private boolean isLauncherManagedJvmArg(String arg) {
        return arg == null
            || arg.isBlank()
            || arg.startsWith("-Djava.library.path=")
            || arg.startsWith("-Dminecraft.launcher.brand=")
            || arg.startsWith("-Dminecraft.launcher.version=")
            || arg.startsWith("-Dgamble.capes.")
            || arg.equals("-DFabricMcEmu=")
            || "net.minecraft.client.main.Main".equals(arg)
            || arg.endsWith(".KnotClient")
            || arg.equals("-cp")
            || arg.equals("-classpath")
            || arg.contains("${classpath}")
            || arg.contains("${natives_directory}");
    }

    private String replaceJvmPlaceholders(String arg, File gameDir, VersionProfile profile, List<File> classpath, File nativesDir) {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("${natives_directory}", nativesDir.getAbsolutePath());
        replacements.put("${launcher_name}", "GambleClientLauncher");
        replacements.put("${launcher_version}", LAUNCHER_VERSION);
        replacements.put("${classpath}", joinClasspath(classpath));
        replacements.put("${game_directory}", gameDir.getAbsolutePath());
        replacements.put("${version_name}", profile.id);
        return replacePlaceholders(arg, replacements);
    }

    private boolean isOfflineLaunchSelected() {
        return crackedMode || microsoftAccount == null || microsoftAccount.refreshToken == null || microsoftAccount.refreshToken.trim().isEmpty();
    }

    private void validateExtraJavaArgs(List<String> args) {
        for (String arg : args) {
            if ("net.minecraft.client.main.Main".equals(arg) || arg.endsWith(".KnotClient")) {
                throw new IllegalArgumentException(
                    "Java Args should only contain JVM options.\n\n"
                        + "Remove the Minecraft main class and any game arguments from Java Args: " + arg
                );
            }
        }
    }

    private List<String> splitArgs(String value) {
        List<String> args = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) return args;

        StringBuilder current = new StringBuilder();
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaping = false;

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }

            if (ch == '\\' && !singleQuoted) {
                char next = i + 1 < value.length() ? value.charAt(i + 1) : '\0';
                if (Character.isWhitespace(next) || next == '\\' || next == '"' || next == '\'') {
                    escaping = true;
                    continue;
                }
                current.append(ch);
                continue;
            }

            if (ch == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
                continue;
            }

            if (ch == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
                continue;
            }

            if (Character.isWhitespace(ch) && !singleQuoted && !doubleQuoted) {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(ch);
        }

        if (escaping) current.append('\\');
        if (singleQuoted || doubleQuoted) {
            throw new IllegalArgumentException("Close the quote in Java args before launching.");
        }
        if (current.length() > 0) args.add(current.toString());
        return args;
    }

    private NativeArtifact nativeArtifact(Library library) {
        if (library.natives.isEmpty()) return null;

        String classifier = library.natives.get(osName());
        if (classifier == null || classifier.isEmpty()) return null;
        classifier = classifier.replace("${arch}", is64Bit() ? "64" : "32");

        String path = library.classifierPaths.get(classifier);
        String url = library.classifierUrls.get(classifier);
        if (path == null || path.isEmpty()) return null;
        return new NativeArtifact(path, url == null ? "" : url);
    }

    private boolean rulesAllow(List<Object> rules) {
        if (rules.isEmpty()) return true;

        boolean allowed = false;
        for (Object value : rules) {
            Map<String, Object> rule = Json.asObject(value);
            if (!ruleApplies(rule)) continue;

            String action = Json.string(rule.get("action"));
            if ("allow".equals(action)) allowed = true;
            if ("disallow".equals(action)) allowed = false;
        }

        return allowed;
    }

    private boolean ruleApplies(Map<String, Object> rule) {
        Map<String, Object> os = Json.asObject(rule.get("os"));
        if (!os.isEmpty()) {
            String name = Json.string(os.get("name"));
            if (!name.isEmpty() && !name.equals(osName())) return false;
        }

        Map<String, Object> features = Json.asObject(rule.get("features"));
        for (Map.Entry<String, Object> feature : features.entrySet()) {
            if (jsonBoolean(feature.getValue()) != featureEnabled(feature.getKey())) {
                return false;
            }
        }

        return true;
    }

    private boolean featureEnabled(String feature) {
        return false;
    }

    private MicrosoftToken requestMicrosoftBrowserToken(String clientId, boolean forceAccountPicker) throws IOException, InterruptedException {
        String state = randomBase64Url(24);
        String codeVerifier = randomBase64Url(48);
        String codeChallenge = sha256Base64Url(codeVerifier);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> code = new AtomicReference<>("");
        AtomicReference<String> error = new AtomicReference<>("");
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", MICROSOFT_REDIRECT_PORT), 0);
        ExecutorService callbackExecutor = Executors.newSingleThreadExecutor();
        Runnable cancel = new Runnable() {
            @Override
            public void run() {
                error.compareAndSet("", "Microsoft sign-in canceled.");
                latch.countDown();
                server.stop(0);
            }
        };
        server.createContext("/", exchange -> {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String responseTitle = "Microsoft sign-in complete";
            String responseText = "You can close this tab and return to the Gamble Client launcher.";
            boolean complete = true;

            if (!state.equals(query.get("state"))) {
                responseTitle = "Microsoft sign-in ignored";
                responseText = "This callback was not for the active sign-in. Return to the Microsoft tab.";
                complete = false;
            } else if (query.containsKey("error")) {
                error.set(query.getOrDefault("error_description", query.get("error")));
                responseTitle = "Microsoft sign-in failed";
                responseText = error.get();
            } else {
                code.set(query.getOrDefault("code", ""));
                if (code.get().isEmpty()) {
                    error.set("Microsoft did not return an authorization code.");
                    responseTitle = "Microsoft sign-in failed";
                    responseText = error.get();
                }
            }

            writeMicrosoftCallbackResponse(exchange, responseTitle, responseText);
            if (complete) latch.countDown();
        });
        server.setExecutor(callbackExecutor);

        try {
            microsoftSignInCancel = cancel;
            updateMicrosoftUi();
            server.start();
            String authUrl = microsoftAuthorizeUrl(clientId, codeChallenge, state, forceAccountPicker);
            log("Opening Microsoft sign-in in your browser.");
            if (!open(authUrl)) {
                log("Open failed. Visit this URL to sign in: " + authUrl);
            }

            if (!latch.await(180, TimeUnit.SECONDS)) {
                throw new IOException("Microsoft sign-in timed out.");
            }
            if (!error.get().isEmpty()) throw new IOException(error.get());
            if (code.get().isEmpty()) throw new IOException("Microsoft did not return an authorization code.");
            return exchangeMicrosoftAuthorizationCode(clientId, code.get(), codeVerifier);
        } finally {
            server.stop(0);
            if (microsoftSignInCancel == cancel) microsoftSignInCancel = null;
            callbackExecutor.shutdownNow();
        }
    }

    private String microsoftAuthorizeUrl(String clientId, String codeChallenge, String state, boolean forceAccountPicker) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("client_id", clientId);
        query.put("response_type", "code");
        query.put("redirect_uri", MICROSOFT_REDIRECT_URI);
        query.put("response_mode", "query");
        query.put("scope", MICROSOFT_SCOPE);
        query.put("code_challenge", codeChallenge);
        query.put("code_challenge_method", "S256");
        query.put("state", state);
        if (forceAccountPicker) query.put("prompt", "select_account");
        return MICROSOFT_AUTHORIZE_URL + "?" + formEncode(query);
    }

    private MicrosoftToken exchangeMicrosoftAuthorizationCode(String clientId, String code, String codeVerifier) throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("client_id", clientId);
        form.put("code", code);
        form.put("redirect_uri", MICROSOFT_REDIRECT_URI);
        form.put("code_verifier", codeVerifier);
        form.put("scope", MICROSOFT_SCOPE);
        return parseMicrosoftToken(formRequest(MICROSOFT_TOKEN_URL, form, 200).body);
    }

    private void writeMicrosoftCallbackResponse(HttpExchange exchange, String title, String message) throws IOException {
        String html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>"
            + htmlEscape(title)
            + "</title><style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#11131a;color:#f4f6fb;font-family:system-ui,sans-serif}main{max-width:440px;padding:28px;border:1px solid #2b2f3a;background:#181b24}h1{margin:0 0 10px;font-size:24px}p{margin:0;color:#b8bfcc;line-height:1.5}</style></head><body><main><h1>"
            + htmlEscape(title)
            + "</h1><p>"
            + htmlEscape(message)
            + "</p></main></body></html>";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (java.io.OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private MicrosoftDeviceCode requestMicrosoftDeviceCode(String clientId, boolean forceAccountPicker) throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("scope", MICROSOFT_SCOPE);
        if (forceAccountPicker) form.put("prompt", "select_account");

        ApiResponse response = formRequest(MICROSOFT_DEVICE_CODE_URL, form, 200);
        return new MicrosoftDeviceCode(
            Json.string(response.body.get("device_code")),
            Json.string(response.body.get("user_code")),
            Json.string(response.body.get("verification_uri")),
            Json.string(response.body.get("verification_uri_complete")),
            Json.string(response.body.get("message")),
            (int) jsonLong(response.body.get("interval")),
            jsonLong(response.body.get("expires_in"))
        );
    }

    private MicrosoftToken pollMicrosoftDeviceCode(String clientId, MicrosoftDeviceCode device) throws IOException, InterruptedException {
        long expiresAt = System.currentTimeMillis() + (Math.max(60L, device.expiresInSeconds) * 1000L);
        int interval = Math.max(2, device.intervalSeconds);

        while (System.currentTimeMillis() < expiresAt) {
            Thread.sleep(interval * 1000L);

            Map<String, String> form = new LinkedHashMap<>();
            form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            form.put("client_id", clientId);
            form.put("device_code", device.deviceCode);

            ApiResponse response = formRequest(MICROSOFT_TOKEN_URL, form, 200, 400);
            if (response.status == 200) return parseMicrosoftToken(response.body);

            String error = Json.string(response.body.get("error"));
            if ("authorization_pending".equals(error)) continue;
            if ("slow_down".equals(error)) {
                interval += 5;
                continue;
            }
            if ("authorization_declined".equals(error)) throw new IOException("Microsoft sign-in was declined.");
            if ("expired_token".equals(error)) throw new IOException("Microsoft sign-in code expired.");
            String description = Json.string(response.body.get("error_description"));
            throw new IOException(description.isEmpty() ? "Microsoft sign-in failed: " + error : description);
        }

        throw new IOException("Microsoft sign-in expired. Try again.");
    }

    private MicrosoftToken refreshMicrosoftToken(String clientId, String refreshToken) throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("client_id", clientId);
        form.put("refresh_token", refreshToken);
        form.put("scope", MICROSOFT_SCOPE);
        return parseMicrosoftToken(formRequest(MICROSOFT_TOKEN_URL, form, 200).body);
    }

    private MicrosoftToken parseMicrosoftToken(Map<String, Object> body) throws IOException {
        String accessToken = Json.string(body.get("access_token"));
        String refreshToken = Json.string(body.get("refresh_token"));
        if (accessToken.isEmpty()) throw new IOException("Microsoft did not return an access token.");
        return new MicrosoftToken(accessToken, refreshToken, jsonLong(body.get("expires_in")));
    }

    private MinecraftAuth exchangeMicrosoftForMinecraft(String microsoftAccessToken) throws IOException {
        XboxToken xbox = requestXboxToken(microsoftAccessToken);
        XboxToken xsts = requestXstsToken(xbox.token);
        MinecraftToken minecraft = requestMinecraftToken(xsts.userHash, xsts.token);
        MinecraftProfile profile = requestMinecraftProfile(minecraft.accessToken);
        return new MinecraftAuth(
            profile.name,
            profile.uuid,
            minecraft.accessToken,
            xsts.xuid,
            minecraft.expiresInSeconds
        );
    }

    private XboxToken requestXboxToken(String microsoftAccessToken) throws IOException {
        String body = "{"
            + "\"Properties\":{"
            + "\"AuthMethod\":\"RPS\","
            + "\"SiteName\":\"user.auth.xboxlive.com\","
            + "\"RpsTicket\":\"d=" + jsonEscape(microsoftAccessToken) + "\""
            + "},"
            + "\"RelyingParty\":\"http://auth.xboxlive.com\","
            + "\"TokenType\":\"JWT\""
            + "}";
        return parseXboxToken(jsonRequest("POST", XBOX_AUTH_URL, body, "", 200).body, "Xbox Live");
    }

    private XboxToken requestXstsToken(String xboxToken) throws IOException {
        String body = "{"
            + "\"Properties\":{"
            + "\"SandboxId\":\"RETAIL\","
            + "\"UserTokens\":[\"" + jsonEscape(xboxToken) + "\"]"
            + "},"
            + "\"RelyingParty\":\"rp://api.minecraftservices.com/\","
            + "\"TokenType\":\"JWT\""
            + "}";
        return parseXboxToken(jsonRequest("POST", XSTS_AUTH_URL, body, "", 200).body, "Xbox XSTS");
    }

    private XboxToken parseXboxToken(Map<String, Object> body, String label) throws IOException {
        String token = Json.string(body.get("Token"));
        Map<String, Object> displayClaims = Json.asObject(body.get("DisplayClaims"));
        List<Object> xui = Json.asArray(displayClaims.get("xui"));
        Map<String, Object> first = xui.isEmpty() ? Collections.emptyMap() : Json.asObject(xui.get(0));
        String userHash = Json.string(first.get("uhs"));
        String xuid = Json.string(first.get("xid"));
        if (token.isEmpty() || userHash.isEmpty()) {
            throw new IOException(label + " did not return a usable token.");
        }
        return new XboxToken(token, userHash, xuid);
    }

    private MinecraftToken requestMinecraftToken(String userHash, String xstsToken) throws IOException {
        String xToken = "XBL3.0 x=" + userHash + ";" + xstsToken;
        String body = "{"
            + "\"xtoken\":\"" + jsonEscape(xToken) + "\","
            + "\"platform\":\"PC_LAUNCHER\""
            + "}";
        Map<String, Object> response;
        try {
            response = jsonRequest("POST", MINECRAFT_LOGIN_URL, body, "", 200).body;
        } catch (IOException e) {
            String message = rootMessage(e);
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("invalid app registration") || lower.contains("appreginfo")) {
                throw new IOException("Minecraft Services rejected the launcher app registration after Microsoft login succeeded. Make sure this launcher is updated and using the approved Microsoft app ID.");
            }
            throw new IOException("Minecraft Services rejected the Xbox login: " + message);
        }
        String accessToken = Json.string(response.get("access_token"));
        if (accessToken.isEmpty()) throw new IOException("Minecraft did not return an access token.");
        return new MinecraftToken(accessToken, jsonLong(response.get("expires_in")));
    }

    private MinecraftProfile requestMinecraftProfile(String minecraftAccessToken) throws IOException {
        Map<String, Object> response = jsonRequest("GET", MINECRAFT_PROFILE_URL, "", minecraftAccessToken, 200).body;
        String id = Json.string(response.get("id"));
        String name = Json.string(response.get("name"));
        if (id.isEmpty() || name.isEmpty()) {
            throw new IOException("This Microsoft account does not have a Minecraft Java profile.");
        }
        return new MinecraftProfile(id.replace("-", ""), name);
    }

    private ApiResponse formRequest(String urlText, Map<String, String> values, int... acceptedStatuses) throws IOException {
        byte[] bytes = formEncode(values).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) URI.create(urlText).toURL().openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        connection.setRequestProperty("User-Agent", "GambleClientLauncher/" + LAUNCHER_VERSION);
        try (java.io.OutputStream out = connection.getOutputStream()) {
            out.write(bytes);
        }
        return parseJsonResponse(connection, urlText, acceptedStatuses);
    }

    private ApiResponse jsonRequest(String method, String urlText, String body, String bearerToken, int... acceptedStatuses) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(urlText).toURL().openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "GambleClientLauncher/" + LAUNCHER_VERSION);
        if (bearerToken != null && !bearerToken.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken.trim());
        }
        if (!body.isEmpty()) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            try (java.io.OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
        }
        return parseJsonResponse(connection, urlText, acceptedStatuses);
    }

    private ApiResponse parseJsonResponse(HttpURLConnection connection, String label, int... acceptedStatuses) throws IOException {
        int status = connection.getResponseCode();
        String responseText = readAll(status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream());
        Map<String, Object> responseBody = Collections.emptyMap();
        if (!responseText.isEmpty()) {
            try {
                responseBody = Json.asObject(Json.parse(responseText));
            } catch (Exception ignored) {
                responseBody = Collections.emptyMap();
            }
        }

        if (!statusAccepted(status, acceptedStatuses)) {
            String message = Json.string(responseBody.get("message"));
            if (message.isEmpty()) message = Json.string(responseBody.get("error_description"));
            if (message.isEmpty()) message = Json.string(responseBody.get("errorMessage"));
            if (message.isEmpty()) message = Json.string(responseBody.get("error"));
            if (message.isEmpty()) message = xboxXstsMessage(Json.string(responseBody.get("XErr")));
            if (message.isEmpty() && !responseText.isEmpty()) message = responseText;
            if (message.isEmpty()) message = label + " returned HTTP " + status;
            throw new HttpStatusException(status, message);
        }

        return new ApiResponse(status, responseBody);
    }

    private String formEncode(Map<String, String> values) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (out.length() > 0) out.append('&');
            out.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            out.append('=');
            out.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return out.toString();
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) return values;

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            String value = equals >= 0 ? pair.substring(equals + 1) : "";
            values.put(
                URLDecoder.decode(key, StandardCharsets.UTF_8),
                URLDecoder.decode(value, StandardCharsets.UTF_8)
            );
        }
        return values;
    }

    private ApiResponse apiRequest(String method, String path, String body, String bearerToken, int... acceptedStatuses) throws IOException {
        String urlText = path.startsWith("http://") || path.startsWith("https://") ? path : siteUrl() + path;
        HttpURLConnection connection = (HttpURLConnection) URI.create(urlText).toURL().openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "GambleClientLauncher/" + LAUNCHER_VERSION);
        if (bearerToken != null && !bearerToken.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken.trim());
        }

        if (!body.isEmpty()) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            try (java.io.OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
        }

        int status = connection.getResponseCode();
        String responseText = readAll(status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream());
        Map<String, Object> responseBody = Collections.emptyMap();
        if (!responseText.isEmpty()) {
            try {
                responseBody = Json.asObject(Json.parse(responseText));
            } catch (Exception ignored) {
                responseBody = Collections.emptyMap();
            }
        }

        if (!statusAccepted(status, acceptedStatuses)) {
            String message = Json.string(responseBody.get("message"));
            if (message.isEmpty() && !responseText.isEmpty()) message = responseText;
            if (status == 404 && path.startsWith("/api/launcher/")) {
                message = "Launcher backend route is not live at " + siteUrl() + ". Deploy the backend launcher routes and D1 migration.";
            }
            if (status == 426) {
                throw new LauncherOutdatedException(
                    message.isEmpty() ? "This launcher is out of date." : message,
                    Json.string(responseBody.get("version")),
                    Json.string(responseBody.get("downloadUrl"))
                );
            }
            if (message.isEmpty()) message = "Backend returned HTTP " + status;
            throw new HttpStatusException(status, message);
        }

        return new ApiResponse(status, responseBody);
    }

    private boolean statusAccepted(int status, int... acceptedStatuses) {
        for (int accepted : acceptedStatuses) {
            if (status == accepted) return true;
        }
        return false;
    }

    private int httpStatus(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpStatusException statusError) return statusError.status;
            current = current.getCause();
        }
        return 0;
    }

    private LauncherUser parseLauncherUser(Object value) {
        Map<String, Object> user = Json.asObject(value);
        return new LauncherUser(
            Json.string(user.get("email")),
            Json.string(user.get("displayName")),
            Json.string(user.get("discordUsername")),
            Json.string(user.get("selectedPlan")),
            Json.string(user.get("accessStatus")),
            jsonBoolean(user.get("ownerAccess")),
            jsonBoolean(user.get("mediaAccess")),
            jsonBoolean(user.get("testerAccess")),
            jsonBoolean(user.get("betaAccess")),
            jsonBoolean(user.get("adTierAccess"))
        );
    }

    private LauncherAccount parseLauncherAccount(Map<String, Object> body) {
        return new LauncherAccount(
            parseLauncherUser(body.get("user")),
            parseLauncherAds(body.get("ads"))
        );
    }

    private LauncherAds parseLauncherAds(Object value) {
        Map<String, Object> ads = Json.asObject(value);
        return new LauncherAds(
            jsonBoolean(ads.get("required")),
            jsonBoolean(ads.get("paid")),
            jsonBoolean(ads.get("canWatch")),
            jsonBoolean(ads.get("active")),
            Json.string(ads.get("tier")),
            Json.string(ads.get("message")),
            (int) jsonLong(ads.get("adSeconds")),
            jsonLong(ads.get("remainingSeconds")),
            Json.string(ads.get("adUrl"))
        );
    }

    private String accountLabel(LauncherUser user) {
        if (user == null) return "Not signed in";
        if (!user.displayName.isEmpty()) return user.displayName;
        if (!user.discordUsername.isEmpty()) return user.discordUsername;
        return user.email.isEmpty() ? "Signed in" : user.email;
    }

    private String accountStatusText(LauncherUser user) {
        String status = user == null ? "" : user.accessStatus;
        String plan = user == null ? "" : user.selectedPlan;
        if ("tester".equals(plan) || user != null && user.testerAccess) return "Tester";
        if (!status.isEmpty() && !plan.isEmpty() && !status.equals(plan)) return accessLabel(status) + " / " + accessLabel(plan);
        if (!status.isEmpty()) return accessLabel(status);
        if (!plan.isEmpty()) return accessLabel(plan);
        return "Signed in";
    }

    private String accessLabel(String value) {
        String normalized = value == null ? "" : value;
        return switch (normalized) {
            case "ad_tier" -> "Ad Tier";
            case "beta_plus", "lifetime_beta" -> "Beta++";
            case "owned", "release" -> "Release";
            case "tester" -> "Tester";
            case "media" -> "Media";
            case "owner" -> "Owner";
            case "banned" -> "Banned";
            case "revoked" -> "Revoked";
            case "limited" -> "Ad Tier";
            default -> value == null || value.isBlank() ? "Signed in" : value;
        };
    }

    private void selectBestBuildForUser(LauncherUser user) {
        Build best = bestBuildForUser(user);
        if (best == null) return;

        Object selected = buildBox.getSelectedItem();
        if (explicitBuildSelection && selected instanceof Build && canUseBuild(user, ((Build) selected).id)) return;
        if (selected instanceof Build && ((Build) selected).id.equals(best.id)) return;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                applyingAutomaticBuildSelection = true;
                try {
                    buildBox.setSelectedItem(best);
                } finally {
                    applyingAutomaticBuildSelection = false;
                }
                log("Selected best available build: " + best.label + ".");
            }
        });
    }

    private Build bestBuildForUser(LauncherUser user) {
        if (hasOwnerAccess(user)) return findBuild("media");
        String[] priority = {"media", "beta_plus", "release", "ad_tier"};
        for (String buildId : priority) {
            if (canUseBuild(user, buildId)) return findBuild(buildId);
        }
        return findBuild("ad_tier");
    }

    private Build findBuild(String id) {
        for (Build build : BUILDS) {
            if (build.id.equals(id)) return build;
        }
        return null;
    }

    private boolean canUseBuild(LauncherUser user, String buildId) {
        if (user == null) return false;
        if ("media".equals(buildId)) return hasMediaAccess(user);
        if ("beta_plus".equals(buildId)) return hasBetaAccess(user);
        if ("release".equals(buildId)) return isOwnedAccess(user.accessStatus);
        if ("ad_tier".equals(buildId)) return !isBlockedAccess(user.accessStatus) && !user.email.isEmpty();
        return false;
    }

    private boolean hasMediaAccess(LauncherUser user) {
        return "media".equals(user.accessStatus) || "owner".equals(user.accessStatus) ||
            "media".equals(user.selectedPlan) || "tester".equals(user.selectedPlan) || "owner".equals(user.selectedPlan) ||
            user.mediaAccess || user.testerAccess || user.ownerAccess;
    }

    private boolean hasOwnerAccess(LauncherUser user) {
        return user != null && (user.ownerAccess || "owner".equals(user.accessStatus) || "owner".equals(user.selectedPlan));
    }

    private boolean hasBetaAccess(LauncherUser user) {
        return "beta_plus".equals(user.accessStatus) || hasMediaAccess(user) ||
            "beta_plus".equals(user.selectedPlan) || "lifetime_beta".equals(user.selectedPlan) ||
            user.betaAccess;
    }

    private boolean isOwnedAccess(String value) {
        return "owned".equals(value) || "beta_plus".equals(value) || "media".equals(value) || "owner".equals(value);
    }

    private boolean isBlockedAccess(String value) {
        return "banned".equals(value) || "revoked".equals(value);
    }

    private long jsonLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0L;
        }
    }

    private boolean jsonBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private String compactDuration(long seconds) {
        if (seconds <= 0) return "0m";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return Math.max(1, minutes) + "m";
    }

    private String jsonEscape(String value) {
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                    break;
            }
        }
        return out.toString();
    }

    private void downloadFile(String urlText, File output, String label, boolean updateProgress) throws IOException {
        File parent = output.getParentFile();
        if (parent != null) {
            try {
                Files.createDirectories(parent.toPath());
            } catch (IOException e) {
                throw new IOException("Failed to create folder: " + parent, e);
            }
        }

        File temp = new File(output.getAbsolutePath() + ".part");
        Files.deleteIfExists(temp.toPath());
        HttpURLConnection connection = (HttpURLConnection) URI.create(urlText).toURL().openConnection();
        boolean completed = false;
        try {
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "GambleClientLauncher/0.1");

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                String body = readSmall(connection.getErrorStream());
                throw new IOException(label + " returned HTTP " + status + (body.isEmpty() ? "" : ": " + body));
            }

            long length = connection.getContentLengthLong();
            if (length > MAX_DOWNLOAD_BYTES) {
                throw new IOException(label + " exceeds the 512 MiB safety limit.");
            }
            if (updateProgress) setProgressStatus("Downloading");

            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
                byte[] buffer = new byte[16384];
                long total = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_DOWNLOAD_BYTES) {
                        throw new IOException(label + " exceeds the 512 MiB safety limit.");
                    }
                    out.write(buffer, 0, read);
                    if (updateProgress && length > 0) {
                        int value = (int) Math.min(99, (total * 100L) / length);
                        setProgress(value, value + "%");
                    }
                }
            }

            if (temp.length() == 0) {
                throw new IOException("Downloaded file is empty: " + label);
            }

            Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
            completed = true;
        } finally {
            connection.disconnect();
            if (!completed) Files.deleteIfExists(temp.toPath());
        }
    }

    private String sha256Hex(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("SHA-256 is not available.", e);
        }

        try (InputStream in = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        StringBuilder out = new StringBuilder();
        for (byte value : digest.digest()) {
            out.append(String.format("%02x", value & 0xff));
        }
        return out.toString();
    }

    private File installPayloadJar(File downloaded, LauncherManifest manifest) throws IOException {
        File payloads = getPayloadsFolder();
        if (!payloads.exists() && !payloads.mkdirs()) {
            throw new IOException("Failed to create payload folder: " + payloads);
        }

        File installed = payloadJarFile(manifest);
        Files.move(downloaded.toPath(), installed.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try {
            hardenPrivateFile(installed);
        } catch (IOException error) {
            Files.deleteIfExists(installed.toPath());
            throw error;
        }
        return installed;
    }

    private void cleanupManagedClientJarsFromMods() throws IOException {
        File mods = getModsFolder();
        File backupDir = new File(mods, ".gamble-client-backups");
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw new IOException("Failed to create backup folder: " + backupDir);
        }

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        File[] existing = mods.listFiles();
        if (existing != null) {
            for (File file : existing) {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (!file.isFile()) continue;
                if (!name.endsWith(".jar") && !name.endsWith(".jar.disabled")) continue;
                if (!hasManagedClientIdentity(file)) continue;

                File backup = new File(backupDir, stamp + "-" + file.getName());
                Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log("Moved old jar to backup: " + backup.getName());
            }
        }
    }

    private void ensureLoaderJar() throws IOException {
        File mods = getModsFolder();
        if (!mods.exists() && !mods.mkdirs()) {
            throw new IOException("Failed to create mods folder: " + mods);
        }

        File loader = new File(mods, LOADER_JAR_NAME);
        if (loader.isFile()) return;

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(loader))) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            String json = "{"
                + "\"schemaVersion\":1,"
                + "\"id\":\"gamble-client-loader\","
                + "\"version\":\"" + jsonEscape(LAUNCHER_VERSION) + "\","
                + "\"name\":\"Gamble Client Loader\","
                + "\"description\":\"Launcher-managed bootstrap marker for Gamble Client.\","
                + "\"authors\":[\"Gamble Client\"],"
                + "\"environment\":\"client\","
                + "\"depends\":{\"fabricloader\":\">=0.18.0\"}"
                + "}";
            zip.write(json.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        log("Installed loader marker: " + loader.getName());
    }

    private void writeLicenseKey(String key) throws IOException {
        Files.deleteIfExists(getLicenseFile().toPath());
        Files.deleteIfExists(new File(getProfileDataFolder(), "license.txt").toPath());
        Files.deleteIfExists(new File(new File(getLegacyMinecraftFolder(), "cg-mod"), "license.txt").toPath());
        log("Local license files cleared; launch tickets handle current client access.");
    }

    private void writeInstallMarker(String buildId, LauncherManifest manifest, File installed) throws IOException {
        File folder = getProfileDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Failed to create launcher data folder: " + folder);
        }

        String fileName = manifest == null ? "" : manifest.fileName;
        String text = buildId + System.lineSeparator() + fileName + System.lineSeparator();
        Files.write(new File(folder, "installed-build.txt").toPath(), text.getBytes(StandardCharsets.UTF_8));

        String installedPath = installed == null ? "" : installed.getAbsolutePath();
        String json = "{"
            + "\"schema\":1,"
            + "\"build\":\"" + jsonEscape(buildId) + "\","
            + "\"fileName\":\"" + jsonEscape(fileName) + "\","
            + "\"buildVersion\":\"" + jsonEscape(manifest == null ? "" : manifest.buildVersion) + "\","
            + "\"sha256\":\"" + jsonEscape(manifest == null ? "" : normalizeSha256(manifest.sha256)) + "\","
            + "\"size\":" + (manifest == null ? 0L : manifest.size) + ","
            + "\"installedAt\":\"" + jsonEscape(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).format(new Date())) + "\","
            + "\"path\":\"" + jsonEscape(installedPath) + "\""
            + "}";
        Files.write(new File(folder, "loader-manifest.json").toPath(), (json + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
    }

    private void saveLauncherToken(String token) throws IOException {
        File folder = getLauncherDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Failed to create launcher data folder: " + folder);
        }

        Files.write(getLauncherSessionFile().toPath(), (token + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        hardenPrivateFile(getLauncherSessionFile());
    }

    private String readLauncherToken() {
        File file = getLauncherSessionFile();
        if (!file.isFile()) {
            String legacyToken = readLegacyLauncherToken();
            if (!legacyToken.isEmpty()) {
                try {
                    saveLauncherToken(legacyToken);
                    log("Migrated existing launcher sign-in to the managed game folder.");
                } catch (IOException e) {
                    log("Could not migrate launcher sign-in: " + e.getMessage());
                }
                return legacyToken;
            }
            return "";
        }
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private String readLegacyLauncherToken() {
        File legacy = new File(new File(getLegacyMinecraftFolder(), "cg-mod"), "launcher-session.txt");
        if (!legacy.isFile()) return "";
        try {
            return new String(Files.readAllBytes(legacy.toPath()), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private void deleteLauncherToken() {
        try {
            Files.deleteIfExists(getLauncherSessionFile().toPath());
        } catch (IOException e) {
            log("Could not remove launcher session: " + e.getMessage());
        }
    }

    private void saveMicrosoftAccount(MicrosoftAccount account) throws IOException {
        File folder = getLauncherDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Failed to create launcher data folder: " + folder);
        }

        String json = "{"
            + "\"name\":\"" + jsonEscape(account.name) + "\","
            + "\"uuid\":\"" + jsonEscape(account.uuid) + "\","
            + "\"xuid\":\"" + jsonEscape(account.xuid) + "\","
            + "\"refreshToken\":\"" + jsonEscape(account.refreshToken) + "\","
            + "\"minecraftExpiresAt\":" + account.minecraftExpiresAt
            + "}";
        Files.write(getMicrosoftAccountFile().toPath(), (json + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        hardenPrivateFile(getMicrosoftAccountFile());
    }

    private MicrosoftAccount readMicrosoftAccount() {
        File file = getMicrosoftAccountFile();
        if (!file.isFile()) return null;

        try {
            Map<String, Object> body = Json.asObject(Json.parse(readFile(file)));
            String refreshToken = Json.string(body.get("refreshToken"));
            if (refreshToken.isEmpty()) return null;
            return new MicrosoftAccount(
                Json.string(body.get("name")),
                Json.string(body.get("uuid")),
                Json.string(body.get("xuid")),
                refreshToken,
                jsonLong(body.get("minecraftExpiresAt"))
            );
        } catch (Exception e) {
            log("Could not read Microsoft account: " + rootMessage(e));
            return null;
        }
    }

    private void deleteMicrosoftAccount() {
        try {
            Files.deleteIfExists(getMicrosoftAccountFile().toPath());
        } catch (IOException e) {
            log("Could not remove Microsoft account: " + e.getMessage());
        }
    }

    private boolean hasGambleJarInstalled() {
        File[] files = getModsFolder().listFiles();
        if (files == null) return false;

        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar") && hasManagedClientIdentity(file)) {
                return true;
            }
        }
        return false;
    }

    private boolean isGambleClientJar(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.startsWith("cg-client") || lower.startsWith("cg-mod");
    }

    private boolean isGambleClientLoaderJar(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.equals(LOADER_JAR_NAME) || lower.equals(LOADER_JAR_NAME + ".disabled");
    }

    private boolean isFabricApiJar(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.startsWith("fabric-api-") && (lower.endsWith(".jar") || lower.endsWith(".jar.disabled"));
    }

    private boolean isModMenuJar(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.startsWith("modmenu-") && (lower.endsWith(".jar") || lower.endsWith(".jar.disabled"));
    }

    private boolean isManagedCompatibilityJar(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".jar") && !lower.endsWith(".jar.disabled")) return false;
        for (ManagedFabricMod mod : COMPATIBILITY_MODS) {
            if (lower.startsWith(mod.filePrefix)) return true;
        }
        return false;
    }

    private String findInstalledClientJarName(boolean includeDisabled) {
        File[] files = getModsFolder().listFiles();
        if (files == null) return "";

        String disabled = "";
        for (File file : files) {
            String name = file.getName();
            String lower = name.toLowerCase(Locale.ROOT);
            if (!file.isFile()) continue;
            boolean enabledJar = lower.endsWith(".jar");
            boolean disabledJar = includeDisabled && lower.endsWith(".jar.disabled");
            if (!enabledJar && !disabledJar) continue;
            if (!isGambleClientJar(lower)) continue;
            if (enabledJar) return name;
            if (disabled.isEmpty()) disabled = name;
        }

        return disabled;
    }

    private void pipeProcessOutput(final Process process) {
        minecraftOutputThreadsRunning = 2;
        pipeProcessStream(process.getInputStream(), "[MC]", "gamble-minecraft-stdout");
        pipeProcessStream(process.getErrorStream(), "[MC-ERR]", "gamble-minecraft-stderr");
    }

    private void pipeProcessStream(final InputStream stream, final String prefix, final String threadName) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        observeMinecraftLine(line);
                        log(prefix + " " + line);
                    }
                } catch (IOException e) {
                    log(threadName + " stopped: " + e.getMessage());
                } finally {
                    minecraftOutputThreadsRunning = Math.max(0, minecraftOutputThreadsRunning - 1);
                }
            }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private void observeMinecraftLine(String line) {
        String lower = (line == null ? "" : line).toLowerCase(Locale.ROOT);
        if (lower.contains("[render thread/info]")
            || lower.contains("setting user:")
            || lower.contains("initializing gamble client")
            || lower.contains("created: ")
            || lower.contains("sound engine started")) {
            minecraftStartupComplete = true;
        }

        String failure = classifyFailureLine(line);
        if (!failure.isEmpty()) {
            minecraftFatalDetected = true;
            if (minecraftDetectedFailure.isEmpty()) minecraftDetectedFailure = failure;
        }
    }

    private void monitorMinecraftProcess(final Process process) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                final int exitCode;
                try {
                    exitCode = process.waitFor();
                    Thread.sleep(300L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            if (minecraftProcess == process) minecraftProcess = null;
                            setProgressStatus("Closed");
                            setBusy(false);
                            log("Minecraft process watcher stopped.");
                        }
                    });
                    return;
                }

                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        finishMinecraftProcess(process, exitCode);
                    }
                });
            }
        }, "gamble-minecraft-watch");
        thread.setDaemon(true);
        thread.start();
    }

    private void finishMinecraftProcess(Process process, int exitCode) {
        if (minecraftProcess == process) minecraftProcess = null;
        if (minecraftStopRequested) {
            minecraftStopRequested = false;
            captureLaunchLog = false;
            setProgress(0, "Killed");
            log("Minecraft stopped by launcher request.");
            setBusy(false);
            return;
        }
        long elapsedMs = minecraftProcessStartedAt > 0 ? Math.max(0, System.currentTimeMillis() - minecraftProcessStartedAt) : 0;
        boolean abnormalStartup = !minecraftStartupComplete;
        boolean normal = exitCode == 0 && !minecraftFatalDetected && !abnormalStartup;

        log("Minecraft exit code: " + exitCode + ".");

        if (normal) {
            setProgress(0, "Closed");
            log("Minecraft closed normally.");
        } else {
            setProgressStatus(exitCode == 0 ? "Startup aborted" : "Crashed");
            LaunchDiagnosis diagnosis = diagnoseLaunchFailure(exitCode, elapsedMs, abnormalStartup);
            log("Launch diagnostics: " + diagnosis.summary);
            if (!diagnosis.detected.isEmpty()) log("Detected: " + diagnosis.detected);
            if (!diagnosis.probableCause.isEmpty()) log("Probable cause: " + diagnosis.probableCause);
            if (!diagnosis.recommendedFix.isEmpty()) log("Recommended fix: " + diagnosis.recommendedFix);
            log("Last " + recentLaunchLines.size() + " launch log lines:");
            for (String line : lastLaunchLines(100)) log("  " + line);
            try {
                File crashLog = saveCrashLogSnapshot(exitCode);
                log("Saved launch failure log: " + crashLog.getAbsolutePath());
            } catch (IOException e) {
                log("Could not save launch failure log: " + e.getMessage());
            }
            List<String> enabledCompat = autoEnableCompatibilityLayersForCrash();
            if (!enabledCompat.isEmpty()) {
                String joined = String.join(", ", enabledCompat);
                log("Auto-enabled compatibility layer(s) for next launch: " + joined);
                JOptionPane.showMessageDialog(frame,
                    "Enabled compatibility layer(s) for the next launch:\n" + joined,
                    "Compatibility enabled",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        }

        captureLaunchLog = false;
        setBusy(false);
    }

    private File saveCrashLogSnapshot(int exitCode) throws IOException {
        File folder = new File(getLauncherDataFolder(), "crash-logs");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Failed to create crash log folder: " + folder);
        }

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        File file = new File(folder, "minecraft-crash-" + stamp + ".txt");
        String body = "Gamble Client Launcher crash log" + System.lineSeparator()
            + "Exit code: " + exitCode + System.lineSeparator()
            + "Time: " + new Date() + System.lineSeparator()
            + "Game folder: " + getMinecraftFolder().getAbsolutePath() + System.lineSeparator()
            + System.lineSeparator()
            + log.getText();
        Files.write(file.toPath(), body.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private List<String> lastLaunchLines(int limit) {
        synchronized (launchLogLock) {
            int skip = Math.max(0, recentLaunchLines.size() - limit);
            List<String> out = new ArrayList<>();
            int i = 0;
            for (String line : recentLaunchLines) {
                if (i++ >= skip) out.add(line);
            }
            return out;
        }
    }

    private List<String> autoEnableCompatibilityLayersForCrash() {
        List<String> lines = lastLaunchLines(200);
        if (lines.isEmpty()) return Collections.emptyList();

        String text = String.join("\n", lines).toLowerCase(Locale.ROOT);
        File mods = getModsFolder();
        if (!mods.isDirectory()) return Collections.emptyList();

        List<String> enabled = new ArrayList<>();
        for (ManagedFabricMod mod : COMPATIBILITY_MODS) {
            if (!mod.matchesFailure(text)) continue;
            if (findManagedFabricModJar(mods, mod.filePrefix, false) != null) continue;

            File disabled = findManagedFabricModJar(mods, mod.filePrefix, true);
            if (disabled == null) {
                try {
                    ensureManagedFabricModInstalled(mods, mod, false);
                    disabled = findManagedFabricModJar(mods, mod.filePrefix, true);
                } catch (IOException e) {
                    log("Could not prepare " + mod.displayName + " after crash: " + e.getMessage());
                }
            }

            if (disabled != null && enableDisabledJar(disabled)) {
                enabled.add(mod.displayName);
            }
        }
        return enabled;
    }

    private boolean enableDisabledJar(File disabled) {
        String name = disabled.getName();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".disabled")) return false;
        File target = new File(disabled.getParentFile(), name.substring(0, name.length() - ".disabled".length()));
        try {
            Files.move(disabled.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log("Could not enable " + disabled.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private String classifyFailureLine(String line) {
        String lower = (line == null ? "" : line).toLowerCase(Locale.ROOT);
        if (lower.contains("classnotfoundexception")) return "ClassNotFoundException";
        if (lower.contains("noclassdeffounderror")) return "NoClassDefFoundError";
        if (lower.contains("unsatisfiedlinkerror")) return "Native library failure (UnsatisfiedLinkError)";
        if ((lower.contains("mixin apply") && lower.contains("failed"))
            || lower.contains("mixinapplyerror")
            || lower.contains("mixin transformation")
            || lower.contains("invalidinjectionexception")
            || lower.contains("mixinsquared") && lower.contains("failed")) return "Mixin failure";
        if (lower.contains("mod resolution failed") || lower.contains("requires any version") || lower.contains("depends on")) return "Fabric dependency failure";
        if (lower.contains("a fatal error has been detected by the java runtime")) return "JVM crash";
        if (lower.contains("exception in thread")) return "Unhandled Java exception";
        if (lower.contains("[83ac]")) return "Native Library Error ([83ac])";
        if (lower.contains("failed to load") && lower.contains("native")) return "Native library load failure";
        return "";
    }

    private LaunchDiagnosis diagnoseLaunchFailure(int exitCode, long elapsedMs, boolean abnormalStartup) {
        String detected = minecraftDetectedFailure == null ? "" : minecraftDetectedFailure;
        String summary = exitCode == 0 && abnormalStartup
            ? "Minecraft exited with code 0 before client startup completed after " + String.format(Locale.ROOT, "%.1f", elapsedMs / 1000.0) + "s."
            : "Minecraft exited with code " + exitCode + " after " + String.format(Locale.ROOT, "%.1f", elapsedMs / 1000.0) + "s.";

        if (detected.contains("[83ac]")) {
            return new LaunchDiagnosis(
                summary,
                detected,
                "A native library failed before Minecraft reached client initialization.",
                "Your Java installation may be corrupted. Try reinstalling Java 17+ and restarting your launcher."
            );
        }
        if (detected.contains("ClassNotFoundException") || detected.contains("NoClassDefFoundError")) {
            return new LaunchDiagnosis(summary, detected, "A mod or loader referenced a class that is not on the classpath.", "Install the missing dependency or use a mod build for Minecraft " + MINECRAFT_VERSION + ".");
        }
        if (detected.contains("Mixin")) {
            return new LaunchDiagnosis(summary, detected, "A mixin failed during Fabric startup.", "Check the listed mixin config and remove/update the mod that owns it.");
        }
        if (detected.contains("Fabric dependency")) {
            return new LaunchDiagnosis(summary, detected, "Fabric dependency resolution failed.", "Open the mod list in latest-launch.log and install the missing dependency.");
        }
        if (detected.contains("Native") || detected.contains("UnsatisfiedLinkError")) {
            return new LaunchDiagnosis(summary, detected, "A native library failed to load.", "Your Java installation may be corrupted. Try reinstalling Java 17+ and restarting your launcher.");
        }
        if (abnormalStartup) {
            return new LaunchDiagnosis(
                summary,
                detected,
                "Minecraft did not reach client initialization. A pre-launch entrypoint, Java agent, or native-backed mod likely aborted silently.",
                "Inspect latest-launch.log. Disable recently added mods one at a time, starting with native or protected loaders."
            );
        }
        return new LaunchDiagnosis(summary, detected, "Minecraft reported a non-normal shutdown.", "Check the last 100 lines and any generated crash report.");
    }

    private void setBusy(boolean busy) {
        boolean gambleProfile = selectedProfile().includesGambleClient;
        boolean signedIn = launcherUser != null && launcherToken != null && !launcherToken.trim().isEmpty();
        boolean signingIn = isLauncherSignInActive();
        boolean running = minecraftProcess != null && minecraftProcess.isAlive();
        installButton.setEnabled(!busy && !running && gambleProfile && signedIn);
        accountManagerButton.setEnabled(!busy && !running);
        launchButton.setText(running ? "Kill" : "Launch");
        launchButton.setEnabled(running || (!busy && signedIn));
        signInButton.setText(signingIn ? "Cancel" : "Sign In");
        promptSignInButton.setText(signingIn ? "Cancel" : "Sign In");
        signInButton.setEnabled(!busy || signingIn);
        signOutButton.setEnabled(!busy && !running && launcherUser != null && !launcherToken.isEmpty());
        promptSignInButton.setEnabled(!busy || signingIn);
        promptLaterButton.setEnabled(!busy);
        adButton.setEnabled(!busy && !running && (launcherUser == null || launcherToken.isEmpty() || launcherAds == null || launcherAds.canWatch));
        profileBox.setEnabled(!busy && !running);
        buildBox.setEnabled(!busy && !running && gambleProfile);
        settingsButton.setEnabled(!busy);
        settingsBackButton.setEnabled(!busy);
        settingsGameFolderButton.setEnabled(!busy && !running);
        settingsModsButton.setEnabled(!busy && !running);
        settingsSiteButton.setEnabled(!busy);
        autoCheckUpdates.setEnabled(!busy);
        microsoftSignInButton.setEnabled((!busy && !running) || isMicrosoftSignInActive());
        microsoftSignOutButton.setEnabled(!busy && !running && microsoftAccount != null && !microsoftAccount.refreshToken.isEmpty());
        username.setEnabled(!busy && !running);
        memoryGb.setEnabled(!busy && !running);
        javaArgs.setEnabled(!busy && !running);
    }

    private void log(final String message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    log(message);
                }
            });
            return;
        }

        String stamped = "[" + new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date()) + "] " + message;
        appendColoredLog(stamped);
        log.setCaretPosition(log.getDocument().getLength());
        recordLogLine(stamped);
    }

    private void appendColoredLog(String line) {
        StyledDocument document = log.getStyledDocument();
        String severity = logSeverity(line);
        Style style = log.getStyle(severity);
        if (style == null) {
            style = log.addStyle(severity, null);
            StyleConstants.setForeground(style, switch (severity) {
                case "error" -> new Color(255, 81, 72);
                case "warning" -> new Color(255, 159, 28);
                default -> new Color(214, 219, 229);
            });
            StyleConstants.setBold(style, "error".equals(severity));
        }

        try {
            document.insertString(document.getLength(), line + System.lineSeparator(), style);
        } catch (BadLocationException exception) {
            System.err.println(line);
        }
    }

    private String logSeverity(String line) {
        String value = line == null ? "" : line.toLowerCase(Locale.ROOT);
        if (value.matches(".*\\b(error|failed|failure|fatal|exception|crash|broken|denied|invalid)\\b.*")) return "error";
        if (value.matches(".*\\b(warn|warning|retry|stale|missing|unavailable|offline)\\b.*")) return "warning";
        return "normal";
    }

    private void recordLogLine(String line) {
        appendLine(getLauncherLogFile(), line);
        if (!captureLaunchLog) return;
        synchronized (launchLogLock) {
            recentLaunchLines.addLast(line);
            while (recentLaunchLines.size() > 300) recentLaunchLines.removeFirst();
        }
        appendLine(getLatestLaunchLogFile(), line);
    }

    private void appendLine(File file, String line) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Files.writeString(file.toPath(), line + System.lineSeparator(), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            hardenPrivateFile(file);
        } catch (IOException e) {
            System.err.println("Launcher log write failed: " + e.getMessage());
        }
    }

    private void startLatestLaunchLog() {
        synchronized (launchLogLock) {
            recentLaunchLines.clear();
        }
        try {
            File file = getLatestLaunchLogFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Files.writeString(file.toPath(), "", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            hardenPrivateFile(file);
        } catch (IOException e) {
            log("Could not reset latest-launch.log: " + e.getMessage());
        }
        captureLaunchLog = true;
    }

    private void copyLauncherLog() {
        String text = log.getText();
        if (text.length() > 60000) {
            text = "[trimmed to last 60000 characters]" + System.lineSeparator()
                + text.substring(text.length() - 60000);
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        log("Copied launcher log to clipboard.");
    }

    private void clearLog() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    clearLog();
                }
            });
            return;
        }

        log.setText("");
    }

    private void setProgress(final int value, final String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    setProgress(value, text);
                }
            });
            return;
        }

        progress.setValue(value);
        progress.setString(text);
    }

    private void setProgressStatus(final String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    setProgressStatus(text);
                }
            });
            return;
        }

        progress.setString(text);
    }

    private void setUpdateStatus(final String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    setUpdateStatus(text);
                }
            });
            return;
        }

        updateStatus.setText(text);
    }

    private File getModsFolder() {
        return new File(getMinecraftFolder(), "mods");
    }

    private File getResourcePacksFolder(LaunchProfile profile) {
        return new File(getMinecraftFolder(profile), "resourcepacks");
    }

    private boolean isResourcePackLikeFile(File file) {
        if (file == null) return false;
        String lower = file.getName().toLowerCase(Locale.ROOT);
        return (file.isFile() && (lower.endsWith(".zip") || lower.endsWith(".zip.disabled")))
            || (file.isDirectory() && !lower.equals("server-resource-packs"));
    }

    private boolean isEnabledResourcePack(File file) {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        return file.isDirectory() ? !lower.endsWith(".disabled") : lower.endsWith(".zip");
    }

    private void setResourcePackEnabled(LaunchProfile profile, File file, boolean enabled) throws IOException {
        File options = new File(getMinecraftFolder(profile), "options.txt");
        List<String> lines = options.isFile()
            ? Files.readAllLines(options.toPath(), StandardCharsets.UTF_8)
            : new ArrayList<>();
        String packName = enabledResourcePackName(file);
        String entry = "file/" + packName;
        boolean found = false;

        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).startsWith("resourcePacks:")) continue;
            List<String> packs = parseResourcePackList(lines.get(i).substring("resourcePacks:".length()));
            packs.removeIf(value -> value.equals(entry) || value.equals("file/" + disabledResourcePackName(file)));
            if (enabled) packs.add(entry);
            lines.set(i, "resourcePacks:" + encodeResourcePackList(packs));
            found = true;
            break;
        }

        if (!found && enabled) lines.add("resourcePacks:" + encodeResourcePackList(Collections.singletonList(entry)));
        if (!lines.stream().anyMatch(line -> line.startsWith("incompatibleResourcePacks:"))) {
            lines.add("incompatibleResourcePacks:[]");
        }
        Files.write(options.toPath(), lines, StandardCharsets.UTF_8);
    }

    private String enabledResourcePackName(File file) {
        String name = file.getName();
        return name.endsWith(".disabled") ? name.substring(0, name.length() - ".disabled".length()) : name;
    }

    private String disabledResourcePackName(File file) {
        String name = file.getName();
        return name.endsWith(".disabled") ? name : name + ".disabled";
    }

    private List<String> parseResourcePackList(String raw) {
        List<String> packs = new ArrayList<>();
        java.util.regex.Matcher matcher = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(raw);
        while (matcher.find()) packs.add(matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        return packs;
    }

    private String encodeResourcePackList(List<String> packs) {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (String pack : new LinkedHashSet<>(packs)) {
            if (!first) builder.append(',');
            builder.append('"').append(pack.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            first = false;
        }
        return builder.append(']').toString();
    }

    private File getLauncherDataFolder() {
        return new File(getManagedMinecraftRoot(), "cg-mod");
    }

    private void hardenPrivateFile(File file) throws IOException {
        if (file == null || !file.exists()) return;
        try {
            Files.setPosixFilePermissions(file.toPath(), Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            ));
            return;
        } catch (UnsupportedOperationException ignored) {
            // Windows and non-POSIX filesystems use the owner-only File API fallback.
        }
        boolean hardened = file.setReadable(false, false);
        hardened &= file.setWritable(false, false);
        hardened &= file.setExecutable(false, false);
        hardened &= file.setReadable(true, true);
        hardened &= file.setWritable(true, true);
        if (!hardened) {
            throw new IOException("Could not restrict private file permissions: " + file);
        }
    }

    private File getLauncherLogFile() {
        return new File(getLauncherDataFolder(), "launcher.log");
    }

    private File getLatestLaunchLogFile() {
        return new File(getLauncherDataFolder(), "latest-launch.log");
    }

    private File getProfileDataFolder() {
        return new File(getMinecraftFolder(), "cg-mod");
    }

    private File getPayloadsFolder() {
        return new File(getProfileDataFolder(), "payloads");
    }

    private File payloadJarFile(LauncherManifest manifest) {
        return new File(getPayloadsFolder(), manifest.fileName);
    }

    private File prepareLaunchPayload(File source) throws IOException {
        if (source == null || !source.isFile()) {
            throw new IOException("Managed client payload is missing.");
        }
        File folder = new File(getProfileDataFolder(), "launch");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Failed to create launch payload folder: " + folder);
        }
        cleanupStaleLaunchPayloads(folder);
        File target = new File(folder, "payload-" + randomBase64Url(24) + ".jar");
        try {
            Files.copy(source.toPath(), target.toPath());
            hardenPrivateFile(target);
            return target;
        } catch (IOException error) {
            Files.deleteIfExists(target.toPath());
            throw error;
        }
    }

    private void cleanupStaleLaunchPayloads(File folder) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (file.isFile() && name.matches("^payload-[a-z0-9_-]{32}\\.jar$")) {
                Files.deleteIfExists(file.toPath());
            }
        }
    }

    private boolean isSafeFileName(String value) {
        if (value == null || value.isBlank() || value.contains("/") || value.contains("\\") || value.equals(".") || value.equals("..")) return false;
        return new File(value).getName().equals(value) && !new File(value).isAbsolute();
    }

    private File writeLaunchTicketFile(LaunchTicket launchTicket) throws IOException {
        File folder = new File(getProfileDataFolder(), "launch");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Failed to create launch ticket folder: " + folder);
        }

        File file = new File(folder, "ticket-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".txt");
        String payload = "ticket=" + launchTicket.ticket + "\n"
            + "build=" + launchTicket.build + "\n"
            + "expiresAt=" + launchTicket.expiresAt + "\n";
        Files.writeString(file.toPath(), payload, StandardCharsets.UTF_8);
        hardenPrivateFile(file);
        file.deleteOnExit();
        log("Launch ticket issued for " + launchTicket.build + "; expires at " + launchTicket.expiresAt + ".");
        return file;
    }

    private void scheduleLaunchArtifactCleanup(Process process, File launchTicketFile, File livePayload) {
        if (process == null || (launchTicketFile == null && livePayload == null)) return;

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    process.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    deleteQuietly(launchTicketFile);
                    deleteQuietly(livePayload);
                }
            }
        }, "gamble-launch-artifact-cleanup");
        thread.setDaemon(true);
        thread.start();
    }

    private void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) file.deleteOnExit();
    }

    private List<String> redactLaunchSecrets(List<String> values) {
        List<String> redacted = new ArrayList<>();
        boolean redactNext = false;
        for (String value : values) {
            if (redactNext) {
                redacted.add("<redacted>");
                redactNext = false;
            } else if (value != null && (value.equals("--accessToken") || value.equals("--clientId") || value.equals("--xuid") || value.equals("--uuid"))) {
                redacted.add(value);
                redactNext = true;
            } else if (value != null && value.startsWith("-Dgamble.launchTicket")) {
                redacted.add("-Dgamble.launchTicket=<redacted>");
            } else {
                redacted.add(value);
            }
        }
        return redacted;
    }

    private File getLicenseFile() {
        return new File(getLauncherDataFolder(), "license.txt");
    }

    private File getLauncherSessionFile() {
        return new File(getLauncherDataFolder(), "launcher-session.txt");
    }

    private File getSelectedProfileFile() {
        return new File(getLauncherDataFolder(), "launcher-profile.txt");
    }

    private File getLauncherSettingsFile() {
        return new File(getLauncherDataFolder(), "launcher-settings.json");
    }

    private File getMicrosoftAccountFile() {
        return new File(getLauncherDataFolder(), "microsoft-account.json");
    }

    private File getMinecraftFolder() {
        return getMinecraftFolder(selectedProfile());
    }

    private File getMinecraftFolder(LaunchProfile profile) {
        return new File(new File(getManagedMinecraftRoot(), "profiles"), profile.id);
    }

    private File getManagedMinecraftRoot() {
        String configured = System.getProperty("gamble.gameDir", "").trim();
        if (configured.isEmpty()) configured = System.getenv("GAMBLE_CLIENT_GAME_DIR");
        if (configured != null && !configured.trim().isEmpty()) {
            return new File(configured.trim());
        }

        return new File(getAppDataFolder(), "minecraft");
    }

    private File getAppDataFolder() {
        String userHome = System.getProperty("user.home");
        switch (getOS()) {
            case WINDOWS:
                String appData = System.getenv("APPDATA");
                return new File(appData != null && !appData.isEmpty() ? appData : userHome, "Gamble Client");
            case OSX:
                return new File(userHome, "Library/Application Support/Gamble Client");
            default:
                String xdgDataHome = System.getenv("XDG_DATA_HOME");
                File dataHome = xdgDataHome != null && !xdgDataHome.trim().isEmpty()
                    ? new File(xdgDataHome.trim())
                    : new File(userHome, ".local/share");
                return new File(dataHome, "gamble-client");
        }
    }

    private File getLegacyMinecraftFolder() {
        String userHome = System.getProperty("user.home");
        switch (getOS()) {
            case WINDOWS:
                String appData = System.getenv("APPDATA");
                return new File(appData != null && !appData.isEmpty() ? appData : userHome, ".minecraft");
            case OSX:
                return new File(userHome, "Library/Application Support/minecraft");
            default:
                return new File(userHome, ".minecraft");
        }
    }

    private String readLegacyLicenseKey() {
        String managedKey = readLicenseKey(new File(getLauncherDataFolder(), "license.txt"));
        if (!managedKey.isEmpty()) return managedKey;

        String profileKey = readLicenseKey(new File(getProfileDataFolder(), "license.txt"));
        if (!profileKey.isEmpty()) return profileKey;

        File legacy = new File(new File(getLegacyMinecraftFolder(), "cg-mod"), "license.txt");
        return readLicenseKey(legacy);
    }

    private String readLicenseKey(File file) {
        if (!file.isFile()) return "";
        try {
            String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return raw.lines()
                .filter(line -> !line.trim().startsWith("#"))
                .map(line -> line.startsWith("license=") ? line.substring("license=".length()) : line)
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !LICENSE_PLACEHOLDER.equals(line))
                .findFirst()
                .orElse("");
        } catch (IOException e) {
            return "";
        }
    }

    private OperatingSystem getOS() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (os.contains("linux") || os.contains("unix")) return OperatingSystem.LINUX;
        if (os.contains("mac")) return OperatingSystem.OSX;
        if (os.contains("win")) return OperatingSystem.WINDOWS;

        return OperatingSystem.UNKNOWN;
    }

    private String osName() {
        switch (getOS()) {
            case WINDOWS:
                return "windows";
            case OSX:
                return "osx";
            default:
                return "linux";
        }
    }

    private boolean open(String url) {
        try {
            if (!allowedBrowserUrl(url)) {
                log("Blocked an untrusted browser URL.");
                return false;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(URI.create(url));
                    return true;
                }
            }
            getOS().open(url);
            return true;
        } catch (Exception e) {
            log("Open failed: " + e.getMessage());
            return false;
        }
    }

    private boolean allowedBrowserUrl(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (uri.getUserInfo() != null || host.isEmpty()) return false;
            if ("http".equals(scheme) && ("localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host))) return true;
            if (!"https".equals(scheme)) return false;
            return Set.of(
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

    private boolean open(File file) {
        try {
            if (!file.exists()) file.mkdirs();
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(file);
                    return true;
                }
            }
            getOS().open(file.toURI().toString());
            return true;
        } catch (Exception e) {
            log("Open failed: " + e.getMessage());
            return false;
        }
    }

    private String resolveSiteUrl(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("http://") || text.startsWith("https://")) return text;
        String base = siteUrl();
        if (!base.endsWith("/") && !text.startsWith("/")) return base + "/" + text;
        if (base.endsWith("/") && text.startsWith("/")) return base.substring(0, base.length() - 1) + text;
        return base + text;
    }

    private String readUrl(String urlText) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(urlText).toURL().openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "GambleClientLauncher/0.1");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " reading " + urlText);
        }
        return readAll(connection.getInputStream());
    }

    private String readFile(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private String readSmall(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int remaining = 4096;
        int read;
        while (remaining > 0 && (read = in.read(buffer, 0, Math.min(buffer.length, remaining))) != -1) {
            out.write(buffer, 0, read);
            remaining -= read;
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
    }

    private String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.toString() : current.getMessage();
    }

    private String microsoftSignInMessage(Throwable throwable) {
        String message = rootMessage(throwable);
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("xbox profile is not ready") || lower.contains("xbox services rejected")) {
            return message + " Open xbox.com or the official launcher once to finish Xbox profile setup, then try Microsoft Sign In again.";
        }
        if (lower.contains("minecraft services rejected")) {
            return message;
        }
        if (lower.contains("invalid app registration") || lower.contains("appreginfo")) {
            return "Invalid Microsoft app registration. If this appeared before the browser login finished, check Azure: set Supported account types to include personal Microsoft accounts, add http://localhost:"
                + MICROSOFT_REDIRECT_PORT
                + "/ under Mobile and desktop applications, and enable public client flows. If it appeared after login, Minecraft Services may still need to approve this client ID at https://aka.ms/AppRegInfo.";
        }
        if (lower.contains("client_secret")) {
            return "Microsoft is treating this as a Web/confidential app. Move http://localhost:"
                + MICROSOFT_REDIRECT_PORT
                + "/ to Mobile and desktop applications and enable public client flows. Do not add a client secret to the launcher.";
        }
        return message;
    }

    private String xboxXstsMessage(String xerr) {
        if (xerr == null || xerr.isBlank()) return "";
        return switch (xerr.trim()) {
            case "2148916233" -> "Xbox services rejected the account because the Xbox profile is not ready. Sign in at xbox.com once and finish gamertag/profile setup.";
            case "2148916235" -> "Xbox services rejected the account because Xbox Live is not available for this account region.";
            case "2148916236", "2148916238" -> "Xbox services rejected the account because child/family settings block third-party Minecraft login. Check account age and Xbox privacy settings.";
            case "2148916237" -> "Xbox services rejected the account because the Xbox account appears restricted.";
            default -> "Xbox services rejected the account with XErr " + xerr + ".";
        };
    }

    private String randomBase64Url(int bytes) {
        byte[] data = new byte[bytes];
        new SecureRandom().nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private String sha256Base64Url(String value) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IOException("Could not create Microsoft sign-in challenge.", e);
        }
    }

    private String htmlEscape(String value) {
        return String.valueOf(value == null ? "" : value)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private static String siteUrl() {
        String configured = System.getProperty("gamble.siteUrl", "").trim();
        if (configured.isEmpty()) configured = System.getenv("GAMBLE_CLIENT_SITE_URL");
        if (configured == null || configured.trim().isEmpty()) configured = DEFAULT_SITE_URL;
        configured = configured.trim();
        while (configured.endsWith("/")) configured = configured.substring(0, configured.length() - 1);
        return configured;
    }

    private static String launcherLogoutUrl() {
        return siteUrl() + "/api/auth/logout?next=" + URLEncoder.encode("/login.html", StandardCharsets.UTF_8);
    }

    private static String microsoftClientId() {
        String configured = System.getProperty("gamble.microsoftClientId", "").trim();
        if (configured.isEmpty()) configured = System.getenv("GAMBLE_MICROSOFT_CLIENT_ID");
        if (configured == null || configured.trim().isEmpty()) configured = DEFAULT_MICROSOFT_CLIENT_ID;
        return configured == null ? "" : configured.trim();
    }

    private String cleanUsername(String value) {
        return value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9_]", "");
    }

    private static String defaultUsername() {
        return "BaseToucher";
    }

    private void addDefaultCapeProviderProperties(List<String> command) {
        if (hasJavaProperty(command, "gamble.capes.ownersUrl") || hasJavaProperty(command, "gamble.capes.capesUrl")) return;

        String base = siteUrl();
        command.add("-Dgamble.capes.ownersUrl=" + base + CAPE_OWNERS_PATH);
        command.add("-Dgamble.capes.capesUrl=" + base + CAPES_PATH);
    }

    private boolean hasJavaProperty(List<String> args, String key) {
        String prefix = "-D" + key + "=";
        for (String arg : args) {
            if (arg != null && arg.startsWith(prefix)) return true;
        }
        return false;
    }

    private String javaExecutable() {
        File bin = new File(new File(System.getProperty("java.home"), "bin"), getOS() == OperatingSystem.WINDOWS ? "javaw.exe" : "java");
        return bin.exists() ? bin.getAbsolutePath() : "java";
    }

    private String joinClasspath(List<File> files) {
        StringBuilder builder = new StringBuilder();
        for (File file : files) {
            if (builder.length() > 0) builder.append(File.pathSeparatorChar);
            builder.append(file.getAbsolutePath());
        }
        return builder.toString();
    }

    private String replacePlaceholders(String value, Map<String, String> replacements) {
        String out = value;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            out = out.replace(entry.getKey(), entry.getValue());
        }
        return out;
    }

    private String offlineUuid(String playerName) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
        return uuid.toString().replace("-", "");
    }

    private boolean is64Bit() {
        return System.getProperty("os.arch", "").contains("64");
    }

    private static final class Build {
        final String label;
        final String id;

        Build(String label, String id) {
            this.label = label;
            this.id = id;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class LaunchProfile {
        final String id;
        final String label;
        final String description;
        final boolean fabric;
        final boolean includesGambleClient;
        final boolean requiresFabricApi;

        LaunchProfile(String id, String label, String description, boolean fabric, boolean includesGambleClient, boolean requiresFabricApi) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.fabric = fabric;
            this.includesGambleClient = includesGambleClient;
            this.requiresFabricApi = requiresFabricApi;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class LauncherSession {
        final String token;
        final LauncherUser user;
        final LauncherAds ads;

        LauncherSession(String token, LauncherUser user, LauncherAds ads) {
            this.token = token;
            this.user = user;
            this.ads = ads;
        }
    }

    private static final class LauncherAccount {
        final LauncherUser user;
        final LauncherAds ads;

        LauncherAccount(LauncherUser user, LauncherAds ads) {
            this.user = user;
            this.ads = ads;
        }
    }

    private static final class LauncherUser {
        final String email;
        final String displayName;
        final String discordUsername;
        final String selectedPlan;
        final String accessStatus;
        final boolean ownerAccess;
        final boolean mediaAccess;
        final boolean testerAccess;
        final boolean betaAccess;
        final boolean adTierAccess;

        LauncherUser(String email, String displayName, String discordUsername, String selectedPlan, String accessStatus, boolean ownerAccess, boolean mediaAccess, boolean testerAccess, boolean betaAccess, boolean adTierAccess) {
            this.email = email;
            this.displayName = displayName;
            this.discordUsername = discordUsername;
            this.selectedPlan = selectedPlan;
            this.accessStatus = accessStatus;
            this.ownerAccess = ownerAccess;
            this.mediaAccess = mediaAccess;
            this.testerAccess = testerAccess;
            this.betaAccess = betaAccess;
            this.adTierAccess = adTierAccess;
        }
    }

    private static final class LauncherLicense {
        final String licenseKey;

        LauncherLicense(String licenseKey) {
            this.licenseKey = licenseKey;
        }
    }

    private static final class LauncherAds {
        final boolean required;
        final boolean paid;
        final boolean canWatch;
        final boolean active;
        final String tier;
        final String message;
        final int adSeconds;
        final long remainingSeconds;
        final String adUrl;

        LauncherAds(boolean required, boolean paid, boolean canWatch, boolean active, String tier, String message, int adSeconds, long remainingSeconds, String adUrl) {
            this.required = required;
            this.paid = paid;
            this.canWatch = canWatch;
            this.active = active;
            this.tier = tier;
            this.message = message;
            this.adSeconds = adSeconds;
            this.remainingSeconds = remainingSeconds;
            this.adUrl = adUrl == null ? "" : adUrl;
        }
    }

    private static final class LauncherManifest {
        final String build;
        final String fileName;
        final String downloadUrl;
        final String licenseKey;
        final String sha256;
        final long size;
        final String buildVersion;

        LauncherManifest(String build, String fileName, String downloadUrl, String licenseKey, String sha256, long size, String buildVersion) {
            this.build = build;
            this.fileName = fileName;
            this.downloadUrl = downloadUrl;
            this.licenseKey = licenseKey;
            this.sha256 = sha256;
            this.size = size;
            this.buildVersion = buildVersion;
        }
    }

    private static final class LaunchTicket {
        final String build;
        final String ticket;
        final long expiresAt;

        LaunchTicket(String build, String ticket, long expiresAt) {
            this.build = build;
            this.ticket = ticket;
            this.expiresAt = expiresAt;
        }
    }

    private static final class LauncherVersion {
        final String version;
        final String minVersion;
        final String fileName;
        final String downloadUrl;

        LauncherVersion(String version, String minVersion, String fileName, String downloadUrl) {
            this.version = version;
            this.minVersion = minVersion;
            this.fileName = fileName;
            this.downloadUrl = downloadUrl;
        }
    }

    private static final class VersionPanelState {
        String launcherReleased = "unknown";
        String clientReleased;
    }

    private static final class ModEntry {
        final File file;
        final boolean enabled;
        final boolean locked;

        ModEntry(File file, boolean enabled, boolean locked) {
            this.file = file;
            this.enabled = enabled;
            this.locked = locked;
        }

        @Override
        public String toString() {
            return (enabled ? "On  " : "Off ") + file.getName() + (locked ? "  (required)" : "");
        }
    }

    private static final class ManagedFabricMod {
        final String displayName;
        final String filePrefix;
        final String modrinthUrl;
        final String tempPrefix;
        final String directUrl;
        final String directFileName;
        final String[] failureMarkers;

        ManagedFabricMod(String displayName, String filePrefix, String modrinthUrl, String tempPrefix, String[] failureMarkers) {
            this(displayName, filePrefix, modrinthUrl, tempPrefix, "", "", failureMarkers);
        }

        ManagedFabricMod(String displayName, String filePrefix, String modrinthUrl, String tempPrefix, String directUrl, String directFileName, String[] failureMarkers) {
            this.displayName = displayName;
            this.filePrefix = filePrefix;
            this.modrinthUrl = modrinthUrl;
            this.tempPrefix = tempPrefix;
            this.directUrl = directUrl == null ? "" : directUrl;
            this.directFileName = directFileName == null ? "" : directFileName;
            this.failureMarkers = failureMarkers == null ? new String[0] : failureMarkers;
        }

        boolean matchesFailure(String text) {
            if (text == null || text.isEmpty()) return false;
            for (String marker : failureMarkers) {
                if (marker != null && !marker.isEmpty() && text.contains(marker.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class ModrinthRelease {
        final String fileName;
        final String url;

        ModrinthRelease(String fileName, String url) {
            this.fileName = fileName;
            this.url = url;
        }
    }

    private static final class UpdateResult {
        final File file;
        final boolean updated;
        final String message;

        UpdateResult(File file, boolean updated, String message) {
            this.file = file;
            this.updated = updated;
            this.message = message;
        }
    }

    private static final class LaunchValidation {
        final List<String> ok = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        void ok(String message) {
            ok.add(message);
        }

        void warn(String message) {
            warnings.add(message);
        }

        void error(String message) {
            errors.add(message);
        }
    }

    private static final class LaunchDiagnosis {
        final String summary;
        final String detected;
        final String probableCause;
        final String recommendedFix;

        LaunchDiagnosis(String summary, String detected, String probableCause, String recommendedFix) {
            this.summary = summary;
            this.detected = detected;
            this.probableCause = probableCause;
            this.recommendedFix = recommendedFix;
        }
    }

    private static final class ApiResponse {
        final int status;
        final Map<String, Object> body;

        ApiResponse(int status, Map<String, Object> body) {
            this.status = status;
            this.body = body;
        }
    }

    private static final class HttpStatusException extends IOException {
        private final int status;

        private HttpStatusException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static final class LauncherOutdatedException extends IOException {
        final String version;
        final String downloadUrl;

        LauncherOutdatedException(String message, String version, String downloadUrl) {
            super(message);
            this.version = version == null ? "" : version;
            this.downloadUrl = downloadUrl == null ? "" : downloadUrl;
        }
    }

    private static final class LaunchIdentity {
        final String playerName;
        final String uuid;
        final String accessToken;
        final String xuid;
        final String userType;

        private LaunchIdentity(String playerName, String uuid, String accessToken, String xuid, String userType) {
            this.playerName = playerName;
            this.uuid = uuid;
            this.accessToken = accessToken;
            this.xuid = xuid;
            this.userType = userType;
        }

        static LaunchIdentity offline(String playerName) {
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
            return new LaunchIdentity(playerName, uuid.toString().replace("-", ""), "0", "", "legacy");
        }

        static LaunchIdentity online(String playerName, String uuid, String accessToken, String xuid) {
            return new LaunchIdentity(playerName, uuid.replace("-", ""), accessToken, xuid, "msa");
        }
    }

    private static final class MicrosoftAccount {
        final String name;
        final String uuid;
        final String xuid;
        final String refreshToken;
        final long minecraftExpiresAt;

        MicrosoftAccount(String name, String uuid, String xuid, String refreshToken, long minecraftExpiresAt) {
            this.name = name;
            this.uuid = uuid;
            this.xuid = xuid;
            this.refreshToken = refreshToken;
            this.minecraftExpiresAt = minecraftExpiresAt;
        }
    }

    private static final class MicrosoftDeviceCode {
        final String deviceCode;
        final String userCode;
        final String verificationUri;
        final String verificationUriComplete;
        final String message;
        final int intervalSeconds;
        final long expiresInSeconds;

        MicrosoftDeviceCode(String deviceCode, String userCode, String verificationUri, String verificationUriComplete, String message, int intervalSeconds, long expiresInSeconds) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.verificationUriComplete = verificationUriComplete;
            this.message = message;
            this.intervalSeconds = intervalSeconds <= 0 ? 5 : intervalSeconds;
            this.expiresInSeconds = expiresInSeconds <= 0 ? 900 : expiresInSeconds;
        }
    }

    private static final class MicrosoftToken {
        final String accessToken;
        final String refreshToken;
        final long expiresInSeconds;

        MicrosoftToken(String accessToken, String refreshToken, long expiresInSeconds) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    private static final class XboxToken {
        final String token;
        final String userHash;
        final String xuid;

        XboxToken(String token, String userHash, String xuid) {
            this.token = token;
            this.userHash = userHash;
            this.xuid = xuid;
        }
    }

    private static final class MinecraftToken {
        final String accessToken;
        final long expiresInSeconds;

        MinecraftToken(String accessToken, long expiresInSeconds) {
            this.accessToken = accessToken;
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    private static final class MinecraftProfile {
        final String uuid;
        final String name;

        MinecraftProfile(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    private static final class MinecraftAuth {
        final String name;
        final String uuid;
        final String accessToken;
        final String xuid;
        final long expiresInSeconds;

        MinecraftAuth(String name, String uuid, String accessToken, String xuid, long expiresInSeconds) {
            this.name = name;
            this.uuid = uuid;
            this.accessToken = accessToken;
            this.xuid = xuid;
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    private static final class VersionProfile {
        String id = "";
        String mainClass = "";
        String assetIndexId = "";
        String assetIndexUrl = "";
        String clientVersionId = "";
        String clientJarUrl = "";
        final List<Library> libraries = new ArrayList<>();
        final List<String> jvmArguments = new ArrayList<>();
        final List<String> gameArguments = new ArrayList<>();
    }

    private static final class AssetDownload {
        final String hash;
        final File file;

        AssetDownload(String hash, File file) {
            this.hash = hash;
            this.file = file;
        }
    }

    private static final class AssetDownloadException extends RuntimeException {
        AssetDownloadException(String hash, IOException cause) {
            super("Asset " + hash + " failed: " + cause.getMessage(), cause);
        }
    }

    private static final class Library {
        String name = "";
        String artifactPath = "";
        String artifactUrl = "";
        List<Object> rules = new ArrayList<>();
        Map<String, String> natives = new LinkedHashMap<>();
        Map<String, String> classifierPaths = new LinkedHashMap<>();
        Map<String, String> classifierUrls = new LinkedHashMap<>();
    }

    private static final class NativeArtifact {
        final String path;
        final String url;

        NativeArtifact(String path, String url) {
            this.path = path;
            this.url = url;
        }
    }

    private enum OperatingSystem {
        LINUX,
        WINDOWS {
            @Override
            void open(String value) throws IOException {
                Runtime.getRuntime().exec(new String[] {"rundll32", "url.dll,FileProtocolHandler", value});
            }
        },
        OSX {
            @Override
            void open(String value) throws IOException {
                Runtime.getRuntime().exec(new String[] {"open", value});
            }
        },
        UNKNOWN;

        void open(String value) throws IOException {
            Runtime.getRuntime().exec(new String[] {"xdg-open", value});
        }
    }

    private static final class Json {
        private final String text;
        private int index;

        private Json(String text) {
            this.text = text;
        }

        static Object parse(String text) {
            Json parser = new Json(text);
            Object value = parser.parseValue();
            parser.skipWhitespace();
            if (parser.index != parser.text.length()) {
                throw new IllegalArgumentException("Invalid JSON trailing content near character " + parser.index + ".");
            }
            return value;
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> asObject(Object value) {
            if (value instanceof Map) return (Map<String, Object>) value;
            return Collections.emptyMap();
        }

        @SuppressWarnings("unchecked")
        static List<Object> asArray(Object value) {
            if (value instanceof List) return (List<Object>) value;
            return Collections.emptyList();
        }

        static String string(Object value) {
            return value == null ? "" : String.valueOf(value);
        }

        static Map<String, String> asStringMap(Map<String, Object> object) {
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                out.put(entry.getKey(), string(entry.getValue()));
            }
            return out;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= text.length()) return null;
            char c = text.charAt(index);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' && text.startsWith("true", index)) {
                index += 4;
                return Boolean.TRUE;
            }
            if (c == 'f' && text.startsWith("false", index)) {
                index += 5;
                return Boolean.FALSE;
            }
            if (c == 'n' && text.startsWith("null", index)) {
                index += 4;
                return null;
            }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> object = new LinkedHashMap<>();
            index++;
            skipWhitespace();
            if (peek('}')) {
                index++;
                return object;
            }

            while (index < text.length()) {
                skipWhitespace();
                String key = parseString();
                if (object.containsKey(key)) {
                    throw new IllegalArgumentException("Invalid JSON duplicate key: " + key + ".");
                }
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                object.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return object;
                }
                expect(',');
            }
            throw new IllegalArgumentException("Invalid JSON unterminated object.");
        }

        private List<Object> parseArray() {
            List<Object> array = new ArrayList<>();
            index++;
            skipWhitespace();
            if (peek(']')) {
                index++;
                return array;
            }

            while (index < text.length()) {
                array.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return array;
                }
                expect(',');
            }
            throw new IllegalArgumentException("Invalid JSON unterminated array.");
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            boolean closed = false;
            while (index < text.length()) {
                char c = text.charAt(index++);
                if (c == '"') {
                    closed = true;
                    break;
                }
                if (c == '\\' && index < text.length()) {
                    char escaped = text.charAt(index++);
                    switch (escaped) {
                        case '"':
                        case '\\':
                        case '/':
                            builder.append(escaped);
                            break;
                        case 'b':
                            builder.append('\b');
                            break;
                        case 'f':
                            builder.append('\f');
                            break;
                        case 'n':
                            builder.append('\n');
                            break;
                        case 'r':
                            builder.append('\r');
                            break;
                        case 't':
                            builder.append('\t');
                            break;
                        case 'u':
                            if (index + 4 > text.length()) {
                                throw new IllegalArgumentException("Invalid JSON unicode escape near character " + index + ".");
                            }
                            String hex = text.substring(index, index + 4);
                            builder.append((char) Integer.parseInt(hex, 16));
                            index += 4;
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid JSON escape near character " + (index - 1) + ".");
                    }
                } else {
                    if (c < 0x20) {
                        throw new IllegalArgumentException("Invalid JSON control character near character " + (index - 1) + ".");
                    }
                    builder.append(c);
                }
            }
            if (!closed) {
                throw new IllegalArgumentException("Invalid JSON unterminated string.");
            }
            return builder.toString();
        }

        private Number parseNumber() {
            int start = index;
            if (peek('-')) index++;
            if (index >= text.length()) {
                throw new IllegalArgumentException("Invalid JSON number near character " + start + ".");
            }
            if (peek('0')) {
                index++;
                if (index < text.length() && Character.isDigit(text.charAt(index))) {
                    throw new IllegalArgumentException("Invalid JSON leading zero near character " + start + ".");
                }
            } else {
                int integerStart = index;
                while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
                if (integerStart == index) {
                    throw new IllegalArgumentException("Invalid JSON number near character " + start + ".");
                }
            }
            if (peek('.')) {
                index++;
                int fractionStart = index;
                while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
                if (fractionStart == index) {
                    throw new IllegalArgumentException("Invalid JSON fraction near character " + start + ".");
                }
            }
            if (peek('e') || peek('E')) {
                index++;
                if (peek('+') || peek('-')) index++;
                int exponentStart = index;
                while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
                if (exponentStart == index) {
                    throw new IllegalArgumentException("Invalid JSON exponent near character " + start + ".");
                }
            }

            String number = text.substring(start, index);
            if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0) {
                return Double.valueOf(number);
            }
            return Long.valueOf(number);
        }

        private void skipWhitespace() {
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') index++;
                else break;
            }
        }

        private boolean peek(char expected) {
            return index < text.length() && text.charAt(index) == expected;
        }

        private void expect(char expected) {
            if (index >= text.length() || text.charAt(index) != expected) {
                throw new IllegalArgumentException("Invalid JSON near character " + index + ", expected '" + expected + "'.");
            }
            index++;
        }
    }
}
