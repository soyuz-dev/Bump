package org.soyuz.engine.dsl

import org.soyuz.engine.collision.Collider
import org.soyuz.engine.physics.PhysicsBody

@BumpDsl
class PhysicsScope internal constructor(
    var body: PhysicsBody?,
    var collider: Collider?
)