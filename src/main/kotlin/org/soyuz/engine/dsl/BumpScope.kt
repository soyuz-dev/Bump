package org.soyuz.engine.dsl

import org.soyuz.engine.behaviour.routeCollisions
import org.soyuz.engine.collision.RuntimeCollisionSystem
import org.soyuz.engine.core.Application
import org.soyuz.engine.core.RuntimeEngine
import org.soyuz.engine.events.RuntimeEventBus
import org.soyuz.engine.physics.RuntimePhysicsSystem
import org.soyuz.engine.render.Camera
import org.soyuz.engine.render.Mesh
import org.soyuz.engine.render.RuntimeRenderSystem
import org.soyuz.util.Assets
import org.soyuz.windowing.Window

@BumpDsl
class BumpScope internal constructor() {

    fun window(
        title: String = "Bump",
        width: Int = 800,
        height: Int = 600,
        createWindow: (String, Int, Int) -> Window = ::Window,
        configure: WindowScope.() -> Unit
    ): Window  {
        require(width > 0 && height > 0) { "Window dimensions should be positive" }

        val window = createWindow(title, width, height)
        val camera = Camera()
        val eventBus = RuntimeEventBus()
        val collisionSystem = RuntimeCollisionSystem()
        val physicsSystem = RuntimePhysicsSystem(
            collisionSystem,
            eventBus
        )

        val engine = RuntimeEngine(
            window,
            physicsSystem,
            camera,
        )

        eventBus.routeCollisions {
            engine.activeScene
        }

        Application.windows.add(window, engine) {
            engine.shader = Assets.shader("default")
            engine.renderSystem = RuntimeRenderSystem(
                Mesh.quad(),
                Mesh.circle(32)
            )
        }

        val scope = WindowScope(
            window,
            engine,
            camera,
            physicsSystem,
            collisionSystem,
        )
        scope.configure()
        scope.finish()

        return window
    }
}