# Bump v1.4-alpha

A 2D game engine built in Kotlin with LWJGL. Designed for a DSL-driven workflow where entities own their data directly — no ECS, no ceremony.

## Status

Core features are complete for 2D games. The engine runs fast, the commit messages are unhinged, the DSL is next.

## Quickstart

See [GUIDE.md](GUIDE.md) for a quickstart to using Bump.

## What's New in v1.4-alpha

### 3D Math Library
- **`Vector3D`** — full 3D vector class with dot product, cross product, normalization, projection, reflection, lerp, clamp, and distance. Mirrors the `Vector2D` API exactly.
- **`Quaternion`** — rotation representation with axis-angle conversion, Euler angles, quaternion multiplication, rotation application, conjugation, normalization, slerp, and matrix conversion.
- **`Matrix4f.transform(Vector3D)`** — homogeneous transform with perspective divide support.

### Scene Switching Fix
- Removed automatic `cleanup()` from `loadScene()` — scenes persist in memory when switching. Switch back instantly without re-adding entities.

### License Change
- LGPL-3.0 → MPL-2.0. File-level copyleft. Games built on Bump are not derivatives — engine modifications stay open, game code stays closed.

### Dependencies Cleanup
- Removed unused dependencies. The engine now has exactly what it needs: LWJGL, JNA, and nothing else.

### Infix Sugar
- `entity at position` — set entity position
- `entity with shape` — set entity shape
- `entity with painter` — set entity painter
- `entity within scene` — add entity to scene
- `body with forceField` — add force field to physics body
- `Collider(shape)` — factory that returns the right collider type

Fluent entity creation:
```kotlin
DefaultGameEntity("ball") at
    Vector2D(400.0, 300.0) with
    CircleShape(50.0) with
    SolidColor(Color(255, 100, 80)) within
    scene
```

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
- UI hit-testing does not account for camera rotation

## Warning
**Alpha.** Transparent windowing is experimental. Tested only on Windows with Intel and NVIDIA GPUs. Expect platform-specific quirks.

### What works
- **Application & Windowing:** `Application` object owns GLFW lifecycle and global config. `Window` is a pure GLFW wrapper with mutable properties and recursion-safe callbacks. `TransparentWindow` and `CustomWindow` for borderless, transparent windows with custom chrome. `WindowManager` orchestrates multiple windows with context switching and per-window input routing.
- **Engine:** `RuntimeEngine` scopes `UISystem`, `UI` factory, `Input`, timers (`everyFrame`, `forEvery`, `after`, `during`), and the `Dynamic` update loop. No GLFW knowledge. Fixed-timestep physics.
- **Rendering:** Modern OpenGL 3.3 core profile, shader-based (`Shader`, `Mesh`, `Camera`), `Painter` interface with `SolidColor`, `ImagePainter`, and `TextPainter`, `DynamicPainter` for animated visuals, VAO/VBO circle/quad/triangle meshes with UV support, STB-based `Texture` loading, STB TrueType `Font` rendering, context-aware `Assets` caching, alpha blending, `RenderSystem` abstraction, `Camera` with rotation, zoom, and offset support
- **Audio:** OpenAL-based `AudioSystem`, `AudioClip` (OGG via STB Vorbis), `AudioSource` with volume/pitch/looping, spatial audio support
- **Input:** Per-window `Input` instance wrapping `KeyListener`/`MouseListener` with window-handle-keyed state
- **UI System:** `Interactive` interface with decorator chain (`clickable`, `hoverable`, `draggable`, `scrollable`, `focusable`, `doubleClickable`, `keyboardInput`), `UISystem` input router with hover/press/drag/focus/scroll state management, `UI` convenience factory (`button`, `label`, `slider`, `textInput`), hit-testing via existing collider infrastructure, `UIState` per entity
- **Math:** `Vector2D`, `Vector3D`, `Quaternion`, `Matrix4f`, `Transform`, `Polynomial` with arithmetic/calculus, `Easing` library (30+ curves), `MathUtil`, `Color`
- **Shapes:** `CircleShape`, `RectangleShape`, `TriangleShape` with `equilateral` and `isosceles` factories, `Aabb2D`, `ShapeQueries`
- **Physics bodies:** `PointMass` (Verlet integration), `RigidBody` (linear + angular with moment of inertia, friction field)
- **Force fields:** `ConstantForceField`, `ConstantAccelerationField`, `VelocityForceField` (polynomial-based drag/thrust), `GravityField` (n-body with IEEE 754 bit-hack exponent optimization), `EntityAwareForceField` interface for multi-body forces
- **Joints:** `RodJoint` (hard constraint with Baumgarte stabilization), `RopeJoint` (one-way constraint), `SpringJoint` (soft constraint with damping), sealed `Joint` interface with `StrictJoint` and `PermissiveJoint`
- **Collision detection:** `CircleCollider`, `RectangleCollider` with full SAT and clipped-edge contact points, `TriangleCollider` with SAT and barycentric point test, circle-rect closest-point, circle-triangle, bounding circle broadphase
- **Collision resolution:** Impulse-based with positional correction, friction (geometric mean of restitution), restitution, angular effects for rigidbodies, approach-speed threshold for resting contacts, CCD contact tracking with ENTER/EXIT/STAY events
- **CCD:** Swept circle-vs-AABB with substepping and corner mitigation (tested to 300k+ px/s without tunneling)
- **Event bus:** `RuntimeEventBus` with type-based pub/sub, `CollisionEvent` with `CollisionEventType` (ENTER/EXIT/STAY)
- **Scene graph:** `Scene`, `RuntimeScene`, entity management, scene switching (persistent scenes)
- **Observable entities:** Properties like `position`, `rotation`, and `shape` fire change listeners for reactive updates
- **Timer system:** Deferred registration via `pendingTimers` to prevent concurrent modification. `everyFrame`, `forEvery`, `after`, `during`.
- **Debug utility:** Zero-overhead inline logging via `Debug` object
- **Cross-platform:** Windows, Linux, macOS (Intel + Apple Silicon) via Gradle build with auto-detected natives

