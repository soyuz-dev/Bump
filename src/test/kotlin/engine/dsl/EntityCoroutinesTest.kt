package org.soyuz.engine.dsl

import org.soyuz.engine.entity.DefaultGameEntity
import org.soyuz.engine.entity.GameEntity
import org.soyuz.engine.behaviour.Subscription
import org.soyuz.engine.scene.RuntimeScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class EntityCoroutinesTest {
    @Test
    fun `entity launch waits for installation and provides entity receiver`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("bomb")
        val scope = EntityScope(entity)
        val events = mutableListOf<String>()
        val behaviour = scope.launch {
            assertSame(entity, this.entity)
            events += id
            wait(1.seconds)
            events += "exploded:$id"
        }

        scene.updateTimers(10.seconds)
        assertTrue(events.isEmpty())
        scene.addEntity(entity)
        scope.installBehaviour(scene)
        scene.updateTimers(1.seconds)
        assertEquals(listOf("bomb"), events)
        scene.updateTimers(1.seconds)
        assertEquals(listOf("bomb", "exploded:bomb"), events)
        assertFalse(behaviour.isActive)
    }

    @Test
    fun `entity launch can be cancelled before installation`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("bomb")
        val scope = EntityScope(entity)
        var calls = 0
        val behaviour = scope.launch { calls++ }
        behaviour.cancel()

        scene.addEntity(entity)
        scope.installBehaviour(scene)
        scene.updateTimers(1.seconds)

        assertEquals(0, calls)
        assertFalse(behaviour.isActive)
    }

    @Test
    fun `entity removal cancels its behaviour and runs finally`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("bomb")
        val scope = EntityScope(entity)
        var cleaned = false
        var completed = false
        val behaviour = scope.launch {
            try {
                wait(10.seconds)
                completed = true
            } finally {
                cleaned = true
            }
        }
        scene.addEntity(entity)
        scope.installBehaviour(scene)
        scene.updateTimers(1.seconds)

        scene.removeEntity(entity.id)
        scene.updateTimers(20.seconds)

        assertTrue(cleaned)
        assertFalse(completed)
        assertFalse(behaviour.isActive)
    }

    @Test
    fun `replacing an entity cancels old behaviour without affecting scene behaviour`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("bomb")
        scene.addEntity(entity)
        var oldCompleted = false
        var sceneCompleted = false
        val old = scene.launch(entity) {
            wait(1.seconds)
            oldCompleted = true
        }
        val independent = scene.launch {
            wait(1.seconds)
            sceneCompleted = true
        }
        scene.updateTimers(1.seconds)

        scene.addEntity(DefaultGameEntity("bomb"))
        scene.updateTimers(1.seconds)

        assertFalse(oldCompleted)
        assertTrue(sceneCompleted)
        assertFalse(old.isActive)
        assertFalse(independent.isActive)
        assertFailsWith<IllegalArgumentException> { scene.launch(entity) {} }
    }

    @Test
    fun `failed entity installation cancels deferred and already registered behaviours`() {
        val scene = object : RuntimeScene("game") {
            override fun after(
                delay: Duration,
                owner: GameEntity?,
                callback: () -> Unit
            ): Subscription = error("registration failed")
        }
        val entity = DefaultGameEntity("bomb")
        val scope = EntityScope(entity)
        var calls = 0
        val behaviour = scope.launch { calls++ }
        val timer = scope.after(Duration.ZERO) { calls++ }
        scene.addEntity(entity)

        assertFailsWith<IllegalStateException> { scope.installBehaviour(scene) }
        scene.updateTimers(1.seconds)

        assertEquals(0, calls)
        assertFalse(behaviour.isActive)
        assertFalse(timer.isActive)
    }

    @Test
    fun `discarded entity configuration cancels pending launch`() {
        val scope = EntityScope(DefaultGameEntity("bomb"))
        val behaviour = scope.launch { error("Must not run") }

        scope.discard()

        assertFalse(behaviour.isActive)
        assertFailsWith<IllegalStateException> { scope.launch {} }
    }

    @Test
    fun `cancelling one behaviour leaves other behaviours of the same entity alive`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("bomb")
        scene.addEntity(entity)
        var cancelledCompleted = false
        var siblingCompleted = false
        val cancelled = scene.launch(entity) {
            wait(1.seconds)
            cancelledCompleted = true
        }
        val sibling = scene.launch(entity) {
            wait(1.seconds)
            siblingCompleted = true
        }

        scene.updateTimers(1.seconds)
        cancelled.cancel()
        scene.updateTimers(1.seconds)

        assertFalse(cancelledCompleted)
        assertTrue(siblingCompleted)
        assertFalse(sibling.isActive)
    }
}
