package com.cheroliv.graphify

import com.cheroliv.graphify.model.GraphModel
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

class ScanWorkspaceTaskTest {

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

    @Test
    fun `should exclude build directories`() {
        writeFile("src/MyClass.kt")
        writeFile("build/classes/MyClass.class")

        task.scan()

        val graph = parseOutput()
        val nodeIds = graph.nodes.map { it.id }
        assertThat(nodeIds).noneMatch { it.contains("build") }
        assertThat(nodeIds).anyMatch { it.contains("src/MyClass.kt") }
    }

    @Test
    fun `should exclude gradle cache directory`() {
        writeFile("src/main.kt")
        writeFile(".gradle/caches/modules-2/foo.jar")

        task.scan()

        val graph = parseOutput()
        val nodeIds = graph.nodes.map { it.id }
        assertThat(nodeIds).noneMatch { it.contains(".gradle") }
        assertThat(nodeIds).anyMatch { it.contains("src/main.kt") }
    }

    @Test
    fun `should exclude git directory`() {
        writeFile("README.md")
        writeFile(".git/HEAD")

        task.scan()

        val graph = parseOutput()
        val nodeIds = graph.nodes.map { it.id }
        assertThat(nodeIds).noneMatch { it.contains(".git") }
    }

    @Test
    fun `should exclude node_modules directory`() {
        writeFile("package.json")
        writeFile("node_modules/lodash/index.js")

        task.scan()

        val graph = parseOutput()
        val nodeIds = graph.nodes.map { it.id }
        assertThat(nodeIds).noneMatch { it.contains("node_modules") }
    }

    @Test
    fun `should exclude idea directory`() {
        writeFile("src/main.kt")
        writeFile(".idea/workspace.xml")

        task.scan()

        val graph = parseOutput()
        val nodeIds = graph.nodes.map { it.id }
        assertThat(nodeIds).noneMatch { it.contains(".idea") }
    }

    @Test
    fun `should exclude target directory`() {
        writeFile("pom.xml")
        writeFile("target/classes/App.class")

        task.scan()

        val graph = parseOutput()
        val nodeIds = graph.nodes.map { it.id }
        assertThat(nodeIds).noneMatch { it.contains("target") }
    }

    @Test
    fun `should extract file nodes with correct type and extension metadata`() {
        writeFile("src/main/kotlin/App.kt")
        writeFile("docs/readme.adoc")

        task.scan()

        val graph = parseOutput()
        val fileNodes = graph.nodes.filter { it.type == "file" }
        assertThat(fileNodes).hasSizeGreaterThanOrEqualTo(2)

        val ktNode = fileNodes.find { it.label == "App.kt" }
        assertThat(ktNode).isNotNull
        assertThat(ktNode!!.metadata["extension"]).isEqualTo("kt")

        val adocNode = fileNodes.find { it.label == "readme.adoc" }
        assertThat(adocNode).isNotNull
        assertThat(adocNode!!.metadata["extension"]).isEqualTo("adoc")
    }

    @Test
    fun `should extract directory nodes`() {
        writeFile("src/main/kotlin/App.kt")

        task.scan()

        val graph = parseOutput()
        val dirNodes = graph.nodes.filter { it.type == "directory" }
        assertThat(dirNodes).isNotEmpty
        val dirIds = dirNodes.map { it.id }
        assertThat(dirIds).anyMatch { it.endsWith("src/main/kotlin") }
    }

    @Test
    fun `should detect project nodes via build gradle kts`() {
        val sub = tempDir.resolve("my-project")
        sub.createDirectories()
        sub.resolve("build.gradle.kts").writeText("")
        writeFilePath(sub, "src/main/kotlin/App.kt")

        task.scan()

        val graph = parseOutput()
        val projectNodes = graph.nodes.filter { it.type == "project" }
        assertThat(projectNodes).isNotEmpty
        assertThat(projectNodes.any { it.label == "my-project" }).isTrue
    }

    @Test
    fun `should detect project nodes via pom xml`() {
        val sub = tempDir.resolve("maven-project")
        sub.createDirectories()
        sub.resolve("pom.xml").writeText("")
        writeFilePath(sub, "src/main/java/App.java")

        task.scan()

        val graph = parseOutput()
        val projectNodes = graph.nodes.filter { it.type == "project" }
        assertThat(projectNodes).isNotEmpty
    }

