
package org.soyuz.engine.physics

import org.soyuz.engine.collision.CollisionSystem
import org.soyuz.engine.collision.RuntimeCollisionSystem
import org.soyuz.engine.events.EventBus
import org.soyuz.engine.events.RuntimeEventBus

class ScenePhysicsContext(
    val collisionSystem: CollisionSystem,
    val eventBus: EventBus,
    val physicsSystem: PhysicsSystem
) {
    companion object {
        fun create(): ScenePhysicsContext {
            val collisionSystem = RuntimeCollisionSystem()
            val eventBus = RuntimeEventBus()

            val physicsSystem = RuntimePhysicsSystem(
                collisionSystem,
                eventBus
            )

            return ScenePhysicsContext(
                collisionSystem,
                eventBus,
                physicsSystem
            )
        }
    }
}
