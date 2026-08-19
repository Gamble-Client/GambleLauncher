# Keep only the manifest entry point and the exact bridge members used by the
# JavaFX compatibility frontend. All unrelated implementation names remain free
# to obfuscate, while source/debug tables are removed.
-dontoptimize
-dontnote
-dontwarn
-ignorewarnings

-overloadaggressively
-useuniqueclassmembernames
-adaptclassstrings
-adaptresourcefilecontents **.css,**.properties

-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature

-keep public class com.gambleclient.launcher.LauncherBootstrap {
    public static void main(java.lang.String[]);
}

-keep,allowobfuscation public class com.gambleclient.launcher.FxLauncher
-keepclassmembers public class com.gambleclient.launcher.FxLauncher {
    public static void main(java.lang.String[]);
}

-keepclassmembers class com.gambleclient.launcher.Main {
    *** autoCheckUpdates;
    *** launcherUser;
    *** microsoftAccount;
    *** crackedMode;
    *** profileBox;
    *** buildBox;
    *** memoryGb;
    *** username;
    *** javaArgs;
    *** log;
    *** launchButton;
    *** accountName;
    *** signInButton;
    *** progress;
    *** launcherInstalledVersion;
    *** launcherReleasedVersion;
    *** clientInstalledVersion;
    *** clientReleasedVersion;
    *** launcherAds;
    *** accountStatus;

    private javax.swing.JPanel createRoot();
    private boolean readSlotSoundsEnabled();
    private boolean readSlotWinSoundsEnabled();
    private void installSelectedBuild(boolean);
    private void launch();
    private void copyLauncherLog();
    private void saveAutoCheckUpdates(boolean);
    private void saveSlotSoundsEnabled(boolean);
    private void saveSlotWinSoundsEnabled(boolean);
    private java.lang.String checkForUpdatesNow();
    private static java.lang.String siteUrl();
    private static java.lang.String launcherVersion();
    private boolean antiScreenshareEnabled();
    private java.lang.String antiScreenshareStatus();
    private java.lang.String antiScreenshareConfigPath();
    private java.lang.String enableAntiScreenshare();
    private java.lang.String toggleAntiScreenshareModule(java.lang.String,boolean);
    private java.lang.String saveAntiScreenshareConfig();
    private java.lang.String setAntiScreenshareModuleSetting(java.lang.String,java.lang.String,java.lang.String);
    private boolean antiScreenshareHudEnabled();
    private java.util.Map antiScreenshareHudInfo();
    private java.util.List antiScreenshareModules();
    private java.util.List communityConfigs();
    private void selectCrackedAccount();
    private void selectMicrosoftAccount();
    private void signOutMicrosoft();
    private void startMicrosoftSignIn(boolean);
    private void ensureProfileFolders(com.gambleclient.launcher.Main$LaunchProfile);
    private boolean isGameRunning();
    private boolean isLauncherSignInActive();
    private void cancelLauncherSignIn();
    private void switchLauncherAccount();
    private void startSignIn();
    private java.util.Map beginSponsorBreakForOverlay();
    private java.lang.String completeSponsorBreakForOverlay();
    private java.io.File getModsFolder();
    private java.io.File getResourcePacksFolder(com.gambleclient.launcher.Main$LaunchProfile);
    private java.io.File getMinecraftFolder();
}

-keepclassmembers class com.gambleclient.launcher.Main$MicrosoftAccount {
    *** refreshToken;
    *** name;
}

-keepclassmembers class com.gambleclient.launcher.Main$LauncherAds {
    *** required;
    *** canWatch;
    *** active;
    *** remainingSeconds;
    *** message;
}

-keepclassmembers class com.gambleclient.launcher.Main$LauncherUser {
    *** accessStatus;
    *** selectedPlan;
    *** ownerAccess;
    *** mediaAccess;
    *** testerAccess;
    *** betaAccess;
    *** email;
}
