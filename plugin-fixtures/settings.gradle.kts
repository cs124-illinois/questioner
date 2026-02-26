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
    id("org.cs124.questioner.settings") version "2026.2.3"
}

questioner {
    external("external/test") {
        exclude("com/github/cs124_illinois/questioner/testing/external/excluded")
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

rootProject.name = "plugin-fixtures"
