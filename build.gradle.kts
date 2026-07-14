plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.gambleclient"
version = "0.1.90"

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
}

tasks.jar {
    archiveBaseName = "gamble-client-launcher"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}

fun jpackageExecutable(): String {
    val javaHome = System.getProperty("java.home")
    val executable = if (System.getProperty("os.name").lowercase().contains("win")) "jpackage.exe" else "jpackage"
    return file("$javaHome/bin/$executable").absolutePath
}

val launcherJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
val nativeOutputDir = layout.buildDirectory.dir("native")

tasks.register<Exec>("packageNativeImage") {
    group = "distribution"
    description = "Builds a portable native launcher image for the current OS using jpackage."
    dependsOn(tasks.jar)

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
    dependsOn(tasks.jar)
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
    dependsOn(tasks.jar)
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
    dependsOn(tasks.jar)
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
