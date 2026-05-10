package com.cheroliv.graphify.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GraphModelTest {

    private val mapper: ObjectMapper = ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .registerKotlinModule()

    @Test
    fun `should serialize graph model to JSON`() {
        val model = GraphModel(
            nodes = listOf(GraphNode(id = "src/main.kt", label = "main.kt", type = "file")),
            edges = emptyList(),
            communities = emptyList()
        )

        val json = mapper.writeValueAsString(model)
        assertThat(json).contains("src/main.kt")
        assertThat(json).contains("main.kt")
    }

    @Test
    fun `should deserialize graph model from JSON round trip`() {
        val original = GraphModel(
            nodes = listOf(
                GraphNode(
                    id = "src/App.kt",
                    label = "App.kt",
                    type = "file",
                    community = "my-project",
                    metadata = mapOf("extension" to "kt", "size" to 1024)
                )
            ),
            edges = listOf(),
            communities = listOf(GraphCommunity(id = "my-project", label = "my-project", size = 5))
        )

        val json = mapper.writeValueAsString(original)
        val restored = mapper.readValue<GraphModel>(json)
        assertThat(restored.nodes).hasSize(1)
        assertThat(restored.nodes.first().id).isEqualTo("src/App.kt")
        assertThat(restored.nodes.first().community).isEqualTo("my-project")
        assertThat(restored.nodes.first().metadata["extension"]).isEqualTo("kt")
        assertThat(restored.communities).hasSize(1)
    }

    @Test
    fun `should use default values for optional fields`() {
        val model = GraphModel()
        assertThat(model.nodes).isEmpty()
        assertThat(model.edges).isEmpty()
        assertThat(model.communities).isEmpty()
    }

    @Test
    fun `should serialize GraphNode with all fields`() {
        val node = GraphNode(
            id = "readme.md",
            label = "readme.md",
            type = "file",
            community = "docs",
            metadata = mapOf("extension" to "md")
        )

        val json = mapper.writeValueAsString(node)
        assertThat(json).contains("\"id\"")
        assertThat(json).contains("\"label\"")
        assertThat(json).contains("\"type\"")
        assertThat(json).contains("\"community\"")
        assertThat(json).contains("\"metadata\"")
    }

    @Test
    fun `should serialize GraphEdge omitting null label`() {
        val edgeWithLabel = GraphEdge(source = "a.kt", target = "b.kt", type = "import", label = "com.example.Foo")
        val json = mapper.writeValueAsString(edgeWithLabel)
        assertThat(json).contains("\"label\"")

        val edgeWithoutLabel = GraphEdge(source = "dir/a.kt", target = "dir/b.kt", type = "contains")
        val json2 = mapper.writeValueAsString(edgeWithoutLabel)
        assertThat(json2).doesNotContain("\"label\"")
    }

    @Test
    fun `should serialize GraphCommunity correctly`() {
        val community = GraphCommunity(id = "engine", label = "engine", size = 42)
        val json = mapper.writeValueAsString(community)
        assertThat(json).contains("\"size\":42")
    }
}