    @Test
    fun `should detect project nodes via package json`() {
        val sub = tempDir.resolve("npm-project")
        sub.createDirectories()
        sub.resolve("package.json").writeText("")
        writeFilePath(sub, "index.js")

        task.scan()

        val graph = parseOutput()
        val projectNodes = graph.nodes.filter { it.type == "project" }
        assertThat(projectNodes).isNotEmpty
    }

    @Test
    fun `should extract contains edges between directories and files`() {
        writeFile("src/main/kotlin/App.kt")

        task.scan()

        val graph = parseOutput()
        val containsEdges = graph.edges.filter { it.type == "contains" }
        assertThat(containsEdges).isNotEmpty
    }

    @Test
    fun `should extract import edges from kotlin files`() {
        val sub = tempDir.resolve("my-app")
        sub.createDirectories()
        sub.resolve("build.gradle.kts").writeText("")
        writeFilePath(sub, "src/main/kotlin/com/example/App.kt",
            """
            package com.example
            import com.example.Util
            import java.util.List
            """.trimIndent()
        )

        task.scan()

        val graph = parseOutput()
        val importEdges = graph.edges.filter { it.type == "import" }
        assertThat(importEdges).isNotEmpty
    }

    @Test
    fun `should extract reference edges from adoc files`() {
        writeFile("docs/reference.adoc")
        writeFile("docs/index.adoc",
            "See `docs/reference.adoc` for more details."
        )

        task.scan()

        val graph = parseOutput()
        val refEdges = graph.edges.filter { it.type == "reference" }
        assertThat(refEdges).isNotEmpty
    }

    @Test
    fun `should extract agent reference edges from INDEX adoc`() {
        writeFile(".agents/INDEX.adoc",
            """
            = INDEX
            References: configuration/.agents/AGENT_GOVERNANCE.adoc
            and foundry/OSS/graphify-gradle
            """.trimIndent()
        )

        task.scan()

        val graph = parseOutput()
        val agentRefEdges = graph.edges.filter { it.type == "agent_reference" }
        assertThat(agentRefEdges).isNotEmpty
    }

