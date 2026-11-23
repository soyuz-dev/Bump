
package org.soyuz.engine.dsl

import org.soyuz.engine.behaviour.Subscription
import org.soyuz.engine.behaviour.BehaviourScope
import org.soyuz.engine.collision.CollisionSystem
import org.soyuz.engine.core.RuntimeEngine
import org.soyuz.engine.entity.DefaultGameEntity
import org.soyuz.engine.events.CollisionEvent
import org.soyuz.engine.physics.PhysicsSystem
import org.soyuz.engine.scene.RuntimeScene
import kotlin.time.Duration

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

        try {
            scope.configure()
            scene.addEntity(entity)
            scope.installPhysics(
                physicsSystem,
                collisionSystem
            )
            scope.installBehaviour(scene)
        } catch (failure: Throwable) {
            scope.discard()
            if (scene.findEntity(id) === entity) {
                physicsSystem.unregisterBody(id)
                collisionSystem.unregisterCollider(id)
                scene.removeEntity(id)
            }
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

    fun after(delay: Duration, callback: () -> Unit): Subscription =
        scene.after(delay, callback = callback)

    fun during(duration: Duration, callback: (progress: Double) -> Unit): Subscription =
        scene.during(duration, callback = callback)

    fun forEvery(interval: Duration, callback: () -> Unit): Subscription =
        scene.forEvery(interval, callback = callback)

    fun everyFrame(callback: (dt: Double) -> Unit): Subscription =
        scene.everyFrame(callback = callback)

    fun launch(block: suspend BehaviourScope.() -> Unit): Subscription =
        scene.launch(block = block)
}
