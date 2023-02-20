plugins {
    kotlin("jvm")
    antlr
    java
    `java-gradle-plugin`
    `maven-publish`
    id("org.jmailen.kotlinter")
    id("com.google.devtools.ksp")
}
dependencies {
    antlr("org.antlr:antlr4:4.12.0")

    implementation(gradleApi())
    implementation(project(":lib"))
    implementation("org.jetbrains:markdown:0.4.0") {
        exclude(module = "kotlin-runtime")
        exclude(module = "kotlin-js")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("com.google.googlejavaformat:google-java-format:1.15.0")
    implementation("com.google.guava:guava:31.1-jre")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.14.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
    implementation("com.github.slugify:slugify:3.0.2")
    implementation("org.apache.httpcomponents.client5:httpclient5-fluent:5.2.1")

    testImplementation("io.kotest:kotest-runner-junit5:5.5.5")
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
            id = "com.github.cs124-illinois.questioner"
            implementationClass = "edu.illinois.cs.cs125.questioner.plugin.QuestionerPlugin"
        }
    }
}
