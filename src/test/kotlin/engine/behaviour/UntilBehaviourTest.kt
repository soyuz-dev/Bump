package org.soyuz.engine.behaviour

import org.soyuz.engine.dsl.EntityScope
import org.soyuz.engine.entity.DefaultGameEntity
import org.soyuz.engine.scene.RuntimeScene
import org.soyuz.util.math.Vector2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.nanoseconds

class UntilBehaviourTest {
    @Test
    fun `rounded progress does not complete before actual duration elapses`() {
        val scene = RuntimeScene("game")
        val progress = mutableListOf<Double>()
        var finished = false
        scene.launch {
            until(365.days) { progress += it }
            finished = true
        }

        scene.updateTimers(1.milliseconds)
        scene.updateTimers(365.days - 1.nanoseconds)
        assertTrue(progress.single() < 1.0)
        assertFalse(finished)
        scene.updateTimers(1.nanoseconds)
        assertEquals(1.0, progress.last())
        assertEquals(2, progress.size)
        assertTrue(finished)
    }

    @Test
    fun `until reports progress on updates and finishes after final callback`() {
        val scene = RuntimeScene("game")
        val engineThread = Thread.currentThread()
        val progress = mutableListOf<Double>()
        var finished = false
        val behaviour = scene.launch {
            until(1.seconds) {
                assertSame(engineThread, Thread.currentThread())
                assertFalse(finished)
                progress += it
            }
            assertSame(engineThread, Thread.currentThread())
            assertEquals(1.0, progress.last())
            finished = true
        }

        scene.updateTimers(1.milliseconds)
        assertTrue(progress.isEmpty())
        scene.updateTimers(250.milliseconds)
        scene.updateTimers(250.milliseconds)
        assertEquals(listOf(0.25, 0.5), progress)
        assertFalse(finished)
        scene.updateTimers(500.milliseconds)
        scene.updateTimers(1.seconds)

        assertEquals(listOf(0.25, 0.5, 1.0), progress)
        assertTrue(finished)
        assertFalse(behaviour.isActive)
    }

    @Test
    fun `overshoot emits one final value and does not advance next until early`() {
        val scene = RuntimeScene("game")
        val first = mutableListOf<Double>()
        val second = mutableListOf<Double>()
        var finished = false
        scene.launch {
            until(1.seconds) { first += it }
            until(1.seconds) { second += it }
            finished = true
        }

        scene.updateTimers(1.milliseconds)
        scene.updateTimers(10.seconds)
        assertEquals(listOf(1.0), first)
        assertTrue(second.isEmpty())
        assertFalse(finished)
        scene.updateTimers(500.milliseconds)
        assertEquals(listOf(0.5), second)
        scene.updateTimers(500.milliseconds)
        assertEquals(listOf(0.5, 1.0), second)
        assertTrue(finished)
    }

    @Test
    fun `until preserves progress while scene is inactive or elapsed is zero`() {
        val game = RuntimeScene("game")
        val menu = RuntimeScene("menu")
        val progress = mutableListOf<Double>()
        game.launch { until(1.seconds) { progress += it } }

        game.updateTimers(1.milliseconds)
        game.updateTimers(250.milliseconds)
        menu.updateTimers(10.seconds)
        game.updateTimers(10.seconds) { false }
        game.updateTimers(Duration.ZERO)
        assertEquals(listOf(0.25), progress)
        game.updateTimers(750.milliseconds)
        assertEquals(listOf(0.25, 1.0), progress)
    }

    @Test
    fun `cancellation stops progress without forcing final value and runs finally once`() {
        val scene = RuntimeScene("game")
        val progress = mutableListOf<Double>()
        var cleanups = 0
        var finished = false
        val behaviour = scene.launch {
            try {
                until(1.seconds) { progress += it }
                finished = true
            } finally {
                cleanups++
            }
        }

        scene.updateTimers(1.milliseconds)
        scene.updateTimers(250.milliseconds)
        behaviour.cancel()
        behaviour.cancel()
        scene.updateTimers(10.seconds)

        assertEquals(listOf(0.25), progress)
        assertEquals(1, cleanups)
        assertFalse(finished)
        assertFalse(behaviour.isActive)
    }

    @Test
    fun `progress callback can cancel its own behaviour`() {
        val scene = RuntimeScene("game")
        lateinit var behaviour: BehaviourHandle
        var callbacks = 0
        var cleaned = false
        var finished = false
        behaviour = scene.launch {
            try {
                until(1.seconds) {
                    callbacks++
                    behaviour.cancel()
                }
                finished = true
            } finally {
                cleaned = true
            }
        }

        scene.updateTimers(1.milliseconds)
        scene.updateTimers(250.milliseconds)
        scene.updateTimers(1.seconds)

        assertEquals(1, callbacks)
        assertTrue(cleaned)
        assertFalse(finished)
    }

