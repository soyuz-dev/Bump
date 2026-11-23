
# Bump v1.6-alpha

[![](https://jitpack.io/v/soyuz-dev/Bump.svg)](https://jitpack.io/#soyuz-dev/Bump)\
\
A 2D game engine built in Kotlin with LWJGL. Designed for a DSL-driven workflow where entities own their data directly — no ECS, no ceremony.

## Status

Core features are complete for 2D games. The engine runs fast, the commit messages are unhinged, and **the DSL is finally here. IT'S HERE!**

## Quickstart

See [GUIDE.md](GUIDE.md) for a quickstart to using Bump.

Or, with the new DSL:

```kotlin
package org.soyuz

import org.soyuz.engine.dsl.*
import org.soyuz.engine.render.SolidColor
import org.soyuz.engine.shape.CircleShape
import org.soyuz.util.Color
import org.soyuz.util.math.Vector2D

fun main() = bump {
    window("Hello, Bump!", 800, 600) {
        scene("main") {
            entity("ball") {
                position = Vector2D(400.0, 300.0)
                shape = CircleShape(30.0)
                painter = SolidColor(Color(255, 100, 80))
            }
        }
    }
}
```

No manual `Application.init()`, `RuntimeEngine`, `WindowManager`, or `Application.run()`. Bump handles the plumbing.

The imperative API is still there. The DSL just means you don't have to use it for everything.

## What's New in v1.6-alpha

### DEE ESS ELL

The Kotlin DSL is here. Windows, scenes, entities, physics, and behaviours can now be configured declaratively.

- `bump { ... }` — initializes and runs the application.
- `window("Title", width, height) { ... }` — creates a window, engine, camera, and rendering infrastructure.
- `createWindow = ::TransparentWindow` — use a `Window` subclass without manually setting up its engine.
- `scene("id") { ... }` — creates a persistent scene with its own physics context.
- `entity("id") { ... }` — configures entities with position, rotation, shape, painter, collider, and physics body.
- `physics { ... }` — registers a physics body and collider without manually accessing the physics and collision systems.
- `onKeyDown`, `onKeyUp`, `onKeyHeld` — scene-scoped keyboard callbacks.
- `onCollisionEvent` — scene-wide collision event callbacks, including ENTER, EXIT, and STAY.
- `onCollision` — entity collision callbacks.
- `Subscription` — cancellable callbacks, with entity-owned keyboard subscriptions automatically cleaned up on removal.
- `ScenePhysicsContext` — physics bodies, colliders, contact history, and events are isolated by scene.
- Runtime entity creation works through the same DSL. The configuration API doesn't stop being useful once the game starts.
- Multiple windows, multiple scenes, and the existing imperative API all work alongside the DSL.

```kotlin
scene("game") {
    val ball = entity("ball") {
        position = Vector2D(400.0, 300.0)
        shape = CircleShape(20.0)
        painter = SolidColor(Color(255, 100, 80))

        physics {
            body = PointMass(mass = 1.0)
            collider = Collider(CircleShape(20.0))
        }
    }

    onKeyDown(GLFW_KEY_ESCAPE) {
        engine.quit()
    }

    onCollisionEvent { event ->
        if (event.type == CollisionEventType.ENTER) {
            println("Bump! ${event.sourceEntityId} hit ${event.otherEntityId}")
        }
    }
}
```

### Window Factories

The DSL no longer assumes every window is a regular GLFW window. Pass a constructor reference through `createWindow` to use a `Window` subclass:

```kotlin
bump {
    window(
        "Transparent!",
        800,
        600,
        createWindow = ::TransparentWindow
    ) {
        scene("main") {
            // ...
        }
    }
}
```

The default is still `Window`. Transparent and other custom window types use the same engine and render setup.

### Scene-Aware Physics

- Each scene now has its own `ScenePhysicsContext`, containing its physics system, collision system, and event bus.
- Physics registrations are no longer shared between DSL scenes.
- Entity IDs only need to be unique within their own scene.
- The engine runs physics for the active scene.
- Removing entities cleans up their physics bodies and colliders.
- Collision events are routed back to the scene that owns them.
- Persistent scenes retain their state when switched away from.

### Scene-Scoped Behaviours

- Keyboard callbacks belong to scenes, not the entire application.
- Entity-owned keyboard subscriptions are cancelled when the entity is removed.
- Scene collision subscriptions can be explicitly cancelled.
- Callback dispatch is safe against subscription changes during iteration.
- The imperative behaviour API remains available independently of DSL configuration.

### DSL Demos

Four existing demos have been rewritten to use the DSL:

- **MainDsl.kt (BrickPit DSL):** Full physics sandbox. Click to spawn balls and bricks. Drag entities. Collision sounds. Now with DSL entity construction, physics registration, and scene collision events.
- **MultiSceneDSL.kt:** Three persistent scenes with circles, rectangles, and triangles. Scene-scoped keyboard callbacks for switching. No manual `KeyListener` polling.
- **MultiWindowDSL.kt:** Two independent windows, each with its own engine, scene, and animation loop. Rotating triangle in one, pulsing circle in the other.
- **AVTestDSL.kt:** Transparent window, clickable cat/dog textures, audio playback, text rendering, timers, pulsing circle, and camera shake.

All four have been run successfully.

The DSL is no longer just a bunch of unit tests.

## Platform Support

- ✅ Windows 10/11 with Intel Iris Xe Graphics (works out of the box)
- ✅ Windows 10/11 with NVIDIA GPU (requires control panel setting for transparency: Vulkan/OpenGL present method → "Prefer native")
- ✅ Linux (tested on Ubuntu 22.04 LTS and 24.04 LTS, both native and WSL2 with WSLg)
  - WSL2 audio may require: `export PULSE_SERVER=unix:/mnt/wslg/PulseServer`
- ❓ macOS (not tested, architecturally supported)

## Known Issues

- Dynamic window resize causes entity/collider desync (fixed-size windows only for now)
- Multi-window transparency not tested
- Close button requires entities to be added in correct z-order
- Rotated content is not yet stencil-clipped — entities near the edges can render into the transparent margins
- Mouse callbacks are not yet exposed through the scene behaviour DSL; existing mouse input APIs still work
- Joint cleanup and inactive-scene disposal need further lifecycle testing

## Warning

**Alpha.** Transparent and rotating windowing is experimental. Tested on Windows with Intel and NVIDIA GPUs, with Linux support tested separately. Expect platform-specific quirks.

### What works

- **Application & Windowing:** `Application` object owns GLFW lifecycle and global config. `Window` is a pure GLFW wrapper with mutable properties and recursion-safe callbacks. `TransparentWindow` for borderless transparency. `CustomWindow` for rotated custom chrome. `WindowManager` orchestrates multiple windows.
- **Engine:** `RuntimeEngine` scopes `UISystem`, `UI` factory, `Input`, timers, and the `Dynamic` update loop. No GLFW knowledge. Fixed-timestep physics.
- **DSL:** `bump`, `BumpScope`, `WindowScope`, `SceneScope`, `EntityScope`. Declarative configuration of windows (including custom window factories), scenes, entities, rendering, physics, and behaviours. Runtime entity creation supported.
- **Behaviour API:** Scene-scoped keyboard input, cancellable `Subscription` objects, entity-owned callbacks, collision event routing, and lifecycle cleanup.
- **Rendering:** Modern OpenGL 3.3 core profile, shader-based (`Shader`, `Mesh`, `Camera`), `Painter` interface with `SolidColor`, `ImagePainter`, `TextPainter`, `DynamicPainter`. `Camera` supports rotation, zoom, position, and separate backing/content dimensions.
- **Audio:** OpenAL-based `AudioSystem`, `AudioClip` (OGG via STB Vorbis), `AudioSource` with volume/pitch/looping, spatial audio support
- **Input:** Per-window `Input` instance wrapping `KeyListener`/`MouseListener` with window-handle-keyed state. Scene-scoped keyboard callbacks.
- **UI System:** `Interactive` decorator chain, `UISystem` with world-space hit-testing, `UI` factory (`button`, `label`, `slider`, `textInput`), `UIState` per entity
- **Math:** `Vector2D`, `Vector3D`, `Quaternion`, `Matrix4f`, `Transform`, `Polynomial` with arithmetic/calculus, `Easing` library (30+ curves), `MathUtil`, `Color`
- **Shapes:** `CircleShape`, `RectangleShape`, `TriangleShape`, `Aabb2D`, `ShapeQueries`
- **Physics bodies:** `PointMass` (Verlet), `RigidBody` (linear + angular with moment of inertia, friction field)
- **Force fields:** `ConstantForceField`, `ConstantAccelerationField`, `VelocityForceField`, `GravityField` (n-body with IEEE 754 bit-hack), `EntityAwareForceField`
- **Joints:** `RodJoint`, `RopeJoint`, `SpringJoint`, sealed `Joint` with `StrictJoint` and `PermissiveJoint`
- **Collision detection:** Circle, Rectangle (SAT + clipped-edge contact points), Triangle (SAT + barycentric), bounding circle broadphase
- **Collision resolution:** Impulse-based with positional correction, friction (geometric mean), restitution, angular effects, CCD ENTER/EXIT/STAY events
- **CCD:** Swept circle-vs-AABB with substepping and corner mitigation (tested to 300k+ px/s)
- **Event bus:** `RuntimeEventBus` with type-based pub/sub, `CollisionEvent`, scene-specific collision routing
- **Scene graph:** `Scene`, `RuntimeScene`, persistent scenes for instant switching, scene-owned physics contexts
- **Observable entities:** Position, rotation, shape fire change listeners
- **Timer system:** Standalone `TimerScheduler` with `Duration`, cancellable subscriptions, deferred registration, and scene-owned timers that pause while inactive
- **Debug utility:** Zero-overhead logging via `Debug`
- **Cross-platform:** Windows, Linux, macOS via auto-detected natives

### In progress

- Coroutine-based gameplay behaviours and game-time waiting
- Mouse behaviour callbacks in the DSL
- Spatial hash broadphase
- `PainterModifier` system (tint, opacity, clip)
- Stencil clipping for rotated windows

### Demos

- **Main.kt (BrickPit):** Full physics sandbox. Click to spawn balls and bricks. Drag entities. Collision sounds.
- **MainDsl.kt (BrickPit DSL):** The same physics sandbox, now using the configuration DSL.
- **MultiSceneDSL.kt:** Three persistent scenes with scene-scoped input.
- **MultiWindowDSL.kt:** Two independent windows using the DSL.
- **AVTestDSL.kt:** Transparent multimedia demo with the DSL — clickable cat and dog textures, audio, text, timers, and camera shake.
- **AVTest.kt:** Multimedia demo — cat/dog sounds, FPS, title text, pulsing circle, play button with camera shake.
- **DragTest.kt:** UI — draggable panel, circle hover, slider, text input.
- **TriangleTest.kt:** Triangle rendering and collision.
- **Game.kt (Asteroids):** Playable clone with thrust, shooting, asteroid splitting.
- **MultiWindowTest.kt:** Two independent windows.
- **CustomWindowTest.kt:** Transparent window with custom chrome — now with live rotation.
- **SceneSwitchTest.kt:** Three switchable scenes.

## Building

```bash
./gradlew build
```

To run the tests:

```bash
./gradlew clean test
```

## Running

```bash
./gradlew run
```

Or run any of the demo `main()` functions directly from IntelliJ IDEA.

The DSL demos are in `org.soyuz`, with entry points such as:

- `org.soyuz.MainDslKt`
- `org.soyuz.MultiSceneDSLKt`
- `org.soyuz.MultiWindowDSLKt`
- `org.soyuz.AVTestDSLKt`

Or build a fat jar:

```bash
./gradlew jar
java -jar build/libs/KotlinGameEngine.jar
```

## Installation as a Dependency

Add JitPack to your `settings.gradle.kts`:

```kotlin
repositories {
  mavenCentral()
  maven { url = uri("https://jitpack.io") }
}
```

Add Bump to your `build.gradle.kts`:

```kotlin
dependencies {
  implementation("com.github.soyuz-dev:Bump:v1.6-alpha")
  runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
  runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
  runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
  runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")
  runtimeOnly("org.lwjgl:lwjgl-openal::$lwjglNatives")
}
```


## Architecture

```text
org.soyuz
├── engine
│   ├── audio          — AudioSystem, AudioClip, AudioSource
│   ├── behaviour      — Subscription, SceneKeyboardDispatcher, collision routing
│   ├── collision      — Collider, CircleCollider, RectangleCollider, TriangleCollider, CollisionSystem, Contact
│   ├── core           — Application (object), Engine, RuntimeEngine
│   ├── dsl            — BumpScope, WindowScope, SceneScope, EntityScope
│   ├── entity         — GameEntity, DefaultGameEntity (observable properties)
│   ├── events         — EventBus, RuntimeEventBus, CollisionEvent, CollisionEventType
│   ├── physics
│   │   ├── forcefields — ForceField, EntityAwareForceField, ConstantForceField, ConstantAccelerationField, VelocityForceField, GravityField
│   │   └── joints     — Joint, StrictJoint, PermissiveJoint, RodJoint, RopeJoint, SpringJoint
│   ├── physics        — PhysicsBody, PointMass, RigidBody, PhysicsSystem, ScenePhysicsContext
│   ├── render
│   │   ├── image      — Texture, ImagePainter
│   │   └── text       — Font, TextPainter
│   ├── render         — Shader, Mesh, Camera, Painter, SolidColor, DynamicPainter, RenderSystem, RuntimeRenderSystem
│   ├── scene          — Scene, RuntimeScene
│   └── ui             — Interactive, InteractiveDecorator, UISystem, UIState, UI (factory), TextInputEntity
├── input              — Input (per-window instance), KeyListener, MouseListener
├── util
│   └── math           — Vector2D, Vector3D, Quaternion, Matrix4f, Transform, Polynomial, Easing, MathUtil
├── util               — Aabb2D, ShapeQueries, Assets (context-aware), Color, Debug, Dynamic
└── windowing          — Window, TransparentWindow, CustomWindow, WindowManager, WindowRuntime
```

## Design decisions

- **Doubles everywhere.** No floats in engine math. Floats only at the GL/AL boundary.
- **Immutable data where possible.** Vector and matrix operators return new instances.
- **Bodies own their forces.** No global force registry.
- **Physics is multi-phase.** Force accumulation → permissive joint forces → CCD position update → discrete collision → impulse resolution → strict joint solving → velocity finalization → dynamic field updates.
- **No ECS.** Entities are objects with direct references to their data.
- **DSL and imperative API coexist.** Declarative configuration for convenience, direct access to runtime objects when needed.
- **Scene-owned physics.** Each scene owns its physics registrations, colliders, event bus, and contact history. The engine executes physics for the active scene.
- **Scene-scoped behaviours.** Input and collision callbacks belong to scenes, with cancellable subscriptions and entity-owned callback lifetimes.
- **Application owns the platform.** GLFW, audio, and window lifecycle. `RuntimeEngine` knows nothing about the OS.
- **Engine-scoped dependencies.** `UISystem`, `UI`, `Input` are per-engine instances.
- **Camera as a full transform pipeline.** Backing dimensions decouple physical framebuffer from logical content, enabling unclipped rotation.
- **World-space UI.** `UISystem` converts mouse to world space via `Camera.screenToWorld()`, so hit-testing works under any camera transform.
- **Mass-zero means infinite mass.** No `isStatic` flag.
- **CCD by default.** Swept circle-vs-AABB with substepping.
- **Painter for rendering.** Entities carry appearance. `SolidColor`, `ImagePainter`, `TextPainter`, `DynamicPainter`.
- **Interactive decorator chain.** UI behavior composed via `.clickable { }.hoverable { }.draggable { }`.
- **Observable entities.** Properties fire change listeners.
- **Polynomials for curves.** Full arithmetic and calculus powers `Easing`.
- **Context-aware Assets.** Cached per OpenGL context.
- **Cross-platform natives.** Gradle auto-detects OS and architecture.

## Performance

Tested on a Lenovo Yoga L13 Gen 2 (integrated graphics, passive cooling):

- 50-body n-body simulation with CCD and per-pair gravity: 2000 FPS without vsync, fans off
- Desktop (dedicated GPU): brickpit at 3,000+ FPS
- Transparent window with UI: 3,700 FPS on integrated graphics
- Rotated custom window with UI: smooth 700+ FPS
- Idle (AV test with UI): 4% CPU, 120MB RAM
- All tests with IntelliJ and Chrome running in the background

## Ideas

See [IDEAS.md](IDEAS.md) for the chaos board.

## License

MPL-2.0
