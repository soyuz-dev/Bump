package org.soyuz

import org.lwjgl.glfw.GLFW.*
import org.soyuz.engine.dsl.*
import org.soyuz.engine.render.SolidColor
import org.soyuz.engine.shape.CircleShape
import org.soyuz.engine.shape.RectangleShape
import org.soyuz.engine.shape.TriangleShape
import org.soyuz.util.Assets
import org.soyuz.util.Color
import org.soyuz.util.math.Vector2D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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