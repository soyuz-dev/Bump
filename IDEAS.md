
# Ideas

Random brain dumps worth revisiting later. No deadlines, no promises.

---

## Novel

Things that make Bump more than a conventional 2D game engine.

### Rotatable Windows

- Simulation window (A): rotated, fully physical, contains the scene
- OS window (B): axis-aligned, borderless, transparent background
- Render A inside B, clip to A's rotated rect via stencil buffer
- Custom window chrome (title bar, buttons) rendered as entities inside A
- Custom chrome buttons call `window.quit()`, `window.minimize()`, etc.

**Status:** Rotation works. The backing-square trick, `Camera.setDimensions`, `Camera.screenToWorld`, and world-space UI hit-testing are all in place. **Remaining:** stencil clipping so entities don't bleed into the transparent margins.

### Window as RigidBody

Window bounds are physical entities with colliders. Dragging/resizing the window pushes objects in the scene. The OS window becomes a game mechanic, not a container.

### Growing Window on Collision

Ball hits window edge → window expands in that direction. The viewport fights back.

### Bump Video (separate repo/module)

Headless rendering + JavaCV/FFmpeg. Declarative video authoring built on Bump's entity system, timers, and `Easing`. Mobject-style animation primitives (`play()`, `fadeIn()`, `moveTo()`, `rotate()`).

### WebAssembly Target

Kotlin/Wasm + WebGL. Bump in the browser. Long-term fever dream.

### Barnes-Hut-like Gravity

Replace O(n²) pairwise force calculations with a quadtree. Centre-of-mass approximation for distant clusters. O(n log n). The bit-hack exponent check still applies at the tree node level.

Could also add a mass-weighted opening criterion (`m*s/d³ < theta`) to bound tidal error rather than geometric error.

---

## Conventional

The usual features and improvements that make a game engine more capable.

### Physics

#### Buoyancy / Fluid Simulation

Simple density-based buoyancy. Bodies with `density < fluidDensity` float upward. `BuoyancyField` applying force based on body volume and submersion depth.

#### Weld / Fixed Joints

Glue two bodies together at a fixed relative offset. Zero degrees of freedom. Useful for building compound objects from simple shapes.

#### Soft Body / Jelly Physics

Mesh of `PointMass` instances connected by `SpringJoint`s. Deforms on collision. Satisfying squish.

### Rendering

#### Debug Drawing Layer

Toggleable overlay for colliders, contact points, normals, AABBs, velocity vectors. Press F1 to show/hide. Critical for physics debugging.

#### PainterModifier Chain

```kotlin
painter = SolidColor(1.0, 0.3, 0.1) + OpacityModifier(0.8) + TintModifier(0.2, 0.0, 0.1)
```

Modifiers applied in order at bind time. Composability without subclass explosion.

#### Particle System

Lightweight GPU-particle system for trails, sparks, debris on collision. `ParticleEmitter` as a component. Fires on `CollisionEvent`.

#### Render Layers / Z-Ordering

Entities sorted by layer before drawing. Background, game, foreground, UI. Prevents painter's algorithm issues.

#### Sprite Sheet / Animation

`AnimatedPainter` that cycles through UV regions of a texture atlas. Frame-based or time-based animation.

#### Stencil Clipping

Clip rendering to a rotated rectangle. Required for rotatable windows to prevent entities from bleeding into transparent margins.

### UI System

#### Checkbox / Toggle

Clickable entity with on/off state. `UI.toggle(id, x, y, initial) { value -> ... }`. Uses observable state internally.

#### Dropdown / Context Menu

List of options that appears on click. Uses `Interactive` for each item. Renders on top of other elements.

#### Layout System

Flexbox-like or anchor-based layout. UI elements position relative to parent or screen edges. `UI.hbox { }`, `UI.vbox { }` builders.

#### UI Themes

Predefined colour schemes and painter configurations. `UI.theme = Theme.DARK` applies consistent styling to all UI factory elements.

### Entities / DSL

#### Entity Templates

Pre-configured entity blueprints (bouncy ball, heavy box, static wall, attractor) that the DSL can reference by name.

#### Entity Lifecycle Hooks

`onSpawn`, `onDestroy`, `onCollisionEnter`, `onCollisionExit`, `onCollisionStay`.

Collision events are already routed to scenes and entities. Dedicated lifecycle hooks and per-phase entity callbacks remain to be added.

