
package org.soyuz.engine.physics

import org.soyuz.engine.collision.Collider
import org.soyuz.engine.entity.DefaultGameEntity
import org.soyuz.engine.events.CollisionEvent
import org.soyuz.engine.events.CollisionEventType
import org.soyuz.engine.scene.RuntimeScene
import org.soyuz.engine.shape.CircleShape
import kotlin.test.*

class ScenePhysicsIsolationTest {

    private fun newScene(id: String): RuntimeScene =
        RuntimeScene(id).apply {
            attachPhysics(ScenePhysicsContext.create())
        }

    @Test
    fun `identical ids have independent physics bodies`() {
        val first = newScene("first")
        val second = newScene("second")

        val a = DefaultGameEntity("player")
        val b = DefaultGameEntity("player")

        first.addEntity(a)
        second.addEntity(b)

        val bodyA = PointMass(mass = 1.0)
        val bodyB = PointMass(mass = 5.0)

        first.physicsContext!!.physicsSystem
            .registerBody("player", bodyA)

        second.physicsContext!!.physicsSystem
            .registerBody("player", bodyB)

        assertSame(
            bodyA,
            first.physicsContext!!.physicsSystem
                .getBody("player")
        )

        assertSame(
            bodyB,
            second.physicsContext!!.physicsSystem
                .getBody("player")
        )
    }

    @Test
    fun `removing entity affects only its scene`() {
        val first = newScene("first")
        val second = newScene("second")

        val shapeA = CircleShape(10.0)
        val shapeB = CircleShape(10.0)

        first.addEntity(
            DefaultGameEntity("player", shape = shapeA)
        )
        second.addEntity(
            DefaultGameEntity("player", shape = shapeB)
        )

        val contextA = first.physicsContext!!
        val contextB = second.physicsContext!!

        contextA.physicsSystem.registerBody(
            "player", PointMass(mass = 1.0)
        )
        contextB.physicsSystem.registerBody(
            "player", PointMass(mass = 2.0)
        )

        contextA.collisionSystem.registerCollider(
            "player", Collider(shapeA)
        )
        contextB.collisionSystem.registerCollider(
            "player", Collider(shapeB)
        )

        first.removeEntity("player")

        assertNull(
            contextA.physicsSystem.getBody("player")
        )
        assertNull(
            contextA.collisionSystem.getCollider("player")
        )

        assertNotNull(
            contextB.physicsSystem.getBody("player")
        )
        assertNotNull(
            contextB.collisionSystem.getCollider("player")
        )
    }

    @Test
    fun `collision events stay within their scene`() {
        val first = newScene("first")
        val second = newScene("second")

        for (scene in listOf(first, second)) {
            scene.addEntity(DefaultGameEntity("a"))
            scene.addEntity(DefaultGameEntity("b"))
        }

        var firstCount = 0
        var secondCount = 0

        first.onCollisionEvent { firstCount++ }
        second.onCollisionEvent { secondCount++ }

        first.physicsContext!!.eventBus.publish(
            CollisionEvent(
                "a", "b", CollisionEventType.ENTER
            )
        )

        assertEquals(1, firstCount)
        assertEquals(0, secondCount)

        second.physicsContext!!.eventBus.publish(
            CollisionEvent(
                "a", "b", CollisionEventType.ENTER
            )
        )

        assertEquals(1, firstCount)
        assertEquals(1, secondCount)
    }

    @Test
    fun `cleanup removes physics and subscriptions`() {
        val scene = newScene("game")
        val context = scene.physicsContext!!

        val shape = CircleShape(10.0)
        scene.addEntity(
            DefaultGameEntity("player", shape = shape)
        )

        context.physicsSystem.registerBody(
            "player", PointMass(mass = 1.0)
        )
        context.collisionSystem.registerCollider(
            "player", Collider(shape)
        )

        var calls = 0
        val subscription = scene.onCollisionEvent {
            calls++
        }

        scene.cleanup()

        assertFalse(subscription.isActive)
        assertTrue(scene.allEntities().isEmpty())
        assertNull(context.physicsSystem.getBody("player"))
        assertNull(context.collisionSystem.getCollider("player"))

        context.eventBus.publish(
            CollisionEvent("player", "wall")
        )

        assertEquals(0, calls)
    }

    @Test
    fun `removed id can be reused safely`() {
        val scene = newScene("game")
        val context = scene.physicsContext!!

        scene.addEntity(DefaultGameEntity("player"))

        context.physicsSystem.registerBody(
            "player", PointMass(mass = 1.0)
        )

        scene.removeEntity("player")

        val replacement = DefaultGameEntity("player")
        val replacementBody = PointMass(mass = 3.0)

        scene.addEntity(replacement)
        context.physicsSystem.registerBody(
            "player", replacementBody
        )

        assertSame(
            replacementBody,
            context.physicsSystem.getBody("player")
        )
    }
}
