package org.soyuz.engine.behaviour

interface Subscription {
    val isActive: Boolean

    fun cancel()
}

internal class CallbackSubscription(
    private val onCancel: (CallbackSubscription) -> Unit
) : Subscription  {
    override var isActive: Boolean = true
        private set

    override fun cancel() {
        if (!isActive) return

        isActive = false
        onCancel(this)
    }
}