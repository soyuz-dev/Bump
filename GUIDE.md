
# Bump — User Guide

A quickstart guide to building things with Bump. For architecture details and design philosophy, see [README.md](README.md).

The DSL is the easiest way to get started. The imperative API is still available when you want direct control.

## Table of Contents
1. [Installation](#installation)
2. [Minimal Program](#minimal-program)
3. [Entities & Shapes](#entities--shapes)
4. [Rendering](#rendering)
5. [Scene Management](#scene-management)
6. [Physics](#physics)
7. [Joints](#joints)
8. [Input & Behaviours](#input--behaviours)
9. [UI System](#ui-system)
10. [Audio](#audio)
11. [Timers & Game Loop](#timers--game-loop)
12. [Windowing](#windowing)
13. [DSL Demo Examples](#dsl-demo-examples)
14. [Camera & Screen Effects](#camera--screen-effects)
15. [Observable Entities](#observable-entities)
16. [Where Files Go](#where-files-go)
17. [Common Issues and Quick Fixes](#common-issues-and-quick-fixes)
18. [File Format Issues](#file-format-issues)
19. [Quick Reference](#quick-reference)

---

## Installation

### As a Dependency (JitPack)

Add JitPack to your `settings.gradle.kts` under `dependencyResolutionManagement.repositories` (or your project's equivalent repository configuration):

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}
```

Add Bump and the LWJGL natives to your `build.gradle.kts`:

```kotlin
val lwjglNatives = when {
    System.getProperty("os.name").contains("Windows") -> "natives-windows"
    System.getProperty("os.name").contains("Linux") -> "natives-linux"
    System.getProperty("os.name").contains("Mac") -> "natives-macos"
    else -> throw GradleException("Unsupported OS")
}

dependencies {
    implementation("com.github.soyuz-dev:Bump:v1.6-alpha")

    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-openal::$lwjglNatives")
}
```

The `v1.6-alpha` dependency will resolve once the Git tag has been published and built by JitPack. Match your other LWJGL dependencies to the version used by Bump.

### From Source

```bash
git clone https://github.com/soyuz-dev/Bump.git
cd Bump
./gradlew build
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

---

## Minimal Program

```kotlin
import org.soyuz.engine.dsl.*
import org.soyuz.engine.render.SolidColor
import org.soyuz.engine.shape.CircleShape
import org.soyuz.util.Color
import org.soyuz.util.math.Vector2D

fun main() = bump {
    window("Hello Bump", 800, 600) {
        scene("main") {
            entity("circle") {
                position = Vector2D(400.0, 300.0)
                shape = CircleShape(50.0)
                painter = SolidColor(Color(255, 100, 80))
            }
        }
    }
}
```

That's a window, an engine, a scene, and a visible circle. No manual `Application.init()`, `RuntimeEngine`, or `Application.run()`.

### The Imperative API

If you want to wire things up yourself, you still can:

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

    engine.loadScene(RuntimeScene("main"))
    Application.run()
}
```

Both APIs use the same runtime components. The DSL handles setup; it doesn't hide the engine from you.

---

## Entities & Shapes

Entities are the core of Bump. Everything — physics objects, UI elements, sprites — can be made with a `GameEntity`.

### With the DSL

```kotlin
// Inside scene("main") { ... }
val circle = entity("my_circle") {
    position = Vector2D(400.0, 300.0)
    shape = CircleShape(50.0)
    painter = SolidColor(Color(255, 100, 80))
}

// `circle` is a real entity, not a DSL-only representation.
circle.position = Vector2D(450.0, 300.0)
```

You can call the scene's entity builder at runtime, too — that's how BrickPit DSL spawns new balls and bricks after the game starts.

### Direct Entity Creation

```kotlin
val circle = DefaultGameEntity("my_circle")
circle.position = Vector2D(400.0, 300.0)
circle.shape = CircleShape(50.0)
circle.painter = SolidColor(Color(255, 100, 80))
scene.addEntity(circle)
```

### The Fluent Entity Builder

Bump also provides `infix` functions for declarative entity creation:

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

**Colliders** can be created from shapes:

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
entity.painter = ImagePainter(Assets.texture("cat"))  // /textures/cat.png
```

### Text

```kotlin
val font = Assets.font("roboto")  // /fonts/roboto.ttf
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

Scenes hold collections of entities. You can create multiple scenes and switch between them. **Switching doesn't remove the old scene's entities.**

### With the DSL

```kotlin
// Inside window("Game", 800, 600) { ... }
val menu = scene("menu") {
    entity("menu_background") {
        position = Vector2D(400.0, 300.0)
        shape = RectangleShape(500.0, 300.0)
        painter = SolidColor(Color(60, 120, 200))
    }
}

val game = scene("game") {
    entity("player") {
        position = Vector2D(400.0, 300.0)
        shape = CircleShape(30.0)
        painter = SolidColor(Color(255, 100, 80))
    }
}

startWith("menu")

// Switch later:
engine.loadScene(game)
```

You can reuse an entity ID in different scenes. Each DSL scene has its own physics context, so a `player` in one scene doesn't share physics registrations with a `player` in another.

### Imperative Scene Creation

```kotlin
val menuScene = RuntimeScene("menu")
val gameScene = RuntimeScene("game")

engine.loadScene(menuScene)

// Later:
engine.loadScene(gameScene)
```

Scenes persist in memory when switched away from. If you permanently dispose of a scene, call `scene.cleanup()` when it is no longer active and won't be reused.

---

## Physics

### Physics in the DSL

```kotlin
val gravity = ConstantAccelerationField(Vector2D(0.0, 981.0))

entity("ball") {
    position = Vector2D(300.0, 200.0)
    shape = CircleShape(20.0)
    painter = SolidColor(Color(255, 100, 80))

    physics {
        body = PointMass(mass = 1.0, restitution = 0.7) with gravity
        collider = Collider(CircleShape(20.0))
    }
}
```

The DSL registers the body and collider with that scene's physics systems. No separate registration calls needed.

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

body.addField(gravity)

// Or with infix (import org.soyuz.engine.physics.with):
val fallingBody = PointMass(mass = 1.0) with gravity
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

// Constant thrust
body.addField(VelocityForceField(Polynomial(-0.5)))
```

---

## Joints

Connect two bodies together:

```kotlin
val rod = RodJoint(bodyA, bodyB, restLength = 150.0)
val rope = RopeJoint(bodyA, bodyB, maxLength = 200.0)
val spring = SpringJoint(bodyA, bodyB, restLength = 100.0, stiffness = 500.0, damping = 10.0)

physicsSystem.addJoint(rod)
```

- `RodJoint` — rigid bar, exact length
- `RopeJoint` — can go slack, constrains max distance only
- `SpringJoint` — soft, force-based with damping

For a scene created through the DSL, use that scene's `physicsContext.physicsSystem` when adding joints directly. Bodies connected by a joint should belong to the same scene.

---

## Input & Behaviours

### Scene-Scoped Keyboard Input

```kotlin
// Inside scene("game") { ... }
onKeyDown(GLFW_KEY_SPACE) {
    println("Jump!")
}

onKeyUp(GLFW_KEY_SPACE) {
    println("Space released")
}

onKeyHeld(GLFW_KEY_RIGHT) { dt ->
    player.position += Vector2D(200.0 * dt, 0.0)
}
```

The callbacks belong to the scene. They only run while that scene is active.

### Entity-Owned Subscriptions

```kotlin
val player = entity("player") {
    position = Vector2D(400.0, 300.0)
    shape = CircleShape(20.0)
    painter = SolidColor(Color(255, 100, 80))
}

onKeyDown(GLFW_KEY_SPACE, owner = player) {
    println("Player jumped")
}
```

When the entity is removed, its owned keyboard subscriptions are cancelled too.

You can also cancel a subscription yourself:

```kotlin
val subscription = onKeyDown(GLFW_KEY_R) {
    println("Reload")
}

subscription.cancel()
```

### Collision Events

```kotlin
onCollisionEvent { event ->
    when (event.type) {
        CollisionEventType.ENTER -> println("Contact started")
        CollisionEventType.STAY  -> println("Still touching")
        CollisionEventType.EXIT  -> println("Contact ended")
    }
}
```

For entity-level callbacks, use `onCollision` inside the entity builder. This handles new contacts; scene-level events expose all three phases.

### Mouse Input

The scene DSL doesn't have `onMouseDown` yet. You can still use the existing per-window input APIs or `MouseListener` in a frame callback:

```kotlin
// Inside a window/scene DSL where `engine` and `window` are available
engine { dt ->
    if (MouseListener.isMouseJustPressed(window.handle, GLFW_MOUSE_BUTTON_LEFT)) {
        val mouseWorld = camera.screenToWorld(MouseListener.getPos(window.handle))
        println("Clicked at $mouseWorld")
    }
}
```

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

The UI factory and `Interactive` APIs work with both DSL-created and manually created scenes.

---

## Audio

```kotlin
val sound = AudioSource()
sound.play(Assets.audio("meow"))    // /audio/meow.ogg

sound.volume = 0.5f
sound.pitch = 1.2f
sound.looping = true
sound.stop()
```

Audio files go in `src/main/resources/audio/`. OGG Vorbis only. `Application.init()` sets up the audio system; `bump {}` takes care of that for you.

An `AudioSource` can also be played from an interaction or timer. For example:

```kotlin
engine.forEvery(500.milliseconds) {
    AudioSource().play(Assets.audio("click"))
}
```

See `AVTestDSL.kt` for audio mixed with click interactions, UI, and camera effects.

On WSL2, audio may require:

```bash
export PULSE_SERVER=unix:/mnt/wslg/PulseServer
```

---

## Timers & Game Loop

The behaviour API doesn't replace the frame loop. You can use both.

Timer delays and intervals use `kotlin.time.Duration`. Import the units used in these examples:

```kotlin
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
```

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
engine.after(2.seconds) {
    println("2 seconds passed")
}

// Repeating interval
engine.forEvery(1.seconds) {
    println("Every second")
}

// For a duration (fires every frame with progress 0..1)
engine.during(500.milliseconds) { progress ->
    entity.position = start + (end - start) * progress
}
```

Frame callbacks still receive `dt` as a `Double` in seconds; `during` receives normalized `Double` progress from 0 to 1. Delays must be finite and non-negative; repeating intervals and `during` durations must be finite and positive. A zero delay is deferred rather than executed inline. Coroutine-based `wait(Duration)` behaviour is planned, but not part of v1.6-alpha.

All four timer methods return a `Subscription`, including the `engine { ... }` shorthand:

```kotlin
val spawning = engine.forEvery(1.seconds) { spawnEnemy() }
spawning.cancel()
```

Cancellation is immediate and safe inside callbacks, including during interval catch-up. Completed one-shot and `during` timers become inactive before their final callback. Engine timers continue across scene switches and are cancelled when the engine stops.

Use scene timers for gameplay that should pause while its scene is inactive:

```kotlin
scene("game") {
    val spawning = forEvery(2.seconds) { spawnEnemy() }
    after(30.seconds) { spawning.cancel() }
    during(500.milliseconds) { progress -> animateIntro(progress) }
    everyFrame { dt -> updateGameplay(dt) }
}
```

These methods also exist on `Scene` and `RuntimeScene` and return cancellable subscriptions. Each scene owns its scheduler. Inactive scenes preserve elapsed time and pending registrations; cleanup cancels both active and pending timers. Scene timers run after engine timers. If a scene changes earlier in an engine update, neither the old nor the new scene's timers advance in that update. If a scene timer switches scenes, remaining callbacks and interval catch-up stop immediately, even if the callback switches back. The new scene begins advancing on the next engine update. Timers skipped after a switch do not accrue that interrupted update's elapsed time.

`TimerScheduler` in `org.soyuz.engine.timing` owns timer storage and execution independently of the engine. Registrations made before an update start accumulating in that update; registrations made inside callbacks wait until the next update. This applies to `everyFrame` too. A zero elapsed duration does not advance or dispatch timers. The scheduler accepts `Duration` in `update`, so it can also be driven without a window. Use it on one thread; recursive updates are rejected. A failing callback cancels its timer and propagates the exception, leaving later timers unadvanced for that update.

---

## Windowing

### Standard Window (DSL)

```kotlin
bump {
    window("My Game", 800, 600) {
        scene("main") {
            // entities go here
        }
    }
}
```

### Standard Window (Imperative)

```kotlin
val window = Window("My Game", 800, 600)
val engine = RuntimeEngine(window, physicsSystem, camera)
Application.windows.add(window, engine) { /* setup */ }
```

### Transparent Window

A transparent window is borderless, with only its rendered content visible. Use the `createWindow` factory to configure it through the DSL:

```kotlin
bump {
    window(
        "Overlay",
        400,
        300,
        createWindow = ::TransparentWindow
    ) {
        scene("main") {
            // ...
        }
    }
}
```

Or construct it directly through the imperative API:

```kotlin
val window = TransparentWindow("Overlay", 400, 300)
```

`createWindow` accepts a function that constructs a `Window` (or subclass) from a title, width, and height. The default is a normal `Window`, so existing DSL programs don't need to change.

**Note:** On NVIDIA GPUs, transparency may require this control panel setting:
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
window.camera.setZoom(0.5f)
window.camera.setRotation(Math.PI.toFloat() / 4)
window.camera.setPosition(100f, 50f)
```

The content rotates around its center. The backing window is sized to the diagonal of the content, so corners never clip. Mouse hit-testing works correctly at any rotation.

See `CustomWindowTest.kt` for a full example.

> **Warning:** Custom windows are in alpha. Content is not yet stencil-clipped to the rotated rect — entities near the edges may render into the transparent margins.

### Multiple Windows

```kotlin
bump {
    window("Triangle", 600, 500) {
        scene("triangle") { /* ... */ }
    }

    window("Circle", 600, 500) {
        scene("circle") { /* ... */ }
    }
}
```

Each window has its own engine, scene, and UI system. Input is per-window. See `MultiWindowDSL.kt` for a working example.

---

## DSL Demo Examples

The demos are in `org.soyuz`:

- **`MainDsl.kt` (BrickPit):** Physics sandbox with balls and bricks spawned at runtime, dragging, and collision sounds.
- **`MultiSceneDSL.kt`:** Persistent scenes switched using scene-scoped keyboard callbacks.
- **`MultiWindowDSL.kt`:** Two separate windows, engines, and animations.
- **`AVTestDSL.kt`:** Transparent window with clickable cat and dog images, audio, labels, a pulsing circle, timers, and camera shake.

All four DSL demos have been tested successfully as running applications.

---

## Camera & Screen Effects

### Following a Target

```kotlin
var cameraX = trackedEntity.position.x.toFloat()
var cameraY = trackedEntity.position.y.toFloat()

engine { dt ->
    cameraX += (trackedEntity.position.x.toFloat() - cameraX) * 0.1f
    cameraY += (trackedEntity.position.y.toFloat() - cameraY) * 0.1f
    camera.lookAt(cameraX, cameraY)
}
```

`lookAt(x, y)` points the camera at a world position. The math to convert that to screen-relative offset is handled internally. Use `camera.setPosition(...)` directly only when you need raw control (e.g., camera shake).

### Camera Shake

```kotlin
engine.during(500.milliseconds) { progress ->
    val intensity = (1.0 - progress) * 20.0
    val ox = ((Math.random() - 0.5) * intensity).toFloat()
    val oy = ((Math.random() - 0.5) * intensity).toFloat()
    camera.setPosition(ox, oy)
}
engine.after(500.milliseconds) {
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

```kotlin
engine.during(1.seconds) { progress ->
    val eased = Easing.cubicOut(progress)
    entity.position = start + (end - start) * eased
}
```

**Available easings:** `linear`, `quadIn/Out/InOut`, `cubicIn/Out/InOut`, `sinIn/Out`, `expoIn/Out`, `bounceOut`, `elasticOut`, `backOut`, and more. See `Easing` for the full list.

---

## Observable Entities

Entities fire callbacks when properties change:

```kotlin
entity.onPositionChanged { newPos ->
    label.position = newPos + Vector2D(0.0, -50.0)
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

```text
src/main/resources/
├── shaders/     — default.vert, default.frag (GLSL 330 core)
├── textures/    — PNG images only
├── fonts/       — TTF fonts only
└── audio/       — OGG Vorbis only
```

---

## Common Issues and Quick Fixes

### Window opens but nothing renders
- Are your entities positioned within the visible area?
- Do visible entities have both a shape and a painter?
- If you're using the imperative API, did you add the entities to the scene and load it with `engine.loadScene(scene)`?

### Entity doesn't move with physics
- Is the mass zero? `mass = 0.0` means infinite mass — the body won't move.
- Did you give it an initial velocity or add a force field?
- With the DSL, put the body in `physics { body = ... }`. With the imperative API, register it with the physics system that updates the scene.

### Collisions aren't working
- Both objects need physics bodies and colliders.
- Check that the entities are in the same scene; physics state is scene-specific.
- `onCollision` runs when a contact begins. For `ENTER`, `STAY`, and `EXIT`, use `onCollisionEvent`.

### Keyboard input isn't responding
- Is the intended window focused?
- Scene-scoped callbacks only run while their scene is active.
- An entity-owned subscription stops receiving callbacks when its entity is removed.

### Text doesn't appear
- Does the font name match the file in `resources/fonts/`?
- Is the text colour visible against the background?
- If you're constructing `TextPainter` manually, check its text and font size.

### Audio doesn't play
- Audio must be OGG Vorbis. Renaming a WAV or MP3 file to `.ogg` won't convert it.
- Check that the file exists under `resources/audio/`.
- On WSL2, try `export PULSE_SERVER=unix:/mnt/wslg/PulseServer`.

### Multi-window rendering looks wrong
- Each window has its own OpenGL context. Avoid reusing a shader or texture from one context in another unless you've deliberately set up sharing.
- Use `Assets` to load resources for the window's current context.

### Transparent window appears opaque on NVIDIA
- Open NVIDIA Control Panel → 3D Settings → Vulkan/OpenGL present method → set to "Prefer native".

### Rotated custom window has visual artefacts
- Content is not yet stencil-clipped to the rotated rectangle. Entities near the edges may render into the transparent margins or appear visually outside the window.

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
- **What happens if wrong:** The decoder may fail or playback may be silent if the codec doesn't match.
- **Fix:** Convert to OGG Vorbis using Audacity or ffmpeg. Renaming `.wav` to `.ogg` will **not** work.

### Shaders (`/shaders/`)
- **Expected:** GLSL 330 core (`.vert`, `.frag`)
- **What happens if wrong:** Shaders may fail compilation or render incorrectly.
- **Fix:** Shaders must declare `#version 330 core` and match the engine's uniform names (`uProjection`, `uModel`, `uColor`, `uTexture`, `uUseTexture`).

---

## Quick Reference

| Task                  | Code                                                                |
|-----------------------|---------------------------------------------------------------------|
| Start the DSL         | `bump { ... }`                                                      |
| Create window         | `window("Title", 800, 600) { ... }`                                 |
| Custom window factory | `window("Title", w, h, createWindow = ::TransparentWindow) { ... }` |
| Create scene          | `scene("game") { ... }`                                             |
| Create entity         | `entity("ball") { ... }`                                            |
| Set position          | `position = Vector2D(x, y)`                                         |
| Set shape             | `shape = CircleShape(r)`                                            |
| Set colour            | `painter = SolidColor(Color(r, g, b))`                              |
| Add physics in DSL    | `physics { body = ...; collider = ... }`                            |
| Key press callback    | `onKeyDown(key) { ... }`                                            |
| Key held callback     | `onKeyHeld(key) { dt -> ... }`                                      |
| Collision events      | `onCollisionEvent { event -> ... }`                                 |
| Cancel subscription   | `subscription.cancel()`                                             |
| Switch scene          | `engine.loadScene(scene)`                                           |
| Every frame           | `engine { dt -> ... }`                                              |
| After delay           | `engine.after(2.seconds) { ... }`                                   |
| Load font             | `Assets.font("roboto")`                                             |
| Load texture          | `Assets.texture("cat")`                                             |
| Load audio            | `Assets.audio("meow")`                                              |
| Make button           | `engine.ui.button("id", x, y, w, h) { ... }`                        |
| Make label            | `engine.ui.label("id", x, y, "text", font, size, color)`            |
| Transparent window    | `TransparentWindow("title", w, h)`                                  |
| Custom window         | `CustomWindow("title", w, h, font)`                                 |
| Rotate window         | `customWindow.camera.setRotation(radians)`                          |
| Set Camera Position   | `camera.lookAt(x, y)`                                               |
