
package org.soyuz

import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import org.soyuz.engine.audio.AudioSource
import org.soyuz.engine.collision.Collider
import org.soyuz.engine.dsl.*
import org.soyuz.engine.entity.DefaultGameEntity
import org.soyuz.engine.events.CollisionEventType
import org.soyuz.engine.physics.PointMass
import org.soyuz.engine.physics.RigidBody
import org.soyuz.engine.physics.forcefields.ConstantAccelerationField
import org.soyuz.engine.physics.with
import org.soyuz.engine.render.SolidColor
import org.soyuz.engine.shape.CircleShape
import org.soyuz.engine.shape.RectangleShape
import org.soyuz.engine.ui.Interactive
import org.soyuz.util.Assets
import org.soyuz.util.math.Vector2D
import kotlin.math.roundToInt

fun main() = bump {
    window("Bump - BrickPit DSL", 800, 800) {
        vsync = false

        val runtime = engine
        val gameWindow = window
        val gameCamera = camera

        scene("brickpit") {
            val sceneBuilder = this
            val gravity = ConstantAccelerationField(Vector2D(0.0, 981.0))
            val sound = AudioSource()
            var entityCount = 0

            onCollisionEvent { event ->
                if (event.type != CollisionEventType.ENTER) return@onCollisionEvent

                val a = event.sourceEntityId
                val b = event.otherEntityId
                if (a.startsWith("wall") && b.startsWith("wall")) return@onCollisionEvent

                println("Bump! $a hit $b")
                sound.play(Assets.audio("click"))
            }

            fun makeBall(
                x: Double, y: Double, vx: Double, vy: Double,
                mass: Double = 1.0, radius: Double = 10.0, restitution: Double = 0.5
            ): DefaultGameEntity {
                val id = "ball_${entityCount++}"
                val circle = CircleShape(radius)
                val body = PointMass(mass = mass, restitution = restitution) with gravity
                body.velocity = Vector2D(vx, vy)

                val ball = sceneBuilder.entity(id) {
                    position = Vector2D(x, y)
                    shape = circle
                    painter = SolidColor(Math.random(), Math.random(), Math.random(), 1.0)

                    physics {
                        this.body = body
                        collider = Collider(circle)
                    }
                }

                val collider = requireNotNull(ball.collider)
                ball.interactive = Interactive.draggable(
                    containsPoint = { collider.containsPoint(it, ball.transform) },
                    onDrag = { _, currentPos ->
                        ball.position = currentPos
                        body.velocity = Vector2D.ZERO
                    }
                )

                return ball
            }

            fun makeBrick(
                x: Double, y: Double, vx: Double, vy: Double,
                w: Double = 40.0, h: Double = 30.0, mass: Double = 1.0,
                angVel: Double = 0.0, restitution: Double = 0.5
            ): DefaultGameEntity {
                val id = "brick_${entityCount++}"
                val rectangle = RectangleShape(w, h)
                val body = RigidBody(
                    mass = mass, restitution = restitution, friction = 0.4, width = w, height = h
                ) with gravity

                body.velocity = Vector2D(vx, vy)
                body.angularVelocity = angVel

                val brick = sceneBuilder.entity(id) {
                    position = Vector2D(x, y)
                    shape = rectangle
                    painter = SolidColor(0.8, 0.4, 0.1, 1.0)

                    physics {
                        this.body = body
                        collider = Collider(rectangle)
                    }
                }

                val collider = requireNotNull(brick.collider)
                brick.interactive = Interactive.draggable(
                    containsPoint = { collider.containsPoint(it, brick.transform) },
                    onDrag = { _, currentPos ->
                        brick.position = currentPos
                        body.velocity = Vector2D.ZERO
                    }
                )

                return brick
            }

            fun makeWall(id: String, x: Double, y: Double, w: Double, h: Double): DefaultGameEntity {
                val rectangle = RectangleShape(w, h)

                return sceneBuilder.entity(id) {
                    position = Vector2D(x, y)
                    shape = rectangle
                    painter = SolidColor(0.2, 0.2, 0.3, 1.0)

                    physics {
                        body = PointMass(mass = 0.0, restitution = 1.0)
                        collider = Collider(rectangle)
                    }
                }
            }

            val width = 800.0
            val height = 800.0
            val thickness = 100.0

            makeWall("wall_0", -thickness / 2, height / 2, thickness * 1.5, height)
            makeWall("wall_1", width + thickness / 2, height / 2, thickness * 1.5, height)
            makeWall("wall_2", width / 2, -thickness / 2, width, thickness * 1.5)
            makeWall("wall_3", width / 2, height + thickness / 2, width, thickness * 1.5)

            makeBall(width / 3, height / 2, 200.0, -500.0, mass = 0.5, radius = 8.0)
            makeBall(2 * width / 3, height / 2, -200.0, -300.0, mass = 0.5, radius = 8.0)

            runtime { dt ->
                if (runtime.input.isMouseJustPressed(GLFW_MOUSE_BUTTON_LEFT)) {
                    val mouse = gameCamera.screenToWorld(runtime.input.mousePosition)

                    if (Math.random() < 0.5) {
                        val radius = 5.0 + Math.random() * 15.0
                        val vx = (Math.random() - 0.5) * 600.0
                        val vy = (Math.random() - 0.5) * 600.0 - 300.0

                        makeBall(
                            mouse.x, mouse.y, vx, vy, mass = 0.5 + Math.random() * 2.0, radius = radius
                        )
                    } else {
                        val w = 20.0 + Math.random() * 60.0
                        val h = 15.0 + Math.random() * 40.0
                        val vx = (Math.random() - 0.5) * 400.0
                        val vy = (Math.random() - 0.5) * 400.0 - 200.0
                        val angVel = (Math.random() - 0.5) * 10.0

                        makeBrick(
                            mouse.x, mouse.y, vx, vy, w, h, mass = 0.5 + Math.random() * 2.0, angVel = angVel
                        )
                    }
                }

                gameWindow.title = "Bump DSL! Thinguses: $entityCount, fps: ${(1.0 / dt).roundToInt()}"
            }
        }
    }
}