package org.soyuz

import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.soyuz.engine.core.Application
import org.soyuz.input.KeyListener
import org.soyuz.util.Assets
import org.soyuz.util.Color
import org.soyuz.windowing.CustomWindow

fun main() {
    println("1. Before Application.init()")
    Application.init()
    println("2. After Application.init()")
    val window = CustomWindow("My App", 600, 400, "roboto")
    val font = Assets.font("roboto")
    println("4. after window creation")
    window.x = 200; window.y = 200
    println("5. after window repos")
    val label = window.engine.ui.label("hello", 300.0, 200.0, "Hello!", font, 20f, Color(255,255,255))
    println("6. label")
    window.addEntity(label)
    window.show()
    var rotation = 0f
    window.engine {
        rotation += it.toFloat()
        window.camera.setRotation(rotation)
    }
    Application.run()
}