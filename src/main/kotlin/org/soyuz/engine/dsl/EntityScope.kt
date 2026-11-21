
package org.soyuz.engine.dsl

import org.soyuz.engine.collision.Collider
import org.soyuz.engine.collision.CollisionSystem
import org.soyuz.engine.entity.DefaultGameEntity
import org.soyuz.engine.entity.GameEntity
import org.soyuz.engine.physics.PhysicsSystem
import org.soyuz.engine.physics.PhysicsBody
import org.soyuz.engine.render.Painter
import org.soyuz.engine.scene.RuntimeScene
import org.soyuz.engine.shape.Shape2D
import org.soyuz.engine.ui.Interactive
import org.soyuz.util.math.Transform
import org.soyuz.util.math.Vector2D

@BumpDsl
class EntityScope internal constructor(
    val entity: DefaultGameEntity
) {
    var position: Vector2D
        get() = entity.position
        set(value) {
            entity.position = value
        }

    var rotation: Double
        get() = entity.rotation
        set(value) {
            entity.rotation = value
        }

    var transform: Transform
        get() = entity.transform
        set(value) {
            entity.transform = value
        }

    var shape: Shape2D?
        get() = entity.shape
        set(value) {
            entity.shape = value
        }

    var painter: Painter?
        get() = entity.painter
        set(value) {
            entity.painter = value
        }

    var collider: Collider?
        get() = entity.collider
        set(value) {
            entity.collider = value
        }

    var interactive: Interactive?
        get() = entity.interactive
        set(value) {
            entity.interactive = value
        }

    private var physicsBody: PhysicsBody? = null

    private val behaviour =
        mutableListOf<(RuntimeScene) -> Unit>()

    fun physics(configure: PhysicsScope.() -> Unit) {
        val scope = PhysicsScope(
            body = physicsBody,
            collider = entity.collider
        )

        scope.configure()

        physicsBody = scope.body
        entity.collider = scope.collider
    }

    fun onKeyDown(
        key: Int,
        callback: GameEntity.() -> Unit
    ) {
        behaviour += { scene ->
            scene.onKeyDown(key, owner = entity) {
                callback(entity)
            }
        }
    }

    fun onKeyUp(
        key: Int,
        callback: GameEntity.() -> Unit
    ) {
        behaviour += { scene ->
            scene.onKeyUp(key, owner = entity) {
                callback(entity)
            }
        }
    }

    fun onKeyHeld(
        key: Int,
        callback: GameEntity.(Double) -> Unit
    ) {
        behaviour += { scene ->
            scene.onKeyHeld(key, owner = entity) { dt ->
                callback(entity, dt)
            }
        }
    }

    fun onCollision(
        callback: GameEntity.(GameEntity) -> Unit
    ) {
        entity.onCollision { other ->
            callback(entity, other)
        }
    }

    internal fun installPhysics(
        physicsSystem: PhysicsSystem,
        collisionSystem: CollisionSystem
    ) {
        val body = physicsBody
        val collider = entity.collider

        if (body != null) {
            physicsSystem.registerBody(entity.id, body)
        }

        if (collider != null) {
            collisionSystem.registerCollider(
                entity.id,
                collider
            )
        }
    }

    internal fun installBehaviour(scene: RuntimeScene) {
        behaviour.forEach { register ->
            register(scene)
        }
    }
}