# ADR 0002: Service-owned playback with a shared PCM stream

Status: accepted design; G2 audio has recorded acceptance. G3 background integration and
physical acceptance remain OPEN/PARTIAL following the 2026-10-09 source review. Stage 3
repair packages complete continuous playback, cache identity, focus and timer execution.

MediaSessionService owns the player and session engine. Training emits immutable
timelines. The in-project EARP1 sample player renders musical PCM; miniaudio decodes/converts
prerecorded voices. They feed one AudioTrack stream. Rendering/native access is serialized off
the UI thread; Media3's SimpleBasePlayer adapts controls/state.

Review refinement (2026-10-07): render in bounded blocks and cache the current and
next exercises, each at most 60 s and together at most 48 MiB of PCM. This allows
pause/flush/resume at retained consumed position without rebuilding a synthesizer's
envelopes. Generated/submitted positions are distinct from the consumed estimate.
Hardware and Bluetooth latency require physical measurement.

Oboe remains a measured alternative behind AudioOutput. Adopt only when a reproduced
AudioTrack defect justifies it. No second voice player or coroutine-delay musical clock.

The proof must cover real silence, simultaneous notes, note envelopes, clipping,
sample-rate conversion, interruptions, expiry, cancellation and resource release.
Inspect licenses, native dependencies, supported ABIs, ELF and APK 16 KiB alignment.
Native/source and waveform tests supplement physical phone listening.
