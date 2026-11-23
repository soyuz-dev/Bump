package org.soyuz.engine.core

import org.soyuz.engine.behaviour.SceneKeyboardDispatcher
import org.soyuz.engine.scene.RuntimeScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SimulationPauseTest {
    private class Harness {
        val loop = SimulationLoop(0.125)
        val scene = RuntimeScene("game")
        var running = true
        var active = true
        var inputCalls = 0
        var physicsSteps = 0
        var uiCalls = 0
        var dynamicsCalls = 0
        var input: (Double, () -> Boolean) -> Unit = { _, _ -> }
        var physics: () -> Unit = {}
        var ui: () -> Unit = {}
        var dynamics: () -> Unit = {}

        fun update(dt: Float = 0.125f) {
            loop.update(
                dt = dt,
                isRunning = { running },
                isActive = { active },
                dispatchInput = { delta, shouldContinue ->
                    inputCalls++
                    input(delta, shouldContinue)
                },
                stepPhysics = {
                    physicsSteps++
                    physics()
                },
                updateUI = {
                    uiCalls++
                    ui()
                },
                updateDynamics = { _, _ ->
                    dynamicsCalls++
                    dynamics()
                },
                updateSceneTimers = { elapsed, shouldContinue -> scene.updateTimers(elapsed, shouldContinue) }
            )
        }
    }

    @Test
    fun `paused frames run frame callbacks and freeze every simulation phase`() {
        val game = Harness()
        var frames = 0
        var engineCalls = 0
        var sceneCalls = 0
        game.loop.frames.everyFrame { frames++ }
        game.loop.timers.everyFrame { engineCalls++ }
        game.scene.everyFrame { sceneCalls++ }

        game.loop.pause()
        game.update(10f)
        game.update(10f)

        assertEquals(2, frames)
        assertEquals(0, engineCalls)
        assertEquals(0, sceneCalls)
        assertEquals(0, game.inputCalls)
        assertEquals(0, game.physicsSteps)
        assertEquals(0, game.uiCalls)
        assertEquals(0, game.dynamicsCalls)
    }

    @Test
    fun `engine and scene delays retain remaining time without paused catch up`() {
        val game = Harness()
        var engineCalls = 0
        var sceneCalls = 0
        game.loop.timers.after(1.seconds) { engineCalls++ }
        game.scene.after(1.seconds) { sceneCalls++ }
        game.update(0.25f)

        game.loop.pause()
        game.update(120f)
        game.loop.resume()
        game.update(0.5f)
        assertEquals(0, engineCalls)
        assertEquals(0, sceneCalls)
        game.update(0.25f)

        assertEquals(1, engineCalls)
        assertEquals(1, sceneCalls)
    }

    @Test
    fun `physics preserves fractional step and never accumulates paused wall time`() {
        val game = Harness()
        game.update(0.0625f)
        assertEquals(0, game.physicsSteps)

        game.loop.pause()
        game.loop.pause()
        game.update(120f)
        game.loop.resume()
        game.loop.resume()
        game.update(0.0625f)

        assertEquals(1, game.physicsSteps)
        assertEquals(2, game.inputCalls)
        assertEquals(2, game.uiCalls)
        assertEquals(2, game.dynamicsCalls)
    }

    @Test
    fun `frame callback can resume simulation without advancing the paused frame delta`() {
        val game = Harness()
        var resume = true
        game.loop.pause()
        game.loop.frames.everyFrame {
            if (resume) {
                game.loop.resume()
                resume = false
            }
        }

        game.update(120f)
        assertFalse(game.loop.isPaused)
        assertEquals(0, game.physicsSteps)
        assertEquals(0, game.inputCalls)
        game.update()
        assertEquals(1, game.physicsSteps)
    }

    @Test
    fun `frame callback can pause before gameplay input or timers run`() {
        val game = Harness()
        game.loop.frames.everyFrame { game.loop.pause() }
        var timers = 0
        game.loop.timers.after(Duration.ZERO) { timers++ }

        game.update()

        assertTrue(game.loop.isPaused)
        assertEquals(0, game.inputCalls)
        assertEquals(0, game.physicsSteps)
        assertEquals(0, timers)
    }

    @Test
    fun `pause in input stops remaining keyboard handlers and simulation work`() {
        val game = Harness()
        val keyboard = SceneKeyboardDispatcher()
        var laterCalls = 0
        keyboard.onKeyDown(80) { game.loop.pause() }
        keyboard.onKeyDown(80) { laterCalls++ }
        game.input = { delta, shouldContinue ->
            keyboard.dispatch(delta, { false }, { true }, { false }, shouldContinue)
        }

        game.update()

        assertEquals(0, laterCalls)
        assertEquals(0, game.physicsSteps)
        assertEquals(0, game.uiCalls)
    }

    @Test
    fun `pause during physics finishes current step and discards remaining catch up steps`() {
        val game = Harness()
        game.physics = { game.loop.pause() }

        game.update(1f)

        assertEquals(1, game.physicsSteps)
        assertEquals(0, game.uiCalls)
        game.physics = {}
        game.loop.resume()
        game.update()
        assertEquals(2, game.physicsSteps)
    }

    @Test
    fun `pause during UI or dynamics prevents later simulation phases`() {
        for (pauseInUI in listOf(true, false)) {
            val game = Harness()
            var timers = 0
            game.loop.timers.after(Duration.ZERO) { timers++ }
            if (pauseInUI) game.ui = { game.loop.pause() }
            else game.dynamics = { game.loop.pause() }

            game.update()

            assertEquals(if (pauseInUI) 0 else 1, game.dynamicsCalls)
            assertEquals(0, timers)
        }
    }

    @Test
    fun `transient pause and resume stops interval catch up and invalidates rest of frame`() {
        val game = Harness()
        var calls = 0
        var sceneCalls = 0
        val timer = game.loop.timers.forEvery(125.milliseconds) {
            calls++
            game.loop.pause()
            game.loop.resume()
        }
        game.scene.everyFrame { sceneCalls++ }

        game.update(0.5f)

        assertEquals(1, calls)
        assertEquals(0, sceneCalls)
        assertFalse(game.loop.isPaused)
        timer.cancel()
        game.update()
        assertEquals(1, sceneCalls)
    }

    @Test
    fun `pause inside scene timer stops later scene callbacks`() {
        val game = Harness()
        var calls = 0
        val timer = game.scene.forEvery(125.milliseconds) { game.loop.pause() }
        game.scene.everyFrame { calls++ }

        game.update()
        assertEquals(0, calls)
        timer.cancel()
        game.loop.resume()
        game.update()
        assertEquals(1, calls)
    }

    @Test
    fun `coroutine waits and until preserve time and progress during pause`() {
        val game = Harness()
        var completed = false
        val progress = mutableListOf<Double>()
        game.scene.launch {
            wait(500.milliseconds)
            completed = true
        }
        game.scene.launch { until(500.milliseconds) { progress += it } }
        game.update()
        game.update(0.25f)
        assertEquals(listOf(0.5), progress)

        game.loop.pause()
        game.update(120f)
        game.loop.resume()
        game.update()
        assertFalse(completed)
        assertEquals(listOf(0.5, 0.75), progress)
        game.update()
        assertTrue(completed)
        assertEquals(listOf(0.5, 0.75, 1.0), progress)
    }

    @Test
    fun `awaited completion stays queued while simulation is paused`() {
        val game = Harness()
        val source = RuntimeScene("source")
        val target = source.launch { wait(Duration.ZERO) }
        var resumed = false
        game.scene.launch {
            target.await()
            resumed = true
        }
        source.updateTimers(125.milliseconds)
        game.update()

        game.loop.pause()
        source.updateTimers(125.milliseconds)
        game.update(10f)
        assertFalse(resumed)
        game.loop.resume()
        game.update()
        assertTrue(resumed)
    }

    @Test
    fun `pause during final until callback defers following coroutine code`() {
        for (resumeImmediately in listOf(false, true)) {
            val game = Harness()
            var continued = false
            game.scene.launch {
                until(500.milliseconds) {
                    if (it == 1.0) {
                        game.loop.pause()
                        if (resumeImmediately) game.loop.resume()
                    }
                }
                continued = true
            }
            game.update()
            game.update(0.5f)
            assertFalse(continued)

            if (!resumeImmediately) {
                game.update(120f)
                assertFalse(continued)
                game.loop.resume()
            }
            game.update()
            assertTrue(continued)
        }
    }

    @Test
    fun `frame callback can cancel suspended behaviour while simulation remains paused`() {
        val game = Harness()
        var cleaned = false
        val behaviour = game.scene.launch {
            try {
                wait(10.seconds)
            } finally {
                cleaned = true
            }
        }
        game.update()
        game.loop.pause()
        game.loop.frames.everyFrame { behaviour.cancel() }

        game.update()

        assertTrue(cleaned)
        assertFalse(behaviour.isActive)
        assertTrue(game.loop.isPaused)
    }

    @Test
    fun `pause is local to a simulation and other windows can keep advancing`() {
        val paused = Harness()
        val running = Harness()
        var pausedCalls = 0
        var runningCalls = 0
        paused.scene.after(125.milliseconds) { pausedCalls++ }
        running.scene.after(125.milliseconds) { runningCalls++ }
        paused.loop.pause()

        paused.update()
        running.update()

        assertEquals(0, pausedCalls)
        assertEquals(1, runningCalls)
    }

    @Test
    fun `stopped engine does not dispatch frame controls and clear cancels both clocks`() {
        val game = Harness()
        var calls = 0
        val frame = game.loop.frames.everyFrame { calls++ }
        val timer = game.loop.timers.after(Duration.ZERO) { calls++ }
        game.running = false

        game.update()
        assertEquals(0, calls)
        game.loop.clear()
        assertFalse(frame.isActive)
        assertFalse(timer.isActive)
    }
}
