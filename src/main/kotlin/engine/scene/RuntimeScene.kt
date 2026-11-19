
package org.soyuz.engine.scene

import org.soyuz.engine.behaviour.SceneKeyboardDispatcher
import org.soyuz.engine.behaviour.Subscription
import org.soyuz.engine.entity.GameEntity
import org.soyuz.input.Input

open class RuntimeScene(
    final override val id: String
) : Scene {
    private val entities: LinkedHashMap<String, GameEntity> =
        linkedMapOf()

    private val keyboard = SceneKeyboardDispatcher()

    final override fun addEntity(entity: GameEntity) {
        val previous = entities.put(entity.id, entity)

        if (previous != null && previous !== entity) {
            keyboard.cancelOwnedBy(previous)
        }
    }

    final override fun removeEntity(entityId: String) {
        val entity = entities.remove(entityId) ?: return
        keyboard.cancelOwnedBy(entity)
    }

    final override fun findEntity(
        entityId: String
    ): GameEntity? = entities[entityId]

    final override fun allEntities(): List<GameEntity> =
        entities.values.toList()

    override fun onKeyDown(
        key: Int,
        owner: GameEntity?,
        callback: () -> Unit
    ): Subscription {
        validateOwner(owner)
        return keyboard.onKeyDown(key, owner, callback)
    }

    override fun onKeyUp(
        key: Int,
        owner: GameEntity?,
        callback: () -> Unit
    ): Subscription {
        validateOwner(owner)
        return keyboard.onKeyUp(key, owner, callback)
    }

    override fun onKeyHeld(
        key: Int,
        owner: GameEntity?,
        callback: (Double) -> Unit
    ): Subscription {
        validateOwner(owner)
        return keyboard.onKeyHeld(key, owner, callback)
    }

    override fun dispatchInput(input: Input, dt: Double) {
        keyboard.dispatch(input, dt)
    }

    private fun validateOwner(owner: GameEntity?) {
        if (owner == null) return

        require(entities[owner.id] === owner) {
            "Entity '${owner.id}' does not belong to scene '$id'"
        }
    }

    override fun init() = Unit

    override fun fixedUpdate(dt: Float) {
        entities.values.forEach { entity ->
            entity.update(dt)
        }
    }

    override fun render(
        delta: Float,
        alpha: Float
    ) = Unit

    override fun cleanup() {
        keyboard.clear()
        entities.clear()
    }
}