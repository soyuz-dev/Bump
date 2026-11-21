
package org.soyuz.engine.behaviour

import org.soyuz.engine.collision.Collider
import org.soyuz.engine.collision.RuntimeCollisionSystem
import org.soyuz.engine.entity.DefaultGameEntity
import org.soyuz.engine.events.CollisionEventType
import org.soyuz.engine.events.RuntimeEventBus
import org.soyuz.engine.physics.PointMass
import org.soyuz.engine.physics.RuntimePhysicsSystem
import org.soyuz.engine.scene.RuntimeScene
import org.soyuz.engine.shape.CircleShape
import org.soyuz.engine.shape.RectangleShape
import org.soyuz.util.math.Vector2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhysicsEventIntegrationTest {

    @Test
    fun `physics collision reaches both entities`() {
        val bus = RuntimeEventBus()
        val collisions = RuntimeCollisionSystem()
        val physics = RuntimePhysicsSystem(collisions, bus)
        val scene = RuntimeScene("game")

        val ballShape = CircleShape(20.0)
        val floorShape = RectangleShape(400.0, 40.0)

        val ball = DefaultGameEntity("ball").apply {
            position = Vector2D(200.0, 100.0)
            shape = ballShape
        }

        val floor = DefaultGameEntity("floor").apply {
            position = Vector2D(200.0, 300.0)
            shape = floorShape
        }

        scene.addEntity(ball)
        scene.addEntity(floor)

        val ballBody = PointMass(
            mass = 1.0,
            restitution = 0.0
        ).apply {
            velocity = Vector2D(0.0, 200.0)
        }

        val floorBody = PointMass(mass = 0.0)

        physics.registerBody(ball.id, ballBody)
        physics.registerBody(floor.id, floorBody)

        collisions.registerCollider(
            ball.id, Collider(ballShape)
        )
        collisions.registerCollider(
            floor.id, Collider(floorShape)
        )

        var ballHits = 0
        var floorHits = 0
        val phases = mutableListOf<CollisionEventType>()

        ball.onCollision { other ->
            assertEquals("floor", other.id)
            ballHits++
        }

        floor.onCollision { other ->
            assertEquals("ball", other.id)
            floorHits++
        }

        scene.onCollisionEvent { event ->
            phases += event.type
        }

        val routing = bus.routeCollisions { scene }

        repeat(200) {
            physics.step(scene, 0.01)
        }

        assertTrue(
            ballHits > 0,
            "Ball did not receive a collision callback"
        )
        assertEquals(ballHits, floorHits)

        assertTrue(
            CollisionEventType.ENTER in phases,
            "Physics did not publish an ENTER event"
        )

        routing.cancel()
    }

    @Test
    fun `collision routing follows active scene`() {
        val bus = RuntimeEventBus()
        val collisions = RuntimeCollisionSystem()
        val physics = RuntimePhysicsSystem(collisions, bus)

        val first = RuntimeScene("first")
        val second = RuntimeScene("second")

        var active = first
        var firstHits = 0
        var secondHits = 0

        first.onCollisionEvent { firstHits++ }
        second.onCollisionEvent { secondHits++ }

        bus.routeCollisions { active }

        // Same engine, different active scene.
        // The test exercises routing ownership.
        val event = org.soyuz.engine.events.CollisionEvent(
            "a", "b", CollisionEventType.ENTER
        )

        for (scene in listOf(first, second)) {
            scene.addEntity(DefaultGameEntity("a"))
            scene.addEntity(DefaultGameEntity("b"))
        }

        bus.publish(event)
        active = second
        bus.publish(event)

        assertEquals(1, firstHits)
        assertEquals(1, secondHits)
    }
}
