plugins {
    alias(libs.plugins.graphify)
}

graphify {
    rootDir.set(file("/home/cheroliv/workspace"))
    outputFile.set(file("graph.json"))
    foundryDir.set(file("/home/cheroliv/workspace/foundry/public"))
    dagLevels.set(mapOf(
        "graphify-gradle" to 0,
        "codebase-gradle" to 1,
        "bakery-gradle" to 2,
        "codex-gradle" to 2,
        "magic-stick" to 2,
        "planner-gradle" to 2,
        "plantuml-gradle" to 2,
        "quizz-benchmark-gradle" to 2,
        "quizz-benchmark-plugin" to 2,
        "readme-gradle" to 2,
        "slider-gradle" to 2,
        "training-gradle" to 2,
        "engine" to 3,
        "waiter-plugin" to 2,
        "notebook-gradle" to 2,
        "newpipe-gradle" to 2,
        "saas-deploy-gradle" to 2
    ))
}
