package org.soyuz.engine.core

import org.soyuz.engine.scene.Scene
import org.soyuz.engine.behaviour.Subscription

interface Engine {
    val isPaused: Boolean

    fun loadScene(scene: Scene)
    fun start()
    fun stop()
    fun run()
    fun pause()
    fun resume()
    fun togglePause()
    fun onFrame(callback: (dt: Double) -> Unit): Subscription

    operator fun invoke(callback: (dt: Double) -> Unit): Subscription
}
