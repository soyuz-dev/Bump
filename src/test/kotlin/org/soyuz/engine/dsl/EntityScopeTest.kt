package org.soyuz.org.soyuz.engine.dsl

import org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
import org.soyuz.engine.entity.DefaultGameEntity
import org.soyuz.engine.physics.PointMass
import org.soyuz.engine.scene.RuntimeScene
import org.soyuz.engine.shape.CircleShape
import org.soyuz.util.math.Vector2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class EntityScopeTest {

    @Test
    fun `configuration mutates the underlying entity`() {
        val entity = DefaultGameEntity("player")
        val scope = EntityScope(entity)

        scope.position = Vector2D(100.0, 200.0)
        scope.shape = CircleShape(25.0)

        assertEquals(Vector2D(100.0, 200.0), entity.position)
        assertNotNull(entity.shape)
    }

    @Test
    fun `physics configuration accepts existing body types`() {
        val entity = DefaultGameEntity("player")
        val scope = EntityScope(entity)
        val body = PointMass(mass = 2.0)

        scope.physics {
            this.body = body
        }

        assertNotNull(body)
    }

    @Test
    fun `entity keyboard handlers belong to their scene`() {
        val scene = RuntimeScene("game")
        val entity = DefaultGameEntity("player")
        val scope = EntityScope(entity)

        var presses = 0

        scope.onKeyDown(GLFW_KEY_SPACE) {
            presses++
        }

        scene.addEntity(entity)
        scope.installBehaviour(scene)

        assertSame(entity, scene.findEntity("player"))

        scene.removeEntity("player")
        assertEquals(0, presses)
    }

    @Test
    fun `duplicate entity ids can be detected`() {
        val scene = RuntimeScene("game")
        scene.addEntity(DefaultGameEntity("player"))

        assertFailsWith<IllegalArgumentException> {
            require(scene.findEntity("player") == null) {
                "Duplicate entity ID"
            }
        }
    }
}
