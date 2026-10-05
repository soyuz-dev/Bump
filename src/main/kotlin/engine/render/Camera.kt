package org.soyuz.engine.render

import org.soyuz.util.math.Matrix4f
import org.soyuz.util.math.Vector2D

class Camera {
    private var backingWidth = 0f
    private var backingHeight = 0f
    private var contentWidth = 0f
    private var contentHeight = 0f

    private var positionX = 0f
    private var positionY = 0f
    private var zoom = 1f
    private var rotationRadians = 0f

    private var projection = Matrix4f()
    private var viewProjection = Matrix4f()

    // Pass both the physical backing window size and the logical UI content size
    fun setDimensions(backingWidth: Float, backingHeight: Float, contentWidth: Float, contentHeight: Float) {
        this.backingWidth = backingWidth
        this.backingHeight = backingHeight
        this.contentWidth = contentWidth
        this.contentHeight = contentHeight
        rebuild()
    }

    fun setOrtho(width: Float, height: Float) = setDimensions(width, height, width, height)

    fun setPosition(x: Float, y: Float) {
        positionX = x
        positionY = y
        rebuild()
    }

    fun setZoom(scale: Float) {
        zoom = scale
        rebuild()
    }

    fun zoom(factor: Float) {
        zoom *= factor
        rebuild()
    }

    fun setRotation(radians: Float) {
        rotationRadians = radians
        rebuild()
    }

    private fun rebuild() {
        // Projection is based on the actual OpenGL backing framebuffer
        projection = Matrix4f.ortho(backingWidth / zoom, backingHeight / zoom)

        // Calculate how much we need to shift to center the content
        val offsetX = (backingWidth - contentWidth) / 2f
        val offsetY = (backingHeight - contentHeight) / 2f

        val view = Matrix4f.identity()
            // 5. Shift to OpenGL center coordinates
            .translate(positionX - backingWidth / 2f, positionY - backingHeight / 2f)
            // 4. Center the content inside the larger backing window
            .translate(offsetX, offsetY)
            // 3. Move the origin back to the content's rotation center
            .translate(contentWidth / 2f, contentHeight / 2f)
            // 2. Rotate around the center of the content
            .rotate(rotationRadians)
            // 1. Move logical (0,0) so the content center is at the origin
            .translate(-contentWidth / 2f, -contentHeight / 2f)

        viewProjection = projection * view
    }

    fun screenToWorld(screenPos: Vector2D): Vector2D {
        // Because of the math in rebuild(), reversing the matrices elegantly
        // collapses down to this! The offsets annihilate each other.

        // 1. Undo zoom relative to the backing window center
        val vx = (screenPos.x - backingWidth / 2.0) / zoom
        val vy = (screenPos.y - backingHeight / 2.0) / zoom

        // 2. Undo Camera Position
        val tx = vx - positionX
        val ty = vy - positionY

        // 3. Undo Rotation
        val invRot = -rotationRadians.toDouble()
        val c = kotlin.math.cos(invRot)
        val s = kotlin.math.sin(invRot)

        val rx = tx * c - ty * s
        val ry = tx * s + ty * c

        // 4. Shift back to the Content's top-left origin
        val wx = rx + contentWidth / 2.0
        val wy = ry + contentHeight / 2.0

        return Vector2D(wx, wy)
    }

    fun getViewProjection(): FloatArray = viewProjection.toFloatArray()

    fun lookAt(x: Float, y: Float) {
        setPosition(-x + backingWidth / 2f, -y + backingHeight / 2f)
    }
}