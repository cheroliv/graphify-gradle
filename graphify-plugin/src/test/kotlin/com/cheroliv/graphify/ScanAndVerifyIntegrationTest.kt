package com.cheroliv.graphify

import com.cheroliv.graphify.model.GraphModel
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.*

class ScanAndVerifyIntegrationTest {

    @TempDir
    lateinit var tempDir: Path
    private lateinit var workspace: Path
    private lateinit var graphOutput: Path
    private val mapper = ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .registerKotlinModule()
    private val dagLevels = mapOf(
        "graphify-gradle" to 0, "codebase-gradle" to 1,
        "bakery-gradle" to 2, "codex-gradle" to 2,
        "plantuml-gradle" to 2, "engine" to 3
    )
    private val project = org.gradle.testfixtures.ProjectBuilder.builder().build()

    @Nested
    inner class ScanThenVerify {

        @Test
        fun `should produce valid graph json then verify DAG with no violations`() {
            val ws = tempDir.resolve("ws1")
            val fd = ws.resolve("foundry/public")
            fd.createDirectories()
            graphOutput = tempDir.resolve("g1.json")

            createProject(fd, "graphify-gradle") { "plugins { java }" }
            createProject(fd, "codebase-gradle") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\" }" }
            createProject(fd, "engine") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\"; id(\"com.cheroliv.codebase\") version \"0.0.1\" }" }

            val st = project.tasks.register("sw", ScanWorkspaceTask::class.java).get()
            st.rootDir = ws.toFile()
            st.outputFile = graphOutput.toFile()
            st.scan()

            assertThat(graphOutput.toFile()).exists()
            val g = mapper.readValue(graphOutput.toFile(), GraphModel::class.java)
            assertThat(g.nodes).isNotEmpty
            assertThat(g.edges).isNotEmpty
            assertThat(g.communities).isNotEmpty

            val vt = project.tasks.register("vd", VerifyDagAcyclicTask::class.java).get()
            vt.dagLevels = dagLevels
            vt.foundryDir = fd.toFile()
            assertThatCode { vt.verify() }.doesNotThrowAnyException()
        }

        @Test
        fun `should produce valid graph then detect DAG violation`() {
            val ws = tempDir.resolve("ws2")
            val fd = ws.resolve("foundry/public")
            fd.createDirectories()
            graphOutput = tempDir.resolve("g2.json")

            createProject(fd, "graphify-gradle") { "plugins { id(\"com.cheroliv.bakery\") version \"0.1.4\" }" }
            createProject(fd, "bakery-gradle") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\" }" }

            val st = project.tasks.register("sw2", ScanWorkspaceTask::class.java).get()
            st.rootDir = ws.toFile()
            st.outputFile = graphOutput.toFile()
            st.scan()

            assertThat(graphOutput.toFile()).exists()
            val g = mapper.readValue(graphOutput.toFile(), GraphModel::class.java)
            assertThat(g.communities.find { it.id == "graphify-gradle" }).isNotNull

            val vt = project.tasks.register("vd2", VerifyDagAcyclicTask::class.java).get()
            vt.dagLevels = dagLevels
            vt.foundryDir = fd.toFile()
            assertThatThrownBy { vt.verify() }
                .isInstanceOf(RuntimeException::class.java)
                .hasMessageContaining("DAG violations")
                .hasMessageContaining("graphify-gradle")
        }
    }

    @Nested
    inner class EngineConsumer {

        @Test
        fun `should validate graph json contract for engine consumption`() {
            val ws = tempDir.resolve("ws3")
            val fd = ws.resolve("foundry/public")
            fd.createDirectories()
            graphOutput = tempDir.resolve("g3.json")

            createProject(fd, "engine") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\" }" }
            createProject(fd, "codebase-gradle") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\" }" }
            createProject(fd, "graphify-gradle") { "plugins { java }" }

            val st = project.tasks.register("sw3", ScanWorkspaceTask::class.java).get()
            st.rootDir = ws.toFile()
            st.outputFile = graphOutput.toFile()
            st.scan()

            val g = mapper.readValue(graphOutput.toFile(), GraphModel::class.java)
            val json = graphOutput.readText()
            assertThat(json).contains("\"nodes\"", "\"edges\"", "\"communities\"")

            for (n in g.nodes) {
                assertThat(n.id).isNotBlank
                assertThat(n.label).isNotBlank
                assertThat(n.type).isIn("file", "directory", "project")
            }
            for (e in g.edges) {
                assertThat(e.source).isNotBlank
                assertThat(e.target).isNotBlank
                assertThat(e.type).isNotBlank
            }
            assertThat(g.communities).isNotEmpty
            assertThat(g.communities.find { it.id == "engine" }).isNotNull

            val vt = project.tasks.register("vd3", VerifyDagAcyclicTask::class.java).get()
            vt.dagLevels = dagLevels
            vt.foundryDir = fd.toFile()
            assertThatCode { vt.verify() }.doesNotThrowAnyException()
        }
    }

    private fun createProject(fd: Path, name: String, buildContent: () -> String) {
        val dir = fd.resolve(name)
        dir.createDirectories()
        dir.resolve("build.gradle.kts").writeText(buildContent())
        dir.resolve(".git").createDirectories()
        val sf = dir.resolve("src/main/kotlin/Placeholder.kt")
        sf.parent.createDirectories()
        sf.writeText("package com.cheroliv.$name\nclass Placeholder")
    }
}