    @Test
    fun `should detect communities from git repos`() {
        val repoRoot = tempDir.resolve("my-git-project")
        repoRoot.createDirectories()
        repoRoot.resolve(".git").createDirectories()
        writeFilePath(repoRoot, "src/main.kt")

        task.scan()

        val graph = parseOutput()
        assertThat(graph.communities).isNotEmpty
        val community = graph.communities.find { it.id == "my-git-project" }
        assertThat(community).isNotNull
        assertThat(community!!.size).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `should assign community to nodes within git repo`() {
        val repoRoot = tempDir.resolve("my-repo")
        repoRoot.createDirectories()
        repoRoot.resolve(".git").createDirectories()
        writeFilePath(repoRoot, "src/App.kt")

        task.scan()

        val graph = parseOutput()
        val nodesInRepo = graph.nodes.filter { it.id.startsWith("my-repo") }
        assertThat(nodesInRepo).isNotEmpty
        assertThat(nodesInRepo).allMatch { it.community == "my-repo" }
    }

    @Test
    fun `should produce valid JSON output`() {
        writeFile("README.md")

        task.scan()

        val graph = parseOutput()
        assertThat(graph.nodes).isNotEmpty
        assertThat(graph.edges).isNotEmpty
    }

    @Test
    fun `should respect custom exclude patterns`() {
        task.excludePatterns = listOf("**/secret/**")
        writeFile("src/main.kt")
        writeFile("secret/keys.txt")

        task.scan()

        val graph = parseOutput()
        val nodeIds = graph.nodes.map { it.id }
        assertThat(nodeIds).noneMatch { it.contains("secret") }
        assertThat(nodeIds).anyMatch { it.contains("src/main.kt") }
    }

    @Test
    fun `should handle empty workspace gracefully`() {
        task.scan()

        val graph = parseOutput()
        assertThat(graph.nodes).isEmpty()
        assertThat(graph.edges).isEmpty()
        assertThat(graph.communities).isEmpty()
    }

    @Test
    fun `should detect agent references across multiple subproject agents directories`() {
        val projA = tempDir.resolve("proj-a")
        projA.createDirectories()
        projA.resolve(".git").createDirectories()
        writeFilePath(projA, ".agents/INDEX.adoc",
            """
            = INDEX — ProjA
            References proj-b/config and proj-c/lib
            """.trimIndent()
        )

        val projB = tempDir.resolve("proj-b")
        projB.createDirectories()
        projB.resolve(".git").createDirectories()
        writeFilePath(projB, ".agents/INDEX.adoc",
            """
            = INDEX — ProjB
            Consumed by proj-a/.agents/INDEX
            """.trimIndent()
        )

        val projC = tempDir.resolve("proj-c")
        projC.createDirectories()
        projC.resolve(".git").createDirectories()
        writeFilePath(projC, ".agents/INDEX.adoc",
            """
            = INDEX — ProjC
            See proj-a/AGENT.adoc and proj-b/build.gradle.kts
            """.trimIndent()
        )

        task.scan()

        val graph = parseOutput()
        val agentRefEdges = graph.edges.filter { it.type == "agent_reference" }
        assertThat(agentRefEdges).isNotEmpty
        val targets = agentRefEdges.map { it.target }
        assertThat(targets).anyMatch { it.contains("proj-b/config") }
        assertThat(targets).anyMatch { it.contains("proj-c/lib") }
        assertThat(targets).anyMatch { it.contains("proj-a/.agents/INDEX") }
        assertThat(targets).anyMatch { it.contains("proj-a/AGENT.adoc") }
        assertThat(targets).anyMatch { it.contains("proj-b/build.gradle.kts") }
    }

    @Test
    fun `should detect agent references from root level agents INDEX to subprojects`() {
        writeFile(".agents/INDEX.adoc",
            """
            = INDEX
            Portefeuille:
            - graphify-gradle in foundry/OSS/graphify-gradle
            - engine in foundry/OSS/engine
            - bakery-gradle in foundry/OSS/bakery-gradle
            """.trimIndent()
        )

        task.scan()

        val graph = parseOutput()
        val agentRefEdges = graph.edges.filter { it.type == "agent_reference" }
        assertThat(agentRefEdges).isNotEmpty
        val targets = agentRefEdges.map { it.target }
        assertThat(targets).anyMatch { it.contains("foundry/OSS/graphify-gradle") }
        assertThat(targets).anyMatch { it.contains("foundry/OSS/engine") }
        assertThat(targets).anyMatch { it.contains("foundry/OSS/bakery-gradle") }
    }

    @Test
    fun `should link agent references from correct source INDEX adoc files`() {
        val projA = tempDir.resolve("project-alpha")
        projA.createDirectories()
        projA.resolve(".git").createDirectories()
        writeFilePath(projA, ".agents/INDEX.adoc", "Ref: project-beta/core")

        val projB = tempDir.resolve("project-beta")
        projB.createDirectories()
        projB.resolve(".git").createDirectories()
        writeFilePath(projB, ".agents/INDEX.adoc", "Ref: project-alpha/api")

        task.scan()

        val graph = parseOutput()
        val agentRefEdges = graph.edges.filter { it.type == "agent_reference" }

        val fromAlpha = agentRefEdges.filter { it.source.startsWith("project-alpha") }
        assertThat(fromAlpha).isNotEmpty
        assertThat(fromAlpha.first().target).isEqualTo("project-beta/core")

        val fromBeta = agentRefEdges.filter { it.source.startsWith("project-beta") }
        assertThat(fromBeta).isNotEmpty
        assertThat(fromBeta.first().target).isEqualTo("project-alpha/api")
    }

    @Test
    fun `should serialize and deserialize graph model round trip`() {
        writeFile("file.txt")

        task.scan()

        val graphJson = outputFile.readText()
        assertThat(graphJson).isNotEmpty

        val graph = mapper.readValue(outputFile, GraphModel::class.java)
        assertThat(graph.nodes).isNotEmpty
        val fileNode = graph.nodes.find { it.type == "file" }
        assertThat(fileNode).isNotNull
        assertThat(fileNode!!.id).isEqualTo("file.txt")
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

    private fun writeFilePath(base: Path, relativePath: String, content: String = "") {
        val file = base.resolve(relativePath)
        file.parent.createDirectories()
        file.writeText(content)
    }
}
