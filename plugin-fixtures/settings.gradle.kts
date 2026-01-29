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
    id("org.cs124.questioner.settings") version "2026.1.4"
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
