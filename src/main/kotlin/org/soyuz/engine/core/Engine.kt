package org.soyuz.engine.core

import org.soyuz.engine.scene.Scene
import org.soyuz.engine.behaviour.Subscription

interface Engine {
    fun loadScene(scene: Scene)
    fun start()
    fun stop()
    fun run()

    operator fun invoke(callback: (dt: Double) -> Unit): Subscription
}
