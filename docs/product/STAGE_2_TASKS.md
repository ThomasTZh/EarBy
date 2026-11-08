# Stage 2: Audio proof

Status: COMPLETE. Gate: G1 (passed) and G2 (passed 2026-10-09). Inherit AGENT_WORKFLOW.
WP2.1 has produced and machine-validated the voice pack, but its piano SoundFont and human
listening review are still open. WP2.2 builds, packages and ABI-checks the native renderer;
its host test suite and SoundFont copy-to-private-storage step remain.
WP2.3 and WP2.4 are not started. See [STATUS](../development/STATUS.md).
Complete G0-device and G0-IDE before G2 acceptance; native/asset work can progress on the host.

Introduce core:audio as an Android library only in WP2.2. It depends on core:training
and core:music and has no Compose/media/data dependency. Add the Android library
plugin alias at the existing AGP version, built-in Kotlin and pinned NDK/CMake.

## WP2.1 — Approved voice and piano assets

**Goal:** produce a small offline asset pack with executable integrity checks.
**Scope:** tools/voice-assets, tools/audio-analysis, app/src/main/assets initially,
scripts/check-assets.sh, docs/audio, notices and relevant dependency records.
Move asset ownership to core:audio when that module exists; package one copy.
**Interfaces:** versioned voice manifest, requiredVoiceIds(definition), AssetCatalog
adapter supplying durations/metadata to the domain; SoundFont descriptor.

### Steps

1. Create separate uv projects with Python 3.11 and locked dependencies. System
   Python 3.14 remains suitable for existing guard scripts, not assumed compatible
   with the TTS stack. Reuse installed FFmpeg. Follow OPEN_SOURCE.
2. Generate just the 13 Stage 1 interval phrases. Use Chatterbox's English base model;
   measure a three-phrase CPU batch before the full run. Record model revision,
   license, settings, seed and time/memory. If the CPU route fails, exceeds ten minutes
   for three phrases or exceeds 8 GiB peak RAM, evaluate Piper with a documented
   English model. Treat voice
   choice as an asset decision, not a runtime architecture change.
3. Store raw/candidate audio in ignored tools/**/raw or candidates. Offer a local
   command-line review using existing playback tools (ffplay is sufficient). Generate
   alternative takes only where pronunciation or clipping requires them.
4. Postprocess to mono 48,000 Hz PCM16 little-endian WAV using FFmpeg. Trim excessive
   outer silence while preserving consonants; target 50–150 ms leading/trailing pads.
   Use measured two-pass loudnorm, target -20 LUFS integrated and -3 dBTP ceiling.
   Very short clips may not yield stable integrated loudness; require a documented
   bounded-gain fallback and listening review instead of infinite/undefined gain.
5. Manifest schema v1 includes packId/version/language/voice, per-entry semantic ID,
   relative file path, transcript, sampleRate, channels, bitDepth, frameCount, SHA-256,
   source/license record and review status. Derive duration from frameCount/sampleRate.
   Production entries require humanAccepted=true with review date.
6. Check duplicate IDs, missing/unreferenced files, path traversal, absolute paths,
   unsupported schema, empty/over-8-second clips, wrong format/rate, invalid hashes,
   nonfinite samples and clipping. Reject required vocabulary gaps before playback.
7. Obtain the upstream MuseScore General SF2 source and license. Use Polyphone to copy
   bank 0/program 0 piano into a new SF2 with all linked samples/instruments and remove
   unused content. Preserve attribution; record editing/export steps and hashes.
   Goal is at most 32 MiB bundled piano. If it exceeds this, record the measured size
   and complete a bounded asset-selection task before accepting a replacement.
8. Store exact asset/version/source details and license texts. Full source fonts and
   model weights stay local; approved derived assets and reproducibility metadata
   are repository inputs. Human pronunciation review remains an explicit gate.

### Tests / done

Validator fixtures include each malformed manifest condition and a valid small WAV.
Test frame-derived duration, unknown voice IDs and vocabulary completeness.
check-assets is deterministic, read-only and becomes part of check-full. Runtime
catalog validation additionally uses the same schema/required IDs in Kotlin through
kotlinx.serialization; add that consumer in WP2.2.

Machine validation may pass with review pending; G2 may not. Do not assert that a
generated waveform sounds correct without a listening result.
Document accepted phrases/SoundFont in docs/audio/ASSETS.md.

