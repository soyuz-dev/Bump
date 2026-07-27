package org.soyuz.windowing

import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.lwjgl.glfw.GLFW.glfwGetCurrentContext
import org.lwjgl.opengl.GL
import org.soyuz.engine.collision.Collider
import org.soyuz.engine.core.Application
import org.soyuz.engine.core.RuntimeEngine
import org.soyuz.engine.entity.DefaultGameEntity
import org.soyuz.engine.render.Camera
import org.soyuz.engine.render.Mesh
import org.soyuz.engine.render.RuntimeRenderSystem
import org.soyuz.engine.render.SolidColor
import org.soyuz.engine.render.text.Font
import org.soyuz.engine.scene.RuntimeScene
import org.soyuz.engine.shape.CircleShape
import org.soyuz.engine.shape.RectangleShape
import org.soyuz.engine.ui.Interactive
import org.soyuz.engine.ui.clickable
import org.soyuz.engine.ui.draggable
import org.soyuz.engine.ui.hoverable
import org.soyuz.input.KeyListener
import org.soyuz.input.MouseListener
import org.soyuz.util.Assets
import org.soyuz.util.Color
import org.soyuz.util.math.Vector2D

class CustomWindow(
    title: String,
    width: Int = 800,
    height: Int = 600,
    fontName: String = "roboto",
) : TransparentWindow(title, width, height) {

    val font by lazy {Assets.font(fontName)}

    val engine: RuntimeEngine
    val camera: Camera
    val scene = RuntimeScene("window_$title")

    private var dragStartWindowX = 0
    private var dragStartWindowY = 0
    private var dragStartMouseX = 0.0
    private var dragStartMouseY = 0.0

    init {

        println("CustomWindow.init: context=${glfwGetCurrentContext()}")
        GL.createCapabilities()
        camera = Camera()
        camera.setOrtho(width.toFloat(), height.toFloat())
        engine = RuntimeEngine(this, physicsSystem = null, camera)
        engine  {
            if (KeyListener.isKeyJustPressed(handle, GLFW_KEY_ESCAPE)) {
                engine.quit()
            }
        }

        // Title bar
        val titleBar = DefaultGameEntity("titlebar_$title")
        titleBar.position = Vector2D(width / 2.0, 15.0)
        titleBar.shape = RectangleShape(width.toDouble(), 30.0)
        titleBar.painter = SolidColor(Color(40, 40, 50))
        scene.addEntity(titleBar)

        // Title label
        val titleLabel = engine.ui.label("titlelabel_$title", 10.0, 15.0, title, font, 16f, Color(220, 220, 220))
        val title_x = (titleLabel.shape as RectangleShape).width/2.0 + 10.0
        titleLabel.position = Vector2D(title_x, 15.0)
        scene.addEntity(titleLabel)

        // Close button
        val closeBtn = DefaultGameEntity("close_$title")
        closeBtn.position = Vector2D(width - 20.0, 15.0)
        closeBtn.shape = CircleShape(10.0)
        closeBtn.painter = SolidColor(Color(220, 60, 60))
        closeBtn.collider = Collider(CircleShape(10.0))
        closeBtn.interactive = Interactive { closeBtn.collider!!.containsPoint(it, closeBtn.transform) }
            .clickable { engine.quit() }
            .hoverable(
                onHoverEnter = { closeBtn.painter = SolidColor(Color(255, 80, 80)) },
                onHoverExit = { closeBtn.painter = SolidColor(Color(220, 60, 60)) }
            )


        // Body
        val body = DefaultGameEntity("body_$title")
        body.position = Vector2D(width / 2.0, height / 2.0 + 15.0)
        body.shape = RectangleShape(width.toDouble(), (height - 30).toDouble())
        body.painter = SolidColor(Color(30, 30, 40, 230))
        scene.addEntity(body)

        // Draggable title bar overlay
        val titleDrag = DefaultGameEntity("titledrag_$title")
        titleDrag.position = Vector2D(width / 2.0, 15.0)
        titleDrag.shape = RectangleShape(width.toDouble(), 30.0)
        titleDrag.painter = SolidColor(Color(0, 0, 0, 0))
        titleDrag.collider = Collider(RectangleShape(width.toDouble(), 30.0))
        titleDrag.interactive = Interactive { titleDrag.collider!!.containsPoint(it, titleDrag.transform) }
            .draggable(
                onDragStart = { _, _ ->
                    dragStartWindowX = x
                    dragStartWindowY = y
                    val (sx, sy) = MouseListener.getDesktopPos()
                    dragStartMouseX = sx
                    dragStartMouseY = sy
                },
                onDrag = { _, _ ->
                    val s = MouseListener.getDesktopPos()
                    x = dragStartWindowX + s.x.toInt() - dragStartMouseX.toInt()
                    y = dragStartWindowY + s.y.toInt() - dragStartMouseY.toInt()
                }
            )
        scene.addEntity(titleDrag)
        scene.addEntity(closeBtn)
    }

    fun show() {
        Application.windows.add(this, engine) {
            engine.shader = Assets.shader("default")
            engine.renderSystem = RuntimeRenderSystem(Mesh.quad(), Mesh.circle(32))
        }
        engine.loadScene(scene)
    }

    fun addEntity(entity: DefaultGameEntity) {
        scene.addEntity(entity)
    }

    fun onUpdate(callback: (dt: Double) -> Unit) {
        engine.everyFrame(callback)
    }
}