buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.5.0")
    }
}

plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.gambleclient"
version = "0.1.116"

val javafxVersion = "22.0.2"
val javafxModuleNames = listOf("base", "graphics", "controls", "media", "web")
val javafxPlatforms = listOf("linux", "win", "mac", "mac-aarch64")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "com.gambleclient.launcher.LauncherBootstrap"
}

javafx {
    version = javafxVersion
    modules = listOf("javafx.controls", "javafx.graphics", "javafx.media", "javafx.web")
}

dependencies {
    javafxModuleNames.forEach { moduleName ->
        javafxPlatforms.forEach { platform ->
            runtimeOnly("org.openjfx:javafx-$moduleName:$javafxVersion:$platform")
        }
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val launcherClassesJar = tasks.register<Jar>("launcherClassesJar") {
    archiveBaseName = "gamble-client-launcher-classes"
    archiveClassifier = "raw"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
}

val obfuscatedLauncherClasses = layout.buildDirectory.file("obfuscation/launcher-classes.jar")
val obfuscateLauncherClasses = tasks.register("obfuscateLauncherClasses", proguard.gradle.ProGuardTask::class) {
    dependsOn(launcherClassesJar)
    injars(launcherClassesJar.flatMap { it.archiveFile })
    outjars(obfuscatedLauncherClasses)
    libraryjars(
        mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
        "${System.getProperty("java.home")}/jmods/"
    )
    configurations.compileClasspath.get().filter { it.exists() }.forEach {
        libraryjars(mapOf("filter" to "!module-info.class"), it)
    }
    configuration(file("proguard-launcher.pro"))
    printmapping(layout.buildDirectory.file("obfuscation/launcher-mapping.txt").get().asFile)
}

tasks.jar {
    enabled = false
}

val hardenedLauncherJar = tasks.register<Jar>("hardenedLauncherJar") {
    dependsOn(obfuscateLauncherClasses)
    archiveBaseName = "gamble-client-launcher"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    from({ zipTree(obfuscatedLauncherClasses.get().asFile) })
    from({
        configurations.runtimeClasspath.get().map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}

tasks.assemble {
    dependsOn(hardenedLauncherJar)
}

val verifyHardenedLauncherJar = tasks.register<Exec>("verifyHardenedLauncherJar") {
    group = "verification"
    description = "Checks that the release launcher hides implementation class names and debug tables."
    dependsOn(hardenedLauncherJar)
    inputs.file(hardenedLauncherJar.flatMap { it.archiveFile })
    commandLine(
        "python3",
        layout.projectDirectory.file("scripts/verify-hardened-jar.py").asFile.absolutePath,
        layout.buildDirectory.file("libs/gamble-client-launcher-${project.version}.jar").get().asFile.absolutePath
    )
}

tasks.check {
    dependsOn(verifyHardenedLauncherJar)
}

fun jpackageExecutable(): String {
    val javaHome = System.getProperty("java.home")
    val executable = if (System.getProperty("os.name").lowercase().contains("win")) "jpackage.exe" else "jpackage"
    return file("$javaHome/bin/$executable").absolutePath
}

val launcherJar = hardenedLauncherJar.flatMap { it.archiveFile }
val nativeOutputDir = layout.buildDirectory.dir("native")

tasks.register<Exec>("packageNativeImage") {
    group = "distribution"
    description = "Builds a portable native launcher image for the current OS using jpackage."
    dependsOn(hardenedLauncherJar)

    doFirst {
        val output = nativeOutputDir.get().asFile
        delete(output)
        output.mkdirs()
        commandLine(
            jpackageExecutable(),
            "--type", "app-image",
            "--name", "GambleClientLauncher",
            "--app-version", project.version.toString(),
            "--input", launcherJar.get().asFile.parentFile.absolutePath,
            "--main-jar", launcherJar.get().asFile.name,
            "--main-class", application.mainClass.get(),
            "--dest", output.absolutePath
        )
    }
}

tasks.register<Exec>("packageWindowsExe") {
    group = "distribution"
    description = "Builds a Windows .exe installer. Run this on Windows with jpackage and WiX available."
    dependsOn(hardenedLauncherJar)
    onlyIf {
        System.getProperty("os.name").lowercase().contains("win")
    }

    doFirst {
        val output = nativeOutputDir.get().asFile
        output.mkdirs()
        commandLine(
            jpackageExecutable(),
            "--type", "exe",
            "--name", "GambleClientLauncher",
            "--app-version", project.version.toString(),
            "--input", launcherJar.get().asFile.parentFile.absolutePath,
            "--main-jar", launcherJar.get().asFile.name,
            "--main-class", application.mainClass.get(),
            "--dest", output.absolutePath,
            "--win-dir-chooser",
            "--win-menu",
            "--win-shortcut"
        )
    }
}

tasks.register<Exec>("packageLinuxRpm") {
    group = "distribution"
    description = "Builds a Linux .rpm package for the current OS using jpackage."
    dependsOn(hardenedLauncherJar)
    onlyIf {
        System.getProperty("os.name").lowercase().contains("linux") && file("/usr/bin/rpmbuild").exists()
    }

    doFirst {
        val output = nativeOutputDir.get().asFile
        output.mkdirs()
        commandLine(
            jpackageExecutable(),
            "--type", "rpm",
            "--name", "GambleClientLauncher",
            "--app-version", project.version.toString(),
            "--input", launcherJar.get().asFile.parentFile.absolutePath,
            "--main-jar", launcherJar.get().asFile.name,
            "--main-class", application.mainClass.get(),
            "--dest", output.absolutePath,
            "--linux-app-category", "Game",
            "--linux-shortcut"
        )
    }
}

tasks.register<Exec>("packageLinuxDeb") {
    group = "distribution"
    description = "Builds a Linux .deb package for the current OS using jpackage."
    dependsOn(hardenedLauncherJar)
    onlyIf {
        System.getProperty("os.name").lowercase().contains("linux") && file("/usr/bin/dpkg-deb").exists()
    }

    doFirst {
        val output = nativeOutputDir.get().asFile
        output.mkdirs()
        commandLine(
            jpackageExecutable(),
            "--type", "deb",
            "--name", "GambleClientLauncher",
            "--app-version", project.version.toString(),
            "--input", launcherJar.get().asFile.parentFile.absolutePath,
            "--main-jar", launcherJar.get().asFile.name,
            "--main-class", application.mainClass.get(),
            "--dest", output.absolutePath,
            "--linux-app-category", "Game",
            "--linux-shortcut"
        )
    }
}

tasks.register("packageNativeInstallers") {
    group = "distribution"
    description = "Builds native launcher packages supported by the current OS."
    dependsOn("packageNativeImage", "packageLinuxRpm", "packageLinuxDeb", "packageWindowsExe")
}