## WP2.2 — Native build and bounded renderer contract

**Goal:** build reusable synthesis/decoding for phone and emulator.
**Gate:** G1; WP2.1 schema ready. Asset human review may still be open.
**Scope:** core:audio Kotlin/JNI/C++, root/module build integration,
tools/native source-lock metadata, scripts/check-native.sh, native tests.
**Interfaces:** MusicRenderer, VoiceDecoder, RenderFormat, RenderRequest, PcmBlock,
RenderFailure; Kotlin ownership wrapper implements close.

### Steps

1. Integrate FluidSynth 2.6.1 shared library, its pinned GCEM dependency and miniaudio.
   Use upstream CMake source and a staged source manifest. Check flags against the
   pinned release: enable-network=OFF; disable unused audio drivers, libsndfile/SF3,
   LADSPA, readline, OpenMP and optional effects dependencies where supported.
   Use SF2 and direct float PCM rendering. No configure-time downloads.
2. Set Android API 29, ABIs arm64-v8a and x86_64. Confirm the phone's ABI from G0-device.
   If it requires another ABI, add it as an explicit compatibility change. Use the
   SDK's NDK 28.2.13676358 and CMake 3.22.1, not Linux libraries from /usr/lib.
3. Use miniaudio only for WAV decoding/conversion and test-tone waveform generation.
   Disable device I/O. AudioTrack owns the device. Keep supported codecs restricted
   to the accepted WAV format. Lock the single-header revision and license.
4. JNI exposes checked create/load/render/reset/close operations and typed failures.
   Native handles remain inside core:audio. One worker owns each synth; no JNI access
   from Compose and no unsynchronized finalizer/resource cleanup.
5. Copy the verified SoundFont to app-private no-backup storage using a temporary
   file and atomic rename, keyed by version/hash. Validate an existing cache before
   reuse. Failed copy/load leaves no partially accepted file.
6. Reset program, gain, notes and effects for each independently renderable exercise.
   Disable chorus/reverb initially. Select bank/program explicitly. Bound polyphony.
   Use MIDI velocity 96 and polyphony 32 initially; include these in RenderRequest.
7. Use RAII in C++; clean up on partial initialization and exceptions. Validate buffer
   lengths, MIDI bounds and asset limits on both sides of JNI. close is idempotent;
   use after close returns an explicit error.
8. Native check builds the same render library for host tests and Android; GoogleTest
   remains a test dependency. Compile host and Android artifacts separately.

### Tests / done

Create/destroy cycles; malformed/missing SoundFont; failed allocation/loading path;
invalid handles/buffer lengths; all-notes-off/reset; note/chord PCM; sequential rerender
with no prior-note leakage. Sanitizer run on the host renderer where supported.

Inspect all APK .so dependencies/ABIs and ELF LOAD alignment with NDK llvm-readelf;
verify APK zip alignment with SDK zipalign for 16 KiB pages. Do not equate a compiler
probe with these checks. A 16 KiB runtime remains an additional acceptance item in G8.

Run check-native, check-fast, release assembly/lint and packaged permission checks.
Extend Spotless/architecture guards to the new module, plus dependency-recording
tasks. Document native API ownership in ARCHITECTURE and docs/audio/AUDIO_ENGINE.md.

## WP2.3 — Sample timeline, PCM cache and AudioTrack output

**Goal:** play one correct musical/speech timeline with reproducible offline analysis.
**Gate:** WP2.2 and valid machine-checked assets.
**Scope:** core:audio, tools/audio-analysis, scripts/check-native-tests.sh and
scripts/check-audio-tools.sh, renderer tests (the originally proposed check-audio.sh was not created).
**Interfaces:** FrameTimeline, RenderedExercise, AudioOutput, OutputPosition and
AudioFailure. Domain Kotlin Duration never becomes an Android wall-clock timestamp.

### Rendering and buffering

1. Use stereo float32 at 48 kHz first. If initialization rejects it, try 44.1 kHz with
   explicit conversion through miniaudio. Fail clearly if neither format is supported.
   Capture route, requested/actual track sample rate, buffer size and underrun counters.
2. Convert each absolute microsecond boundary with integer rational rounding, half up:
   round(offsetUs × sampleRate / 1,000,000). Compute absolute boundaries, not repeated
   rounded deltas. Bound arithmetic before multiplication. A converted audible window
   must contain at least one frame.
