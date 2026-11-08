# Stage 3: Background session proof

Status: IMPLEMENTED Stage 3 service/controller/timers, reviewed and repaired 2026-10-11.
G2 has historical acceptance; G3 remains open for unsupported acceptance. See
[STATUS](../development/STATUS.md) and [the review ledger](../development/PROJECT_REVIEW_2026-10-11.md).
Inherit AGENT_WORKFLOW. Current product controls are Start/Stop; focus loss/noisy route/task
removal stop playback, ducking attenuates while playing, and return never restarts playback.
Domain pause/resume/navigation contracts remain internal and covered. These current owner
decisions supersede older pause/continue-on-task-removal descriptions below.

The review repair package covers WP3.1a/3.3a/3.4a and automated WP3.5 evidence: typed
PlaybackIdentity, immutable per-item recipe/gains, 20 backward items, two PCM entries/48 MiB,
no unbounded source timeline cache, all-boundary failure cleanup and serialized transport futures.
Live edits preserve current/backward items and replace every queued future item. Shared bundled
voice durations validate complete recipes before playback. Hidden-answer filtering occurs
before snapshots/metadata are published. Timer fade and focus duck multiply independently.
The bounded wake-lock lease renews while active; expiry invalidates work and releases resources.
Use `scripts/check-full.sh --automated-only` for the isolated physical-phone harness; its real
30-minute screen-off deadline does not substitute for acoustic/accessory/TalkBack acceptance.

## WP3.1 — Serialized session state and exercise queue

**Goal:** deterministic controls independent of Android/UI.
**Scope:** core/training state/queue code and tests; add coroutines-core matching the
existing 1.10.2 baseline. Turbine was not introduced: current tests assert StateFlow
values with virtual time; add it only if a real Flow consumer needs it.
**Interfaces:** SessionCommand, SessionSnapshot, SessionId, PauseReason, SessionError,
SessionController, AudioPort and MonotonicClock. Android adapter implements AudioPort.

### State and commands

States: Idle, Preparing, Playing, Paused, Stopping, Completed, Error.
Snapshot contains session ID, phase, concrete exercise/index, answer visibility,
consumed position, played duration, pause/stop reason and available controls.
Keep the generator, queue cursor and command serialization inside one owned scope.

| Input | Required outcome |
| --- | --- |
| Start(valid config, seed) in stopped/error state | Validate all assets/config, create identity, prepare first item, then play |
| Start while preparing/active | Reject AlreadyActive; replacement requires explicit Stop then Start |
| Pause while Playing | Cancel writes, retain consumed frame, flush, enter Paused |
| Resume while Paused | Check deadline and assets/output readiness, then continue retained PCM |
| Next | Flush current; advance to cached forward item; preserve whether previously paused |
| Previous | Restart current if position >=3 s; otherwise select prior history item, or restart if none |
| Repeat | Restart the same concrete item without advancing generator; preserve pause state |
| Stop | Invalidate pending work, silence, close active output, enter Completed(user) |
| Renderer/output failure | Silence, release resources, publish Error; no automatic retry |
| Duplicated pause/resume/stop | Idempotent or explicit unavailable result; never duplicate playback |

Completed and Error retain enough summary/configuration for an explicit new Start.
Do not implicitly resume them. A Stop arriving during Preparing wins over late render
callbacks. All asynchronous results carry the originating session and generation token.

### Queue and position

Generate lazily, at most one next item ahead. Keep at most 20 previous concrete
definitions. Previous/Repeat reuse definitions and do not consume randomness.
When navigating backward, Next traverses retained forward history before generating
a new item. Navigation alone never alters generated future items; an explicit live edit discards queued
future items and draws replacements under the new setup.

PCM cache holds current/next only; rerender retained past items if needed.
A replaced cache entry is discarded safely after its output reader stops.
The clock measures elapsed deadlines; logical position comes from consumed audio.
Playing includes intentional timeline silence; Paused/Preparing do not advance it.
Cumulative played duration sums consumed traversal deltas; navigation resets item
position but never subtracts from that cumulative duration or adds skipped frames.
Publish AnswerStarted only when its frame is consumed, not when queued/rendered.

