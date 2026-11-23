package org.soyuz.engine.timing

import org.soyuz.engine.behaviour.CallbackSubscription
import org.soyuz.engine.behaviour.Subscription
import kotlin.time.Duration
import kotlin.time.DurationUnit

class TimerScheduler {
    private enum class TimerType { INTERVAL, ONE_SHOT, DURING, FOREVER }

    private class Timer(
        val interval: Duration,
        val type: TimerType,
        val owner: Any?,
        val subscription: Subscription,
        val callback: (Double) -> Unit,
        var accumulator: Duration = Duration.ZERO
    )

    private val timers = mutableListOf<Timer>()
    private val pendingTimers = mutableListOf<Timer>()
    private var updating = false

    fun after(delay: Duration, callback: () -> Unit): Subscription =
        after(delay, null, callback)

    internal fun after(delay: Duration, owner: Any?, callback: () -> Unit): Subscription {
        require(delay.isFinite() && delay >= Duration.ZERO) {
            "Timer delay must be finite and non-negative."
        }
        return register(delay, TimerType.ONE_SHOT, owner) { callback() }
    }

    fun during(duration: Duration, callback: (progress: Double) -> Unit): Subscription =
        during(duration, null, callback)

    internal fun during(duration: Duration, owner: Any?, callback: (Double) -> Unit): Subscription {
        require(duration.isFinite() && duration > Duration.ZERO) {
            "Timer duration must be finite and positive."
        }
        return register(duration, TimerType.DURING, owner, callback)
    }

    fun forEvery(interval: Duration, callback: () -> Unit): Subscription =
        forEvery(interval, null, callback)

    internal fun forEvery(interval: Duration, owner: Any?, callback: () -> Unit): Subscription {
        require(interval.isFinite() && interval > Duration.ZERO) {
            "Timer interval must be finite and positive."
        }
        return register(interval, TimerType.INTERVAL, owner) { callback() }
    }

    fun everyFrame(callback: (dt: Double) -> Unit): Subscription =
        everyFrame(null, callback)

    internal fun everyFrame(owner: Any?, callback: (Double) -> Unit): Subscription =
        register(Duration.ZERO, TimerType.FOREVER, owner, callback)

    private fun register(
        interval: Duration,
        type: TimerType,
        owner: Any?,
        callback: (Double) -> Unit
    ): Subscription {
        val subscription = CallbackSubscription { cancelled ->
            timers.removeAll { it.subscription === cancelled }
            pendingTimers.removeAll { it.subscription === cancelled }
        }
        pendingTimers += Timer(interval, type, owner, subscription, callback)
        return subscription
    }

    /**
     * Advances timers by [elapsed]. Zero leaves timers and pending registrations
     * untouched. Interval timers catch up once per elapsed interval.
     *
     * A callback failure cancels that timer and propagates to the caller; later
     * timers are not advanced in that update. Recursive updates are unsupported.
     */
    fun update(elapsed: Duration, shouldContinue: () -> Boolean = { true }) {
        require(elapsed.isFinite() && elapsed >= Duration.ZERO) {
            "Elapsed time must be finite and non-negative."
        }
        check(!updating) { "TimerScheduler.update cannot be called recursively." }
        if (elapsed == Duration.ZERO || !shouldContinue()) return

        updating = true
        try {
            timers.addAll(pendingTimers)
            pendingTimers.clear()

            // Callbacks can cancel timers or clear the scheduler while iterating.
            for (timer in timers.toList()) {
                if (!shouldContinue()) break
                if (!timer.subscription.isActive) continue

                timer.accumulator += elapsed
                try {
                    when (timer.type) {
                        TimerType.INTERVAL -> {
                            while (timer.subscription.isActive && timer.accumulator >= timer.interval && shouldContinue()) {
                                timer.accumulator -= timer.interval
                                timer.callback(0.0)
                            }
                        }
                        TimerType.ONE_SHOT -> {
                            if (timer.accumulator >= timer.interval) {
                                timer.subscription.cancel()
                                timer.callback(1.0)
                            }
                        }
                        TimerType.DURING -> {
                            val progress = (timer.accumulator / timer.interval).coerceIn(0.0, 1.0)
                            if (timer.accumulator >= timer.interval) timer.subscription.cancel()
                            timer.callback(progress)
                        }
                        TimerType.FOREVER -> timer.callback(elapsed.toDouble(DurationUnit.SECONDS))
                    }
                } catch (failure: Throwable) {
                    timer.subscription.cancel()
                    throw failure
                }
            }
        } finally {
            updating = false
        }
    }

    internal fun cancelOwnedBy(owner: Any) {
        (timers + pendingTimers)
            .filter { it.owner === owner }
            .forEach { it.subscription.cancel() }
    }

    /** Cancels active and pending timers. The scheduler can be reused afterward. */
    fun clear() {
        (timers + pendingTimers).forEach { it.subscription.cancel() }
    }
}