3. Decode exact voice frame counts and resample through miniaudio. Honor its latency/
   flush behavior. Pad/trim to the scheduled target frame count, with at most the
   documented one-frame duration rounding difference. Never clip the last consonant.
4. Render blocks of at most 1024 frames into a bounded current/next exercise cache.
   Each exercise is at most 60 s. Two 48 kHz float32 stereo exercises use 46,080,000
   bytes; enforce the 48 MiB combined PCM limit before allocation, including in-flight
   and JNI copies. Reserve/free cache space before rendering a replacement.
   Decode one voice at a time with at most 2 MiB scratch; use block-sized conversion
   scratch. Do not cache the whole future session.
5. Music/voice share one frame clock. Audible note windows include a short final
   envelope (initially 5 ms) and end in silence at their specified end; clear synth
   release tails so gaps really are silent. Harmonic notes share a start frame.
   Avoid duplicate harmonic-unison note-ons.
6. Render cached music/voice at fixed headroom. Apply respective gains to their windows;
   the output stage applies master/timer fade. Gains never change system volume.
   For current recipes music and voice do not overlap. Future overlap needs explicit
   peak tests. Reject nonfinite samples; never hide clipping with an undocumented limiter.
7. AudioTrack streaming writes run off the main thread, handle short writes and
   ERROR_DEAD_OBJECT, and can be cancelled without waiting for a long write. Start
   with a buffer based on getMinBufferSize; tune from measured underruns.
8. Track generated, queued and consumed frames separately. Use timestamp/playback-head
   evidence, handle counter wrap/reset and clamp to the submitted range. Consumed
   position is an estimate of hardware playback, not acoustic Bluetooth latency.
9. Pause/stop interrupt writes and flush queued output. Resume uses the same cached
   PCM at the retained consumed frame, with a short onset ramp. No random draw,
   new exercise or note-state reconstruction. The retained checkpoint is tested to
   within the output-position measurement tolerance, not claimed sample-perfect to ears.
10. Cancellation carries a generation/session token. Discard stale render completion;
    it must never start audio for a stopped or replaced session.

### Offline analysis and done

Use the same native rendering code in a host harness, output WAV via miniaudio's
encoder, analyze with NumPy/SciPy/soundfile. Fixtures: C4, A4, ascending/descending
interval, harmonic third, unison and spoken answer. Export frame markers and request
metadata alongside WAV; do not include private paths.

Assert sample-count boundaries within one frame, exact zero gaps outside envelopes,
no NaN/Inf/clipping, and sine A4 frequency within 1 cent on a sufficient steady window.
Piano checks use multi-harmonic evidence and a documented tolerance (initially 15 cents,
MIDI 48..84); the largest FFT bin alone is not a fundamental detector.
Use numerical tolerances across ABIs, not whole-file hash equality for synth output.

Test arbitrary block splits, conversion 48 ↔ 44.1 kHz, short writes, lost device,
counter reset and cancellation with fake outputs. check-full now runs check-native-tests
for host rendering/numeric analysis. Run check-native, check-native-tests, check-audio-tools
and check-fast; record output limits in audio docs.

## WP2.4 — Diagnostic proof and G2 acceptance

**Goal:** make the first sequence inspectable on actual hardware.
**Scope:** app/src/debug diagnostic UI, core:audio integration, device tests/docs.
**Interfaces:** debug-only command facade; activity lifecycle owns and closes this
temporary foreground-only proof controller. WP3.2 replaces ownership with the service.

Provide generate/play, pause/resume, repeat and stop; single note, interval and voice
preview; current seed/config, voice ID, timeline and output metrics. Commands reach
audio through the facade; Composables never call JNI. Stop safely when the activity
leaves foreground during this proof. Do not claim background support.

Run default and boundary interval fixtures on the physical phone offline. Confirm
notes → real silence → correct intelligible answer → next exercise, volume balance,
no clicks, pause during note/voice/silence and resume at retained position.
Inject missing voice, invalid SoundFont and renderer failure: show Error and silence;
repair/retry requires an explicit command. Log IDs and error codes locally.

G2 requires machine asset checks, human asset review, native/WAV checks, strict debug
and release builds with no INTERNET permission, both emulator installations, physical
phone listening/launch and documented ABI/page checks. Use check-full and the audio
scripts. Record which checks are automated and which were performed by the user.
Stop expanding features until this proof is accepted.
