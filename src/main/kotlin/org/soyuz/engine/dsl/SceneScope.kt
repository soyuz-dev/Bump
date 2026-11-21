
package org.soyuz.engine.dsl

import org.soyuz.engine.behaviour.Subscription
import org.soyuz.engine.collision.CollisionSystem
import org.soyuz.engine.core.RuntimeEngine
import org.soyuz.engine.entity.DefaultGameEntity
import org.soyuz.engine.events.CollisionEvent
import org.soyuz.engine.physics.PhysicsSystem
import org.soyuz.engine.scene.RuntimeScene

@BumpDsl
class SceneScope internal constructor(
    val scene: RuntimeScene,
    val engine: RuntimeEngine,
    private val physicsSystem: PhysicsSystem,
    private val collisionSystem: CollisionSystem
) {
    fun entity(
        id: String,
        configure: EntityScope.() -> Unit = {}
    ): DefaultGameEntity {
        require(scene.findEntity(id) == null) {
            "Duplicate entity ID '$id' in scene '${scene.id}'"
        }

        val entity = DefaultGameEntity(id)
        val scope = EntityScope(entity)

        scope.configure()

        // An entity is registered only after configuration
        // has successfully completed.
        scene.addEntity(entity)

        try {
            scope.installPhysics(
                physicsSystem,
                collisionSystem
            )
            scope.installBehaviour(scene)
        } catch (failure: Throwable) {
            physicsSystem.unregisterBody(id)
            collisionSystem.unregisterCollider(id)
            scene.removeEntity(id)
            throw failure
        }

        return entity
    }

    fun onKeyDown(
        key: Int,
        callback: () -> Unit
    ): Subscription = scene.onKeyDown(key, callback = callback)

    fun onKeyUp(
        key: Int,
        callback: () -> Unit
    ): Subscription = scene.onKeyUp(key, callback = callback)

    fun onKeyHeld(
        key: Int,
        callback: (Double) -> Unit
    ): Subscription = scene.onKeyHeld(key, callback = callback)

    fun onCollisionEvent(
        callback: (CollisionEvent) -> Unit
    ): Subscription = scene.onCollisionEvent(callback)
}
