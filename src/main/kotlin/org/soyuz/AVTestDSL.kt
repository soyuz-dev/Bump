package org.soyuz

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.soyuz.engine.audio.AudioSource
import org.soyuz.engine.collision.Collider
import org.soyuz.engine.dsl.*
import org.soyuz.engine.render.SolidColor
import org.soyuz.engine.render.image.ImagePainter
import org.soyuz.engine.render.text.TextPainter
import org.soyuz.engine.shape.CircleShape
import org.soyuz.engine.shape.RectangleShape
import org.soyuz.engine.ui.Interactive
import org.soyuz.util.Assets
import org.soyuz.util.Color
import org.soyuz.util.math.Easing
import org.soyuz.util.math.Vector2D
import org.soyuz.windowing.TransparentWindow

fun main() = bump {
    val width = 800
    val height = 600

    window(
        title = "Bump - AV Test DSL",
        width = width,
        height = height,
        createWindow = ::TransparentWindow
    ) {
        window.x = 0
        window.y = 0

        val runtime = engine
        val gameCamera = camera

        scene("av_test") {
            val font = Assets.font("roboto")

            // ─── Cat ─────────────────────────

            val catShape = RectangleShape(180.0, 180.0)

            val cat = entity("cat") {
                position = Vector2D(
                    width / 4.0,
                    height / 2.0
                )

                shape = catShape
                painter = ImagePainter(
                    Assets.texture("cat")
                )
                collider = Collider(catShape)
            }

            cat.interactive = Interactive.clickable(
                containsPoint = { position ->
                    cat.collider?.containsPoint(
                        position,
                        cat.transform
                    ) ?: false
                }
            ) {
                AudioSource().play(
                    Assets.audio("meow")
                )
            }

            // ─── Dog ─────────────────────────

            val dogShape = RectangleShape(180.0, 180.0)

            val dog = entity("dog") {
                position = Vector2D(
                    3 * width / 4.0,
                    height / 2.0
                )

                shape = dogShape
                painter = ImagePainter(
                    Assets.texture("dog")
                )
                collider = Collider(dogShape)
            }

            dog.interactive = Interactive.clickable(
                containsPoint = { position ->
                    dog.collider?.containsPoint(
                        position,
                        dog.transform
                    ) ?: false
                }
            ) {
                AudioSource().play(
                    Assets.audio("bark")
                )
            }

            // ─── Labels ──────────────────────

            val fpsLabel = runtime.ui.label(
                "fps",
                width / 2.0,
                10.0,
                "Press ESC to exit | FPS: 0",
                font,
                20f,
                Color(255, 255, 255)
            )

            scene.addEntity(fpsLabel)

            val title = runtime.ui.label(
                "title",
                width / 2.0,
                60.0,
                "Bump AV Test DSL",
                font,
                40f,
                Color(255, 200, 255)
            )

            scene.addEntity(title)

            // ─── Pulsing Circle ──────────────

            val circle = entity("circle") {
                position = Vector2D(
                    width / 2.0,
                    height / 2.0
                )

                shape = CircleShape(60.0)

                painter = SolidColor(
                    Color(255, 100, 50, 100)
                )
            }

            // ─── Play Button ─────────────────

            val playButton = runtime.ui.button(
                "play",
                width / 2.0,
                height - 80.0,
                200.0,
                50.0
            ) {
                println("Play clicked!")

                runtime.during(500.milliseconds) { progress ->
                    val intensity =
                        (1.0 - progress) * 20.0

                    val ox = (
                            (Math.random() - 0.5) *
                                    intensity
                            ).toFloat()

                    val oy = (
                            (Math.random() - 0.5) *
                                    intensity * 0.2
                            ).toFloat()

                    gameCamera.setPosition(ox, oy)
                }

                runtime.after(500.milliseconds) {
                    gameCamera.setPosition(0f, 0f)
                }
            }

            scene.addEntity(playButton)

            // ─── Keyboard Behaviour ─────────

            onKeyDown(GLFW_KEY_ESCAPE) {
                runtime.quit()
            }

            // ─── Audio Timers ────────────────

            runtime.forEvery(500.milliseconds) {
                AudioSource().play(
                    Assets.audio("click")
                )
            }

            runtime.after(1.seconds) {
                AudioSource().play(
                    Assets.audio("meow")
                )
            }

            // ─── Animation & FPS ─────────────

            var time = 0.0

            val fpsHistory = mutableListOf<Double>()
            var fpsAccumulator = 0.0
            var fpsFrameCount = 0

            runtime { dt ->
                time += dt

                // Rolling FPS average over 1 second
                fpsAccumulator += dt
                fpsFrameCount++

                if (fpsAccumulator >= 1.0) {
                    val avgFps =
                        fpsFrameCount / fpsAccumulator

                    fpsHistory.add(avgFps)

                    if (fpsHistory.size > 10) {
                        fpsHistory.removeAt(0)
                    }

                    fpsAccumulator = 0.0
                    fpsFrameCount = 0
                }

                val displayFps =
                    if (fpsHistory.isNotEmpty()) {
                        fpsHistory.average()
                    } else {
                        if (dt > 0.0) 1.0 / dt
                        else 0.0
                    }

                val fpsPainter =
                    fpsLabel.painter as TextPainter

                fpsPainter.text =
                    "Press ESC to exit | " +
                            "FPS: ${displayFps.toInt()}"

                fpsLabel.shape = RectangleShape(
                    fpsPainter.texture
                        ?.width?.toDouble() ?: 100.0,

                    fpsPainter.texture
                        ?.height?.toDouble() ?: 30.0
                )

                // Pulsing circle with easing
                val raw = time % 2.0

                val t =
                    if (raw < 1.0) raw
                    else 2.0 - raw

                val pulse = Easing.quadInOut(t)

                circle.shape = CircleShape(
                    60.0 * pulse + 80.0
                )
            }
        }
    }
}
