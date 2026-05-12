package com.cheroliv.graphify

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class VerifyDagAcyclicTaskTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var task: VerifyDagAcyclicTask

    companion object {
        private val levels = mapOf(
            "graphify-gradle" to 0,
            "codebase-gradle" to 1,
            "bakery-gradle" to 2,
            "codex-gradle" to 2,
            "magic-stick" to 2,
            "plantuml-gradle" to 2,
            "engine" to 3
        )
    }

    @BeforeEach
    fun setUp() {
        val project = ProjectBuilder.builder().build()
        task = project.tasks.register("verifyDagAcyclic", VerifyDagAcyclicTask::class.java).get()
        task.foundryDir = tempDir.toFile()
        task.dagLevels = levels
    }

    @Test
    fun `should pass when DAG is acyclic`() {
        writeBuild("engine") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\"; id(\"com.cheroliv.bakery\") version \"0.1.4\" }" }
        writeBuild("bakery-gradle") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\" }" }
        writeBuild("codebase-gradle") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\" }" }
        writeBuild("graphify-gradle") { "plugins { java }" }

        assertThatCode { task.verify() }.doesNotThrowAnyException()
    }

    @Test
    fun `should fail when N1 imports N2`() {
        writeBuild("engine") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\" }" }
        writeBuild("codebase-gradle") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\"; id(\"com.cheroliv.bakery\") version \"0.1.4\" }" }
        writeBuild("bakery-gradle") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\" }" }
        writeBuild("graphify-gradle") { "plugins { java }" }

        assertThatThrownBy { task.verify() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("DAG violations")
    }

    @Test
    fun `should fail when N0 imports N3`() {
        writeBuild("graphify-gradle") { "plugins { id(\"com.cheroliv.engine\") version \"1.0.0\" }" }
        writeBuild("engine") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\" }" }

        assertThatThrownBy { task.verify() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("DAG violations")
    }

    @Test
    fun `should pass on empty foundry directory`() {
        assertThatCode { task.verify() }.doesNotThrowAnyException()
    }

    @Test
    fun `should skip directories without build gradle kts`() {
        tempDir.resolve("some-random-dir").createDirectories()
        tempDir.resolve("some-random-dir/file.txt").writeText("hello")

        assertThatCode { task.verify() }.doesNotThrowAnyException()
    }

    @Test
    fun `should skip directories not in dagLevels registry`() {
        writeBuild("unknown-project") { "plugins { id(\"com.cheroliv.engine\") version \"1.0.0\" }" }

        assertThatCode { task.verify() }.doesNotThrowAnyException()
    }

    @Test
    fun `should pass with multiple same-level imports`() {
        writeBuild("bakery-gradle") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\" }" }
        writeBuild("codex-gradle") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\" }" }
        writeBuild("magic-stick") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\" }" }
        writeBuild("graphify-gradle") { "plugins { java }" }

        assertThatCode { task.verify() }.doesNotThrowAnyException()
    }

    @Test
    fun `should detect violation regardless of version string`() {
        writeBuild("graphify-gradle") { "plugins { id(\"com.cheroliv.engine\") version \"2.5.1-SNAPSHOT\" }" }
        writeBuild("engine") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\" }" }

        assertThatThrownBy { task.verify() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("DAG violations")
    }

    @Test
    fun `should use custom dagLevels configuration`() {
        task.dagLevels = mapOf("alpha" to 0, "beta" to 1, "gamma" to 2)

        writeBuild("alpha") { "plugins { id(\"com.cheroliv.gamma\") version \"1.0.0\" }" }
        writeBuild("gamma") { "plugins { id(\"com.cheroliv.alpha\") version \"0.0.1\" }" }

        assertThatThrownBy { task.verify() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("DAG violations")
    }

    @Test
    fun `should pass with no violations in mixed setup`() {
        writeBuild("engine") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\"; id(\"com.cheroliv.codebase\") version \"0.0.1\"; id(\"com.cheroliv.codex\") version \"0.0.1\" }" }
        writeBuild("plantuml-gradle") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\"; id(\"com.cheroliv.codebase\") version \"0.0.1\" }" }
        writeBuild("codebase-gradle") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\" }" }
        writeBuild("graphify-gradle") { "plugins { java; kotlin }" }

        assertThatCode { task.verify() }.doesNotThrowAnyException()
    }

    @Test
    fun `should resolve plugin id suffixes correctly`() {
        task.dagLevels = mapOf("slider-gradle" to 2, "readme-gradle" to 2, "training-gradle" to 2)
        writeBuild("slider-gradle") { "plugins { id(\"com.cheroliv.readme\") version \"0.1.0\" }" }
        writeBuild("readme-gradle") { "plugins { id(\"com.cheroliv.slider\") version \"0.1.0\" }" }
        writeBuild("training-gradle") { "plugins { id(\"com.cheroliv.slider\") version \"0.1.0\" }" }

        assertThatCode { task.verify() }.doesNotThrowAnyException()
    }

    @Test
    fun `should detect violation with suffix resolution`() {
        task.dagLevels = mapOf("graphify-gradle" to 0, "slider-gradle" to 2)
        writeBuild("graphify-gradle") { "plugins { id(\"com.cheroliv.slider\") version \"0.1.0\" }" }
        writeBuild("slider-gradle") { "plugins { id(\"com.cheroliv.graphify\") version \"0.0.1\" }" }

        assertThatThrownBy { task.verify() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("DAG violations")
    }

    @Test
    fun `levelOf should find by key then by suffixed variants`() {
        val levels = mapOf(
            "graphify-gradle" to 0,
            "quizz-benchmark-plugin" to 2,
            "training-gradle" to 2
        )

        assertThat(task.levelOf("graphify-gradle", levels)).isEqualTo(0)
        assertThat(task.levelOf("graphify", levels)).isEqualTo(0)
        assertThat(task.levelOf("quizz-benchmark", levels)).isEqualTo(2)
        assertThat(task.levelOf("quizz-benchmark-plugin", levels)).isEqualTo(2)
        assertThat(task.levelOf("training-gradle", levels)).isEqualTo(2)
        assertThat(task.levelOf("training", levels)).isEqualTo(2)
        assertThat(task.levelOf("unknown", levels)).isNull()
    }

    private fun writeBuild(projectName: String, content: () -> String) {
        val dir = tempDir.resolve(projectName)
        dir.createDirectories()
        dir.resolve("build.gradle.kts").writeText(content())
    }
}
