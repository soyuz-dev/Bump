package org.soyuz.engine.behaviour

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList

sealed class BehaviourHandle : Subscription {
    internal abstract val completion: BehaviourCompletion
    internal abstract val job: Job?

    suspend fun await() = completion.await(job)
}

internal class CoroutineBehaviourHandle(
    override val job: Job,
    override val completion: BehaviourCompletion,
    private val cancelJob: () -> Unit
) : BehaviourHandle() {
    override val isActive: Boolean
        get() = !job.isCompleted && !job.isCancelled

    override fun cancel() = cancelJob()
}

internal class BehaviourCompletion {
    private val completion = CompletableDeferred<Result<Unit>>()
    private val waiters = AtomicInteger()
    private val dependents = CopyOnWriteArrayList<BehaviourCompletion>()

    @Volatile
    private var observed = false

    @Volatile
    private var outcome: Result<Unit>? = null

    val failureObserved: Boolean
        get() = observed || dependents.any { it.failureObserved }

    val hasWaiters: Boolean
        get() = waiters.get() > 0 || dependents.any { it.hasWaiters }

    fun complete(cause: Throwable?) {
        completeResult(if (cause == null) Result.success(Unit) else Result.failure(cause))
    }

    fun follow(source: BehaviourCompletion) {
        source.dependents.add(this)
        source.completion.invokeOnCompletion {
            source.outcome?.let { completeResult(it) }
        }
    }

    private fun completeResult(result: Result<Unit>) {
        synchronized(this) {
            if (outcome != null) return
            outcome = result
        }
        completion.complete(result)
    }

    suspend fun await(target: Job?) {
        val context = currentCoroutineContext()
        context.ensureActive()
        val waiter = context[Job]
        check(target == null || waiter == null || !contains(target, waiter)) {
            "A behaviour cannot await itself or an ancestor coroutine."
        }

        waiters.incrementAndGet()
        try {
            val result = completion.await()
            currentCoroutineContext().ensureActive()
            if (result.isFailure) observed = true
            result.getOrThrow()
        } finally {
            waiters.decrementAndGet()
        }
    }

    private fun contains(target: Job, candidate: Job): Boolean {
        val pending = ArrayDeque<Job>()
        pending.addLast(target)
        while (pending.isNotEmpty()) {
            val job = pending.removeLast()
            if (job === candidate) return true
            job.children.forEach { pending.addLast(it) }
        }
        return false
    }
}
