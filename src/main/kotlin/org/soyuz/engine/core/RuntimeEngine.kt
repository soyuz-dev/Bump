package org.soyuz.engine.core

import org.soyuz.engine.physics.PhysicsSystem
import org.soyuz.engine.behaviour.Subscription
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

class RuntimeEngine(
    val window: Window,
    private val physicsSystem: PhysicsSystem?,
    private val camera: Camera,
) : Engine, Dynamic {

    val input = Input(window.handle)
    private val uiSystem = UISystem(input, camera)
    val ui = UI(uiSystem)
    private val simulation = SimulationLoop(DEFAULT_PHYSICS_TIMESTEP)

    override val isPaused: Boolean
        get() = simulation.isPaused

    override fun pause() = simulation.pause()

    override fun resume() = simulation.resume()

    override fun togglePause() = simulation.togglePause()

    override fun onFrame(callback: (dt: Double) -> Unit): Subscription =
        simulation.frames.everyFrame(callback)

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
        if (!running || isPaused) return
        val scene = currentScene ?: return
        uiSystem.handleChar(char, scene.allEntities())
    }

    fun forEvery(interval: Duration, callback: () -> Unit): Subscription =
        simulation.timers.forEvery(interval, callback)

    fun everyFrame(callback: (dt: Double) -> Unit): Subscription =
        simulation.timers.everyFrame(callback)

    fun after(delay: Duration, callback: () -> Unit): Subscription =
        simulation.timers.after(delay, callback)

    fun during(duration: Duration, callback: (progress: Double) -> Unit): Subscription =
        simulation.timers.during(duration, callback)

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
        simulation.clear()
        if (running) {
            running = false
            currentScene?.cleanup()
        }
        if (!renderResourcesCleaned && ::renderSystem.isInitialized) {
            renderSystem.cleanup()
            renderResourcesCleaned = true
        }
    }

    override fun update(dt: Float) {
        if (!running || dt <= 0f || !dt.isFinite()) return
        val scene = currentScene
        val revision = sceneRevision
        simulation.update(
            dt = dt,
            isRunning = { running },
            isActive = { scene != null && currentScene === scene && sceneRevision == revision },
            dispatchInput = { delta, shouldContinue -> scene?.dispatchInput(input, delta, shouldContinue) },
            stepPhysics = { delta ->
                if (scene != null) {
                    val physics = (scene as? RuntimeScene)?.physicsContext?.physicsSystem ?: physicsSystem
                    physics?.step(scene, delta)
                }
            },
            updateUI = { scene?.let { uiSystem.update(it.allEntities()) } },
            updateDynamics = { delta, shouldContinue ->
                for (entity in scene?.allEntities().orEmpty()) {
                    if (!shouldContinue()) break
                    (entity.painter as? Dynamic)?.update(delta)
                }
            },
            updateSceneTimers = { elapsed, shouldContinue -> scene?.updateTimers(elapsed, shouldContinue) }
        )
        if (isPaused) uiSystem.suspendInput()
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
