package org.soyuz.engine.behaviour

internal class DeferredSubscription : Subscription {
    private var delegate: Subscription? = null
    private var cancelled = false

    override val isActive: Boolean
        get() = !cancelled && (delegate?.isActive ?: true)

    fun bind(subscription: Subscription) {
        check(delegate == null) { "Subscription has already been installed." }
        delegate = subscription
        if (cancelled) subscription.cancel()
    }

    override fun cancel() {
        if (cancelled) return
        cancelled = true
        delegate?.cancel()
    }
}
