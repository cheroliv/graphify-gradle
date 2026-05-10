plugins {
    `java-library`
    kotlin("jvm")
}

kotlin.jvmToolchain(JavaVersion.VERSION_24.ordinal)

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.slf4j.api)
    testRuntimeOnly(libs.logback.classic)
    testImplementation(libs.assertj)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}
