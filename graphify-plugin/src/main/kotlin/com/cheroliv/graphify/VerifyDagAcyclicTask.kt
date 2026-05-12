package com.cheroliv.graphify

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File

open class VerifyDagAcyclicTask : DefaultTask() {

    @get:Input
    var rootDir: File = project.rootProject.projectDir

    @get:Input
    @Option(option = "dagLevels", description = "Path to a properties file mapping project dir names to DAG levels")
    var dagLevelsPropsFile: String = ""

    @get:Input
    var dagLevels: Map<String, Int> = emptyMap()

    companion object {
        private const val PLUGIN_ID_PREFIX = "com.cheroliv."
    }

    @TaskAction
    fun verify() {
        val levels = resolveDagLevels()
        val projectDirs = rootDir.listFiles { f: File -> f.isDirectory } ?: emptyArray()

        val violations = mutableListOf<String>()

        for (projectDir in projectDirs) {
            val projectName = projectDir.name
            val projectLevel = levelOf(projectName, levels) ?: continue

            val buildFile = projectDir.resolve("build.gradle.kts")
            if (!buildFile.exists()) continue

            val importedIds = extractPluginIds(buildFile)
            for (pluginId in importedIds) {
                val depName = pluginId.removePrefix(PLUGIN_ID_PREFIX)
                val depLevel = levelOf(depName, levels) ?: continue

                if (depLevel > projectLevel) {
                    violations.add(
                        "$projectName (N$projectLevel) imports $depName (N$depLevel) - N$projectLevel cannot depend on N$depLevel"
                    )
                }
            }
        }

        if (violations.isEmpty()) {
            logger.lifecycle("DAG Acyclic OK. No level violations found.")
        } else {
            logger.error("DAG VIOLATIONS DETECTED (${violations.size}):")
            violations.forEach { v -> logger.error("  $v") }
            throw GradleException(
                "DAG violations: ${violations.size} violation(s). Higher-level projects cannot depend on lower-level projects."
            )
        }
    }

    internal fun levelOf(name: String, levels: Map<String, Int>): Int? {
        val candidates = mutableListOf<String>()
        candidates.add(name)
        candidates.add("${name}-gradle")
        candidates.add("${name}-plugin")
        if (name.endsWith("-gradle")) candidates.add(name.removeSuffix("-gradle"))
        if (name.endsWith("-plugin")) candidates.add(name.removeSuffix("-plugin"))

        val dashNorm = name.replace("_", "-")
        candidates.add(dashNorm)
        candidates.add("${dashNorm}-gradle")
        candidates.add("${dashNorm}-plugin")
        if (dashNorm.endsWith("-gradle")) candidates.add(dashNorm.removeSuffix("-gradle"))
        if (dashNorm.endsWith("-plugin")) candidates.add(dashNorm.removeSuffix("-plugin"))

        val underscoreNorm = name.replace("-", "_")
        candidates.add(underscoreNorm)
        candidates.add("${underscoreNorm}-gradle")
        candidates.add("${underscoreNorm}-plugin")
        if (underscoreNorm.endsWith("-gradle")) candidates.add(underscoreNorm.removeSuffix("-gradle"))
        if (underscoreNorm.endsWith("-plugin")) candidates.add(underscoreNorm.removeSuffix("-plugin"))

        return candidates.firstNotNullOfOrNull { levels[it] }
    }

    private fun resolveDagLevels(): Map<String, Int> {
        if (dagLevels.isNotEmpty()) return dagLevels
        if (dagLevelsPropsFile.isNotBlank()) {
            val propsFile = File(dagLevelsPropsFile)
            if (propsFile.exists()) {
                val props = java.util.Properties()
                props.load(propsFile.inputStream())
                return props.map { it.key.toString() to it.value.toString().toInt() }.toMap()
            }
        }
        return emptyMap()
    }

    private fun extractPluginIds(buildFile: File): List<String> {
        val content = buildFile.readText()
        val regex = Regex("""id\(["']([^"']+)["']\)\s+version\s+["']([^"']+)["']""")
        return regex.findAll(content).map { it.groupValues[1] }.toList()
    }
}
