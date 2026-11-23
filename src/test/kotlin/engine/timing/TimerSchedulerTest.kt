package org.soyuz.engine.timing

import org.soyuz.engine.behaviour.Subscription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TimerSchedulerTest {
    @Test
    fun `interrupted interval retains its remainder for a later update`() {
        val scheduler = TimerScheduler()
        var continueUpdate = true
        var calls = 0
        scheduler.forEvery(100.milliseconds) {
            calls++
            if (calls == 1) continueUpdate = false
        }

        scheduler.update(350.milliseconds) { continueUpdate }
        assertEquals(1, calls)
        continueUpdate = true
        scheduler.update(50.milliseconds) { continueUpdate }
        assertEquals(4, calls)
    }

    @Test
    fun `interrupted update leaves later timers elapsed time untouched`() {
        val scheduler = TimerScheduler()
        var continueUpdate = true
        var laterCalls = 0
        scheduler.after(Duration.ZERO) { continueUpdate = false }
        val later = scheduler.after(1.seconds) { laterCalls++ }

        scheduler.update(1.seconds) { continueUpdate }
        assertEquals(0, laterCalls)
        assertTrue(later.isActive)
        scheduler.update(500.milliseconds)
        assertEquals(0, laterCalls)
        scheduler.update(500.milliseconds)
        assertEquals(1, laterCalls)
    }

    @Test
    fun `one shot accumulates from first update and completes once`() {
        val scheduler = TimerScheduler()
        var calls = 0
        val timer = scheduler.after(1.seconds) { calls++ }

        scheduler.update(400.milliseconds)
        assertEquals(0, calls)
        assertTrue(timer.isActive)
        scheduler.update(600.milliseconds)
        assertEquals(1, calls)
        assertFalse(timer.isActive)
        scheduler.update(1.seconds)
        assertEquals(1, calls)
    }

    @Test
    fun `during reports progress and a single final value even on overshoot`() {
        val scheduler = TimerScheduler()
        val progress = mutableListOf<Double>()
        val timer = scheduler.during(1.seconds) { progress += it }

        scheduler.update(250.milliseconds)
        scheduler.update(250.milliseconds)
        scheduler.update(1.seconds)
        scheduler.update(1.seconds)

        assertEquals(listOf(0.25, 0.5, 1.0), progress)
        assertFalse(timer.isActive)
    }

    @Test
    fun `interval catches up and preserves remainder`() {
        val scheduler = TimerScheduler()
        var calls = 0
        val timer = scheduler.forEvery(100.milliseconds) { calls++ }

        scheduler.update(350.milliseconds)
        assertEquals(3, calls)
        scheduler.update(50.milliseconds)
        assertEquals(4, calls)
        assertTrue(timer.isActive)
    }

    @Test
    fun `every frame receives seconds and can be cancelled`() {
        val scheduler = TimerScheduler()
        val deltas = mutableListOf<Double>()
        val timer = scheduler.everyFrame { deltas += it }

        scheduler.update(250.milliseconds)
        scheduler.update(500.milliseconds)
        timer.cancel()
        scheduler.update(1.seconds)

        assertEquals(listOf(0.25, 0.5), deltas)
        assertFalse(timer.isActive)
    }

    @Test
    fun `all timer kinds can be cancelled before registration`() {
        val scheduler = TimerScheduler()
        var calls = 0
        val timers = listOf(
            scheduler.after(Duration.ZERO) { calls++ },
            scheduler.during(1.seconds) { calls++ },
            scheduler.forEvery(1.seconds) { calls++ },
            scheduler.everyFrame { calls++ }
        )

        timers.forEach { it.cancel(); it.cancel() }
        scheduler.update(2.seconds)

        assertEquals(0, calls)
        assertTrue(timers.none { it.isActive })
    }

    @Test
    fun `interval can cancel itself during catch up`() {
        val scheduler = TimerScheduler()
        var calls = 0
        lateinit var timer: Subscription
        timer = scheduler.forEvery(100.milliseconds) {
            calls++
            timer.cancel()
        }

        scheduler.update(1.seconds)
        scheduler.update(1.seconds)

        assertEquals(1, calls)
        assertFalse(timer.isActive)
    }

    @Test
    fun `callback can cancel a later timer in the same update`() {
        val scheduler = TimerScheduler()
        var calls = 0
        lateinit var later: Subscription
        scheduler.after(Duration.ZERO) { later.cancel() }
        later = scheduler.everyFrame { calls++ }

        scheduler.update(1.seconds)

        assertEquals(0, calls)
        assertFalse(later.isActive)
    }

    @Test
    fun `all timer kinds registered inside a callback wait until next update`() {
        val scheduler = TimerScheduler()
        val calls = mutableListOf<String>()
        scheduler.after(Duration.ZERO) {
            scheduler.after(Duration.ZERO) { calls += "after" }
            scheduler.during(1.seconds) { calls += "during" }
            scheduler.forEvery(1.seconds) { calls += "interval" }
            scheduler.everyFrame { calls += "frame" }
        }

        scheduler.update(1.seconds)
        assertTrue(calls.isEmpty())
        scheduler.update(1.seconds)
        assertEquals(listOf("after", "during", "interval", "frame"), calls)
    }

    @Test
    fun `clear from callback cancels active and pending timers and scheduler stays reusable`() {
        val scheduler = TimerScheduler()
        var calls = 0
        lateinit var pending: Subscription
        val first = scheduler.everyFrame {
            pending = scheduler.after(Duration.ZERO) { calls++ }
            scheduler.clear()
        }
        val later = scheduler.everyFrame { calls++ }

        scheduler.update(1.seconds)
        scheduler.update(1.seconds)

        assertEquals(0, calls)
        assertFalse(first.isActive)
        assertFalse(later.isActive)
        assertFalse(pending.isActive)

        scheduler.after(Duration.ZERO) { calls++ }
        scheduler.update(1.seconds)
        assertEquals(1, calls)
    }

    @Test
    fun `clear also cancels timers before the first update`() {
        val scheduler = TimerScheduler()
        var calls = 0
        val timer = scheduler.after(Duration.ZERO) { calls++ }

        scheduler.clear()
        scheduler.clear()
        scheduler.update(1.seconds)

        assertEquals(0, calls)
        assertFalse(timer.isActive)
    }

    @Test
    fun `zero elapsed does not fire zero delay or frame callbacks`() {
        val scheduler = TimerScheduler()
        var calls = 0
        scheduler.after(Duration.ZERO) { calls++ }
        scheduler.everyFrame { calls++ }

        assertEquals(0, calls)
        scheduler.update(Duration.ZERO)
        assertEquals(0, calls)
        scheduler.update(1.milliseconds)
        assertEquals(2, calls)
    }

    @Test
    fun `invalid durations fail at registration and invalid updates do not advance timers`() {
        val scheduler = TimerScheduler()
        for (invalid in listOf((-1).seconds, Duration.INFINITE, -Duration.INFINITE)) {
            assertFailsWith<IllegalArgumentException> { scheduler.after(invalid) {} }
            assertFailsWith<IllegalArgumentException> { scheduler.during(invalid) {} }
            assertFailsWith<IllegalArgumentException> { scheduler.forEvery(invalid) {} }
            assertFailsWith<IllegalArgumentException> { scheduler.update(invalid) }
        }
        assertFailsWith<IllegalArgumentException> { scheduler.during(Duration.ZERO) {} }
        assertFailsWith<IllegalArgumentException> { scheduler.forEvery(Duration.ZERO) {} }

        var calls = 0
        scheduler.after(1.seconds) { calls++ }
        assertFailsWith<IllegalArgumentException> { scheduler.update((-1).seconds) }
        scheduler.update(500.milliseconds)
        assertEquals(0, calls)
        scheduler.update(500.milliseconds)
        assertEquals(1, calls)
    }

    @Test
    fun `callback failure cancels failed timer and leaves scheduler usable`() {
        val scheduler = TimerScheduler()
        val failure = IllegalStateException("callback failed")
        val failed = scheduler.everyFrame { throw failure }
        var calls = 0
        scheduler.after(1.seconds) { calls++ }

        assertSame(failure, assertFailsWith<IllegalStateException> { scheduler.update(1.seconds) })
        assertFalse(failed.isActive)
        assertEquals(0, calls)
        scheduler.update(1.seconds)
        assertEquals(1, calls)
    }

    @Test
    fun `recursive updates are rejected without corrupting later updates`() {
        val scheduler = TimerScheduler()
        val recursive = scheduler.everyFrame { scheduler.update(1.seconds) }

        assertFailsWith<IllegalStateException> { scheduler.update(1.seconds) }
        assertFalse(recursive.isActive)

        var calls = 0
        scheduler.after(Duration.ZERO) { calls++ }
        scheduler.update(1.seconds)
        assertEquals(1, calls)
    }
}