#### Prefabs

Reusable entity definitions. Instantiate with overrides.

```kotlin
prefab("bouncy_ball") {
    position(100, 200)
    velocity(300, 0)
}
```

#### Child Entities

Optional parent-child relationships. World transform computed by walking up the tree. Cleanup cascades. Useful for compound objects and complex UI.

### Systems

#### Broadphase (Spatial Hash)

Replace O(n²) collision detection with spatial partitioning. Divide world into grid cells, only check pairs in same or adjacent cells. Bounding circle broadphase already implemented.

#### Serialization

Scene save/load. Serialize entity graph, transforms, colliders, physics bodies. JSON or a custom binary format. Needed for level editing later.

#### Profiling / Metrics Overlay

FPS, frame time, body count, collision pairs checked, force calculations skipped (via bit-hack counter). Useful for tuning the threshold in `GravityField`.

#### Property Bindings

Formalise the observable entity pattern into a `Binding<T>` system. `label.position bind panel.position + offset`. Animated transitions via `Easing`.

### Platform & Runtime

#### Threaded Simulation

Physics step runs on a worker thread while main thread handles GLFW events + rendering. Avoids visual pause during window drag/resize on Windows. Requires double-buffered scene state.

#### Headless Mode

Run without a window for unit testing physics, AI, or server-side simulation. No GLFW dependency. Foundation for Bump Video — headless rendering + FFmpeg encoding for programmatic video generation.

#### 3D Rendering (2.0-alpha)

The math foundation is in place: `Vector3D`, `Quaternion`, `Matrix4f.transform`.

Remaining: perspective projection, mesh loading (OBJ/glTF), depth buffer, lighting models, GJK/EPA collision, BVH spatial partitioning.

---

## Done (graduated from ideas)

### Physics & Collision
- [x] Velocity Verlet, `PointMass`, and `RigidBody` with angular physics and friction
- [x] Circle, rectangle, and triangle collision detection (SAT, barycentric point test, clipped-edge contacts)
- [x] CCD with substepping and corner mitigation
- [x] Bounding circle broadphase
- [x] Force fields, including entity-aware forces and n-body gravity with bit-hack optimisation
- [x] Rod, rope, and spring joints; stable triple pendulum
- [x] Collision events (`ENTER`, `EXIT`, `STAY`)

### Rendering & Windowing
- [x] OpenGL core-profile rendering, shaders, meshes, and `RenderSystem`
- [x] `Painter` implementations: `SolidColor`, `ImagePainter`, `TextPainter`, `DynamicPainter`
- [x] Context-aware asset caching
- [x] Camera transforms, shake, zoom, rotation, and screen-to-world conversion
- [x] GLFW encapsulation, transparent windows, custom chrome, and multi-window support
- [x] Rotatable windows with backing-square trick and correct world-space input
- [x] Cross-platform build (Windows, Linux, macOS Intel + Apple Silicon)

### Engine & Gameplay
- [x] `RuntimeEngine` with physics, UI, rendering, and `Dynamic` updates
- [x] Per-window input, UI, and engine-scoped dependencies
- [x] Persistent scene switching
- [x] Timers: `everyFrame`, `forEvery`, `after`, `during`, with deferred registration
- [x] Observable entity properties
- [x] OpenAL audio, OGG playback, and spatial audio
- [x] UI interactions, buttons, labels, sliders, and text input
- [x] Playable Asteroids demo

### DSL & Behaviours (v1.6-alpha)
- [x] Kotlin configuration DSL: `bump { window { scene { entity { ... } } } }`
- [x] Declarative entity properties, physics bodies, and colliders
- [x] Runtime entity spawning through DSL builders
- [x] Scene-scoped keyboard callbacks (`onKeyDown`, `onKeyUp`, `onKeyHeld`)
- [x] Cancellable subscriptions and entity-owned input callbacks
- [x] Scene-wide and entity-level collision callbacks
- [x] Scene-isolated physics contexts and collision routing
- [x] BrickPit, MultiScene, and MultiWindow DSL demos

### Utilities & Distribution
- [x] `Vector2D`, `Vector3D`, `Quaternion`, `Matrix4f`, and `Transform`
- [x] `Polynomial` arithmetic/calculus and `Easing` library (30+ curves)
- [x] `Color` utility
- [x] JitPack distribution
- [x] MPL-2.0 license