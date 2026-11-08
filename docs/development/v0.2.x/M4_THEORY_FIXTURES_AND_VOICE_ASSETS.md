# M4 — Theory fixtures, spelling and voice vocabulary

State: PLANNED. Prerequisite: M3. Next: [M5](M5_CHORDS.md).
Read [SC3–SC5](SHARED_CONTRACTS.md), [ToneDear refinements](REFERENCE_PROCEDURES.md),
and WP5.1–WP5.5 in the existing expansion work packages.

## Outcome and scope

Later modes consume one tested musical catalog, one semantic voice registry and the same
measured duration metadata in validation and rendering. Independent fixtures catch musical
mistakes; approved interval assets and generator fixtures remain byte-identical.

Own `core/music` theory values/catalogs, training voice metadata/answer composition,
`tools/music-fixtures` (new), existing voice/audio-analysis tools, asset manifests/notices,
canonical check scripts and tests. No runtime Python/TTS, new synthesizer, voice replacement
or incidental Maven/native/toolchain upgrade. Shared definitions have immediate fixture and
asset-tool consumers; avoid speculative provider classes before their milestones.

## M4.1 — Freeze musical definitions

Add `SpelledPitch`, spelled `Key`, degree, chord-quality/inversion and scale definitions.
Sounding MIDI, theoretical root, written pitch and inversion bass are distinct values.
Use SC5's checked conversion and natural-letter progression; never infer a spelling from a
pitch-class number after losing its theoretical role. Preserve existing simple interval APIs.

Freeze the eleven chord formulas and thirteen scale formulas listed in WP5.2/WP5.3. Record
stable IDs, display aliases, semitone offsets, diatonic letter offsets and voice IDs together.
Major/Ionian and natural-minor/Aeolian each occupy one canonical category. Jazz melodic minor
has the same pitch collection descending. Triads support inversions 0–2, seventh chords 0–3.

Spelling expectations include major/minor pentatonic's omitted degrees, the blues altered
fifth, diminished seventh's double-flat seventh and augmented fifth. Whole-tone spelling uses
the explicit six-letter pattern 1,2,3,#4,#5,#6 relative to a spelled tonic; record this convention
instead of asking an oracle to choose an unspecified enharmonic spelling. Limit accidentals
to -2..2 and fail unrepresentable configurations explicitly rather than relabeling pitches.

Enumerate candidate root/key octaves with checked MIDI 0–127 conversion. Written octave is
not restricted independently when a valid enharmonic spelling crosses an octave boundary.
Absolute-note naming uses the simple chromatic sharp/flat table; functional naming follows key.

## M4.2 — Independent fixture tool

