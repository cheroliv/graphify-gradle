package com.cheroliv.graphify.model

data class GraphModel(
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList(),
    val communities: List<GraphCommunity> = emptyList()
)

data class GraphNode(
    val id: String,
    val label: String,
    val type: String,
    val community: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

data class GraphEdge(
    val source: String,
    val target: String,
    val type: String,
    val label: String? = null
)

data class GraphCommunity(
    val id: String,
    val label: String,
    val size: Int
)
