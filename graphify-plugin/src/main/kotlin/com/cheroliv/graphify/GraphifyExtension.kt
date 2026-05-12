package com.cheroliv.graphify

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import java.io.File
import javax.inject.Inject

open class GraphifyExtension @Inject constructor(objects: ObjectFactory) {
    val rootDir: Property<File> = objects.property(File::class.java)
    val outputFile: Property<File> = objects.property(File::class.java)
    val excludePatterns: ListProperty<String> = objects.listProperty(String::class.java)
    val dagLevels: MapProperty<String, Int> = objects.mapProperty(String::class.java, Int::class.java)
    val dagLevelsPropsFile: Property<String> = objects.property(String::class.java)

    init {
        excludePatterns.convention(
            listOf(
                "**/build/**",
                "**/node_modules/**",
                "**/.gradle/**",
                "**/.git/**",
                "**/.idea/**",
                "**/target/**"
            )
        )
    }
}
