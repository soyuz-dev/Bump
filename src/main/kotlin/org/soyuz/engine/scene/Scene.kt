
package org.soyuz.engine.scene

import org.soyuz.engine.behaviour.Subscription
import org.soyuz.engine.behaviour.BehaviourScope
import org.soyuz.engine.entity.GameEntity
import org.soyuz.engine.events.CollisionEvent
import org.soyuz.input.Input
import kotlin.time.Duration

interface Scene {
    val id: String

    fun addEntity(entity: GameEntity)

    fun removeEntity(entityId: String)

    fun findEntity(entityId: String): GameEntity?

    fun allEntities(): List<GameEntity>

    fun onKeyDown(
        key: Int,
        owner: GameEntity? = null,
        callback: () -> Unit
    ): Subscription

    fun onKeyUp(
        key: Int,
        owner: GameEntity? = null,
        callback: () -> Unit
    ): Subscription

    fun onKeyHeld(
        key: Int,
        owner: GameEntity? = null,
        callback: (Double) -> Unit
    ): Subscription

    fun dispatchInput(input: Input, dt: Double)

    fun after(delay: Duration, owner: GameEntity? = null, callback: () -> Unit): Subscription

    fun during(duration: Duration, owner: GameEntity? = null, callback: (progress: Double) -> Unit): Subscription

    fun forEvery(interval: Duration, owner: GameEntity? = null, callback: () -> Unit): Subscription

    fun everyFrame(owner: GameEntity? = null, callback: (dt: Double) -> Unit): Subscription

    fun launch(owner: GameEntity? = null, block: suspend BehaviourScope.() -> Unit): Subscription

    fun updateTimers(elapsed: Duration, shouldContinue: () -> Boolean = { true })

    fun init()

    fun fixedUpdate(dt: Float)

    fun render(delta: Float, alpha: Float)

    fun cleanup()

    fun onCollisionEvent(
        callback: (CollisionEvent) -> Unit,
    ): Subscription

    fun dispatchCollisionEvent(event: CollisionEvent)
}
