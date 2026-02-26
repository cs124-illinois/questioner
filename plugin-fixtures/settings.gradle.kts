@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://maven.codeawakening.com")
        gradlePluginPortal()
    }
}

plugins {
    id("org.cs124.questioner.settings") version "2026.2.2"
}

questioner {
    external("external/test")
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://maven.codeawakening.com")
        gradlePluginPortal()
    }
}

rootProject.name = "plugin-fixtures"
