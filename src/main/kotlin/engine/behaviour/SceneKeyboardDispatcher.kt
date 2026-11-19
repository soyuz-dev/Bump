package org.soyuz.engine.behaviour

import org.soyuz.engine.entity.GameEntity
import org.soyuz.input.Input
import org.soyuz.util.math.Quaternion
import java.awt.event.KeyEvent

internal class SceneKeyboardDispatcher {
    private enum class EventType {
        DOWN,
        UP,
        HELD
    }

    private class Registration(
        val key: Int,
        val type: EventType,
        val owner: GameEntity?,
        val callback: (Double) -> Unit,
        val subscription: Subscription
    )

    private val registrations = mutableListOf<Registration>()

    private fun register(
        key: Int,
        type: EventType,
        owner: GameEntity?,
        callback: (Double) -> Unit,
    ): Subscription {
        require(key in 0..348) {
            "Invalid GLFW key: $key"
        }

        val subscription = CallbackSubscription { cancelled ->
            registrations.removeAll { it.subscription == cancelled }
        }

        registrations += Registration(
            key = key,
            type = type,
            owner = owner,
            callback = callback,
            subscription = subscription
        )

        return subscription
    }

    fun onKeyDown(
        key: Int,
        owner: GameEntity? = null,
        callback: () -> Unit,
    ): Subscription = register(
        key,
        EventType.DOWN,
        owner,
    ) { callback() }

    fun onKeyUp(
        key: Int,
        owner: GameEntity? = null,
        callback: () -> Unit,
    ): Subscription = register(
        key,
        EventType.UP,
        owner,
    ) { callback() }

    fun onKeyHeld(
        key: Int,
        owner: GameEntity? = null,
        callback: (Double) -> Unit,
    ): Subscription = register(
        key,
        EventType.HELD,
        owner,
        callback,
    )

    fun dispatch(input: Input, dt: Double) {
        dispatch(
            dt,
            input::isKeyDown,
            input::isKeyJustPressed,
            input::isKeyJustReleased
        )
    }

    fun dispatch(
        dt: Double,
        isDown: (Int) -> Boolean,
        isJustPressed: (Int) -> Boolean,
        isJustReleased: (Int) -> Boolean,
    ) {
        require(dt.isFinite() && dt >= 0.0) {
            "Delta time must be finite and non-negative"
        }

        val snapshot = registrations.toList()

        for (registration in snapshot) {
            if (!registration.subscription.isActive) continue

            val shouldFire = when (registration.type) {
                EventType.DOWN -> {
                    isJustPressed(registration.key)
                }

                EventType.UP -> {
                    isJustReleased(registration.key)
                }

                EventType.HELD -> {
                    isDown(registration.key)
                }
            }

            if (shouldFire) {
                registration.callback(dt)
            }
        }
    }

    fun cancelOwnedBy(owner: GameEntity) {
        registrations
            .filter { it.owner == owner }
            .map { it.subscription }
            .forEach { it.cancel() }
    }

    fun clear() {
        registrations
            .map { it.subscription }
            .forEach { it.cancel() }
    }
}