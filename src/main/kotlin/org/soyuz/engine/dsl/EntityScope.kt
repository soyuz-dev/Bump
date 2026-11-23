
package org.soyuz.engine.dsl

import org.soyuz.engine.behaviour.DeferredSubscription
import org.soyuz.engine.behaviour.Subscription
import org.soyuz.engine.behaviour.EntityBehaviourScope
import org.soyuz.engine.behaviour.BehaviourHandle
import org.soyuz.engine.behaviour.DeferredBehaviourHandle
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
import kotlin.time.Duration

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

    private class PendingRegistration(
        val subscription: Subscription,
        val install: (RuntimeScene) -> Unit
    )

    private val pendingRegistrations = mutableListOf<PendingRegistration>()
    private var installedScene: RuntimeScene? = null
    private var discarded = false

    fun after(delay: Duration, callback: GameEntity.() -> Unit): Subscription {
        require(delay.isFinite() && delay >= Duration.ZERO) {
            "Timer delay must be finite and non-negative."
        }
        return registerBehaviour { scene ->
            scene.after(delay, owner = entity) { callback(entity) }
        }
    }

    fun during(duration: Duration, callback: GameEntity.(progress: Double) -> Unit): Subscription {
        require(duration.isFinite() && duration > Duration.ZERO) {
            "Timer duration must be finite and positive."
        }
        return registerBehaviour { scene ->
            scene.during(duration, owner = entity) { callback(entity, it) }
        }
    }

    fun forEvery(interval: Duration, callback: GameEntity.() -> Unit): Subscription {
        require(interval.isFinite() && interval > Duration.ZERO) {
            "Timer interval must be finite and positive."
        }
        return registerBehaviour { scene ->
            scene.forEvery(interval, owner = entity) { callback(entity) }
        }
    }

    fun everyFrame(callback: GameEntity.(dt: Double) -> Unit): Subscription = registerBehaviour { scene ->
        scene.everyFrame(owner = entity) { callback(entity, it) }
    }

    fun launch(block: suspend EntityBehaviourScope.() -> Unit): BehaviourHandle {
        check(!discarded) { "Entity configuration has been discarded." }
        val register: (RuntimeScene) -> BehaviourHandle = { scene ->
            scene.launch(owner = entity) { block(EntityBehaviourScope(entity, this)) }
        }
        installedScene?.let { return register(it) }
        val handle = DeferredBehaviourHandle()
        pendingRegistrations += PendingRegistration(handle) { scene -> handle.bind(register(scene)) }
        return handle
    }

    private fun registerBehaviour(register: (RuntimeScene) -> Subscription): Subscription {
        check(!discarded) { "Entity configuration has been discarded." }
        installedScene?.let { return register(it) }
        val subscription = DeferredSubscription()
        pendingRegistrations += PendingRegistration(subscription) { scene -> subscription.bind(register(scene)) }
        return subscription
    }

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
        check(!discarded && installedScene == null) { "Entity behaviour cannot be installed again." }
        try {
            behaviour.forEach { register -> register(scene) }
            pendingRegistrations.forEach { pending ->
                if (pending.subscription.isActive) {
                    pending.install(scene)
                }
            }
            installedScene = scene
            behaviour.clear()
            pendingRegistrations.clear()
        } catch (failure: Throwable) {
            discard()
            throw failure
        }
    }

    internal fun discard() {
        discarded = true
        pendingRegistrations.forEach { it.subscription.cancel() }
        pendingRegistrations.clear()
        behaviour.clear()
    }
}
