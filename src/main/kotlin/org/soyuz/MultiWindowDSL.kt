package org.soyuz

import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.soyuz.engine.dsl.*
import org.soyuz.engine.render.SolidColor
import org.soyuz.engine.shape.CircleShape
import org.soyuz.engine.shape.TriangleShape
import org.soyuz.util.Color
import org.soyuz.util.math.Vector2D
import kotlin.math.sin

fun main() = bump {

    // ─────────────────────────────────────
    // Window 1: Rotating Triangle
    // ─────────────────────────────────────

    window("Bump - Rotating Triangle", 600, 500) {
        window.x = 100
        window.y = 100

        val triangleWindow = window

        scene("triangle_scene") {

            val triangle = entity("rotating_tri") {
                position = Vector2D(300.0, 250.0)
                shape = TriangleShape.isosceles(
                    60.0, 80.0
                )
                painter = SolidColor(
                    Color(100, 200, 255)
                )
            }

            var triTime = 0.0

            onKeyDown(GLFW_KEY_ESCAPE) {
                engine.quit()
            }

            engine { dt ->
                triTime += dt

                triangle.rotation += dt * 3.0

                // Pulsing colour
                val r = (
                        150 + sin(triTime * 2.0) * 100
                        ).toInt()

                val g = (
                        180 + sin(triTime * 2.5) * 70
                        ).toInt()

                val b = (
                        220 + sin(triTime * 3.0) * 35
                        ).toInt()

                triangle.painter = SolidColor(
                    Color(r, g, b)
                )

                triangleWindow.title =
                    "Triangle | FPS: " +
                            (1.0 / dt).toInt()
            }
        }
    }

    // ─────────────────────────────────────
    // Window 2: Pulsing Circle
    // ─────────────────────────────────────

    window("Bump - Pulsing Circle", 600, 500) {
        window.x = 750
        window.y = 100

        val circleWindow = window

        scene("circle_scene") {

            val circle = entity("pulsing_circle") {
                position = Vector2D(300.0, 250.0)
                shape = CircleShape(80.0)
                painter = SolidColor(
                    Color(255, 130, 80)
                )
            }

            var circleTime = 0.0

            onKeyDown(GLFW_KEY_ESCAPE) {
                engine.quit()
            }

            engine { dt ->
                circleTime += dt

                val pulse =
                    1.0 + sin(circleTime * 4.0) * 0.4

                circle.shape = CircleShape(
                    80.0 * pulse
                )

                val r = 255

                val g = (
                        130 + sin(circleTime * 2.0) * 80
                        ).toInt()

                val b = (
                        80 + sin(circleTime * 2.5) * 100
                        ).toInt()

                circle.painter = SolidColor(
                    Color(r, g, b)
                )

                circleWindow.title =
                    "Circle | FPS: " +
                            (1.0 / dt).toInt()
            }
        }
    }
}
