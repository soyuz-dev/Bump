package org.soyuz.engine.behaviour

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.soyuz.engine.scene.RuntimeScene
import kotlin.coroutines.resume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CoroutineBehaviourTest {
    @Test
    fun `launch is deferred and waits advance only with subsequent simulation updates`() {
        val scene = RuntimeScene("game")
        val engineThread = Thread.currentThread()
        val events = mutableListOf<String>()
        val behaviour = scene.launch {
            assertSame(engineThread, Thread.currentThread())
            events += "start"
            wait(1.seconds)
            assertSame(engineThread, Thread.currentThread())
            events += "first"
            wait(500.milliseconds)
            events += "second"
        }

        assertTrue(behaviour.isActive)
        assertTrue(events.isEmpty())
        scene.updateTimers(Duration.ZERO)
        assertTrue(events.isEmpty())
        scene.updateTimers(100.milliseconds)
        assertEquals(listOf("start"), events)
        scene.updateTimers(400.milliseconds)
        assertEquals(listOf("start"), events)
        scene.updateTimers(600.milliseconds)
        assertEquals(listOf("start", "first"), events)
        scene.updateTimers(500.milliseconds)
        assertEquals(listOf("start", "first", "second"), events)
        assertFalse(behaviour.isActive)
    }

    @Test
    fun `zero wait yields until the next positive scene update`() {
        val scene = RuntimeScene("game")
        var steps = 0
        scene.launch {
            steps++
            wait(Duration.ZERO)
            steps++
            wait(Duration.ZERO)
            steps++
        }

        scene.updateTimers(1.seconds)
        assertEquals(1, steps)
        scene.updateTimers(Duration.ZERO)
        assertEquals(1, steps)
        scene.updateTimers(1.seconds)
        assertEquals(2, steps)
        scene.updateTimers(1.seconds)
        assertEquals(3, steps)
    }

    @Test
    fun `cancel before first update prevents body from starting`() {
        val scene = RuntimeScene("game")
        var calls = 0
        val behaviour = scene.launch { calls++ }

        behaviour.cancel()
        behaviour.cancel()
        scene.updateTimers(1.seconds)

        assertEquals(0, calls)
        assertFalse(behaviour.isActive)
    }

    @Test
    fun `cancellation removes pending waits and runs cleanup on the engine thread`() {
        val scene = RuntimeScene("game")
        val engineThread = Thread.currentThread()
        var resumed = false
        var cleaned = false
        val behaviour = scene.launch {
            assertSame(engineThread, Thread.currentThread())
            try {
                wait(10.seconds)
                resumed = true
            } finally {
                assertSame(engineThread, Thread.currentThread())
                cleaned = true
            }
        }

        scene.updateTimers(1.seconds)
        behaviour.cancel()

        assertTrue(cleaned)
        assertFalse(behaviour.isActive)
        scene.updateTimers(20.seconds)
        assertFalse(resumed)
    }

    @Test
    fun `scene inactivity preserves remaining wait time`() {
        val game = RuntimeScene("game")
        val menu = RuntimeScene("menu")
        var resumed = false
        game.launch {
            wait(1.seconds)
            resumed = true
        }

        game.updateTimers(1.milliseconds)
        game.updateTimers(400.milliseconds)
        menu.updateTimers(30.seconds)
        game.updateTimers(30.seconds) { false }
        assertFalse(resumed)
        game.updateTimers(600.milliseconds)
        assertTrue(resumed)
    }

    @Test
    fun `resumptions queued outside update wait for scene activation`() {
        val scene = RuntimeScene("game")
        lateinit var continuation: CancellableContinuation<Unit>
        var resumed = false
        scene.launch {
            suspendCancellableCoroutine<Unit> { continuation = it }
            resumed = true
        }

        scene.updateTimers(1.seconds)
        continuation.resume(Unit)
        assertFalse(resumed)
        scene.updateTimers(1.seconds) { false }
        assertFalse(resumed)
        scene.updateTimers(1.seconds)
        assertTrue(resumed)
    }

    @Test
    fun `wait created by queued resumption does not count time before it resumed`() {
        val scene = RuntimeScene("game")
        lateinit var continuation: CancellableContinuation<Unit>
        var completed = false
        scene.launch {
            suspendCancellableCoroutine<Unit> { continuation = it }
            wait(1.seconds)
            completed = true
        }

        scene.updateTimers(1.milliseconds)
        continuation.resume(Unit)
        scene.updateTimers(1.seconds)
        assertFalse(completed)
        scene.updateTimers(1.seconds)
        assertTrue(completed)
    }

    @Test
    fun `cleanup cancels queued resumption without executing its success path`() {
        val scene = RuntimeScene("game")
        lateinit var continuation: CancellableContinuation<Unit>
        var resumed = false
        var cleaned = false
        val behaviour = scene.launch {
            try {
                suspendCancellableCoroutine<Unit> { continuation = it }
                resumed = true
            } finally {
                cleaned = true
            }
        }

        scene.updateTimers(1.seconds)
        continuation.resume(Unit)
        scene.cleanup()

        assertFalse(resumed)
        assertTrue(cleaned)
        assertFalse(behaviour.isActive)
    }

    @Test
    fun `scene cleanup cancels suspended and unstarted behaviours`() {
        val scene = RuntimeScene("game")
        var cleaned = false
        var pendingRan = false
        val suspended = scene.launch {
            try {
                wait(10.seconds)
            } finally {
                cleaned = true
            }
        }
        scene.updateTimers(1.seconds)
        val pending = scene.launch { pendingRan = true }

        scene.cleanup()
        scene.cleanup()
        scene.updateTimers(20.seconds)

        assertTrue(cleaned)
        assertFalse(pendingRan)
        assertFalse(suspended.isActive)
        assertFalse(pending.isActive)
    }

    @Test
    fun `behaviour launched inside a timer callback waits until next update`() {
        val scene = RuntimeScene("game")
        var calls = 0
        scene.after(Duration.ZERO) { scene.launch { calls++ } }

        scene.updateTimers(1.seconds)
        assertEquals(0, calls)
        scene.updateTimers(1.seconds)
        assertEquals(1, calls)
    }

    @Test
    fun `scene switch during a resumed behaviour stops later callbacks`() {
        val scene = RuntimeScene("game")
        var active = true
        var laterCalls = 0
        scene.launch {
            wait(1.seconds)
            active = false
        }
        scene.updateTimers(1.milliseconds)
        scene.after(Duration.ZERO) { laterCalls++ }

        scene.updateTimers(1.seconds) { active }

        assertFalse(active)
        assertEquals(0, laterCalls)
    }

    @Test
    fun `failures propagate from update and do not cancel sibling behaviours`() {
        val scene = RuntimeScene("game")
        val failure = IllegalStateException("behaviour failed")
        val failed = scene.launch {
            wait(Duration.ZERO)
            throw failure
        }
        var siblingCompleted = false
        val sibling = scene.launch {
            wait(1.seconds)
            siblingCompleted = true
        }

        scene.updateTimers(1.milliseconds)
        assertSame(failure, assertFailsWith<IllegalStateException> { scene.updateTimers(1.seconds) })
        assertFalse(failed.isActive)
        assertTrue(sibling.isActive)
        scene.updateTimers(1.seconds)
        assertTrue(siblingCompleted)
        assertFalse(sibling.isActive)
    }

    @Test
    fun `cancelling a behaviour also cancels its structured child coroutines`() {
        val scene = RuntimeScene("game")
        var childCleaned = false
        var parentCleaned = false
        val behaviour = scene.launch {
            try {
                coroutineScope {
                    launch {
                        try {
                            wait(10.seconds)
                        } finally {
                            childCleaned = true
                        }
                    }
                    wait(10.seconds)
                }
            } finally {
                parentCleaned = true
            }
        }

        scene.updateTimers(1.seconds)
        behaviour.cancel()

        assertTrue(childCleaned)
        assertTrue(parentCleaned)
        assertFalse(behaviour.isActive)
    }

    @Test
    fun `invalid waits fail in the update that runs the behaviour`() {
        for (invalid in listOf((-1).seconds, Duration.INFINITE, -Duration.INFINITE)) {
            val scene = RuntimeScene("game")
            val behaviour = scene.launch { wait(invalid) }

            assertFailsWith<IllegalArgumentException> { scene.updateTimers(1.seconds) }
            assertFalse(behaviour.isActive)
        }
    }

    @Test
    fun `cancellation exception terminates behaviour without becoming an engine failure`() {
        val scene = RuntimeScene("game")
        val behaviour = scene.launch { throw CancellationException("done") }

        scene.updateTimers(1.seconds)

        assertFalse(behaviour.isActive)
    }
}
