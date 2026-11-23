package org.soyuz.engine.behaviour

import org.soyuz.engine.entity.GameEntity
import kotlin.time.Duration

class BehaviourScope internal constructor(
    private val waitFor: suspend (Duration) -> Unit,
    private val runUntil: suspend (Duration, (Double) -> Unit) -> Unit
) {
    suspend fun wait(duration: Duration) = waitFor(duration)

    suspend fun until(duration: Duration, callback: (progress: Double) -> Unit) = runUntil(duration, callback)
}

class EntityBehaviourScope internal constructor(
    val entity: GameEntity,
    private val behaviour: BehaviourScope
) : GameEntity by entity {
    suspend fun wait(duration: Duration) = behaviour.wait(duration)

    suspend fun until(duration: Duration, callback: (progress: Double) -> Unit) = behaviour.until(duration, callback)
}
