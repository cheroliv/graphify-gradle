package com.cheroliv.graphify

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "DAG verification depends on workspace state across projects")
open class VerifyDagAcyclicTask : DefaultTask() {

    @get:Input
    lateinit var dagLevels: Map<String, Int>

    @get:Input
    lateinit var foundryDir: File

    @TaskAction
    fun verify() {
        val projectDirs = foundryDir.listFiles { f: File -> f.isDirectory } ?: emptyArray()
        val violations = mutableListOf<String>()

        for (projectDir in projectDirs) {
            val projectName = projectDir.name
            val projectLevel = levelOf(projectName, dagLevels) ?: continue

            val buildFile = projectDir.resolve("build.gradle.kts")
            if (!buildFile.exists()) continue

            val content = buildFile.readText()
            val regex = Regex("""id\(["']([^"']+)["']\)\s+version\s+["']([^"']+)["']""")
            val importedIds = regex.findAll(content).map { it.groupValues[1] }.toList()

            for (pluginId in importedIds) {
                val depName = pluginId.removePrefix("com.cheroliv.")
                val depLevel = levelOf(depName, dagLevels) ?: continue

                if (depLevel > projectLevel) {
                    violations.add(
                        "$projectName (N$projectLevel) imports $depName (N$depLevel) — N$projectLevel cannot depend on N$depLevel"
                    )
                }
            }
        }

        if (violations.isEmpty()) {
            logger.lifecycle("DAG Acyclic OK. No level violations found.")
        } else {
            val details = violations.joinToString("\n  ") { it }
            logger.error("DAG VIOLATIONS DETECTED (${violations.size}):\n  $details")
            throw GradleException(
                "DAG violations: $details"
            )
        }
    }

    internal fun levelOf(name: String, levels: Map<String, Int>): Int? {
        val candidates = mutableListOf(name)
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
}
