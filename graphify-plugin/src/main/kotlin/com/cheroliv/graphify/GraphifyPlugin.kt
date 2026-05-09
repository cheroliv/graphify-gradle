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
            task.doNotTrackState("Full filesystem scan — inherently non-incremental")
        }
    }
}
