package org.soyuz.org.soyuz.engine.behaviour

import org.soyuz.engine.behaviour.routeCollisions
import org.soyuz.engine.entity.DefaultGameEntity
import org.soyuz.engine.events.CollisionEvent
import org.soyuz.engine.events.CollisionEventType
import org.soyuz.engine.events.RuntimeEventBus
import org.soyuz.engine.scene.RuntimeScene
import kotlin.collections.plusAssign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CollisionEventRoutingTest {

    private fun scene(id: String): RuntimeScene =
        RuntimeScene(id).apply {
            addEntity(DefaultGameEntity("a"))
            addEntity(DefaultGameEntity("b"))
        }

    @Test
    fun `collision entry notifies both entities`() {
        val bus = RuntimeEventBus()
        val scene = scene("game")

        val a = scene.findEntity("a")!!
        val b = scene.findEntity("b")!!

        var aCount = 0
        var bCount = 0

        a.onCollision { other ->
            assertEquals("b", other.id)
            aCount++
        }

        b.onCollision { other ->
            assertEquals("a", other.id)
            bCount++
        }

        bus.routeCollisions { scene }

        bus.publish(
            CollisionEvent("a", "b", CollisionEventType.ENTER)
        )

        assertEquals(1, aCount)
        assertEquals(1, bCount)
    }

    @Test
    fun `legacy collision callbacks only fire on entry`() {
        val bus = RuntimeEventBus()
        val scene = scene("game")

        var count = 0

        scene.findEntity("a")!!.onCollision {
            count++
        }

        bus.routeCollisions { scene }

        for (type in CollisionEventType.entries) {
            bus.publish(CollisionEvent("a", "b", type))
        }

        assertEquals(1, count)
    }

    @Test
    fun `scene listeners receive collision phases`() {
        val scene = scene("game")
        val types = mutableListOf<CollisionEventType>()

        scene.onCollisionEvent {
            types plusAssign it.type
        }

        for (type in CollisionEventType.entries) {
            scene.dispatchCollisionEvent(
                CollisionEvent("a", "b", type)
            )
        }

        assertEquals(CollisionEventType.entries.toList(), types)
    }

    @Test
    fun `events are routed to active scene only`() {
        val bus = RuntimeEventBus()
        val game = scene("game")
        val menu = scene("menu")

        var active: RuntimeScene = game
        var gameCount = 0
        var menuCount = 0

        game.onCollisionEvent { gameCount++ }
        menu.onCollisionEvent { menuCount++ }

        bus.routeCollisions { active }

        bus.publish(CollisionEvent("a", "b"))
        active = menu
        bus.publish(CollisionEvent("a", "b"))

        assertEquals(1, gameCount)
        assertEquals(1, menuCount)
    }

    @Test
    fun `cancelled bridge no longer routes events`() {
        val bus = RuntimeEventBus()
        val scene = scene("game")

        var count = 0
        scene.onCollisionEvent { count++ }

        val subscription = bus.routeCollisions { scene }
        subscription.cancel()

        bus.publish(CollisionEvent("a", "b"))

        assertFalse(subscription.isActive)
        assertEquals(0, count)
    }

    @Test
    fun `removed entities do not receive collisions`() {
        val bus = RuntimeEventBus()
        val scene = scene("game")

        var count = 0
        scene.findEntity("a")!!.onCollision {
            count++
        }

        bus.routeCollisions { scene }
        scene.removeEntity("b")

        bus.publish(CollisionEvent("a", "b"))

        assertEquals(0, count)
    }

    @Test
    fun `cancellation during dispatch is safe`() {
        val scene = scene("game")
        val calls = mutableListOf<Int>()

        lateinit var second: org.soyuz.engine.behaviour.Subscription

        scene.onCollisionEvent {
            calls += 1
            second.cancel()
        }

        second = scene.onCollisionEvent {
            calls += 2
        }

        scene.dispatchCollisionEvent(CollisionEvent("a", "b"))

        assertEquals(listOf(1), calls)
    }
}
