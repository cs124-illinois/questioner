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
    antlr("org.antlr:antlr4:4.13.1")

    implementation(gradleApi())
    implementation(project(":lib"))
    implementation("org.jetbrains:markdown:0.6.1") {
        exclude(module = "kotlin-runtime")
        exclude(module = "kotlin-js")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("com.github.slugify:slugify:3.0.6")

    testImplementation("io.kotest:kotest-runner-junit5:5.8.1")
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
        languageVersion.set(JavaLanguageVersion.of(17))
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
signing {
    setRequired {
        gradle.taskGraph.allTasks.any { it is PublishToMavenRepository }
    }
}
tasks.withType<Javadoc> {
    exclude("edu/illinois/cs/cs125/questioner/antlr/**")
}