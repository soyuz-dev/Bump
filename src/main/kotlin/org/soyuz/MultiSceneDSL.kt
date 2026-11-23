package org.soyuz

import org.lwjgl.glfw.GLFW.*
import org.soyuz.engine.dsl.*
import org.soyuz.engine.render.SolidColor
import org.soyuz.engine.render.text.Font
import org.soyuz.engine.render.text.TextPainter
import org.soyuz.engine.shape.CircleShape
import org.soyuz.engine.shape.RectangleShape
import org.soyuz.engine.shape.TriangleShape
import org.soyuz.util.Assets
import org.soyuz.util.Color
import org.soyuz.util.math.Vector2D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

fun main() = bump {
    window("Bump - MultiScene DSL", 800, 600) {
        val font = Assets.font("roboto")

        // Scene 1: Colorful circles
        val circles = scene("circles") {
            val colors = listOf(
                Color(255, 80, 80),
                Color(80, 255, 80),
                Color(80, 80, 255),
                Color(255, 255, 80),
                Color(255, 80, 255),
                Color(80, 255, 255)
            )

            for (i in colors.indices) {
                val angle = i * 2.0 * PI / colors.size

                entity("circle_$i") {
                    position = Vector2D(
                        400.0 + cos(angle) * 150.0,
                        250.0 + sin(angle) * 150.0
                    )

                    shape = CircleShape(40.0)
                    painter = SolidColor(colors[i])
                }
            }
            coroutineDemo(font)
        }

        // Scene 2: Rectangles
        val rectangles = scene("rectangles") {
            for (i in 0..3) {
                entity("rect_$i") {
                    position = Vector2D(
                        200.0 + i * 150.0,
                        300.0
                    )

                    shape = RectangleShape(100.0, 60.0)

                    painter = SolidColor(
                        Color(
                            100 + i * 40,
                            150,
                            200 - i * 30
                        )
                    )
                }
            }
            coroutineDemo(font)
        }

        // Scene 3: Triangles
        val triangles = scene("triangles") {
            for (i in 0..4) {
                entity("tri_$i") {
                    position = Vector2D(
                        150.0 + i * 120.0,
                        300.0
                    )

                    shape = TriangleShape.equilateral(60.0)

                    painter = SolidColor(
                        Color(
                            200,
                            150 + i * 20,
                            100
                        )
                    )
                }
            }
            coroutineDemo(font)
        }

        // Scene-specific UI
        circles.addEntity(
            engine.ui.label(
                "label1",
                400.0,
                100.0,
                "Scene 1: Circles (Press 1, 2, 3 to switch)",
                font,
                16f,
                Color(255, 255, 255)
            )
        )

        rectangles.addEntity(
            engine.ui.label(
                "label2",
                400.0,
                100.0,
                "Scene 2: Rectangles (Press 1, 2, 3 to switch)",
                font,
                16f,
                Color(255, 255, 255)
            )
        )

        triangles.addEntity(
            engine.ui.label(
                "label3",
                400.0,
                100.0,
                "Scene 3: Triangles (Press 1, 2, 3 to switch)",
                font,
                16f,
                Color(255, 255, 255)
            )
        )

        // Register input on each persistent scene.
        val allScenes = listOf(
            circles,
            rectangles,
            triangles
        )

        for (current in allScenes) {
            current.onKeyDown(GLFW_KEY_1) {
                engine.loadScene(circles)
            }

            current.onKeyDown(GLFW_KEY_2) {
                engine.loadScene(rectangles)
            }

            current.onKeyDown(GLFW_KEY_3) {
                engine.loadScene(triangles)
            }

            current.onKeyDown(GLFW_KEY_ESCAPE) {
                engine.quit()
            }
        }

        // Explicit initial scene
        engine.loadScene(circles)

        // Global per-frame behaviour
        engine { dt ->
            val sceneName =
                engine.activeScene?.id ?: "none"

            window.title =
                "Scene: $sceneName | FPS: " +
                        (1.0 / dt).toInt()
        }
    }
}

private fun SceneScope.coroutineDemo(font: Font) {
    val current = scene

    fun statusLine(id: String, y: Double, initial: String, fontSize: Float = 16f): (String) -> Unit {
        val label = engine.ui.label(id, 400.0, y, initial, font, fontSize, Color(230, 230, 230))
        current.addEntity(label)
        val text = label.painter as TextPainter
        return { value ->
            text.text = value
            text.update(0f)
            label.shape = RectangleShape(
                text.texture?.width?.toDouble() ?: 1.0,
                text.texture?.height?.toDouble() ?: 1.0
            )
        }
    }

    val clockStatus = statusLine("coroutine_clock_status", 475.0, "Scene clock: awaiting 2s intro")
    val probeStatus = statusLine("coroutine_probe_status", 505.0, "Probe waiting to start")
    statusLine(
        "coroutine_controls",
        560.0,
        "1/2/3: scenes | C: stop clock | X: remove probe | R: respawn | Esc: quit",
        14f
    )

    var activeSeconds = 0
    val intro = launch {
        wait(2.seconds)
        println("[${current.id}] Intro completed; clock can start")
    }
    val clock = launch {
        try {
            intro.await()
            clockStatus("Scene clock: 0s")
            while (true) {
                wait(1.seconds)
                activeSeconds++
                clockStatus("Scene clock: ${activeSeconds}s")
            }
        } finally {
            clockStatus("Scene clock stopped at ${activeSeconds}s")
            println("[${current.id}] Scene clock cancelled at ${activeSeconds}s")
        }
    }

    var generation = 0
    var cleanups = 0

    fun spawnProbe() {
        current.removeEntity("coroutine_probe")
        val number = ++generation
        probeStatus("Probe #$number: ready to move | Cleanups: $cleanups")

        entity("coroutine_probe") {
            position = Vector2D(180.0, 440.0)
            shape = CircleShape(14.0)
            painter = SolidColor(Color(80, 220, 255))

            launch {
                var step = 0
                try {
                    while (true) {
                        val start = transform.position
                        val end = Vector2D(180.0 + ((step + 1) % 6) * 88.0, 440.0)
                        probeStatus("Probe #$number: moving to step ${step + 1} | Cleanups: $cleanups")
                        until(1.seconds) { progress ->
                            transform = transform.copy(position = start + (end - start) * progress)
                        }
                        step++
                        painter = SolidColor(
                            if (step % 2 == 0) Color(80, 220, 255) else Color(255, 180, 60)
                        )
                        probeStatus("Probe #$number: resting at step $step | Cleanups: $cleanups")
                        wait(1.seconds)
                    }
                } finally {
                    cleanups++
                    probeStatus("Probe #$number cancelled | Cleanups: $cleanups")
                    println("[${current.id}] Probe #$number cancelled at step $step; cleanups: $cleanups")
                }
            }
        }
    }

    spawnProbe()

    onKeyDown(GLFW_KEY_C) { clock.cancel() }
    onKeyDown(GLFW_KEY_X) { current.removeEntity("coroutine_probe") }
    onKeyDown(GLFW_KEY_R) { spawnProbe() }
}
