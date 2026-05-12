package com.cheroliv.graphify

import org.gradle.api.Plugin
import org.gradle.api.Project

class GraphifyPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("graphify", GraphifyExtension::class.java)

        project.tasks.register("scanWorkspace", ScanWorkspaceTask::class.java) { task ->
            task.rootDir = extension.rootDir.get()
            task.outputFile = extension.outputFile.get()
            task.excludePatterns = extension.excludePatterns.get()
            task.doNotTrackState("Full filesystem scan - inherently non-incremental")
        }

        project.tasks.register("verifyDagAcyclic", VerifyDagAcyclicTask::class.java) { task ->
            task.rootDir = extension.rootDir.get()
            if (extension.dagLevels.isPresent) {
                task.dagLevels = extension.dagLevels.get()
            }
            if (extension.dagLevelsPropsFile.isPresent) {
                task.dagLevelsPropsFile = extension.dagLevelsPropsFile.get()
            }
        }
    }
}
