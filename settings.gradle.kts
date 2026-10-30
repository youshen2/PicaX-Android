pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PicaX"
include(":app")

providers.exec {
    workingDir(rootDir)
    commandLine("bash", file("scripts/prepare-glasmio.sh").absolutePath)
}.standardOutput.asText.get()

listOf("core", "glass", "ui", "scaffold", "preference", "icons").forEach {
    include(":glasmio-$it")
    project(":glasmio-$it").projectDir = file("build/glasmio/glasmio-$it")
}
