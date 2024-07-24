@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://maven.codeawakening.com")
        gradlePluginPortal()
    }
}

rootProject.name = "questioner"
include("lib", "plugin", "server", "stumperd")
