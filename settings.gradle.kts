pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0") }

rootProject.name = "graphify-gradle"

include(":graphify-plugin")
include(":api-key-pool")
