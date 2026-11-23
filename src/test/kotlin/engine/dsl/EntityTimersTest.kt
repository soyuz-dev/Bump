package org.soyuz.engine.dsl

import org.soyuz.engine.behaviour.Subscription
import org.soyuz.engine.entity.DefaultGameEntity
import org.soyuz.engine.entity.GameEntity
import org.soyuz.engine.scene.RuntimeScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class EntityTimersTest {
    @Test
    fun `entity timers wait for installation and use the entity as receiver`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("bomb")
        val scope = EntityScope(entity)
        val calls = mutableListOf<String>()
        val after = scope.after(1.seconds) { calls += "after:$id" }
        val during = scope.during(1.seconds) { calls += "during:$id:$it" }
        val interval = scope.forEvery(1.seconds) { calls += "interval:$id" }
        val frame = scope.everyFrame { calls += "frame:$id:$it" }

        scene.updateTimers(10.seconds)
        assertTrue(calls.isEmpty())
        assertTrue(after.isActive)

        scene.addEntity(entity)
        scope.installBehaviour(scene)
        scene.updateTimers(1.seconds)

        assertEquals(listOf("after:bomb", "during:bomb:1.0", "interval:bomb", "frame:bomb:1.0"), calls)
        assertFalse(after.isActive)
        assertFalse(during.isActive)
        assertTrue(interval.isActive)
        assertTrue(frame.isActive)
    }

    @Test
    fun `all entity timers can be cancelled before installation`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("bomb")
        val scope = EntityScope(entity)
        var calls = 0
        val timers = listOf(
            scope.after(Duration.ZERO) { calls++ },
            scope.during(1.seconds) { calls++ },
            scope.forEvery(1.seconds) { calls++ },
            scope.everyFrame { calls++ }
        )
        timers.forEach { it.cancel(); it.cancel() }

        scene.addEntity(entity)
        scope.installBehaviour(scene)
        scene.updateTimers(2.seconds)

        assertEquals(0, calls)
        assertTrue(timers.none { it.isActive })
    }

    @Test
    fun `deferred handle cancels its installed timer`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("bomb")
        val scope = EntityScope(entity)
        var calls = 0
        val timer = scope.forEvery(1.seconds) { calls++ }

        scene.addEntity(entity)
        scope.installBehaviour(scene)
        scene.updateTimers(1.seconds)
        timer.cancel()
        scene.updateTimers(1.seconds)

        assertEquals(1, calls)
        assertFalse(timer.isActive)
    }

    @Test
    fun `discarded configuration cancels handles and cannot register more timers`() {
        val scope = EntityScope(DefaultGameEntity("bomb"))
        val timer = scope.after(Duration.ZERO) { error("Must not fire") }

        scope.discard()
        scope.discard()

        assertFalse(timer.isActive)
        assertFailsWith<IllegalStateException> { scope.everyFrame {} }
        assertFailsWith<IllegalStateException> { scope.installBehaviour(RuntimeScene("game")) }
    }

    @Test
    fun `partial installation failure cancels installed and pending timer handles`() {
        val failure = IllegalStateException("registration failed")
        val scene = object : RuntimeScene("game") {
            private var registrations = 0

            override fun after(delay: Duration, owner: GameEntity?, callback: () -> Unit): Subscription {
                if (++registrations == 2) throw failure
                return super.after(delay, owner, callback)
            }
        }
        val entity = DefaultGameEntity("bomb")
        val scope = EntityScope(entity)
        var calls = 0
        val first = scope.after(Duration.ZERO) { calls++ }
        val second = scope.after(Duration.ZERO) { calls++ }
        val third = scope.everyFrame { calls++ }
        scene.addEntity(entity)

        assertSame(failure, assertFailsWith<IllegalStateException> { scope.installBehaviour(scene) })
        scene.updateTimers(1.seconds)

        assertEquals(0, calls)
        assertFalse(first.isActive)
        assertFalse(second.isActive)
        assertFalse(third.isActive)
    }

    @Test
    fun `entity callback can schedule another owned timer after installation`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("bomb")
        val scope = EntityScope(entity)
        var calls = 0
        lateinit var nested: Subscription
        scope.apply {
            after(Duration.ZERO) {
                nested = after(Duration.ZERO) { calls++ }
            }
        }
        scene.addEntity(entity)
        scope.installBehaviour(scene)

        scene.updateTimers(1.seconds)
        assertEquals(0, calls)
        scene.updateTimers(1.seconds)
        assertEquals(1, calls)
        assertFalse(nested.isActive)

        val later = scope.everyFrame { calls++ }
        scene.removeEntity(entity.id)
        scene.updateTimers(1.seconds)
        assertEquals(1, calls)
        assertFalse(later.isActive)
        assertFailsWith<IllegalArgumentException> { scope.after(Duration.ZERO) {} }
    }

    @Test
    fun `entity spawned inside a timer callback starts timers next update`() {
        val scene = RuntimeScene("game")
        var calls = 0
        scene.after(Duration.ZERO) {
            val entity = DefaultGameEntity("bomb")
            val scope = EntityScope(entity)
            scope.everyFrame { calls++ }
            scene.addEntity(entity)
            scope.installBehaviour(scene)
        }

        scene.updateTimers(1.seconds)
        assertEquals(0, calls)
        scene.updateTimers(1.seconds)
        assertEquals(1, calls)
    }

    @Test
    fun `removal during interval callback cancels catch up and pending owned timers`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("bomb")
        val scope = EntityScope(entity)
        var calls = 0
        lateinit var pending: Subscription
        val interval = scope.forEvery(100.milliseconds) {
            calls++
            pending = scope.after(Duration.ZERO) { calls++ }
            scene.removeEntity(id)
        }
        val later = scope.everyFrame { calls++ }
        scene.addEntity(entity)
        scope.installBehaviour(scene)

        scene.updateTimers(1.seconds)
        scene.updateTimers(1.seconds)

        assertEquals(1, calls)
        assertFalse(interval.isActive)
        assertFalse(later.isActive)
        assertFalse(pending.isActive)
    }

    @Test
    fun `invalid durations fail during configuration`() {
        val scope = EntityScope(DefaultGameEntity("bomb"))
        for (invalid in listOf((-1).seconds, Duration.INFINITE, -Duration.INFINITE)) {
            assertFailsWith<IllegalArgumentException> { scope.after(invalid) {} }
            assertFailsWith<IllegalArgumentException> { scope.during(invalid) {} }
            assertFailsWith<IllegalArgumentException> { scope.forEvery(invalid) {} }
        }
        assertFailsWith<IllegalArgumentException> { scope.during(Duration.ZERO) {} }
        assertFailsWith<IllegalArgumentException> { scope.forEvery(Duration.ZERO) {} }
    }
}
