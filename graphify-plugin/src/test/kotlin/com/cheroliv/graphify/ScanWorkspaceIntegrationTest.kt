package com.cheroliv.graphify

import com.cheroliv.graphify.model.GraphModel
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

class ScanWorkspaceIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var task: ScanWorkspaceTask
    private lateinit var outputFile: File
    private val mapper = ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .registerKotlinModule()

    @BeforeEach
    fun setUp() {
        val project = ProjectBuilder.builder().build()
        task = project.tasks.register("scanWorkspace", ScanWorkspaceTask::class.java).get()
        outputFile = tempDir.resolve("graph.json").toFile()
        task.rootDir = tempDir.toFile()
        task.outputFile = outputFile
    }

    @Nested
    inner class MultiBoroughCommunityDetection {

        @Test
        fun `should detect 6 borough communities from independent git repos`() {
            val boroughs = listOf(
                "planner-gradle", "codex-gradle", "codebase-gradle",
                "engine", "plantuml-gradle", "bakery-gradle"
            )

            for (borough in boroughs) {
                val root = tempDir.resolve(borough)
                root.createDirectories()
                root.resolve(".git").createDirectories()
                root.resolve("build.gradle.kts").writeText("")
                writeFilePath(root, "src/main/kotlin/App.kt")
            }

            task.scan()

            val graph = parseOutput()
            val communityIds = graph.communities.map { it.id }.toSet()
            assertThat(communityIds).containsAll(boroughs)
        }

        @Test
        fun `should assign correct community to each node based on git repo membership`() {
            val boroughs = mapOf(
                "planner-gradle" to listOf("src/main/kotlin/Planner.kt", "build.gradle.kts"),
                "engine" to listOf("src/main/kotlin/Engine.kt", "build.gradle.kts")
            )

            for ((name, files) in boroughs) {
                val root = tempDir.resolve(name)
                root.createDirectories()
                root.resolve(".git").createDirectories()
                for (f in files) {
                    writeFilePath(root, f)
                }
            }

            task.scan()

            val graph = parseOutput()
            val plannerNodes = graph.nodes.filter { it.id.startsWith("planner-gradle") }
            assertThat(plannerNodes).isNotEmpty
            assertThat(plannerNodes).allMatch { it.community == "planner-gradle" }

            val engineNodes = graph.nodes.filter { it.id.startsWith("engine") }
            assertThat(engineNodes).isNotEmpty
            assertThat(engineNodes).allMatch { it.community == "engine" }
        }

        @Test
        fun `should detect community sizes correctly`() {
            val borough = "engine"
            val root = tempDir.resolve(borough)
            root.createDirectories()
            root.resolve(".git").createDirectories()
            writeFilePath(root, "src/main/kotlin/A.kt")
            writeFilePath(root, "src/main/kotlin/B.kt")
            writeFilePath(root, "docs/readme.adoc")

            task.scan()

            val graph = parseOutput()
            val community = graph.communities.find { it.id == borough }
            assertThat(community).isNotNull
            assertThat(community!!.size).isGreaterThanOrEqualTo(2)
        }
    }

    @Nested
    inner class CrossProjectEdges {

        @Test
        fun `should create agent_reference edges from multiple borough INDEX adoc files`() {
            val engine = tempDir.resolve("engine")
            engine.createDirectories()
            engine.resolve(".git").createDirectories()
            writeFilePath(engine, ".agents/INDEX.adoc",
                """
                = INDEX — Engine
                Portefeuille: graphify-gradle in foundry/OSS/graphify-gradle
                Consumes plantuml-gradle output
                """.trimIndent()
            )

            val plantuml = tempDir.resolve("plantuml-gradle")
            plantuml.createDirectories()
            plantuml.resolve(".git").createDirectories()
            writeFilePath(plantuml, ".agents/INDEX.adoc",
                """
                = INDEX — PlantUML
                Dependency: graphify-gradle N0
                References engine/AGENT.adoc
                """.trimIndent()
            )

            val graphify = tempDir.resolve("graphify-gradle")
            graphify.createDirectories()
            graphify.resolve(".git").createDirectories()
            writeFilePath(graphify, ".agents/INDEX.adoc",
                """
                = INDEX — Graphify
                Output consumed by engine and plantuml-gradle
                """.trimIndent()
            )

            task.scan()

            val graph = parseOutput()
            val agentRefEdges = graph.edges.filter { it.type == "agent_reference" }
            assertThat(agentRefEdges).isNotEmpty

            val targets = agentRefEdges.map { it.target }.toSet()
            assertThat(targets).anyMatch { it.contains("foundry/OSS/graphify-gradle") }
            assertThat(targets).anyMatch { it.contains("engine/AGENT.adoc") }
        }

        @Test
        fun `should create import edges between kotlin files across boroughs`() {
            val lib = tempDir.resolve("graphify-gradle")
            lib.createDirectories()
            lib.resolve(".git").createDirectories()
            lib.resolve("build.gradle.kts").writeText("")
            writeFilePath(lib, "src/main/kotlin/com/graphify/ScanWorkspaceTask.kt",
                """
                package com.graphify
                class ScanWorkspaceTask
                """.trimIndent()
            )

            val consumer = tempDir.resolve("engine")
            consumer.createDirectories()
            consumer.resolve(".git").createDirectories()
            consumer.resolve("build.gradle.kts").writeText("")
            writeFilePath(consumer, "src/main/kotlin/com/engine/EngineTask.kt",
                """
                package com.engine
                import com.graphify.ScanWorkspaceTask
                class EngineTask
                """.trimIndent()
            )

            task.scan()

            val graph = parseOutput()
            val importEdges = graph.edges.filter { it.type == "import" }
            val crossImport = importEdges.filter { it.source.startsWith("engine") && it.target.startsWith("graphify-gradle") }
            assertThat(crossImport).isNotEmpty
        }

        @Test
        fun `should create contains edges for all files`() {
            val project = tempDir.resolve("engine")
            project.createDirectories()
            project.resolve(".git").createDirectories()
            writeFilePath(project, "src/main/kotlin/Engine.kt")
            writeFilePath(project, "docs/readme.adoc")

            task.scan()

            val graph = parseOutput()
            val containsEdges = graph.edges.filter { it.type == "contains" }
            val engineContains = containsEdges.filter { it.target.startsWith("engine") }
            assertThat(engineContains).hasSizeGreaterThanOrEqualTo(2)
        }
    }

    @Nested
    inner class ExclusionVerification {

        @Test
        fun `should exclude all default ignored directories in realistic workspace`() {
            val exclusions = listOf(
                "build/classes", ".gradle/caches", ".git/objects",
                "node_modules/lodash", ".idea/caches", "target/classes"
            )
            val kept = listOf("src/main.kt", "README.adoc", "docs/index.adoc")

            for (d in exclusions) writeFile(d)
            for (f in kept) writeFile(f)

            task.scan()

            val graph = parseOutput()
            val nodeIds = graph.nodes.map { it.id }.toSet()
            assertThat(nodeIds).noneMatch {
                it.contains("build") || it.contains(".gradle") ||
                it.contains(".git") || it.contains("node_modules") ||
                it.contains(".idea") || it.contains("target")
            }
            assertThat(nodeIds).containsAll(kept)
        }

        @Test
        fun `should exclude deeply nested build directories`() {
            val engine = tempDir.resolve("engine")
            engine.createDirectories()
            engine.resolve(".git").createDirectories()
            writeFilePath(engine, "src/main/kotlin/Engine.kt")
            writeFilePath(engine, "build/classes/kotlin/Engine.class")
            writeFilePath(engine, "build/tmp/compileKotlin/cache.tab")
            writeFilePath(engine, "subproject/build/output/result.bin")

            task.scan()

            val graph = parseOutput()
            val nodeIds = graph.nodes.map { it.id }
            assertThat(nodeIds).noneMatch { it.contains("/build/") }
            assertThat(nodeIds).anyMatch { it.contains("engine/src/main/kotlin/Engine.kt") }
        }

        @Test
        fun `should respect custom exclude patterns in multi-borough scan`() {
            val engine = tempDir.resolve("engine")
            engine.createDirectories()
            engine.resolve(".git").createDirectories()
            writeFilePath(engine, "src/main/kotlin/Engine.kt")
            writeFilePath(engine, "secret/keys.properties")
            writeFilePath(engine, "crypto/wallet.dat")

            val plantuml = tempDir.resolve("plantuml-gradle")
            plantuml.createDirectories()
            plantuml.resolve(".git").createDirectories()
            writeFilePath(plantuml, "src/main/kotlin/PlantUML.kt")
            writeFilePath(plantuml, "secret/tokens.yml")

            task.excludePatterns = listOf("**/secret/**", "**/crypto/**")

            task.scan()

            val graph = parseOutput()
            val nodeIds = graph.nodes.map { it.id }
            assertThat(nodeIds).noneMatch { it.contains("secret") }
            assertThat(nodeIds).noneMatch { it.contains("crypto") }
            assertThat(nodeIds).anyMatch { it.contains("engine/src/main/kotlin/Engine.kt") }
            assertThat(nodeIds).anyMatch { it.contains("plantuml-gradle/src/main/kotlin/PlantUML.kt") }
        }
    }

    @Nested
    inner class OutputValidation {

        @Test
        fun `should produce valid parseable JSON for multi-borough workspace`() {
            val boroughs = listOf("engine", "graphify-gradle", "plantuml-gradle")
            for (borough in boroughs) {
                val root = tempDir.resolve(borough)
                root.createDirectories()
                root.resolve(".git").createDirectories()
                root.resolve("build.gradle.kts").writeText("")
                writeFilePath(root, "src/main/kotlin/App.kt")
            }

            task.scan()

            assertThat(outputFile).exists()
            assertThat(outputFile.length()).isGreaterThan(100)

            val graph = parseOutput()
            assertThat(graph.nodes).isNotEmpty
            assertThat(graph.edges).isNotEmpty
            assertThat(graph.communities).isNotEmpty

            val roundTrip = outputFile.readText()
            val reRead = mapper.readValue(roundTrip, GraphModel::class.java)
            assertThat(reRead.nodes).hasSize(graph.nodes.size)
            assertThat(reRead.edges).hasSize(graph.edges.size)
            assertThat(reRead.communities).hasSize(graph.communities.size)
        }

        @Test
        fun `should include project nodes for gradle maven and npm projects`() {
            val types = listOf(
                "gradle-proj" to "build.gradle.kts",
                "maven-proj" to "pom.xml",
                "npm-proj" to "package.json",
                "gradle-groovy" to "build.gradle"
            )

            for ((name, marker) in types) {
                val root = tempDir.resolve(name)
                root.createDirectories()
                root.resolve(".git").createDirectories()
                root.resolve(marker).writeText("")
                writeFilePath(root, "src/App.kt")
            }

            task.scan()

            val graph = parseOutput()
            val projectNodes = graph.nodes.filter { it.type == "project" }
            val projectNames = projectNodes.map { it.label }.toSet()
            assertThat(projectNames).containsAll(types.map { it.first })
        }

        @Test
        fun `should produce deterministic output for identical workspace`() {
            val engine = tempDir.resolve("engine")
            engine.createDirectories()
            engine.resolve(".git").createDirectories()
            engine.resolve("build.gradle.kts").writeText("")
            writeFilePath(engine, "src/main/kotlin/Engine.kt")

            task.scan()
            val first = outputFile.readText()

            outputFile.delete()
            val secondProject = ProjectBuilder.builder().build()
            val secondTask = secondProject.tasks.register("scanWorkspace", ScanWorkspaceTask::class.java).get()
            secondTask.rootDir = tempDir.toFile()
            secondTask.outputFile = outputFile
            secondTask.scan()
            val second = outputFile.readText()

            assertThat(first).isEqualTo(second)
        }

        @Test
        fun `should handle large multi-borough workspace with many files`() {
            val boroughs = (1..10).map { "project-$it" }
            for (borough in boroughs) {
                val root = tempDir.resolve(borough)
                root.createDirectories()
                root.resolve(".git").createDirectories()
                root.resolve("build.gradle.kts").writeText("")
                writeFilePath(root, "src/main/kotlin/Service.kt")
                writeFilePath(root, "src/test/kotlin/ServiceTest.kt")
                writeFilePath(root, ".agents/INDEX.adoc",
                    """
                    = INDEX — $borough
                    References other projects
                    """.trimIndent()
                )
            }

            task.scan()

            val graph = parseOutput()
            assertThat(graph.communities).hasSize(10)
            assertThat(graph.nodes).hasSizeGreaterThanOrEqualTo(50)
            assertThat(graph.edges).hasSizeGreaterThanOrEqualTo(40)

            val communityIds = graph.communities.map { it.id }.toSet()
            assertThat(communityIds).containsAll(boroughs)

            val withCommunity = graph.nodes.filter { it.community != null }
            assertThat(withCommunity).isNotEmpty
        }
    }

    @Nested
    inner class WorkspaceMimic {

        @Test
        fun `should mimic workspace structure with foundry office and configuration`() {
            val foundry = listOf("engine", "graphify-gradle", "plantuml-gradle", "bakery-gradle")
            for (proj in foundry) {
                val root = tempDir.resolve("foundry").resolve("OSS").resolve(proj)
                root.createDirectories()
                root.resolve(".git").createDirectories()
                root.resolve("build.gradle.kts").writeText("")
                writeFilePath(root, "src/main/kotlin/${proj.replace("-", "/")}/App.kt")
                writeFilePath(root, ".agents/INDEX.adoc",
                    """
                    = INDEX — $proj
                    Portefeuille: foundry/OSS/engine and foundry/OSS/plantuml-gradle
                    Config: configuration/.agents/AGENT_GOVERNANCE.adoc
                    """.trimIndent()
                )
            }

            val office = tempDir.resolve("office")
            office.createDirectories()
            office.resolve(".git").createDirectories()
            writeFilePath(office, "books-collection/README.adoc")
            writeFilePath(office, ".agents/INDEX.adoc",
                """
                = INDEX — Office
                Consumed by foundry/OSS/bakery-gradle and foundry/OSS/engine
                References configuration/.agents/AGENT_GOVERNANCE.adoc
                """.trimIndent()
            )

            task.scan()

            val graph = parseOutput()
            val communityIds = graph.communities.map { it.id }.toSet()
            assertThat(communityIds).containsAll(foundry)
            assertThat(communityIds).contains("office")

            val agentRefEdges = graph.edges.filter { it.type == "agent_reference" }
            assertThat(agentRefEdges).isNotEmpty

            val nodes = graph.nodes.map { it.id }.toSet()
            foundry.forEach { borough ->
                assertThat(nodes.filter { it.contains(borough) }).isNotEmpty
            }
        }
    }

    private fun parseOutput(): GraphModel {
        assertThat(outputFile).exists()
        return mapper.readValue(outputFile, GraphModel::class.java)
    }

    private fun writeFile(relativePath: String, content: String = "") {
        val file = tempDir.resolve(relativePath)
        file.parent.createDirectories()
        file.writeText(content)
    }

    private fun writeFilePath(root: Path, relativePath: String, content: String = "") {
        val file = root.resolve(relativePath)
        file.parent.createDirectories()
        file.writeText(content)
    }
}
