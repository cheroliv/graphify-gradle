package com.cheroliv.graphify

import com.cheroliv.graphify.model.GraphCommunity
import com.cheroliv.graphify.model.GraphEdge
import com.cheroliv.graphify.model.GraphModel
import com.cheroliv.graphify.model.GraphNode
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.*

open class ScanWorkspaceTask : DefaultTask() {

    @get:Internal
    lateinit var rootDir: File

    @get:Internal
    lateinit var outputFile: File

    @get:Internal
    var excludePatterns: List<String> = emptyList()

    private val mapper: ObjectMapper = ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .registerKotlinModule()

    @TaskAction
    fun scan() {
        val root = rootDir.toPath()
        val output = outputFile

        val matchers = excludePatterns.map { pattern ->
            FileSystems.getDefault().getPathMatcher("glob:$pattern")
        }
        val customExcludedNames = extractExcludedNamesFromPatterns(excludePatterns)

        val allFileData = mutableListOf<FileInfo>()
        try {
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (isExcluded(dir, root, matchers, customExcludedNames)) return FileVisitResult.SKIP_SUBTREE
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!isExcluded(file, root, matchers, customExcludedNames)) {
                        allFileData.add(FileInfo(file, attrs.size()))
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult {
                    logger.warn("Skipping inaccessible path: $file")
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: Exception) {
            logger.warn("Walk error: ${e.message}, continuing with partial results")
        }

        val allFiles = allFileData.map { it.path }

        val projects = allFiles.filter { isProjectMarker(it) }
            .map { it.parent }
            .distinct()
        val projectDirs = projects.toSet()

        val dirs = allFiles.asSequence()
            .map { it.parent }
            .distinct()
            .filter { dir -> allFiles.any { it.parent == dir } }
            .toList()

        val repoMap = buildRepoMap(root, allFiles)
        val nodes = extractNodes(root, allFileData, dirs, projects, repoMap)
        val edges = extractEdges(allFiles, projectDirs, root)
        val communities = extractCommunities(repoMap)

        val graph = GraphModel(
            nodes = nodes,
            edges = edges,
            communities = communities
        )

        output.parentFile.mkdirs()
        mapper.writerWithDefaultPrettyPrinter().writeValue(output, graph)
        logger.lifecycle("Graphify scan complete: ${nodes.size} nodes, ${edges.size} edges, ${communities.size} communities -> ${output.absolutePath}")
    }

    private val excludedDirNames = setOf("build", "node_modules", ".gradle", ".git", ".idea", "target")

    private fun isExcluded(path: Path, root: Path, matchers: List<PathMatcher>, customExcludedNames: Set<String>): Boolean {
        if (path == root) return false
        val relative = safe { root.relativize(path) } ?: return true
        if (matchers.any { it.matches(relative) }) return true
        val name = path.fileName.toString()
        if (excludedDirNames.contains(name) || customExcludedNames.contains(name)) return true
        return false
    }

    private fun extractExcludedNamesFromPatterns(patterns: List<String>): Set<String> {
        return patterns.flatMap { pattern ->
            pattern.split("/", "**").filter { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' } }
        }.toSet()
    }

    private fun isProjectMarker(path: Path): Boolean {
        val name = path.fileName.toString()
        return name == "build.gradle.kts" || name == "build.gradle" ||
                name == "pom.xml" || name == "package.json"
    }

    private fun extractNodes(
        root: Path,
        fileData: List<FileInfo>,
        dirs: List<Path>,
        projects: List<Path>,
        repoMap: Map<Path, String>
    ): List<GraphNode> {
        val nodes = mutableListOf<GraphNode>()
        val addedDirs = mutableSetOf<String>()

        for (dir in dirs) {
            val relative = safe { root.relativize(dir).toString() } ?: continue
            val isProject = projects.contains(dir)
            nodes.add(
                GraphNode(
                    id = relative,
                    label = dir.fileName.toString(),
                    type = if (isProject) "project" else "directory",
                    community = repoMap[dir]
                )
            )
            addedDirs.add(relative)
        }

        for ((path, size) in fileData) {
            val relative = safe { root.relativize(path).toString() } ?: continue
            nodes.add(
                GraphNode(
                    id = relative,
                    label = path.fileName.toString(),
                    type = "file",
                    community = repoMap[path.parent],
                    metadata = mapOf(
                        "extension" to (path.extension ?: ""),
                        "size" to size
                    )
                )
            )
        }

        return nodes
    }

    private fun extractEdges(
        files: List<Path>,
        projectDirs: Set<Path>,
        root: Path
    ): List<GraphEdge> {
        val edges = mutableListOf<GraphEdge>()

        for (file in files) {
            val parent = file.parent
            val src = safe { root.relativize(parent).toString() } ?: continue
            val tgt = safe { root.relativize(file).toString() } ?: continue
            edges.add(GraphEdge(source = src, target = tgt, type = "contains"))
        }

        val kotlinFiles = files.filter { it.extension == "kt" || it.extension == "kts" }
        for (file in kotlinFiles) {
            val content = safe { Files.readString(file) } ?: continue
            val imports = extractKotlinImports(content)
            for (import in imports) {
                val targetDir = resolveImportToDir(import, projectDirs, root)
                if (targetDir != null) {
                    val target = safe { root.relativize(targetDir).toString() } ?: continue
                    val fileParent = safe { root.relativize(file.parent).toString() } ?: continue
                    if (target != fileParent) {
                        val src = safe { root.relativize(file).toString() } ?: continue
                        edges.add(
                            GraphEdge(source = src, target = target, type = "import", label = import)
                        )
                    }
                }
            }
        }

        val adocFiles = files.filter { it.extension == "adoc" || it.extension == "ad" }
        for (file in adocFiles) {
            val content = safe { Files.readString(file) } ?: continue
            val refs = extractAdocReferences(content)
            for (ref in refs) {
                val targetFile = safe { resolveReferenceToFile(file.parent, ref) }
                if (targetFile != null && safe { Files.exists(targetFile) } == true) {
                    val src = safe { root.relativize(file).toString() } ?: continue
                    val tgt = safe { root.relativize(targetFile).toString() } ?: continue
                    edges.add(GraphEdge(source = src, target = tgt, type = "reference"))
                }
            }
        }

        val idxFiles = files.filter { it.fileName.toString() == "INDEX.adoc" }
        for (file in idxFiles) {
            val content = safe { Files.readString(file) } ?: continue
            val agentRefs = extractAgentReferences(content)
            for (ref in agentRefs) {
                val src = safe { root.relativize(file).toString() } ?: continue
                edges.add(GraphEdge(source = src, target = ref, type = "agent_reference"))
            }
        }

        return edges
    }

    private fun extractKotlinImports(content: String): List<String> {
        val regex = Regex("""^import\s+([\w.]+)""", RegexOption.MULTILINE)
        return regex.findAll(content).map { it.groupValues[1] }.toList()
    }

    private fun resolveImportToDir(import: String, projectDirs: Set<Path>, root: Path): Path? {
        for (projDir in projectDirs) {
            val srcDir = projDir.resolve("src/main/kotlin")
            val packageDir = srcDir.resolve(import.replace('.', '/').substringBeforeLast("/"))
            if (safe { Files.isDirectory(packageDir) } == true) return projDir
        }
        return null
    }

    private fun extractAdocReferences(content: String): List<String> {
        val refs = mutableListOf<String>()
        val linkRegex = Regex("""(?:link|include|xref|image):([^\[\]\s]+)\[""")
        refs.addAll(linkRegex.findAll(content).map { it.groupValues[1] }.toList())
        val pathRegex = Regex("""`([\w./-]+\.(?:adoc|ad|kt|kts|yml|yaml|json|java|md))`""")
        refs.addAll(pathRegex.findAll(content).map { it.groupValues[1] }.toList())
        return refs
    }

    private fun resolveReferenceToFile(base: Path, ref: String): Path? {
        val candidates = listOf(
            base.resolve(ref).normalize(),
            base.resolve("../$ref").normalize(),
            Path.of(ref)
        )
        return candidates.firstOrNull { safe { Files.isRegularFile(it) } == true }
    }

    private fun extractAgentReferences(content: String): List<String> {
        val regex = Regex("""[\w-]+/[\w-]+(?:/[\w-]+)*""")
        return regex.findAll(content)
            .map { it.value }
            .filter { it.contains("/") }
            .toList()
    }

    private fun buildRepoMap(root: Path, files: List<Path>): Map<Path, String> {
        val result = mutableMapOf<Path, String>()
        for (file in files) {
            var parent: Path? = file.parent
            while (parent != null && parent != root && parent != root.parent) {
                val gitDir = parent.resolve(".git")
                if (safe { Files.isDirectory(gitDir) } == true || safe { Files.isRegularFile(gitDir) } == true) {
                    result[file.parent] = parent.fileName.toString()
                    break
                }
                parent = parent.parent
            }
        }
        return result
    }

    private fun extractCommunities(repoMap: Map<Path, String>): List<GraphCommunity> {
        return repoMap.values
            .groupingBy { it }
            .eachCount()
            .map { (name, size) -> GraphCommunity(id = name, label = name, size = size) }
    }

    private data class FileInfo(val path: Path, val size: Long)

    private fun <T> safe(block: () -> T): T? {
        return try { block() } catch (_: Exception) { null }
    }
}
