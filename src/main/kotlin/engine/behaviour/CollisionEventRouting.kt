package org.soyuz.engine.behaviour

import org.soyuz.engine.events.CollisionEvent
import org.soyuz.engine.events.EventBus
import org.soyuz.engine.scene.Scene

fun EventBus.routeCollisions(
    activeScene: () -> Scene?
): Subscription {
    val listener: (CollisionEvent) -> Unit = { event ->
        activeScene()?.dispatchCollisionEvent(event)
    }

    subscribe(CollisionEvent::class.java, listener)


    return CallbackSubscription {
        unsubscribe(CollisionEvent::class.java, listener)
    }
}