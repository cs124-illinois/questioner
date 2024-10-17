@file:Suppress("PackageUpdate")

plugins {
    kotlin("jvm")
    antlr
    java
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("org.jmailen.kotlinter")
    id("com.google.devtools.ksp")
}
dependencies {
    antlr("org.antlr:antlr4:4.13.2")

    implementation(gradleApi())
    implementation(project(":lib"))
    implementation("org.jetbrains:markdown:0.7.3") {
        exclude(module = "kotlin-runtime")
        exclude(module = "kotlin-js")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.0")
    implementation("com.github.slugify:slugify:3.0.7")

    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:2.0.21")
    implementation("gradle.plugin.com.github.sherter.google-java-format:google-java-format-gradle-plugin:0.9")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
    implementation("org.jmailen.gradle:kotlinter-gradle:4.4.1")

    val ktorVersion = "3.0.0"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("com.beust:klaxon:5.6")
    implementation("io.github.z4kn4fein:semver:2.0.0")

    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
}
tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}
tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)
}
tasks.formatKotlinMain {
    dependsOn(tasks.generateGrammarSource)
}
tasks.lintKotlinMain {
    dependsOn(tasks.generateGrammarSource)
}
tasks.generateGrammarSource {
    outputDirectory = File(projectDir, "src/main/java/edu/illinois/cs/cs125/questioner/antlr")
    arguments.addAll(
        listOf(
            "-visitor",
            "-package", "edu.illinois.cs.cs125.questioner.antlr",
            "-Xexact-output-dir",
            "-lib", "src/main/antlr/edu/illinois/cs/cs125/questioner/antlr/lib/"
        )
    )
}
configurations {
    all {
        exclude("ch.qos.logback")
    }
}
tasks {
    val sourcesJar by creating(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
        dependsOn(getTasksByName("generateGrammarSource", false))
    }
    artifacts {
        add("archives", sourcesJar)
    }
}
gradlePlugin {
    plugins {
        create("plugin") {
            id = "org.cs124.questioner"
            implementationClass = "edu.illinois.cs.cs125.questioner.plugin.QuestionerPlugin"
        }
    }
}
java {
    withJavadocJar()
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
publishing {
    publications {
        afterEvaluate {
            withType<MavenPublication> {
                pom {
                    name = "questioner"
                    description = "Questioner Gradle plugin for CS 124."
                    url = "https://cs124.org"
                    licenses {
                        license {
                            name = "MIT License"
                            url = "https://opensource.org/license/mit/"
                        }
                    }
                    developers {
                        developer {
                            id = "gchallen"
                            name = "Geoffrey Challen"
                            email = "challen@illinois.edu"
                        }
                    }
                    scm {
                        connection = "scm:git:https://github.com/cs124-illinois/questioner.git"
                        developerConnection = "scm:git:https://github.com/cs124-illinois/questioner.git"
                        url = "https://github.com/cs124-illinois/questioner"
                    }
                    signing {
                        sign(this@publications)
                    }
                }
            }
        }
    }
}
tasks.withType<Javadoc> {
    exclude("edu/illinois/cs/cs125/questioner/antlr/**")
}