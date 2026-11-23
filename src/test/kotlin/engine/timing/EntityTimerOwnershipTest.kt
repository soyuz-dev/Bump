package org.soyuz.engine.timing

import org.soyuz.engine.entity.DefaultGameEntity
import org.soyuz.engine.scene.RuntimeScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class EntityTimerOwnershipTest {
    @Test
    fun `removal cancels only that entity's active and pending timers`() {
        val scene = RuntimeScene("game")
        val owner = DefaultGameEntity("bomb")
        val other = DefaultGameEntity("other")
        scene.addEntity(owner)
        scene.addEntity(other)
        var removedCalls = 0
        var otherCalls = 0
        val timers = listOf(
            scene.after(10.seconds, owner) { removedCalls++ },
            scene.during(10.seconds, owner) { removedCalls++ },
            scene.forEvery(10.seconds, owner) { removedCalls++ },
            scene.everyFrame(owner) { removedCalls++ }
        )
        scene.everyFrame { otherCalls++ }
        scene.everyFrame(other) { otherCalls++ }
        scene.updateTimers(1.seconds)
        val pending = scene.after(Duration.ZERO, owner) { removedCalls++ }

        scene.removeEntity(owner.id)
        scene.updateTimers(20.seconds)

        assertEquals(2, removedCalls)
        assertEquals(4, otherCalls)
        assertTrue(timers.none { it.isActive })
        assertFalse(pending.isActive)
    }

    @Test
    fun `replacing entity cancels old timers and new entity can use the same id`() {
        val scene = RuntimeScene("game")
        val old = DefaultGameEntity("bomb")
        scene.addEntity(old)
        var oldCalls = 0
        var newCalls = 0
        val active = scene.everyFrame(old) { oldCalls++ }
        scene.updateTimers(1.seconds)
        val pending = scene.after(Duration.ZERO, old) { oldCalls++ }

        val replacement = DefaultGameEntity("bomb")
        scene.addEntity(replacement)
        scene.everyFrame(replacement) { newCalls++ }
        scene.updateTimers(1.seconds)

        assertEquals(1, oldCalls)
        assertEquals(1, newCalls)
        assertFalse(active.isActive)
        assertFalse(pending.isActive)
        assertFailsWith<IllegalArgumentException> { scene.everyFrame(old) {} }
    }

    @Test
    fun `readding same entity keeps its timers and scene cleanup cancels them`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("bomb")
        scene.addEntity(entity)
        var calls = 0
        val timer = scene.everyFrame(entity) { calls++ }

        scene.addEntity(entity)
        scene.updateTimers(1.seconds)
        assertEquals(1, calls)
        assertTrue(timer.isActive)
        scene.cleanup()
        scene.updateTimers(1.seconds)
        assertEquals(1, calls)
        assertFalse(timer.isActive)
    }

    @Test
    fun `all owned timer methods reject entities outside the scene`() {
        val scene = RuntimeScene("game")
        val registered = DefaultGameEntity("bomb")
        val impostor = DefaultGameEntity("bomb")
        scene.addEntity(registered)

        assertFailsWith<IllegalArgumentException> { scene.after(1.seconds, impostor) {} }
        assertFailsWith<IllegalArgumentException> { scene.during(1.seconds, impostor) {} }
        assertFailsWith<IllegalArgumentException> { scene.forEvery(1.seconds, impostor) {} }
        assertFailsWith<IllegalArgumentException> { scene.everyFrame(impostor) {} }
    }

    @Test
    fun `owned timers preserve elapsed time while the scene is inactive`() {
        val game = RuntimeScene("game")
        val menu = RuntimeScene("menu")
        val entity = DefaultGameEntity("bomb")
        game.addEntity(entity)
        var calls = 0
        game.after(1.seconds, entity) { calls++ }

        game.updateTimers(400.milliseconds)
        menu.updateTimers(10.seconds)
        assertEquals(0, calls)
        game.updateTimers(600.milliseconds)
        assertEquals(1, calls)
    }

    @Test
    fun `scheduler owner cancellation uses identity rather than equality`() {
        data class Owner(val id: String)
        val owner = Owner("same")
        val other = Owner("same")
        val scheduler = TimerScheduler()
        var calls = 0
        val cancelled = scheduler.after(Duration.ZERO, owner) { calls++ }
        val retained = scheduler.after(Duration.ZERO, other) { calls++ }

        scheduler.cancelOwnedBy(owner)
        scheduler.update(1.seconds)

        assertFalse(cancelled.isActive)
        assertFalse(retained.isActive)
        assertEquals(1, calls)
    }
}
