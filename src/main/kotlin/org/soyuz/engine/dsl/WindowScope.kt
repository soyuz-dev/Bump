
package org.soyuz.engine.dsl

import org.soyuz.engine.core.RuntimeEngine
import org.soyuz.engine.collision.CollisionSystem
import org.soyuz.engine.physics.PhysicsSystem
import org.soyuz.engine.physics.ScenePhysicsContext
import org.soyuz.engine.render.Camera
import org.soyuz.engine.scene.RuntimeScene
import org.soyuz.windowing.Window

@BumpDsl
class WindowScope internal constructor(
    val window: Window,
    val engine: RuntimeEngine,
    val camera: Camera,
    private val physicsSystem: PhysicsSystem,
    private val collisionSystem: CollisionSystem
) {
    private val scenes = linkedMapOf<String, RuntimeScene>()
    private var initialSceneId: String? = null

    var vsync: Boolean = true

    fun scene(
        id: String,
        configure: SceneScope.() -> Unit = {}
    ): RuntimeScene {
        require(id !in scenes) {
            "Duplicate scene ID: '$id'"
        }

        val scene = RuntimeScene(id)
        val physics = ScenePhysicsContext.create()

        scene.attachPhysics(physics)

        try {
            SceneScope(
                scene = scene,
                engine = engine,
                physicsSystem = physics.physicsSystem,
                collisionSystem = physics.collisionSystem
            ).configure()

            scenes[id] = scene

            if (initialSceneId == null) {
                initialSceneId = id
            }

            return scene
        } catch (failure: Throwable) {
            scene.cleanup()
            throw failure
        }
    }


    fun startWith(sceneId: String) {
        initialSceneId = sceneId
    }

    fun camera(configure: Camera.() -> Unit) {
        camera.configure()
    }

    fun sceneById(id: String): RuntimeScene? = scenes[id]

    internal fun finish() {
        window.makeContextCurrent()
        window.setVSync(vsync)

        val id = initialSceneId ?: return
        val scene = requireNotNull(scenes[id]) {
            "Initial scene '$id' does not exist"
        }

        engine.loadScene(scene)
    }
}
