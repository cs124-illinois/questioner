plugins {
    kotlin("jvm")
    kotlin("kapt")
    `maven-publish`
}
dependencies {
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.12.0")

    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("com.squareup.moshi:moshi-kotlin-codegen:1.12.0")
    implementation("com.github.slugify:slugify:2.5")
    implementation("org.apache.commons:commons-text:1.9")

    api("com.github.cs125-illinois.jeed:core:2021.5.5")
    api("com.github.cs125-illinois:jenisol:2021.5.5")
    api("io.kotest:kotest-runner-junit5:4.6.0")
    api("com.google.truth:truth:1.1.2")
    api("com.github.cs125-illinois:libcs1:2021.5.7")
}
publishing {
    publications {
        create<MavenPublication>("lib") {
            from(components["java"])
        }
    }
}
kapt {
    includeCompileClasspath = false
}

