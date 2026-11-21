
package org.soyuz.engine.physics

import org.soyuz.engine.scene.RuntimeScene
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class ScenePhysicsContextTest {

    @Test
    fun `scenes can own independent physics contexts`() {
        val menu = RuntimeScene("menu")
        val game = RuntimeScene("game")

        val menuPhysics = ScenePhysicsContext.create()
        val gamePhysics = ScenePhysicsContext.create()

        menu.attachPhysics(menuPhysics)
        game.attachPhysics(gamePhysics)

        assertSame(menuPhysics, menu.physicsContext)
        assertSame(gamePhysics, game.physicsContext)

        assertNotSame(
            menu.physicsContext!!.physicsSystem,
            game.physicsContext!!.physicsSystem
        )

        assertNotSame(
            menu.physicsContext!!.collisionSystem,
            game.physicsContext!!.collisionSystem
        )

        assertNotSame(
            menu.physicsContext!!.eventBus,
            game.physicsContext!!.eventBus
        )
    }

    @Test
    fun `physics context is optional`() {
        val scene = RuntimeScene("empty")

        assertNull(scene.physicsContext)
    }

    @Test
    fun `cannot attach physics twice`() {
        val scene = RuntimeScene("game")

        scene.attachPhysics(ScenePhysicsContext.create())

        assertFailsWith<IllegalStateException> {
            scene.attachPhysics(ScenePhysicsContext.create())
        }
    }

    @Test
    fun `identical entity ids can use separate registries`() {
        val first = ScenePhysicsContext.create()
        val second = ScenePhysicsContext.create()

        val bodyA = PointMass(mass = 1.0)
        val bodyB = PointMass(mass = 2.0)

        first.physicsSystem.registerBody("player", bodyA)
        second.physicsSystem.registerBody("player", bodyB)

        assertSame(
            bodyA,
            first.physicsSystem.getBody("player")
        )

        assertSame(
            bodyB,
            second.physicsSystem.getBody("player")
        )
    }
}