Create an isolated Python 3.11 project using the planned exact pin music21 10.5.0, its own
`pyproject.toml`/`uv.lock`, license notice and recorded dependency metadata. Reconfirm the
authoritative [version metadata](https://pypi.org/project/music21/10.5.0/) and compatibility
when implementation introduces the consumer. Do not change the other Python projects.

The fixture generator builds independent named chords/scales with music21 and checks them
against the documented formula tables. Kotlin production code must not produce its own oracle.
For jazz melodic minor/whole-tone spelling, explicitly encode the stated convention and record
why a library default is inappropriate. Investigate disagreements; never blindly rewrite JSON.

Fixture schema includes oracle/tool version, formula ID, spelled tonic, theoretical members,
MIDI pitches, inversion/bass, direction and expected aliases. Cover twelve pitch classes,
sharp/flat key spellings, every inversion, octave edges and selected out-of-range failures.
Canonical JSON ordering, UTF-8 and no timestamps/absolute paths make regeneration byte-stable.

Add the **planned** `scripts/check-music-fixtures.sh`: generate to a temporary evidence
directory and compare with committed fixtures; exit nonzero for drift or missing cases. An
explicit developer generation command creates proposed fixture updates separately. Host Kotlin
tests consume checked-in JSON without starting Python. Hook read-only fixture validation into
canonical verification and add guards proving ordinary checks cannot replace expected files.

## M4.3 — Semantic vocabulary and answer composition

Replace interval-only hard-coded tool lists with a declarative catalog consumed by tools and
validated against production semantic IDs. Keep physical paths out of domain contracts.

| Namespace | Required content / consumer |
| --- | --- |
| Existing interval IDs | All thirteen existing complete phrases, unchanged. |
| Chord | Eleven qualities, four inversion labels, conjunction “or” for equivalent answers. |
| Scale | Thirteen canonical names; aliases are display metadata, not duplicate answers. |
| Absolute note | A–G, sharp/flat/double-sharp/double-flat, octave -1 through 9; fixed-note solfège namespace. |
| Degree / movable solfège | Degrees 1–7; major and natural-minor syllables from SC5. |
| Direction / connector | Ascending, descending, unison and phrase connectors required by actual answer templates. |
| Harmony | Roman degree/quality phrases and tonic, predominant, dominant functions. |

Specify exact spoken text and token order for each later answer template. A Roman answer must
distinguish major/minor qualities in speech; capitalization alone is not audible. Do not record
arbitrary sentence combinations when reusable short phrases suffice. Alias pronunciation may
share a WAV only through an explicit registry mapping with identical meaning/pronunciation.

Compose multi-token speech with precisely 80 ms between clips; preserve each clip's existing
padding. Required-voice unions cover every reachable answer, including all chord alternatives,
all selected naming preferences and optional octave speech. One reveal marker precedes the
first token. Naming changes rebuild future answers; retained items preserve their own answer.

## M4.4 — Generate only missing assets

Inventory and hash all accepted WAVs/manifests first. Use existing Chatterbox 0.1.7 and FFmpeg
workflow/settings unless evidence requires a scoped change: current default voice, CPU process,
CFG 0.2, exaggeration 0.1, atempo 0.85, mono 48 kHz PCM16, -20 LUFS/-3 dBTP targets. Re-read the
actual tool configuration before execution. Preserve the thirteen interval WAVs/review records
byte-for-byte; never regenerate them merely to fit a new registry layout.

Record text, exact model/checkpoint hashes and revision, generation parameters, licenses,
resampling/normalization command, WAV hashes, frame counts and machine measurements for new
clips. Keep model weights, raw candidates and caches ignored. Mark every new listening/pronunciation
row `humanAccepted=false` until actual approval. Do not infer model provenance from a mutable
“main” label or copy approval fields from another voice entry.

Generate duration metadata explicitly from finalized bundled files, then make ordinary checks
compare catalog/metadata/assets read-only. Validate positive duration ≤8 s, mono/PCM format,
sample rate, finite samples, bounded peaks, missing/unused IDs and packaged-file hashes.
Structural pass permits integration with human rows open; `--require-reviewed` must still fail
for unapproved clips. Rendering checks cannot establish intelligible pronunciation.

## Regression matrix

| ID | Required evidence |
| --- | --- |
| M4-T01 | B#3=C4, Cb4=B3, theoretical letter order, double accidentals and checked extreme MIDI conversion. |
| M4-T02 | Every formula/inversion/scale direction matches independent fixtures; aliases do not add categories. |
| M4-T03 | Two clean fixture generations are identical; altered/missing expected files fail checks without rewriting them. |
| M4-T04 | Every answer template's semantic IDs exist; equivalent chord answers and octave/solfège cases are included. |
| M4-T05 | Measured frame metadata matches WAVs; corruption, oversized clip and missing token fail before Start. |
| M4-T06 | Existing thirteen WAVs/review entries/interval fixtures retain original hashes. |
| M4-T07 | Multi-token offsets/reveal/gaps are exact at 44.1/48 kHz; complete durations below/at/above cap agree. |
| M4-T08 | Three/four-note renders are finite, simultaneous and within existing amplitude policy at default gains; test configured extremes without silently changing gain semantics. |

## Commands and completion

After tool creation, run the following future checks with logs under M4:

```bash
./scripts/check-music-fixtures.sh
./scripts/check-audio-tools.sh
./scripts/check-assets.sh
./gradlew --dependency-verification strict :core:music:test :core:training:test :core:audio:testDebugUnitTest
./scripts/check-fast.sh
EAR_NATIVE_EVIDENCE_DIR=.local/verification/mode-expansion/M4/native ./scripts/check-native-tests.sh
```

Run production-renderer instrumentation on the isolated review target for the new multi-token/
four-note fixtures; use the harness-discovered packaged IDs. Record machine-check results and
pending human speech review separately. Update OPEN_SOURCE, asset provenance, theory/voice
contracts and ledger. Complete when M4-T01–T08 pass and each later mode has a finite documented
vocabulary; a claimed “approved voice pack” without listening evidence is not an exit result.