### Tests / done

Use fake AudioPort and virtual time: every command/state combination, duplicate
commands, interleaved Start/Stop/render callbacks, cancellation, queue boundaries,
history eviction and back/forward traversal. Test pause in a note/voice/gap, consistent
answer visibility and no generator draws during repeat/navigation. No real sleeps.

Run core/training tests and check-fast. Update public contracts and STATUS.
Do not implement Android service or persistence in this package.

## WP3.2 — Media3 service and controller bridge

**Gate:** WP3.1.
**Scope:** new core:media Android library, app manifest/composition root, integration
tests; core:audio port adapter only. Add Media3 common/session.
**Interfaces:** TrainingMediaService, TrainingPlayer extending SimpleBasePlayer,
controller connection, bounded start/config and repeat/stop session commands.

### Steps

1. Service owns one SessionController, player, MediaSession and audio lifetime.
   Compose/ViewModels obtain a MediaController connection; disconnecting a screen
   does not stop an active session. Remove the temporary activity-owned proof path.
2. Keep Media3 Player methods/state publication on its application looper. Forward
   work to the serialized controller; render/writes remain off that looper. Complete
   command futures when accepted/applied or failed; never block the looper for JNI.
3. Map Preparing to buffering, Playing/Paused to ready plus correct playWhenReady,
   Completed to ended and Error to idle with PlaybackException. Update available
   commands and invalidate state when snapshots change. Advertise only supported
   operations: play/pause, previous/next, explicit repeat/stop and metadata.
   Do not offer arbitrary seeking, speed changes or remote playlist injection.
4. Expose current item duration/consumed position in Media3's timeline. Use stable
   exercise IDs and bounded history; the session is generated, not an infinite list.
   Preserve next/previous command availability even at current queue boundaries.
5. Metadata during recognition says "Interval recognition" / "Listen" or "Think".
   Reveal the answer only at consumed AnswerStarted. Hide-answer mode never reveals
   it in visual/accessibility surfaces. Exposure/recall may reveal at their first
   scheduled answer. Apply one policy to screen, notification and session metadata.
6. Use Media3's notification/session support and a session activity PendingIntent.
   The app manifest declares FOREGROUND_SERVICE and FOREGROUND_SERVICE_MEDIA_PLAYBACK;
   service type is mediaPlayback. Set exported deliberately and follow Media3's
   service intent-filter requirements.
7. Authorize custom configuration commands only from this app's controller. Permit
   normal transport commands from supported system/media controllers. Bound payloads
   (16 KiB serialized configuration maximum), validate every field and disallow file
   paths/URLs in controller commands.
8. Initiate playback from visible user action; handle denied foreground-service
   starts and audio-focus requests as observable errors. Account for targetSdk 36.
   Do not add boot autostart or WorkManager playback.
9. Release in order: stop session/writes, cancel/join workers, release output/native
   resources, MediaSession/player and service scope. Repeated onDestroy is safe.
   Task swipe-away continues active playback; an idle/completed service stops itself.

