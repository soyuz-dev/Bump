package org.soyuz

import org.lwjgl.glfw.GLFW.*
import org.soyuz.engine.core.Application
import org.soyuz.engine.core.RuntimeEngine
import org.soyuz.engine.entity.DefaultGameEntity
import org.soyuz.engine.render.Camera
import org.soyuz.engine.render.Mesh
import org.soyuz.engine.render.RuntimeRenderSystem
import org.soyuz.engine.render.SolidColor
import org.soyuz.engine.scene.RuntimeScene
import org.soyuz.engine.scene.Scene
import org.soyuz.engine.shape.CircleShape
import org.soyuz.engine.shape.RectangleShape
import org.soyuz.engine.shape.TriangleShape
import org.soyuz.input.KeyListener
import org.soyuz.util.Assets
import org.soyuz.util.Color
import org.soyuz.util.math.Vector2D
import org.soyuz.windowing.Window

fun main() {
    Application.init()
    val font = Assets.font("roboto")

    val window = Window("Bump - Scene Switching", 800, 600)
    val engine = RuntimeEngine(window, physicsSystem = null, Camera())

    Application.windows.add(window, engine) {
        engine.shader = Assets.shader("default")
        engine.renderSystem = RuntimeRenderSystem(Mesh.quad(), Mesh.circle(32))
    }

    // Scene 1: Colorful circles
    val scene1 = RuntimeScene("circles")
    val colors = listOf(
        Color(255, 80, 80), Color(80, 255, 80), Color(80, 80, 255),
        Color(255, 255, 80), Color(255, 80, 255), Color(80, 255, 255)
    )
    for (i in colors.indices) {
        val angle = i * Math.PI * 2 / colors.size
        DefaultGameEntity("circle_$i") at
                Vector2D(400.0 + kotlin.math.cos(angle) * 150, 250.0 + kotlin.math.sin(angle) * 150) with
                CircleShape(40.0) with
                SolidColor(colors[i]) within
                scene1
    }

    // Scene 2: Rectangles
    val scene2 = RuntimeScene("rectangles")
    for (i in 0..3) {
        DefaultGameEntity("rect_$i") at
                Vector2D(200.0 + i * 150, 300.0) with
                RectangleShape(100.0, 60.0) with
                SolidColor(Color(100 + i * 40, 150, 200 - i * 30)) within
                scene2
    }

    // Scene 3: Triangles
    val scene3 = RuntimeScene("triangles")
    for (i in 0..4) {
        DefaultGameEntity("tri_$i") at
                Vector2D(150.0 + i * 120, 300.0) with
                TriangleShape.equilateral(60.0) with
                SolidColor(Color(200, 150 + i * 20, 100)) within
                scene3
    }

    // Scene labels
    val scene1Label = engine.ui.label("label1", 400.0, 100.0, "Scene 1: Circles (Press 1, 2, 3 to switch)", font, 16f, Color(255,255,255))
    val scene2Label = engine.ui.label("label2", 400.0, 100.0, "Scene 2: Rectangles (Press 1, 2, 3 to switch)", font, 16f, Color(255,255,255))
    val scene3Label = engine.ui.label("label3", 400.0, 100.0, "Scene 3: Triangles (Press 1, 2, 3 to switch)", font, 16f, Color(255,255,255))

    scene1.addEntity(scene1Label)
    scene2.addEntity(scene2Label)
    scene3.addEntity(scene3Label)

    var currentScene: Scene = scene1
    engine.loadScene(currentScene)

    engine { dt ->
        if (KeyListener.isKeyJustPressed(window.handle, GLFW_KEY_1)) {
            currentScene = scene1
            engine.loadScene(currentScene)
        }
        if (KeyListener.isKeyJustPressed(window.handle, GLFW_KEY_2)) {
            currentScene = scene2
            engine.loadScene(currentScene)
        }
        if (KeyListener.isKeyJustPressed(window.handle, GLFW_KEY_3)) {
            currentScene = scene3
            engine.loadScene(currentScene)
        }
        if (KeyListener.isKeyJustPressed(window.handle, GLFW_KEY_ESCAPE)) {
            engine.quit()
        }
        engine.window.title = "Scene: ${currentScene.id} | FPS: ${(1.0 / dt).toInt()}"
    }

    Application.run()
}