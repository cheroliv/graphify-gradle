package com.cheroliv.graphify

import org.gradle.api.Plugin
import org.gradle.api.Project

class GraphifyPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("graphify", GraphifyExtension::class.java)

        val scanWorkspace = project.tasks.register("scanWorkspace", ScanWorkspaceTask::class.java) { task ->
            task.rootDir = extension.rootDir.get()
            task.outputFile = extension.outputFile.get()
            task.excludePatterns = extension.excludePatterns.get()
            task.doNotTrackState("Full filesystem scan — inherently non-incremental")
        }

        project.tasks.register("verifyDagAcyclic", VerifyDagAcyclicTask::class.java) { task ->
            task.dagLevels = extension.dagLevels.get()
            task.foundryDir = extension.foundryDir.get()
        }

        project.tasks.register("scanAndVerify") { task ->
            task.group = "graphify"
            task.description = "Chain scanWorkspace + verifyDagAcyclic for integrated validation"
            task.dependsOn(scanWorkspace)
            task.finalizedBy("verifyDagAcyclic")
        }
    }
}