    @Test
    fun `callback failure can be caught by the surrounding coroutine`() {
        val scene = RuntimeScene("game")
        val failure = IllegalStateException("progress failed")
        var callbacks = 0
        var caught: Throwable? = null
        var continued = false
        val behaviour = scene.launch {
            try {
                until(1.seconds) {
                    callbacks++
                    throw failure
                }
            } catch (cause: IllegalStateException) {
                caught = cause
            }
            wait(Duration.ZERO)
            continued = true
        }

        scene.updateTimers(1.milliseconds)
        scene.updateTimers(250.milliseconds)
        assertSame(failure, caught)
        assertFalse(continued)
        scene.updateTimers(1.seconds)
        scene.updateTimers(1.seconds)

        assertEquals(1, callbacks)
        assertTrue(continued)
        assertFalse(behaviour.isActive)
    }

    @Test
    fun `uncaught callback failure reaches awaiter and cancels progress timer`() {
        val scene = RuntimeScene("game")
        val failure = IllegalArgumentException("progress failed")
        var callbacks = 0
        val target = scene.launch {
            until(1.seconds) {
                callbacks++
                throw failure
            }
        }
        var caught: Throwable? = null
        scene.launch {
            try {
                target.await()
            } catch (cause: IllegalArgumentException) {
                caught = cause
            }
        }

        scene.updateTimers(1.milliseconds)
        scene.updateTimers(250.milliseconds)
        scene.updateTimers(1.seconds)

        assertSame(failure, caught)
        assertEquals(1, callbacks)
        assertFalse(target.isActive)
    }

    @Test
    fun `unobserved callback failure is reported by engine update`() {
        val scene = RuntimeScene("game")
        val failure = IllegalStateException("progress failed")
        val behaviour = scene.launch { until(1.seconds) { throw failure } }
        scene.updateTimers(1.milliseconds)

        assertSame(failure, assertFailsWith<IllegalStateException> { scene.updateTimers(250.milliseconds) })
        scene.updateTimers(1.seconds)
        assertFalse(behaviour.isActive)
    }

    @Test
    fun `final callback scene switch defers following coroutine code until reactivation`() {
        val scene = RuntimeScene("game")
        val progress = mutableListOf<Double>()
        var active = true
        var continued = false
        val behaviour = scene.launch {
            until(1.seconds) {
                progress += it
                active = false
            }
            continued = true
        }

        scene.updateTimers(1.milliseconds)
        scene.updateTimers(1.seconds) { active }
        assertEquals(listOf(1.0), progress)
        assertFalse(continued)
        assertTrue(behaviour.isActive)
        scene.updateTimers(1.seconds) { false }
        assertFalse(continued)
        active = true
        scene.updateTimers(1.seconds) { active }

        assertTrue(continued)
        assertFalse(behaviour.isActive)
        assertEquals(listOf(1.0), progress)
    }

    @Test
    fun `unrelated cleanup failure does not strand a running until`() {
        val scene = RuntimeScene("game")
        val failure = IllegalStateException("cleanup failed")
        val other = scene.launch {
            try {
                wait(10.seconds)
            } finally {
                throw failure
            }
        }
        var completed = false
        scene.launch {
            until(1.seconds) { if (it < 1.0) other.cancel() }
            completed = true
        }
        scene.updateTimers(1.milliseconds)

        assertSame(failure, assertFailsWith<IllegalStateException> { scene.updateTimers(250.milliseconds) })
        scene.updateTimers(750.milliseconds)
        assertTrue(completed)
    }

    @Test
    fun `entity until can mutate entity and is cancelled on removal`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("probe")
        val scope = EntityScope(entity)
        var cleaned = false
        val behaviour = scope.launch {
            try {
                until(1.seconds) {
                    transform = transform.copy(position = Vector2D(it * 100.0, 0.0))
                }
            } finally {
                cleaned = true
            }
        }
        scene.addEntity(entity)
        scope.installBehaviour(scene)
        scene.updateTimers(1.milliseconds)
        scene.updateTimers(250.milliseconds)
        assertEquals(Vector2D(25.0, 0.0), entity.position)

        scene.removeEntity(entity.id)
        scene.updateTimers(1.seconds)

        assertEquals(Vector2D(25.0, 0.0), entity.position)
        assertTrue(cleaned)
        assertFalse(behaviour.isActive)
    }

    @Test
    fun `scene cleanup cancels until before its first progress callback`() {
        val scene = RuntimeScene("game")
        var callbacks = 0
        var cleaned = false
        val behaviour = scene.launch {
            try {
                until(1.seconds) { callbacks++ }
            } finally {
                cleaned = true
            }
        }
        scene.updateTimers(1.milliseconds)

        scene.cleanup()
        scene.updateTimers(10.seconds)

        assertEquals(0, callbacks)
        assertTrue(cleaned)
        assertFalse(behaviour.isActive)
    }

    @Test
    fun `until rejects zero negative and infinite durations when reached`() {
        for (invalid in listOf(Duration.ZERO, (-1).seconds, Duration.INFINITE, -Duration.INFINITE)) {
            val scene = RuntimeScene("game")
            val behaviour = scene.launch { until(invalid) { error("Must not run") } }

            assertFailsWith<IllegalArgumentException> { scene.updateTimers(1.seconds) }
            assertFalse(behaviour.isActive)
        }
    }
}