Use Android's [background service guidance](https://developer.android.com/media/media3/session/background-playback)
and [custom player contract](https://developer.android.com/media/media3/session/player).
A SimpleBasePlayer adapter does not acquire audio focus for custom AudioTrack output.

### Tests / done

Controller commands work through MediaSession on API 29/36; recreate activity, bind
two controllers, disconnect/reconnect and rotate with one audible session.
Verify metadata has no early answer, all command futures finish, unsupported commands
are absent, untrusted custom commands fail, and failed preparation stops foreground
activity cleanly. Test notification media controls, lock-screen controls and app Back.

Use check-fast and check-full on selected devices; inspect packaged service/permissions.
No separate hand-built notification player or network-capable media stack.

## WP3.3 — Audio focus, routes and power ownership

**Gate:** WP3.2.
**Scope:** core:media focus/route lifecycle, core:audio output recovery, device tests.
**Interfaces:** InterruptionPolicy and typed focus/route/output events to controller.

1. Request AudioManager audio focus before sound using matching media/music attributes.
   On target API 35+, ensure the app is eligible (visible or foreground service) before
   requesting it. If focus is denied, stay silent and show a recoverable reason.
2. Initial policy: pause for permanent loss, transient loss and duck requests, preserving
   speech intelligibility. Set pause-on-duck behavior where supported. Never resume
   merely because focus returns; user explicitly resumes.
3. Register ACTION_AUDIO_BECOMING_NOISY and relevant audio-device callbacks with the
   service lifetime. On wired/USB/Bluetooth disconnect pause before any fallback to
   speaker where the platform permits, flush stale output and require explicit resume.
   Do not request Bluetooth scanning/location permission just to enumerate routes.
4. Recreate a lost AudioTrack on explicit resume after route/device error, preserving
   exercise identity and consumed position. Re-convert cached PCM if the accepted
   sample rate changes. Initialization failure remains Error.
5. Hold a scoped partial wake lock while Playing (including timeline silence) or
   Stopping/fading. Acquire/release under the same serialized lifecycle; release on
   pause, completion, error and destruction. Declare WAKE_LOCK with that consumer.
   No battery-optimization exemption request by default.
6. A paused session must tolerate losing foreground status/process lifetime.
   Media3 can demote a service after extended inactivity; never hold a wake lock
   just to retain a paused timer. If the process dies, relaunch stopped with defaults
   at this stage; saved-setup restoration becomes available in WP4.2.
7. Release focus on pause/stop/error. Exercise boundaries do not repeatedly abandon/
   reacquire focus. Foreground/notification state remains consistent with playback.

Sources: [audio focus](https://developer.android.com/media/optimize/audio-focus),
[Media3 service inactivity](https://developer.android.com/media/media3/session/background-playback).

### Tests / done

Inject focus denial/loss/duck/gain and noisy-route callbacks; duplicate events are
idempotent. Verify every exit path releases focus/wake/output resources.
On phone test another media app, call interruption, Bluetooth and wired/USB unplug
where available. Record unsupported accessories as untested, not passed.
Record behavior with notification permission denied on applicable Android versions;
media-session notification behavior has platform exemptions, so do not block playback
behind a blanket POST_NOTIFICATIONS requirement.

## WP3.4 — Duration sleep timer

**Gate:** WP3.3.
**Scope:** core/training timer rules/tests, core:media elapsed clock/fade adapter,
debug timer control.
**Interfaces:** SleepPolicy, SleepTimerState, StopReason; elapsedRealtime clock adapter.

1. Modes now: off or duration 1..180 minutes; expiry policy immediate or finish current
   exercise. Fade 0..60 s, clamped to duration. Default immediate, zero fade.
2. Start the deadline from accepted Start, after configuration validation, using elapsed
   realtime that includes device sleep. Preparation and pause consume this time.
   Replacing an active timer starts a new deadline from the replacement command.
3. For immediate expiry, fade in [deadline-fadeDuration, deadline], evaluated on the
   output master gain. At deadline cancel writes and flush. Bound additional queued
   audio by the measured output buffer; report this bound.
4. Finish-current: at expiry capture the currently audible item; generate/play no next
   item. Let this item's timeline finish, including answer/final pause, then stop.
   Fade over its final configured fade window after expiry, clamped to remaining time.
   If no item is Playing at expiry, complete immediately.
5. If timer expires while Paused or Preparing, complete and prohibit resume.
   While paused the OS may defer callbacks; check the deadline before processing
   every command and again before any sound. No exact wake alarm is needed to keep
   an already silent session silent. Do not promise an exact-time paused notification.
6. Finish-current grace is at most one 60-second exercise. Stop/Next cannot extend it;
   Next during drain stops, Previous/Repeat are unavailable during drain.
7. Fade changes output master gain only. Cancelling/replacing timer restores the
   unfaded product gain. User gain changes remain independent of timer state.
8. Do not persist elapsedRealtime deadlines across process death/reboot. Persist
   default policy later, not an active timer. Wall-clock changes have no effect.

### Tests / done

Virtual time: just before/exactly at/after deadline; changing/cancelling; pause through
expiry; resume/Next/render callback racing expiry; immediate/finish-current; fade zero,
longer than remaining item and mid-voice; repeated Stop; wall-clock jump.
Use one authoritative fake elapsed clock tied to the coroutine test scheduler.

On phone prove screen-off expiry, fade and no reappearance of sound after expired
pause. Measure stop-to-silence separately for speaker and Bluetooth. Document actual
buffer/route latency. No WorkManager or alarm permission.

## WP3.5 — G3 reliability acceptance

**Gate:** WP3.1–3.4 plus WP3.1a/3.3a/3.4a repairs and the Stage 4 start/configuration seam.
**Scope:** scripts/device-smoke.sh, instrumentation, debug-only LeakCanary, test docs.
**Goal:** demonstrate the first useful complete session.

Run a 30-minute default session locked on the phone, with a duration timer ending it.
Repeat a transport/interruption pass using Bluetooth. Exercise note/voice/gap pause,
Next/Previous/Repeat, screen rotation, Home/other app, route changes, focus loss, a
30-minute pause, battery saver, app task removal, process kill and relaunch.

Automate repeatable controller/state checks; output a checklist for actions requiring
a human. Record seed, asset hashes, route/sample rate, underruns, failure count,
memory before/after warmup and timer outcome. A failed route has an issue, not a
silently skipped row. Inspect service/activity leaks; native memory needs separate
process measurements because LeakCanary does not account for the whole native heap.

G3 closes after check-full, native/audio checks and the physical report show: no
duplicate playback, no stuck note, no early answer leak, correct explicit-resume
policy, reliable timer stop, bounded queues and resource release. Log observations
without claiming universal Android/OEM reliability. Full device matrix is in TEST_STRATEGY.

## Remaining integration packages from the 2026-10-09 review

Status 2026-10-09: WP3.1a, WP3.3a and WP3.4a are **implemented and host-verified** in this
session; see STATUS for the exact evidence. WP3.5 physical acceptance remains open. The original
requirements below stay as acceptance targets and as the record of what each repair owes.

The original requirements above remain acceptance targets. Existing component tests and
command success do not demonstrate these production behaviors. Execute the following repairs
in order under an implementation instruction, without changing reviewed assets/toolchain.
Use [ANDROID_STUDIO_MCP](../development/ANDROID_STUDIO_MCP.md) for semantic tracing and
inspections after its import-readiness check. CLI/source work can proceed if the IDE is unavailable.

### WP3.1a — Continuous queue, silent navigation and PCM identity

**Goal:** one Start produces a continuing sequence, and navigation selects exactly the item
heard without producing sound while paused.
**Prerequisites:** existing WP3.1/3.2/2.3 components; review findings recorded in STATUS.
**Scope:** core:training session/queue, app production AudioPort, narrow audio cache seams,
host/connected tests and affected contracts. No new player, provider or history database.
**Interfaces:** consumption/completion events tagged with session/generation/item identity;
AudioPort preparation/cache identity; live SessionSnapshot position and controls.

1. Make full *consumption* of an item's timeline advance automatically to its next item.
   Submitted frames or a completed blocking write are insufficient. Handle EOF/Stop/Next races
   once; discard stale callbacks. Keep commands responsive while rendering/writing.
2. Preserve one-ahead generation, current/next PCM budget and 20-definition history.
   Previously prepared history is not proof its PCM remains resident. Rerender the selected
   concrete item if absent; Repeat/Previous/forward traversal never draw fresh randomness.
3. Paused Next/Previous/Repeat select/reset/prepare only; do not call output start or play.
   Require explicit Resume for sound. During playback navigation flushes stale output before
   playing the selected item and preserves identity/sequence.
4. Publish consumed position, cumulative duration and answer visibility while audio is active.
   Mark the answer at consumed AnswerStarted, never after submission or only after the item ends.
5. Preserve the owner's G2 music-pair pause/replay behavior when integrating production pause;
   document the exact boundary and Resume position for note, answer and gap cases. The debug
   diagnostic's pass is not evidence that the Media3 session implements the same behavior.
   Stop and timer expiry retain their immediate silence requirement.

**Tests:** several automatically completed items from one Start; delayed consumption despite
full submission; command/EOF races; no play/start calls on paused navigation; history cache
eviction followed by the exact selected PCM; v1/v2 sequence unchanged after navigation;
live answer marker and duration accounting; interrupted write/stop/release paths.
Exercise the production adapter or a faithful one-current-cache fake, not an unlimited cache.
**Done:** targeted controller/audio tests, check-fast and check-full pass; phone confirms
multiple distinct intervals continue from one Start and paused navigation stays silent.

### WP3.3a — Focus/readiness acknowledgement before sound

**Gate:** WP3.1a.
**Scope:** core:media focus/routes/service lifetime and the narrow session/audio readiness port.
**Interfaces:** explicit permission/readiness result before output start; typed interruption
commands; service-owned scopes and ordered output/renderer teardown.

1. Obtain focus before submitting sound on Start, Resume and audible navigation. A phase
   observer after PLAYING publication is not a readiness barrier. Delay/denial must leave output
   silent; late grants/results are discarded if the session changed or stopped.
2. Keep route/noisy handling registered for the required playback lifetime. Verify disconnect
   pauses/flushes before fallback where supported and explicit Resume checks the new output.
3. Audit creation/configuration/error/destruction paths: no replaced renderer/AudioTrack left
   open, no unjoined writer using a released renderer, no observer/timer scope surviving service
   destruction. Surface failures; preserve structured cancellation.
4. Document focus ownership on user pause versus transient-loss pause and test that policy.
   Repeated requests at item boundaries must not churn focus or create overlapping outputs.

**Tests:** delayed/denied/lost focus while output is ready; Stop racing a focus grant; route
loss during preparation/playback; repeated Start/Stop and gain reconfiguration; resource
release on every error/exit. Assert zero PCM submission before grant.
**Done:** targeted tests and fast/full checks pass; phone focus/noisy-route rows have actual
results. Record missing accessories as UNTESTED; remaining route rows keep G3 partial.

### WP3.4a — Apply timer effects in the service-owned session

**Gate:** WP3.3a.
**Scope:** timer/session rules in training, media/service adapter, output master-gain seam,
command/state integration and expiry instrumentation. No alarms or persisted deadlines.
**Interfaces:** service-owned scheduler, serialized timer effects and observable deadline,
remaining duration, drain state and ending cause through the controller bridge.

1. Own the scheduler scope with the service and cancel/join it on destruction. Scheduling
   must actually invoke evaluation; `arm()` alone only stores a deadline. Check immediately
   before each command and each possible sound start, including after deferred paused callbacks.
2. Apply StopNow, DrainCurrent and FadeTo to the real controller/output. Prevent automatic
   next-item generation during drain and reset the master gain when cancelled/replaced/stopped.
   Expiry during preparation/pause stays silent and prohibits Resume.
3. Establish a validated Start/timer-policy seam: duration configured at Start measures from
   accepted Start; replacing it from Player sets a new deadline. WP4.1c subsequently carries
   the complete saved count/cycle/duration policy into this mechanism. There is one authoritative
   stop decision, not two independent timers. Persist policy only.
4. Publish service state and command outcomes so a rejected/expired timer cannot appear armed.
   Do not keep the service awake while paused to update a cosmetic countdown.

**Tests:** virtual time across preparation/pause/play; real effects on fake output, fade reset,
replacement and deadline races; delayed callback plus Resume; finish-current bounds and stale
work; service teardown; connected expiry/Completed state, not just payload acceptance.
**Done:** targeted tests, check-fast and check-full pass; before G3 closure, phone listening
proves a locked 30-minute session ends silently and never resumes after expiry. Record speaker
and Bluetooth stop latency separately. The existing timer command test remains useful but
cannot satisfy this acceptance item.
