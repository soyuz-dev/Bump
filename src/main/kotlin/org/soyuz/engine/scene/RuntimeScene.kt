
package org.soyuz.engine.scene

import org.soyuz.engine.behaviour.CallbackSubscription
import org.soyuz.engine.behaviour.SceneKeyboardDispatcher
import org.soyuz.engine.behaviour.Subscription
import org.soyuz.engine.behaviour.BehaviourScope
import org.soyuz.engine.behaviour.BehaviourScheduler
import org.soyuz.engine.behaviour.routeCollisions
import org.soyuz.engine.entity.GameEntity
import org.soyuz.engine.events.CollisionEvent
import org.soyuz.engine.events.CollisionEventType
import org.soyuz.engine.events.EventBus
import org.soyuz.engine.physics.ScenePhysicsContext
import org.soyuz.input.Input
import org.soyuz.engine.timing.TimerScheduler
import kotlin.time.Duration

open class RuntimeScene(
    final override val id: String
) : Scene {
    private val entities: LinkedHashMap<String, GameEntity> =
        linkedMapOf()

    private val keyboard = SceneKeyboardDispatcher()
    private val timerScheduler = TimerScheduler()
    private val behaviours = BehaviourScheduler(timerScheduler)

    private val collisionCallbacks =
        mutableListOf<Pair<Subscription, (CollisionEvent) -> Unit>>()


    var physicsContext: ScenePhysicsContext? = null
        private set

    private var collisionRouting: Subscription? = null


    fun attachPhysics(context: ScenePhysicsContext) {
        check(physicsContext == null) {
            "Scene '$id' already has a physics context"
        }

        physicsContext = context
        collisionRouting = context.eventBus.routeCollisions {
            this
        }
    }


    final override fun addEntity(entity: GameEntity) {
        val existing = entities[entity.id]

        if (physicsContext != null) {
            require(existing == null || existing === entity) {
                "Entity '${entity.id}' already exists " +
                        "in scene '$id'"
            }
        }

        val previous = entities.put(entity.id, entity)

        if (previous != null && previous !== entity) {
            keyboard.cancelOwnedBy(previous)
            behaviours.cancelOwnedBy(previous)
            timerScheduler.cancelOwnedBy(previous)
        }
    }

    final override fun removeEntity(entityId: String) {
        val entity = entities.remove(entityId) ?: return
        keyboard.cancelOwnedBy(entity)
        behaviours.cancelOwnedBy(entity)
        timerScheduler.cancelOwnedBy(entity)
        physicsContext?.let { context ->
            context.physicsSystem.unregisterBody(entityId)
            context.collisionSystem.unregisterCollider(entityId)
        }
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

    override fun after(delay: Duration, owner: GameEntity?, callback: () -> Unit): Subscription {
        validateOwner(owner)
        return timerScheduler.after(delay, owner, callback)
    }

    override fun during(duration: Duration, owner: GameEntity?, callback: (progress: Double) -> Unit): Subscription {
        validateOwner(owner)
        return timerScheduler.during(duration, owner, callback)
    }

    override fun forEvery(interval: Duration, owner: GameEntity?, callback: () -> Unit): Subscription {
        validateOwner(owner)
        return timerScheduler.forEvery(interval, owner, callback)
    }

    override fun everyFrame(owner: GameEntity?, callback: (dt: Double) -> Unit): Subscription {
        validateOwner(owner)
        return timerScheduler.everyFrame(owner, callback)
    }

    override fun updateTimers(elapsed: Duration, shouldContinue: () -> Boolean) {
        behaviours.update(elapsed, shouldContinue)
    }

    override fun launch(owner: GameEntity?, block: suspend BehaviourScope.() -> Unit): Subscription {
        validateOwner(owner)
        return behaviours.launch(owner, block)
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
        behaviours.clear()
        timerScheduler.clear()
        entities.keys.toList().forEach { entityId ->
            removeEntity(entityId)
        }

        keyboard.clear()

        collisionCallbacks
            .map { it.first }
            .forEach { it.cancel() }

        collisionRouting?.cancel()
        collisionRouting = null

        physicsContext = null
    }


    override fun onCollisionEvent(
        callback: (CollisionEvent) -> Unit
    ): Subscription {
        val subscription = CallbackSubscription { cancelled ->
            collisionCallbacks.removeAll {
                it.first == cancelled
            }
        }

        collisionCallbacks += subscription to callback

        return subscription
    }

    override fun dispatchCollisionEvent(event: CollisionEvent) {
        val source = findEntity(event.sourceEntityId) ?: return
        val other = findEntity(event.otherEntityId) ?: return

        for ((subscription, callback) in collisionCallbacks.toList()) {
            if (subscription.isActive) callback(event)
        }

        if (event.type != CollisionEventType.ENTER) return

        if (
            findEntity(source.id) === source &&
            findEntity(other.id) === other
        ) source.dispatchCollision(other)

        if (source !== other &&
            findEntity(source.id) === source &&
            findEntity(other.id) === other
        ) {
            other.dispatchCollision(source)
        }
    }
}
