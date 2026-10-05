
# Bump v1.5-alpha

A 2D game engine built in Kotlin with LWJGL. Designed for a DSL-driven workflow where entities own their data directly — no ECS, no ceremony.

## Status

Core features are complete for 2D games. The engine runs fast, the commit messages are unhinged, the DSL is next.

## Quickstart

See [GUIDE.md](GUIDE.md) for a quickstart to using Bump.

## What's New in v1.5-alpha

### Rotating Windows
- `CustomWindow` now supports arbitrary rotation of its content while maintaining correct mouse hit-testing.
- Backing square trick: the OS window is sized to the diagonal of the logical content, allowing the content rectangle to rotate freely inside without clipping corners.
- `Camera.setDimensions(backingWidth, backingHeight, contentWidth, contentHeight)` — separates the physical framebuffer size from the logical UI coordinate space.
- `Camera.screenToWorld()` — inverts the full transform pipeline (zoom, position, rotation, content offset) for accurate mouse → world conversion.
- `UISystem` now hit-tests in world space, so clicks and drags work correctly at any rotation.

```kotlin
val window = CustomWindow("My App", 600, 400, "roboto")
window.show()

var rotation = 0f
window.engine { dt ->
    rotation += (dt / 20).toFloat()
    window.camera.setRotation(rotation)
}
```

- `Camera.lookAt(x, y)` — point the camera at a world position. The sign-flip math is handled internally.

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

## Warning
**Alpha.** Transparent and rotating windowing is experimental. Tested only on Windows with Intel and NVIDIA GPUs. Expect platform-specific quirks.

### What works
- **Application & Windowing:** `Application` object owns GLFW lifecycle and global config. `Window` is a pure GLFW wrapper with mutable properties and recursion-safe callbacks. `TransparentWindow` for borderless transparency. `CustomWindow` for rotated custom chrome. `WindowManager` orchestrates multiple windows.
- **Engine:** `RuntimeEngine` scopes `UISystem`, `UI` factory, `Input`, timers, and the `Dynamic` update loop. No GLFW knowledge. Fixed-timestep physics.
- **Rendering:** Modern OpenGL 3.3 core profile, shader-based (`Shader`, `Mesh`, `Camera`), `Painter` interface with `SolidColor`, `ImagePainter`, `TextPainter`, `DynamicPainter`. `Camera` supports rotation, zoom, position, and separate backing/content dimensions.
- **Audio:** OpenAL-based `AudioSystem`, `AudioClip` (OGG via STB Vorbis), `AudioSource` with volume/pitch/looping, spatial audio support
- **Input:** Per-window `Input` instance wrapping `KeyListener`/`MouseListener` with window-handle-keyed state
- **UI System:** `Interactive` decorator chain, `UISystem` with world-space hit-testing, `UI` factory (`button`, `label`, `slider`, `textInput`), `UIState` per entity
- **Math:** `Vector2D`, `Vector3D`, `Quaternion`, `Matrix4f`, `Transform`, `Polynomial` with arithmetic/calculus, `Easing` library (30+ curves), `MathUtil`, `Color`
- **Shapes:** `CircleShape`, `RectangleShape`, `TriangleShape`, `Aabb2D`, `ShapeQueries`
- **Physics bodies:** `PointMass` (Verlet), `RigidBody` (linear + angular with moment of inertia, friction field)
- **Force fields:** `ConstantForceField`, `ConstantAccelerationField`, `VelocityForceField`, `GravityField` (n-body with IEEE 754 bit-hack), `EntityAwareForceField`
- **Joints:** `RodJoint`, `RopeJoint`, `SpringJoint`, sealed `Joint` with `StrictJoint` and `PermissiveJoint`
- **Collision detection:** Circle, Rectangle (SAT + clipped-edge contact points), Triangle (SAT + barycentric), bounding circle broadphase
- **Collision resolution:** Impulse-based with positional correction, friction (geometric mean), restitution, angular effects, CCD ENTER/EXIT/STAY events
- **CCD:** Swept circle-vs-AABB with substepping and corner mitigation (tested to 300k+ px/s)
- **Event bus:** `RuntimeEventBus` with type-based pub/sub, `CollisionEvent`
- **Scene graph:** `Scene`, `RuntimeScene`, persistent scenes for instant switching
- **Observable entities:** Position, rotation, shape fire change listeners
- **Timer system:** Deferred registration via `pendingTimers`
- **Debug utility:** Zero-overhead logging via `Debug`
- **Cross-platform:** Windows, Linux, macOS via auto-detected natives

### In progress
- DSL (`engine { scene { ... } }`)
- Spatial hash broadphase
- `PainterModifier` system (tint, opacity, clip)
- Stencil clipping for rotated windows

### Demos
- **Main.kt (BrickPit):** Full physics sandbox. Click to spawn balls and bricks. Drag entities. Collision sounds.
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

## Running

```bash
./gradlew run
```

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
    implementation("com.github.soyuz-dev:Bump:v1.5-alpha")
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-openal::$lwjglNatives")
}
```

## Architecture

```
org.soyuz
├── engine
│   ├── audio          — AudioSystem, AudioClip, AudioSource
│   ├── collision      — Collider, CircleCollider, RectangleCollider, TriangleCollider, CollisionSystem, Contact
│   ├── core           — Application (object), Engine, RuntimeEngine
│   ├── entity         — GameEntity, DefaultGameEntity (observable properties)
│   ├── events         — EventBus, RuntimeEventBus, CollisionEvent, CollisionEventType
│   ├── physics
│   │   ├── forcefields — ForceField, EntityAwareForceField, ConstantForceField, ConstantAccelerationField, VelocityForceField, GravityField
│   │   └── joints     — Joint, StrictJoint, PermissiveJoint, RodJoint, RopeJoint, SpringJoint
│   ├── physics        — PhysicsBody, PointMass, RigidBody, PhysicsSystem
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
