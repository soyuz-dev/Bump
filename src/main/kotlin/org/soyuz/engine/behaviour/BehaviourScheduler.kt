package org.soyuz.engine.behaviour

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.soyuz.engine.timing.TimerScheduler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration

internal class BehaviourScheduler(private val timers: TimerScheduler) {
    private class Registration(val owner: Any?) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Registration>
        lateinit var job: Job
        val completion = BehaviourCompletion()
    }

    private class Task(val context: CoroutineContext, val block: Runnable)
    private class Failure(val completion: BehaviourCompletion?, val cause: Throwable)

    private val tasks = ConcurrentLinkedDeque<Task>()
    private val cancellations = ConcurrentLinkedQueue<() -> Unit>()
    private val failures = ConcurrentLinkedQueue<Failure>()
    private val registrations = ConcurrentHashMap<Job, Registration>()
    @Volatile
    private var thread: Thread? = null
    private var updating = false
    private var clearing = false
    private var shouldContinue: () -> Boolean = { false }

    private val dispatcher = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext): Boolean {
            if (Thread.currentThread() !== thread) return true
            val cancelled = context[Registration]?.job?.isCancelled == true || context[Job]?.isCancelled == true
            return !cancelled && !(updating && shouldContinue())
        }

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(Task(context, block))
        }
    }

    private val scope = CoroutineScope(
        SupervisorJob() + dispatcher + CoroutineExceptionHandler { context, failure ->
            failures.add(Failure(context[Registration]?.completion, failure))
        }
    )

    fun launch(owner: Any?, block: suspend BehaviourScope.() -> Unit): BehaviourHandle {
        checkThread()
        check(!clearing) { "Cannot launch behaviour during cleanup." }
        val registration = Registration(owner)
        val job = scope.launch(registration, start = CoroutineStart.LAZY) {
            block(BehaviourScope(
                waitFor = { duration -> wait(duration, registration) },
                runUntil = { duration, callback -> until(duration, registration, callback) }
            ))
        }
        registration.job = job
        registrations[job] = registration
        val start = timers.after(Duration.ZERO, owner) {
            job.start()
            throwFailure()
        }
        job.invokeOnCompletion { cause ->
            registration.completion.complete(cause)
            registrations.remove(job)
            cancelOnEngineThread { start.cancel() }
        }
        return CoroutineBehaviourHandle(job, registration.completion) {
            checkThread()
            job.cancel()
            drainCancelledTasks()
        }
    }

    private suspend fun wait(duration: Duration, registration: Registration) {
        checkThread()
        registration.job.ensureActive()
        currentCoroutineContext().ensureActive()
        require(duration.isFinite() && duration >= Duration.ZERO) {
            "Wait duration must be finite and non-negative."
        }
        suspendCancellableCoroutine<Unit> { continuation ->
            val timer = timers.after(duration, registration.owner) {
                continuation.resume(Unit)
                throwFailure()
            }
            continuation.invokeOnCancellation {
                cancelOnEngineThread { timer.cancel() }
            }
        }
    }

    private suspend fun until(
        duration: Duration,
        registration: Registration,
        callback: (Double) -> Unit
    ) {
        checkThread()
        registration.job.ensureActive()
        currentCoroutineContext().ensureActive()
        require(duration.isFinite() && duration > Duration.ZERO) {
            "Until duration must be finite and positive."
        }
        val result = suspendCancellableCoroutine<Result<Unit>> { continuation ->
            lateinit var timer: Subscription
            timer = timers.during(duration, registration.owner) { progress ->
                if (!continuation.isActive) return@during
                val completed = !timer.isActive
                try {
                    callback(if (completed) 1.0 else progress.coerceAtMost(Math.nextDown(1.0)))
                } catch (failure: Throwable) {
                    timer.cancel()
                    if (!continuation.isActive) throw failure
                    continuation.resume(Result.failure(failure))
                    throwFailure()
                    return@during
                }
                if (completed && continuation.isActive) {
                    continuation.resume(Result.success(Unit))
                    throwFailure()
                }
            }
            continuation.invokeOnCancellation {
                cancelOnEngineThread { timer.cancel() }
            }
        }
        result.getOrThrow()
    }

    fun update(elapsed: Duration, shouldContinue: () -> Boolean) {
        require(elapsed.isFinite() && elapsed >= Duration.ZERO) {
            "Elapsed time must be finite and non-negative."
        }
        checkThread()
        check(!updating) { "BehaviourScheduler.update cannot be called recursively." }
        thread = Thread.currentThread()
        drainCancellations()
        drainCancelledTasks()
        if (elapsed == Duration.ZERO || !shouldContinue()) return

        updating = true
        this.shouldContinue = shouldContinue
        try {
            throwFailure()
            val pending = tasks.toList()
            timers.update(elapsed, shouldContinue)
            for (task in pending) {
                if (!shouldContinue()) return
                if (tasks.remove(task)) task.block.run()
                throwFailure()
            }
            throwFailure()
        } finally {
            updating = false
            this.shouldContinue = { false }
        }
    }

    fun cancelOwnedBy(owner: Any) {
        checkThread()
        registrations.entries.toList()
            .filter { it.value.owner === owner }
            .forEach { it.key.cancel() }
        drainCancelledTasks()
        drainCancellations()
    }

    fun clear() {
        checkThread()
        clearing = true
        try {
            registrations.keys.toList().forEach { it.cancel() }
            drainCancelledTasks()
            drainCancellations()
        } finally {
            clearing = false
        }
    }

    private fun checkThread() {
        check(thread == null || Thread.currentThread() === thread) {
            "Behaviour must be managed on the engine thread."
        }
    }

    private fun cancelOnEngineThread(action: () -> Unit) {
        if (thread == null || Thread.currentThread() === thread) action()
        else cancellations.add(action)
    }

    private fun drainCancellations() {
        while (true) {
            val action = cancellations.poll() ?: return
            action()
        }
    }

    private fun drainCancelledTasks() {
        while (true) {
            val cancelled = tasks.filter {
                it.context[Registration]?.job?.isCancelled == true || it.context[Job]?.isCancelled == true
            }
            if (cancelled.isEmpty()) return
            cancelled.forEach { task ->
                if (tasks.remove(task)) task.block.run()
            }
        }
    }

    private fun throwFailure() {
        repeat(failures.size) {
            val failure = failures.poll() ?: return
            val completion = failure.completion
            if (completion?.failureObserved == true) return@repeat
            if (completion?.hasWaiters == true) failures.add(failure)
            else throw failure.cause
        }
    }
}
