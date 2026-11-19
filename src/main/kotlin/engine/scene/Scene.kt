
package org.soyuz.engine.scene

import org.soyuz.engine.behaviour.Subscription
import org.soyuz.engine.entity.GameEntity
import org.soyuz.input.Input

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

    fun init()

    fun fixedUpdate(dt: Float)

    fun render(delta: Float, alpha: Float)

    fun cleanup()
}