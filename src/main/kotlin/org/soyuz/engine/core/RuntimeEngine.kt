package org.soyuz.engine.core

import org.soyuz.engine.physics.PhysicsSystem
import org.soyuz.engine.behaviour.Subscription
import org.soyuz.engine.timing.TimerScheduler
import org.soyuz.engine.render.Camera
import org.soyuz.engine.render.RenderSystem
import org.soyuz.engine.render.Shader
import org.soyuz.engine.scene.RuntimeScene
import org.soyuz.engine.scene.Scene
import org.soyuz.engine.ui.UISystem
import org.soyuz.engine.ui.UI
import org.soyuz.input.Input
import org.soyuz.util.Dynamic
import org.soyuz.windowing.Window
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RuntimeEngine(
    val window: Window,
    private val physicsSystem: PhysicsSystem?,
    private val camera: Camera,
) : Engine, Dynamic {

    val input = Input(window.handle)
    private val uiSystem = UISystem(input, camera)
    val ui = UI(uiSystem)
    private val timerScheduler = TimerScheduler()

    lateinit var renderSystem: RenderSystem
    lateinit var shader: Shader

    private var currentScene: Scene? = null
    private var sceneRevision = 0L
    val activeScene: Scene?
        get() = currentScene
    private var running = false
    private var renderResourcesCleaned = false

    fun init() {
        window.setCharCallback { onChar(it) }
        camera.setOrtho(window.width.toFloat(), window.height.toFloat())
    }

    fun onChar(char: Char) {
        val scene = currentScene ?: return
        uiSystem.handleChar(char, scene.allEntities())
    }

    fun forEvery(interval: Duration, callback: () -> Unit): Subscription =
        timerScheduler.forEvery(interval, callback)

    fun everyFrame(callback: (dt: Double) -> Unit): Subscription =
        timerScheduler.everyFrame(callback)

    fun after(delay: Duration, callback: () -> Unit): Subscription =
        timerScheduler.after(delay, callback)

    fun during(duration: Duration, callback: (progress: Double) -> Unit): Subscription =
        timerScheduler.during(duration, callback)

    override fun loadScene(scene: Scene) {
        sceneRevision++
        currentScene = scene
        if (running) scene.init()
    }

    override fun start() {
        if (running) return
        check(!renderResourcesCleaned) { "Cannot restart an engine after its render resources have been cleaned." }
        if (!::renderSystem.isInitialized || !::shader.isInitialized) {
            throw IllegalStateException("renderSystem and shader must be assigned before starting the engine.")
        }
        running = true
        currentScene?.init()
    }

    override fun stop() {
        timerScheduler.clear()
        if (running) {
            running = false
            currentScene?.cleanup()
        }
        if (!renderResourcesCleaned && ::renderSystem.isInitialized) {
            renderSystem.cleanup()
            renderResourcesCleaned = true
        }
    }

    private var physicsAccumulator = 0.0


    override fun update(dt: Float) {
        if (!running || dt <= 0f || !dt.isFinite()) return
        val scene = currentScene ?: return
        val revision = sceneRevision

        scene.dispatchInput(input, dt.toDouble())

        if (!running || sceneRevision != revision) return

        physicsAccumulator += dt.toDouble()

        while (physicsAccumulator >= DEFAULT_PHYSICS_TIMESTEP) {
            val activePhysicsSystem =
                (scene as? RuntimeScene)
                    ?.physicsContext
                    ?.physicsSystem
                    ?: physicsSystem

            activePhysicsSystem?.step(
                scene,
                DEFAULT_PHYSICS_TIMESTEP
            )
            physicsAccumulator -= DEFAULT_PHYSICS_TIMESTEP
        }

        val entities = scene.allEntities()
        uiSystem.update(entities)
        entities.forEach { (it.painter as? Dynamic)?.update(dt) }

        if (!running) return
        val elapsed = dt.toDouble().seconds
        timerScheduler.update(elapsed)
        if (!running || sceneRevision != revision) return
        scene.updateTimers(elapsed) {
            running && currentScene === scene && sceneRevision == revision
        }
    }

    fun render() {
        window.makeContextCurrent()
        renderCurrentContext()
    }

    fun renderCurrentContext() {
        if (!running) return
        val scene = currentScene ?: return
        renderSystem.render(window, scene, camera, shader)
    }

    override fun run() {
        throw UnsupportedOperationException("Use Application and WindowManager to run RuntimeEngine instances.")
    }

    // Window delegation
    fun quit() = window.quit()


    override fun invoke(callback: (dt: Double) -> Unit) = everyFrame(callback)

    companion object {
        const val DEFAULT_MAX_FRAME_DELTA: Double = 0.25
        const val DEFAULT_PHYSICS_TIMESTEP: Double = 0.01
    }
}
