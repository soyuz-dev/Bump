# Bump — User Guide

A quickstart guide to building things with Bump. For architecture details and design philosophy, see [README.md](README.md).

## Table of Contents
1. [Installation](#installation)
2. [Minimal Program](#minimal-program)
3. [Entities & Shapes](#entities--shapes)
4. [Rendering](#rendering)
5. [Scene Management](#scene-management)
6. [Physics](#physics)
7. [Joints](#joints)
8. [UI System](#ui-system)
9. [Audio](#audio)
10. [Timers & Game Loop](#timers--game-loop)
11. [Windowing](#windowing)
12. [Camera & Screen Effects](#camera--screen-effects)
13. [Observable Entities](#observable-entities)
14. [Where Files Go](#where-files-go)
15. [Common Issues and Quick Fixes](#common-issues-and-quick-fixes)
16. [File Format Issues](#file-format-issues)
17. [Quick Reference](#quick-reference)

---

## Installation

### As a Dependency (JitPack)

Add JitPack to your `settings.gradle.kts`:
```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}
```

Add Bump and LWJGL natives to your `build.gradle.kts`:
```kotlin
val lwjglV = "3.3.3"
val lwjglNatives = when {
    System.getProperty("os.name").contains("Windows") -> "natives-windows"
    System.getProperty("os.name").contains("Linux") -> "natives-linux"
    System.getProperty("os.name").contains("Mac") -> "natives-macos"
    else -> throw GradleException("Unsupported OS")
}

dependencies {
    implementation("com.github.soyuz-dev:Bump:v1.5-alpha")
    
    // LWJGL natives (required — platform-specific binaries)
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-openal::$lwjglNatives")
}
```

### From Source
```bash
git clone https://github.com/soyuz-dev/Bump.git
cd Bump
./gradlew build
```

---

## Minimal Program

```kotlin
import org.soyuz.engine.core.Application
import org.soyuz.engine.core.RuntimeEngine
import org.soyuz.engine.render.*
import org.soyuz.engine.scene.RuntimeScene
import org.soyuz.windowing.Window
import org.soyuz.util.Assets

fun main() {
    Application.init()
    val camera = Camera()
    val window = Window("Hello Bump", 800, 600)
    val engine = RuntimeEngine(window, physicsSystem = null, camera)
    
    Application.windows.add(window, engine) {
        engine.shader = Assets.shader("default")
        engine.renderSystem = RuntimeRenderSystem(Mesh.quad(), Mesh.circle(32))
    }
    
    val scene = RuntimeScene("main")
    engine.loadScene(scene)
    Application.run()
}
```

This creates a blank window. No entities yet, just the engine running.

---

## Entities & Shapes

Entities are the core of Bump. Everything — physics objects, UI elements, sprites — can be made with a `GameEntity`.

```kotlin
val circle = DefaultGameEntity("my_circle")
circle.position = Vector2D(400.0, 300.0)       // world position
circle.shape = CircleShape(50.0)               // 50px radius
circle.painter = SolidColor(Color(255, 100, 80)) // orange-red
scene.addEntity(circle)
```

### The Fluent Entity Builder

Bump provides `infix` functions for declarative entity creation:

```kotlin
DefaultGameEntity("ball") at
    Vector2D(400.0, 300.0) with
    CircleShape(50.0) with
    SolidColor(Color(255, 100, 80)) with
    Collider(CircleShape(50.0)) within
    scene
```

**Available chain methods:**
- `at(position)` — set the entity's world position
- `with(shape)` — set the entity's shape
- `with(painter)` — set the entity's painter (color, texture, text)
- `with(collider)` — set the entity's collider
- `within(scene)` — add the entity to a scene

**Shapes available:**
- `CircleShape(radius)` — circle
- `RectangleShape(width, height)` — rectangle
- `TriangleShape(a, b, c)` — arbitrary triangle
- `TriangleShape.equilateral(size)` — equilateral triangle
- `TriangleShape.isosceles(width, height)` — isosceles triangle

**Colliders** are created automatically from shapes:
```kotlin
circle.collider = Collider(circle.shape!!)
```

The `Collider(shape)` factory returns the right collider type (`CircleCollider`, `RectangleCollider`, `TriangleCollider`).

---

## Rendering

### Solid Colors
```kotlin
entity.painter = SolidColor(Color(255, 100, 80))        // RGB 0-255
entity.painter = SolidColor(Color(255, 100, 80, 128))   // with alpha
```

### Textures (Images)
```kotlin
entity.painter = ImagePainter(Assets.texture("cat"))  // loads /textures/cat.png
```

### Text
```kotlin
val font = Assets.font("roboto")  // loads /fonts/roboto.ttf
val textPainter = TextPainter(font)
textPainter.text = "Score: 100"
textPainter.fontSize = 24f
textPainter.color = Color(255, 255, 255)
entity.painter = textPainter
```

### Custom Colors
```kotlin
Color(255, 128, 0)           // orange
Color(255, 100, 80, 150)     // semi-transparent
Color.random()                // random color
```

---

## Scene Management

### Creating and Switching Scenes

Scenes hold collections of entities. You can create multiple scenes and switch between them.

```kotlin
val menuScene = RuntimeScene("menu")
val gameScene = RuntimeScene("game")

// Add entities to each scene
DefaultGameEntity("play_btn") at
    Vector2D(400.0, 300.0) with
    RectangleShape(200.0, 50.0) with
    SolidColor(Color(60, 120, 200)) within
    menuScene

// Start with the menu
engine.loadScene(menuScene)

// Switch scenes at runtime
engine { dt ->
    if (startGame) {
        engine.loadScene(gameScene)
    }
}
```

**Note:** Scenes persist in memory when you switch away from them. Entities are not removed — they stay loaded for instant switching back. If you need to free memory, call `scene.cleanup()` manually on scenes you won't revisit.

---

## Physics

### PointMass (no rotation)
```kotlin
val body = PointMass(mass = 1.0, restitution = 0.7)
body.velocity = Vector2D(200.0, -300.0)
physicsSystem.registerBody("my_circle", body)
```

### RigidBody (with rotation)
```kotlin
val body = RigidBody(mass = 2.0, restitution = 0.5, friction = 0.4, width = 60.0, height = 40.0)
body.angularVelocity = 5.0  // spin
physicsSystem.registerBody("my_brick", body)
```

### Infinite Mass
Set mass to `0.0` for immovable objects (walls, floors):
```kotlin
val wall = PointMass(mass = 0.0)
```

### Force Fields
```kotlin
val gravity = ConstantAccelerationField(Vector2D(0.0, 981.0))

// Add to body:
body.addField(gravity)

// Or with infix:
val body = PointMass(mass = 1.0) with gravity
```

**Available forces:**
- `ConstantForceField(force)` — constant force
- `ConstantAccelerationField(acceleration)` — force proportional to mass
- `VelocityForceField(Polynomial(...))` — drag or thrust based on speed
- `GravityField(G)` — n-body gravitational attraction

### Drag & Thrust via Polynomials
```kotlin
// Linear drag: force = -velocity * 0.5
body.addField(VelocityForceField(Polynomial.linear(0.5)))

// Quadratic air resistance: force = -velocity * 0.1 * |velocity|
body.addField(VelocityForceField(Polynomial.quadratic(0.1)))

// Constant thrust: always push forward
body.addField(VelocityForceField(Polynomial(-0.5)))  // negative = thrust!
```

---

## Joints

Connect two bodies together:

```kotlin
val rod = RodJoint(bodyA, bodyB, restLength = 150.0)     // fixed distance
val rope = RopeJoint(bodyA, bodyB, maxLength = 200.0)    // one-way (can be closer)
val spring = SpringJoint(bodyA, bodyB, restLength = 100.0, stiffness = 500.0, damping = 10.0)

physicsSystem.addJoint(rod)
```

- `RodJoint` — rigid bar, exact length
- `RopeJoint` — can go slack, constrains max distance only
- `SpringJoint` — soft, force-based with damping

---

## UI System

### Buttons
```kotlin
val btn = engine.ui.button("play", 400.0, 300.0, 200.0, 50.0) {
    println("Clicked!")
}
scene.addEntity(btn)
```

### Labels
```kotlin
val label = engine.ui.label("score", 10.0, 10.0, "Score: 0", font, 20f, Color.WHITE)
scene.addEntity(label)
```

### Sliders
```kotlin
val (track, thumb) = engine.ui.slider("volume", 400.0, 500.0, 300.0, 30.0,
    min = 0.0, max = 100.0, initial = 50.0
) { value -> println("Volume: $value") }
scene.addEntity(track)
scene.addEntity(thumb)
```

### Text Input
```kotlin
val (input, bg) = engine.ui.textInput("name", 400.0, 300.0, font = font,
    placeholder = "Enter name...",
    onSubmit = { text -> println("Hello, $text!") }
)
scene.addEntity(bg)
scene.addEntity(input)
```

### Custom Interactive Entities
Chain modifiers for custom behavior:
```kotlin
entity.interactive = Interactive { pos -> entity.collider!!.containsPoint(pos, entity.transform) }
    .clickable { println("clicked") }
    .hoverable(
        onHoverEnter = { entity.painter = hoverColor },
        onHoverExit = { entity.painter = normalColor }
    )
    .draggable(
        onDrag = { _, currentPos -> entity.position = currentPos }
    )
```

**Available modifiers:** `clickable`, `hoverable`, `draggable`, `scrollable`, `focusable`, `doubleClickable`, `keyboardInput`

---

## Audio

```kotlin
// One-time setup (called by Application.init())
AudioSystem.init()

// Load and play
val sound = AudioSource()
sound.play(Assets.audio("meow"))    // loads /audio/meow.ogg

// Controls
sound.volume = 0.5f
sound.pitch = 1.2f
sound.looping = true
sound.stop()
```

Audio files go in `src/main/resources/audio/`. OGG format only.

**WSL2 Audio Setup:**
```bash
echo 'export PULSE_SERVER=unix:/mnt/wslg/PulseServer' >> ~/.bashrc
source ~/.bashrc
```

---

## Timers & Game Loop

```kotlin
// Every frame
engine.everyFrame { dt ->
    // dt = seconds since last frame
}

// Or with invoke shorthand:
engine { dt ->
    // runs every frame
}

// After a delay (one-shot)
engine.after(2000.0) {
    println("2 seconds passed")
}

// Repeating interval
engine.forEvery(1000.0) {
    println("Every second")
}

// For a duration (fires every frame with progress 0..1)
engine.during(500.0) { progress ->
    entity.position = start + (end - start) * progress
}
```

---

## Windowing

### Standard Window

```kotlin
val window = Window("My Game", 800, 600)
val engine = RuntimeEngine(window, physicsSystem, camera)
Application.windows.add(window, engine) { /* setup */ }
```

### Transparent Window
```kotlin
val window = TransparentWindow("Overlay", 400, 300)
// Window is borderless and transparent — only rendered content is visible
```

**Note:** On NVIDIA GPUs, transparency requires a control panel setting:
Vulkan/OpenGL present method → "Prefer native"

### Custom Window

```kotlin
val window = CustomWindow("My App", 600, 400, "roboto")
window.x = 200; window.y = 200
window.addEntity(myContentEntity)
window.show()
```

Creates a transparent window with pre-built title bar, close button, and drag-to-move. Content entities are added normally. The window's `camera` is exposed for full control:

```kotlin
// Zoom
window.camera.setZoom(0.5f)

// Rotate the entire content
window.camera.setRotation(Math.PI.toFloat() / 4)

// Pan
window.camera.setPosition(100f, 50f)
```

The content rotates around its center. The backing window is sized to the diagonal of the content, so corners never clip. Mouse hit-testing works correctly at any rotation.

See `CustomWindowTest.kt` for a full example.

> **Warning:** Custom windows are in alpha. Tested on Windows with Intel and NVIDIA GPUs, and WSLg. Content is not yet stencil-clipped to the rotated rect — entities near the edges may render into the transparent margins.

### Multiple Windows

```kotlin
val window1 = Window("Window 1", 800, 600)
val window2 = Window("Window 2", 600, 400)
val engine1 = RuntimeEngine(window1, null, camera1)
val engine2 = RuntimeEngine(window2, null, camera2)

Application.windows.add(window1, engine1) { /* setup */ }
Application.windows.add(window2, engine2) { /* setup */ }

Application.run()  // runs both windows
```

Each window has its own engine, scene, and UI system. Input is per-window.

---

## Camera & Screen Effects

### Following a Target
```kotlin
var cameraX = trackedEntity.position.x.toFloat()
var cameraY = trackedEntity.position.y.toFloat()

engine { dt ->
    // Smooth follow
    cameraX += (trackedEntity.position.x.toFloat() - cameraX) * 0.1f
    cameraY += (trackedEntity.position.y.toFloat() - cameraY) * 0.1f
    camera.lookAt(cameraX, cameraY)
}
```
`lookAt(x, y)` points the camera at a world position. The math to convert that to screen-relative offset is handled internally. Use `camera.setPosition(...)` directly only when you need raw control (e.g., camera shake).

### Camera Shake
```kotlin
engine.during(500.0) { progress ->
    val intensity = (1.0 - progress) * 20.0
    val ox = ((Math.random() - 0.5) * intensity).toFloat()
    val oy = ((Math.random() - 0.5) * intensity).toFloat()
    camera.setPosition(ox, oy)
}
engine.after(500.0) {
    camera.setPosition(0f, 0f)
}
```

### Zoom
```kotlin
camera.setZoom(2.0f)   // zoom out (see more)
camera.setZoom(0.5f)   // zoom in (magnify)
camera.zoom(1.1f)       // multiply current zoom by 1.1
```

### Rotation
```kotlin
camera.setRotation(Math.PI.toFloat() / 4)  // rotate 45 degrees
camera.setRotation(0f)                      // reset
```

Rotation is centered on the content. Mouse hit-testing converts screen coordinates to world coordinates via `camera.screenToWorld()`, so UI interactions work correctly under any rotation.

### Easing Functions
Use the `Easing` library for smooth animations:
```kotlin
engine.during(1000.0) { progress ->
    val eased = Easing.cubicOut(progress)
    entity.position = start + (end - start) * eased
}
```

**Available easings:** `linear`, `quadIn/Out/InOut`, `cubicIn/Out/InOut`, `sinIn/Out/InOut`, `expoIn/Out`, `bounceOut`, `elasticOut`, `backOut`, and more. See `Easing` for the full list.

---

## Observable Entities

Entities fire callbacks when properties change:

```kotlin
entity.onPositionChanged { newPos ->
    label.position = newPos + Vector2D(0.0, -50.0)  // label follows entity
}

entity.onRotationChanged { newRotation ->
    indicator.rotation = newRotation
}

entity.onShapeChanged { newShape ->
    // update collider, etc.
}
```

---

## Where Files Go

```
src/main/resources/
├── shaders/     — default.vert, default.frag (GLSL 330 core)
├── textures/    — PNG images only
├── fonts/       — TTF fonts only
└── audio/       — OGG Vorbis only
```

---

## Common Issues and Quick Fixes

### "No context is current" crash
OpenGL functions must be called when a window's context is active. If you get this error, make sure you're calling GL operations after `engine.init()` or inside `engine.everyFrame { }`. The engine manages context switching automatically.

### Window opens but nothing renders
- Did you add entities to the scene? `scene.addEntity(entity)`
- Did you call `engine.loadScene(scene)` before `Application.run()`?
- Are your entities positioned within the visible area?

### Entity doesn't move with physics
- Did you register the body? `physicsSystem.registerBody(entity.id, body)`
- Did you register the collider? `collisionSystem.registerCollider(entity.id, collider)`
- Is the mass zero? `mass = 0.0` means infinite mass — the body won't move.
- Did you add forces? Bodies need force fields to accelerate.

### Collision detection isn't working
- Both entities must have colliders registered with the `CollisionSystem`.
- Both entities must have bodies registered with the `PhysicsSystem`.
- Collisions only fire `ENTER` once per contact.

### Text doesn't appear
- Is `GL_BLEND` enabled? The engine does this automatically.
- Did you call `textPainter.update(0f)` after setting text? UI factory methods handle this.
- Is the font loaded? Check `Assets.font("name")` matches the file in `resources/fonts/`.

### Audio doesn't play
- Did you call `Application.init()`? This initializes `AudioSystem`.
- Are your audio files OGG format? WAV is not supported.
- On WSL2: Set `export PULSE_SERVER=unix:/mnt/wslg/PulseServer`.

### Timer callbacks not firing
- Timers added during a timer callback are deferred to the next frame.
- `everyFrame` / `engine { }` must be called during setup.

### Multi-window textures/shaders appear white
- Each window needs its own OpenGL context. `Assets` handles this automatically.
- Don't share `Shader` or `Texture` objects between engines.

### Key presses not detected
- Input is per-window. Use `engine.input.isKeyDown(key)` or `KeyListener.isKeyDown(window.handle, key)`.
- Some key combinations don't work due to keyboard ghosting.

### Transparent window appears opaque on NVIDIA
- Open NVIDIA Control Panel → 3D Settings → Vulkan/OpenGL present method → set to "Prefer native".

### Custom window clicks don't register after rotation
- Make sure `UISystem` is using `camera.screenToWorld()` for hit-testing. The default `RuntimeEngine` setup does this automatically.
- If entities are added in the wrong z-order, the close button may be covered by the title bar drag overlay. Add the close button last.

---

## File Format Issues

### The file extension says one thing, the data says another

Bump uses the file extension to decide how to load a file. If the bytes inside don't match the extension, you'll get crashes, garbage output, or silent failures.

**Always verify your files, not just their names.**

### Textures (`/textures/`)
- **Expected:** PNG format
- **What happens if wrong:** STB Image detects the actual format from the file header. A `.png` file that's actually a JPEG will fail with "Failed to load texture."
- **Fix:** Re-export as PNG. Don't just rename `.jpg` to `.png`.

### Fonts (`/fonts/`)
- **Expected:** TrueType (`.ttf`)
- **What happens if wrong:** STB TrueType expects TTF format. An OpenType `.otf` renamed to `.ttf` may fail silently.
- **Fix:** Use actual TrueType fonts. Google Fonts lets you download TTF specifically.

### Audio (`/audio/`)
- **Expected:** OGG Vorbis (`.ogg`)
- **What happens if wrong:** STB Vorbis will crash, play garbage, or play nothing if the codec doesn't match.
- **Fix:** Convert to OGG Vorbis using Audacity, ffmpeg, or an online converter. Renaming `.wav` to `.ogg` will **not** work.

### Shaders (`/shaders/`)
- **Expected:** GLSL 330 core (`.vert`, `.frag`)
- **What happens if wrong:** Shader compiles but renders black/white or crashes the GPU driver.
- **Fix:** Shaders must declare `#version 330 core` and match the engine's uniform names (`uProjection`, `uModel`, `uColor`, `uTexture`, `uUseTexture`).

### How to check what a file actually is

**Windows (PowerShell):**
```powershell
Get-Content .\file.ogg -Encoding Byte -TotalCount 4
```

**Common magic numbers:**
| Format | First bytes (hex) |
|--------|-------------------|
| PNG    | `89 50 4E 47`     |
| OGG    | `4F 67 67 53`     |
| TTF    | `00 01 00 00`     |
| WAV    | `52 49 46 46`     |
| MP3    | `FF FB` or `ID3`  |

**Linux/macOS:**
```bash
file file.ogg
hexdump -C file.ogg | head -1
```

---

## Quick Reference

| Task               | Code                                                                      |
|--------------------|---------------------------------------------------------------------------|
| Create entity      | `DefaultGameEntity("id")`                                                 |
| Set position       | `entity.position = Vector2D(x, y)`                                        |
| Set shape          | `entity.shape = CircleShape(r)`                                           |
| Set color          | `entity.painter = SolidColor(Color(r,g,b))`                               |
| Add collider       | `entity.collider = Collider(shape)`                                       |
| Add physics        | `body = PointMass(mass) with gravity`                                     |
| Register physics   | `physicsSystem.registerBody(id, body)`                                    |
| Register collider  | `collisionSystem.registerCollider(id, collider)`                          |
| Add to scene       | `scene.addEntity(entity)`                                                 |
| Fluent entity      | `Entity("id") at pos with shape with painter within scene`                |
| Load font          | `Assets.font("roboto")`                                                   |
| Load texture       | `Assets.texture("cat")`                                                   |
| Load audio         | `Assets.audio("meow")`                                                    |
| Every frame        | `engine { dt -> ... }`                                                    |
| After delay        | `engine.after(ms) { ... }`                                                |
| Switch scene       | `engine.loadScene(scene)`                                                 |
| Make button        | `engine.ui.button("id", x, y, w, h) { ... }`                              |
| Make label         | `engine.ui.label("id", x, y, "text", font, size, color)`                  |
| Transparent window | `TransparentWindow("title", w, h)`                                        |
| Custom window      | `CustomWindow("title", w, h, font)`                                       |
| Rotate window      | `customWindow.camera.setRotation(radians)`                                |
| Zoom window        | `customWindow.camera.setZoom(scale)`                                      |
| Follow entity      | `camera.lookAt(target.position.x.toFloat(), target.position.y.toFloat())` |
