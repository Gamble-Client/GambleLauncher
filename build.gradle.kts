plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.gambleclient"
version = "0.1.51"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "com.gambleclient.launcher.FxLauncher"
}

javafx {
    version = "22.0.2"
    modules = listOf("javafx.controls", "javafx.graphics", "javafx.media", "javafx.web")
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