### In progress
- DSL (`engine { scene { ... } }`)
- Spatial hash broadphase
- `PainterModifier` system (tint, opacity, clip)
- Rotatable windows (see [IDEAS.md](IDEAS.md) — architecturally supported)

### Demos
- **Main.kt (BrickPit):** Full physics sandbox. Click to spawn balls and rigidbody bricks with angular physics, friction, and CCD collision. Drag entities with mouse. Collision sounds on impact.
- **AVTest.kt:** Multimedia demo. Clickable cat/dog with sounds, FPS counter, title text, pulsing circle, styled play button with camera shake, dynamic window resizing with easing curves.
- **DragTest.kt:** UI interaction demo. Draggable panel with cat texture, draggable circle with hover effects, volume slider, text input field.
- **TriangleTest.kt:** Triangle rendering and collision. Static colored triangles, draggable triangle, pulsing triangle, rotating triangle.
- **Game.kt (Asteroids):** Playable Asteroids clone. Ship with thrust/rotation/shooting, asteroids with size-based splitting, screen wrapping, score tracking.
- **MultiWindowTest.kt:** Two independent windows running simultaneously via `WindowManager`.
- **CustomWindowTest.kt:** Transparent window with custom chrome — title bar, close button, drag-to-move.
- **SceneSwitchTest.kt:** Three scenes (circles, rectangles, triangles) switchable with 1/2/3 keys.

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
    implementation("com.github.soyuz-dev:Bump:v1.4-alpha")
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
│   ├── core           — Application (object), Engine, RuntimeEngine (timers, update, render)
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
├── input              — Input (per-window instance), KeyListener, MouseListener (window-handle-keyed)
├── util
│   └── math           — Vector2D, Vector3D, Quaternion, Matrix4f, Transform, Polynomial, Easing, MathUtil
├── util               — Aabb2D, ShapeQueries, Assets (context-aware), Color, Debug, Dynamic
└── windowing          — Window, TransparentWindow, CustomWindow, WindowManager, WindowRuntime
```

## Design decisions

- **Doubles everywhere.** No floats. `Vector2D`, `Vector3D`, `Transform`, `Aabb2D` all use `Double`. Floats only at the GL/AL boundary.
- **Immutable data where possible.** Vector and matrix operators return new instances. `Transform` is a data class with `copy()`.
- **Bodies own their forces.** No global force registry. Bodies hold a list of `ForceField` instances. `EntityAwareForceField` for multi-body interactions.
- **Physics is multi-phase.** Force accumulation → permissive joint forces → CCD position update → discrete collision detection → impulse resolution + friction + positional correction → strict joint solving (5 iterations) → velocity finalization → entity-aware field position updates.
- **No ECS.** Entities are objects with direct references to their data. Same `GameEntity` type used for physics objects and UI elements.
- **Application owns the platform.** `Application` object manages GLFW init/terminate and audio lifecycle. `WindowManager` orchestrates multiple windows. `RuntimeEngine` knows nothing about GLFW.
- **Engine-scoped dependencies.** `UISystem`, `UI`, and `Input` are per-engine instances, not singletons. Each window has fully isolated UI state.
- **Per-window input.** `KeyListener` and `MouseListener` key state by window handle.
- **Mass-zero means infinite mass.** No separate `isStatic` boolean. `mass = 0.0` → immovable.
- **CCD by default.** Circle bodies use swept collision against AABBs with substepping. No tunneling.
- **Painter for rendering.** Entities carry their own appearance. `SolidColor`, `ImagePainter`, `TextPainter`, and `DynamicPainter` implementations.
- **Interactive decorator chain.** UI behavior composed via chained modifiers. No subclass explosion.
- **Observable entities.** Position, rotation, and shape fire change listeners for reactive updates.
- **Polynomials for curves.** Full arithmetic and calculus powers easing functions, force curves, and animation blending.
- **Context-aware Assets.** Caches shaders, textures, and fonts keyed by OpenGL context handle.
- **Deferred timer registration.** Prevents concurrent modification during iteration.
- **Cross-platform natives.** Gradle build auto-detects OS and architecture for LWJGL native libraries.

## Performance

Tested on a Lenovo Yoga L13 Gen 2 (integrated graphics, passive cooling):
- 50-body n-body simulation with CCD and per-pair gravity: 2000 FPS without vsync, fans off
- Broadphase bounding circles: 3-7x FPS improvement over brute-force narrowphase
- Triple pendulum with rod constraints: stable for 10+ minutes with no energy drift
- Desktop (dedicated GPU): brickpit simulation at 3,000+ FPS
- Transparent window with UI: 3,700 FPS on integrated graphics
- Idle (AV test with UI): 4% CPU, 120MB RAM
- All tests with IntelliJ and Chrome running in the background

## Ideas

See [IDEAS.md](IDEAS.md) for the chaos board.

## License

MPL-2.0