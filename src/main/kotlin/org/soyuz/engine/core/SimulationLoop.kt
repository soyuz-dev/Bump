package org.soyuz.engine.core

import org.soyuz.engine.timing.TimerScheduler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class SimulationLoop(private val physicsTimeStep: Double) {
    val frames = TimerScheduler()
    val timers = TimerScheduler()
    var isPaused = false
        private set

    private var revision = 0L
    private var physicsAccumulator = 0.0

    init {
        require(physicsTimeStep.isFinite() && physicsTimeStep > 0.0)
    }

    fun pause() {
        if (isPaused) return
        isPaused = true
        revision++
        physicsAccumulator %= physicsTimeStep
    }

    fun resume() {
        if (!isPaused) return
        isPaused = false
        revision++
    }

    fun togglePause() {
        if (isPaused) resume() else pause()
    }

    fun update(
        dt: Float,
        isRunning: () -> Boolean,
        isActive: () -> Boolean,
        dispatchInput: (Double, () -> Boolean) -> Unit,
        stepPhysics: (Double) -> Unit,
        updateUI: () -> Unit,
        updateDynamics: (Float, () -> Boolean) -> Unit,
        updateSceneTimers: (Duration, () -> Boolean) -> Unit
    ) {
        if (dt <= 0f || !dt.isFinite() || !isRunning()) return
        val frameRevision = revision
        val elapsed = dt.toDouble().seconds
        frames.update(elapsed, isRunning)
        val shouldContinue = {
            !isPaused && revision == frameRevision && isRunning() && isActive()
        }
        if (!shouldContinue()) return

        dispatchInput(dt.toDouble(), shouldContinue)
        if (!shouldContinue()) return

        physicsAccumulator += dt.toDouble()
        while (physicsAccumulator >= physicsTimeStep) {
            if (!shouldContinue()) {
                physicsAccumulator %= physicsTimeStep
                return
            }
            physicsAccumulator -= physicsTimeStep
            stepPhysics(physicsTimeStep)
        }
        if (!shouldContinue()) return

        updateUI()
        if (!shouldContinue()) return
        updateDynamics(dt, shouldContinue)
        if (!shouldContinue()) return
        timers.update(elapsed, shouldContinue)
        if (!shouldContinue()) return
        updateSceneTimers(elapsed, shouldContinue)
    }

    fun clear() {
        frames.clear()
        timers.clear()
    }
}
