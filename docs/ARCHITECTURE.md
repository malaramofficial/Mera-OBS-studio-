# Malaram Studio — Production Architecture

## Goal
Malaram Studio is an Android-native OBS-style production studio. The critical rule is:
> Changing a layer's visual properties must never restart the media player, encoder, or live transport.

A video at 01:23 must remain at approximately 01:23 when its position, size, zoom, rotation, crop, opacity, or layer order changes.

## Pipeline
Sources (Camera / Screen / Media3 Video / Image / Text / Browser)
  -> Source & Scene State
  -> RenderPlanBuilder
  -> OpenGL ES Compositor
  -> Preview + Program Surface
  -> MediaCodec H.264
  -> RTMP Transport
  -> YouTube / Custom RTMP

## Hard boundaries
1. Scene state contains source identity, config, transform, crop, opacity, visibility, lock state, z-index and topology.
2. Media player state contains URI, loop, autoplay, volume, playback position and play/pause state.
3. Visual transforms are not media identity.
4. RenderPlan is derived from scene state and consumed by the GPU compositor.
5. The program output surface belongs to the live session and remains attached while layers are edited.

## Visual-only update path
UI Apply -> atomic Source Update -> SceneRepository -> programScene StateFlow -> RenderPlanBuilder -> StudioRenderPipeline.updateRenderPlan() -> next GPU frame.

For visual-only changes there must be no Media3 prepare, player recreation, H.264 encoder restart, or RTMP reconnect.

## Open-source reference architecture
- KrinikCam: GL compositor as the single video path and RootEncoder-based output.
- Stream Prime: mobile RTMP, screen capture, file sources and overlay-oriented controls.
- Ogh: local-first Android broadcasting with live source/audio changes.
- Vivid: scenes, replays, overlays, RTMP/SRT and Media3 integration.

Malaram Studio does not copy their code. Their architecture is engineering reference; licenses and attribution must be respected before importing source code.

## Upgrade order
1. Pipeline stability and no-restart guarantees.
2. Direct transform manipulation.
3. Source/layer production controls.
4. Scene switching and transitions.
5. Media playback controls.
6. Text/graphics/browser sources.
7. Screen + camera composition.
8. Audio mixer.
9. YouTube/RTMP production controls.
10. Telemetry, adaptive bitrate and reconnect.
11. Presets, Shorts mode and Instant Replay.

## Performance rules
- UI work stays off the GL thread.
- Media preparation stays off the UI thread.
- The GL thread owns GL resources.
- Render requests are coalesced.
- Scene updates are atomic.
- Player recreation is allowed only when media identity actually changes.
- Preview and program rendering share the compositor but remain logically separate.
- Live output surface must not be torn down for ordinary source edits.

## Acceptance tests
- Change media position while playing -> playback position unchanged.
- Change media zoom -> playback position unchanged.
- Rotate/crop/opacity change -> no player recreation.
- Reorder layers -> no player recreation.
- Edit a scene while live -> RTMP session remains connected.
- Switch program scene -> encoder remains running.
- Change live bitrate -> no RTMP reconnect.
- Screen/camera changes do not leak capture surfaces.