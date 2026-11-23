package org.soyuz.engine.behaviour

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

internal class DeferredBehaviourHandle : BehaviourHandle() {
    override val completion = BehaviourCompletion()
    private var delegate: BehaviourHandle? = null
    private var cancelled = false

    override val job: Job?
        get() = delegate?.job

    override val isActive: Boolean
        get() = !cancelled && (delegate?.isActive ?: true)

    fun bind(handle: BehaviourHandle) {
        check(delegate == null) { "Behaviour has already been installed." }
        delegate = handle
        if (cancelled) handle.cancel()
        else completion.follow(handle.completion)
    }

    override fun cancel() {
        if (cancelled) return
        cancelled = true
        val handle = delegate
        if (handle != null) handle.cancel()
        else completion.complete(CancellationException("Entity behaviour was cancelled before installation."))
    }
}
