package org.soyuz.engine.behaviour

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.soyuz.engine.scene.RuntimeScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class BehaviourHandleTest {
    @Test
    fun `await resumes after target body and finally complete`() {
        val scene = RuntimeScene("game")
        val events = mutableListOf<String>()
        val target = scene.launch {
            try {
                wait(1.seconds)
                events += "body"
            } finally {
                events += "finally"
            }
        }
        val waiter = scene.launch {
            target.await()
            events += "awaited"
        }

        scene.updateTimers(1.seconds)
        assertTrue(events.isEmpty())
        scene.updateTimers(1.seconds)

        assertEquals(listOf("body", "finally", "awaited"), events)
        assertFalse(target.isActive)
        assertFalse(waiter.isActive)
    }

    @Test
    fun `awaiting an already completed behaviour does not suspend again`() {
        val scene = RuntimeScene("game")
        var calls = 0
        val target = scene.launch { calls++ }
        scene.updateTimers(1.seconds)
        val waiter = scene.launch {
            target.await()
            target.await()
            calls++
        }

        scene.updateTimers(1.seconds)

        assertEquals(2, calls)
        assertFalse(waiter.isActive)
    }

    @Test
    fun `multiple waiters observe the same completion`() {
        val scene = RuntimeScene("game")
        val target = scene.launch { wait(1.seconds) }
        var completed = 0
        repeat(3) {
            scene.launch {
                target.await()
                completed++
            }
        }

        scene.updateTimers(1.seconds)
        assertEquals(0, completed)
        scene.updateTimers(1.seconds)
        assertEquals(3, completed)
    }

    @Test
    fun `active waiter can catch original target failure without engine reporting it again`() {
        val scene = RuntimeScene("game")
        val failure = IllegalStateException("target failed")
        val target = scene.launch {
            wait(Duration.ZERO)
            throw failure
        }
        var caught: Throwable? = null
        val waiter = scene.launch {
            try {
                target.await()
            } catch (cause: IllegalStateException) {
                caught = cause
            }
        }

        scene.updateTimers(1.seconds)
        scene.updateTimers(1.seconds)
        scene.updateTimers(1.seconds)

        assertSame(failure, caught)
        assertFalse(target.isActive)
        assertFalse(waiter.isActive)
    }

    @Test
    fun `uncaught awaited failure is reported once through the waiter`() {
        val scene = RuntimeScene("game")
        val failure = IllegalStateException("target failed")
        val target = scene.launch {
            wait(Duration.ZERO)
            throw failure
        }
        val waiter = scene.launch { target.await() }

        scene.updateTimers(1.seconds)
        assertSame(failure, assertFailsWith<IllegalStateException> { scene.updateTimers(1.seconds) })
        scene.updateTimers(1.seconds)

        assertFalse(target.isActive)
        assertFalse(waiter.isActive)
    }

    @Test
    fun `target cancellation reaches waiter after cleanup`() {
        val scene = RuntimeScene("game")
        var cleaned = false
        var caught = false
        val target = scene.launch {
            try {
                wait(10.seconds)
            } finally {
                cleaned = true
            }
        }
        scene.launch {
            try {
                target.await()
                error("Cancelled target must not complete successfully")
            } catch (_: CancellationException) {
                assertTrue(cleaned)
                caught = true
            }
        }

        scene.updateTimers(1.seconds)
        target.cancel()
        assertTrue(cleaned)
        scene.updateTimers(1.seconds)

        assertTrue(caught)
        assertFalse(target.isActive)
    }

    @Test
    fun `cancelling waiter does not cancel target or count as observing its failure`() {
        val scene = RuntimeScene("game")
        val failure = IllegalStateException("unobserved target failure")
        val target = scene.launch {
            wait(1.seconds)
            throw failure
        }
        val waiter = scene.launch { target.await() }

        scene.updateTimers(1.seconds)
        waiter.cancel()
        assertTrue(target.isActive)
        assertFalse(waiter.isActive)
        assertSame(failure, assertFailsWith<IllegalStateException> { scene.updateTimers(1.seconds) })
    }

    @Test
    fun `cancelled waiter does not resume success path when target completes`() {
        val scene = RuntimeScene("game")
        var targetCompleted = false
        var waiterCompleted = false
        val target = scene.launch {
            wait(1.seconds)
            targetCompleted = true
        }
        val waiter = scene.launch {
            target.await()
            waiterCompleted = true
        }

        scene.updateTimers(1.seconds)
        waiter.cancel()
        scene.updateTimers(1.seconds)

        assertTrue(targetCompleted)
        assertFalse(waiterCompleted)
    }

    @Test
    fun `await resumes only when waiting scene becomes active`() {
        val sourceScene = RuntimeScene("source")
        val waitingScene = RuntimeScene("waiting")
        var completed = false
        val target = sourceScene.launch { wait(Duration.ZERO) }
        waitingScene.launch {
            target.await()
            completed = true
        }

        sourceScene.updateTimers(1.seconds)
        waitingScene.updateTimers(1.seconds)
        sourceScene.updateTimers(1.seconds)
        assertFalse(completed)
        waitingScene.updateTimers(10.seconds) { false }
        assertFalse(completed)
        waitingScene.updateTimers(1.seconds)
        assertTrue(completed)
    }

    @Test
    fun `cancelling paused waiter does not hide an unobserved queued failure`() {
        val sourceScene = RuntimeScene("source")
        val waitingScene = RuntimeScene("waiting")
        val failure = IllegalStateException("unobserved failure")
        val target = sourceScene.launch {
            wait(Duration.ZERO)
            throw failure
        }
        val waiter = waitingScene.launch { target.await() }
        sourceScene.updateTimers(1.seconds)
        waitingScene.updateTimers(1.seconds)

        sourceScene.updateTimers(1.seconds)
        assertTrue(waiter.isActive)
        waiter.cancel()

        assertSame(failure, assertFailsWith<IllegalStateException> { sourceScene.updateTimers(1.seconds) })
        assertFalse(waiter.isActive)
    }

    @Test
    fun `await includes completion of structured children`() {
        val scene = RuntimeScene("game")
        val events = mutableListOf<String>()
        val target = scene.launch {
            coroutineScope {
                launch {
                    wait(1.seconds)
                    events += "child"
                }
            }
        }
        scene.launch {
            target.await()
            events += "awaited"
        }

        scene.updateTimers(1.seconds)
        assertTrue(events.isEmpty())
        scene.updateTimers(1.seconds)
        assertEquals(listOf("child", "awaited"), events)
    }

    @Test
    fun `self await and awaiting ancestor coroutine fail rather than deadlock`() {
        val scene = RuntimeScene("game")
        lateinit var direct: BehaviourHandle
        lateinit var ancestor: BehaviourHandle
        var rejected = 0
        direct = scene.launch {
            try {
                direct.await()
            } catch (_: IllegalStateException) {
                rejected++
            }
        }
        ancestor = scene.launch {
            try {
                coroutineScope { ancestor.await() }
            } catch (_: IllegalStateException) {
                rejected++
            }
        }

        scene.updateTimers(1.seconds)

        assertEquals(2, rejected)
        assertFalse(direct.isActive)
        assertFalse(ancestor.isActive)
    }
}
