package org.soyuz.engine.core

import org.soyuz.engine.physics.PhysicsSystem
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
    private val pendingTimers = mutableListOf<Timer>()

    lateinit var renderSystem: RenderSystem
    lateinit var shader: Shader

    private var currentScene: Scene? = null
    val activeScene: Scene?
        get() = currentScene
    private var running = false
    private var renderResourcesCleaned = false
    private val timers = mutableListOf<Timer>()

    private data class Timer(
        val interval: Duration,
        var accumulator: Duration = Duration.ZERO,
        val type: TimerType = TimerType.INTERVAL,
        val callback: (progress: Double) -> Unit
    )

    private enum class TimerType { INTERVAL, ONE_SHOT, DURING, FOREVER }

    fun init() {
        window.setCharCallback { onChar(it) }
        camera.setOrtho(window.width.toFloat(), window.height.toFloat())
    }

    fun onChar(char: Char) {
        val scene = currentScene ?: return
        uiSystem.handleChar(char, scene.allEntities())
    }

    fun forEvery(interval: Duration, callback: () -> Unit) {
        require(interval.isFinite() && interval > Duration.ZERO) {
            "Timer interval must be finite and positive."
        }
        pendingTimers.add(Timer(interval = interval, type = TimerType.INTERVAL) { callback() })
    }

    fun everyFrame(callback: (dt: Double) -> Unit) {
        timers.add(Timer(interval = Duration.ZERO, type = TimerType.FOREVER) { callback(it) })
    }

    fun after(delay: Duration, callback: () -> Unit) {
        require(delay.isFinite() && delay >= Duration.ZERO) {
            "Timer delay must be finite and non-negative."
        }
        pendingTimers.add(Timer(interval = delay, type = TimerType.ONE_SHOT) { callback() })
    }

    fun during(duration: Duration, callback: (progress: Double) -> Unit) {
        require(duration.isFinite() && duration > Duration.ZERO) {
            "Timer duration must be finite and positive."
        }
        pendingTimers.add(Timer(interval = duration, type = TimerType.DURING, callback = callback))
    }

    override fun loadScene(scene: Scene) {
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

        scene.dispatchInput(input, dt.toDouble())

        if (!running || currentScene !== scene) return

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

        val elapsed = dt.toDouble().seconds
        timers.removeAll { timer ->
            timer.accumulator += elapsed
            when (timer.type) {
                TimerType.INTERVAL -> {
                    while (timer.accumulator >= timer.interval) { timer.accumulator -= timer.interval; timer.callback(0.0) }
                    false
                }
                TimerType.ONE_SHOT -> { if (timer.accumulator >= timer.interval) { timer.callback(1.0); true } else false }
                TimerType.DURING -> {
                    if (timer.accumulator < timer.interval) { timer.callback((timer.accumulator / timer.interval).coerceIn(0.0, 1.0)); false }
                    else { timer.callback(1.0); true }
                }
                TimerType.FOREVER -> { timer.callback(dt.toDouble()); false }
            }
        }

        timers.addAll(pendingTimers)
        pendingTimers.clear()
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
