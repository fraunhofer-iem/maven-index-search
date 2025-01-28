plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"

    application

}

application {
    mainClass = "de.fraunhofer.iem.MavenIndexSearchKt"
}

group = "de.fraunhofer.iem"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val luceneVersion = "10.1.0"

dependencies {
    implementation("org.apache.lucene:lucene-analysis-common:$luceneVersion")
    implementation("org.apache.lucene:lucene-queryparser:$luceneVersion")
    implementation("org.apache.lucene:lucene-backward-codecs:$luceneVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}