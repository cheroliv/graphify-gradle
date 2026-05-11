plugins {
    alias(libs.plugins.graphify)
}

graphify {
    rootDir.set(file("/home/cheroliv/workspace"))
    outputFile.set(file("graph.json"))
}
