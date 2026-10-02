# Malaram Mobile Studio

**Target:** Android OBS-style Mobile Live Streaming & Recording Studio  
**Application ID:** `com.aistudio.mobilestudio.mmlrm`  
**Package:** `com.malaramofficial.mobilestudio`  

---

## 1. Project Purpose
Malaram Mobile Studio is a mobile-first live video production studio and broadcast deck for Android. It mirrors the core capabilities of desktop OBS Studio—allowing creators to assemble multi-source visual scenes (Camera, Screen capture, Overlays, Text banners), mix audio channels in real time, and output to RTMP/RTMPS ingest points or local MP4 files.

---

## 2. Technical Architecture Overview
* **UI Layer:** Declarative Jetpack Compose (Material 3) with OLED-optimized studio theme, dual monitor stages (Preview & Program), and responsive layouts.
* **Domain Layer:** Clean Architecture domain entities (`Scene`, `Source`, `Transform`, `AudioMixerState`, `StreamConfig`, `RecordingConfig`, `AppError`). Decoupled from hardware drivers.
* **Engine Layer:** OpenGL ES 2.0/3.0 multi-source layer compositor (`GpuCompositor`), dedicated `HandlerThread` render pipeline (`StudioRenderPipeline`), zero-copy CameraX `SurfaceTexture` / `GL_TEXTURE_EXTERNAL_OES` ingest (`CameraSourceEngine`), low-latency audio mixing bus, and hardware `MediaCodec` encoders.
* **Transport:** Lightweight RTMP/RTMPS client with adaptive frame-dropping queue and exponential backoff reconnection.
* **Background Service:** Android Foreground Service with explicit `camera`, `microphone`, and `mediaProjection` types, keeping broadcasts active when minimized.
* **Persistence:** Room Database for scene topologies and layer transformations; Preferences DataStore for video configuration; Encrypted storage for stream keys.

---

## 3. Package Structure
```
com.malaramofficial.mobilestudio/
├── core/
│   ├── model/ (AppError, AppResult)
│   └── permissions/ (PermissionManager, StudioPermissionsState)
├── domain/
│   ├── model/
│   │   ├── scene/ (Scene, Source, Transform, Crop, Transition, ChromaKeyConfig, SourceType)
│   │   ├── source/ (SourceConfig: Camera, Screen, Image, Text, Media, Browser)
│   │   ├── render/ (RenderableLayer, RenderPlan, RenderPlanBuilder)
│   │   ├── audio/ (AudioSource, AudioSourceType, AudioChannelState, AudioMixerState, AudioFilter)
│   │   ├── stream/ (StreamConfig, StreamState, StreamStats)
│   │   ├── recording/ (RecordingConfig, RecordingState, RecordingStats)
│   │   └── state/ (StudioAppState)
│   ├── repository/ (SceneRepository)
│   └── engine/ (SceneManager, SourceManager, StreamingController, RecordingController, AudioEngine)
├── data/
│   ├── local/ (StudioDatabase, SceneDao, SourceDao, SceneEntity, SourceEntity)
│   ├── datastore/ (StudioPreferences)
│   ├── security/ (SecureCredentialStore)
│   └── repository/ (SceneRepositoryImpl)
├── engine/
│   ├── camera/ (CameraSourceEngine, CameraState, LensFacing)
│   ├── gpu/ (EglCore, GlShader, GlTexture, CameraInputSurface, GpuCompositor, StudioRenderPipeline)
│   ├── scene/ (SceneManagerImpl)
│   └── source/ (SourceManagerImpl)
├── service/
│   └── StudioService (Android Foreground Service)
└── ui/
    ├── components/ (StudioGlMonitorView)
    ├── theme/ (StudioTheme, StudioColorScheme)
    ├── StudioViewModel
    └── StudioScreen (Dual Monitor, Scene List & Management, Layer Stack, Audio Mixer, Bottom Action Deck)
```

---

## 4. Completed Phases

### Phase 0: Project Foundation
* [x] Android SDK configuration (`minSdk 26`, `compileSdk 36`, `targetSdk 36`).
* [x] Core domain data models, typed domain error hierarchy (`AppError`), and Result types.
* [x] SQLite Room local database persistence with `SceneDao` and `SourceDao`.
* [x] Encrypted stream key credential storage with log sanitization.
* [x] Studio Foreground Service lifecycle structure.

### Phase 1: Scene & Source Engine
* [x] Production `SceneManagerImpl` with create, delete (with auto-recovery), rename, duplicate, reorder, and update.
* [x] Production `SourceManagerImpl` with add, remove, duplicate, rename, layer reordering (bring to front, send to back, move up, move down), visibility, lock, transform, crop, and opacity.
* [x] Deterministic 2D Transform math (`moveBy`, `resize`, `rotateBy`, `scaleBy`, `toMatrix4x4`).
* [x] Validated Crop system preventing impossible/inverted clipping rectangles.
* [x] Decoupled polymorphic `SourceConfig` hierarchy (Camera, Screen, Image, Text, Media, Browser).
* [x] Rendering Boundary: `RenderableLayer`, `RenderPlan`, and `RenderPlanBuilder` translating Scenes into GPU-ready draw blueprints.
* [x] Dual-stage Preview / Program independence and working CUT swap.
* [x] Studio UI controls for Scene & Layer management with dialogs and clear indicators for pending capture backends.
* [x] 100% Passing unit and Robolectric tests for all engines, math, and persistence.
* [x] Zero fake camera/screen frames, zero fake streaming, zero fake metrics.

### Phase 2: Real Camera Source + Camera-to-GPU Pipeline
* [x] Modern Android CameraX (`camera-camera2`, `camera-lifecycle`, `camera-core` 1.5.0) integration.
* [x] Zero-copy hardware surface delivery: `CameraX Preview` -> `Surface(SurfaceTexture)` -> `GL_TEXTURE_EXTERNAL_OES` -> Hardware GPU Compositor.
* [x] Production OpenGL ES 2.0/3.0 Compositor (`GpuCompositor`) with Vertex & Fragment shaders handling 4x4 MVP matrix, hardware orientation transform matrix, real-time crop clipping, opacity blending, and real-time Chroma Key processing.
* [x] Dedicated rendering thread (`StudioRenderPipeline`) running on `HandlerThread` to keep Compose and main thread non-blocking.
* [x] `StudioGlMonitorView` embedded in Compose via `SurfaceView` displaying real-time composited hardware output.
* [x] Lens switching support (`FRONT` and `BACK` camera selection) with live flip button on Program canvas.
* [x] Camera lifecycle and permission handling (auto-pause on background, resume on foreground, request runtime permissions).
* [x] Comprehensive unit and Robolectric tests for Camera pipeline, GPU compositor shaders, and camera lifecycle.
* [x] Zero fake camera frames, zero CPU Bitmap copies.

---

## 5. Build & Test Instructions
```bash
# Compile and build debug APK
gradle assembleDebug

# Run all unit and Robolectric tests
gradle testDebugUnitTest
```

---

## 6. Next Phase: Phase 3 (Multi-Source GPU Compositing & Media Ingestion)
Phase 3 will expand the GPU compositor to concurrently ingest Image overlays, Text rasterization textures, and Video clip textures into the unified `RenderPlan` pipeline.
