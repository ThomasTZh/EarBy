# M9 — Progressions, then intervals in context

State: PLANNED. Prerequisite: M8. Next: [M10](M10_PASSIVE_MELODIC_DICTATION.md).
Execute M9A and its verification before M9B. Each is a complete provider slice using the
[shared contracts](SHARED_CONTRACTS.md) and existing slots/switching/compiler infrastructure.

## Scope and exclusions

Own harmonic function/template/voicing values in music, two typed training providers/configs,
data codecs, mode-specific editors/summaries/starters, required voice mappings and tests.
No arbitrary progression editor, sevenths/secondary dominants, modulation, jazz substitutions,
compound intervals beyond an octave, rhythmic variation or new output engine.

## M9A.1 — Progression settings and musical definitions

| Field | Values / default |
| --- | --- |
| keys | Selected tonic/major-natural-minor combinations; C major |
| templates | Four below; all three major templates initially |
| voicing | Root-position closed triads / nearest allowed inversion; root position |
| allowed inversions | Root/first/second for nearest mode; all three |
| range | Inclusive MIDI range; C3–C6 |
| answer | Roman sequence, directed root-motion sequence, harmonic functions; Roman sequence |
| timing/recipe/seed/stop | Shared settings; chord duration uses noteDuration and inter-chord gap uses sequential gap |

| Stable template ID | Chords and qualities | Harmonic functions |
| --- | --- | --- |
| major_i_iv_v_i | I major, IV major, V major, I major | tonic, predominant, dominant, tonic |
| major_i_vi_iv_v | I major, vi minor, IV major, V major | tonic, tonic, predominant, dominant |
| major_ii_v_i | ii minor, V major, I major | predominant, dominant, tonic |
| minor_i_iv_v_i | i minor, iv minor, V major, i minor | tonic, predominant, dominant, tonic |

Templates carry explicit tonality restrictions, degree, quality and function values. The tonic
function assigned to vi is the fixed teaching simplification for this catalog, not a universal
analysis claim. Minor V uses the raised leading tone, reusing M8's theory rule.

Each selected template must have a selected compatible key and complete realization; each selected
key must support at least one selected template. Reject incompatible-only selections with field
feedback. Empty tonic selection means all roots for selected tonalities, not an empty exercise.

## M9A.2 — Finite voicing and answers

For root position, place template roots at tonic anchor + diatonic offset, then apply root-position
triad formulas. Require all chords to fit the range. For nearest inversion:

1. Enumerate each step's closed triad candidates from allowed inversions and feasible octave
   placements. Keep theoretical roots and sounding basses separately.
2. Anchor the first chord in root position at the chosen key anchor; if root inversion is not
   allowed, choose the lexicographically first allowed feasible first-chord tuple instead.
3. Use dynamic programming across this finite candidate graph to minimize the sum of absolute
   semitone movement between sorted corresponding voices for every adjacent chord pair.
4. Break equal total costs by lexicographic comparison of the entire sequence of MIDI tuples.
   This specifies a global sequence minimum, not an unspecified greedy choice per step.
5. Sample template categories via the label bag, then choose a feasible key/anchor realization
   using the documented canonical candidate order. Voicing optimization itself consumes no RNG.

Roman speech says each degree with its quality (for example, “one major … six minor”); preserve
case/quality in the displayed Roman sequence. Harmonic-function speech follows the table. Root-
motion answers name n-1 directed intervals between the template's theoretical roots placed in
the tonic anchor's octave; they do not describe inversion-bass motion. Returning V→I is a
descending perfect fifth under this rule. Label this answer option “Root motion” clearly.

Each chord is one simultaneous TARGET group. There is no extra context prelude: the progression
is the example. Duration for n chords is `n*d + (n-1)*g`, plus the measured complete answer and
shared recipe/repeats. Enumerate the maximum over selected templates and answer formats.

## M9A.3 — Integration and verification

Add template/key/voicing/inversion/answer controls and a setup summary; keep five mode slots and
add `builtin:chord_progressions:starter:v1`. A live edit or pending switch never splits a
progression between chords. Test the complete unit with every recipe, especially Association.

| ID | Progression acceptance |
| --- | --- |
| M9A-T01 | All four templates/all compatible keys match independent chord-tone/quality/function fixtures. |
| M9A-T02 | Root vs bass, minor raised dominant, exact note ordering and incompatible key/template errors. |
| M9A-T03 | Nearest-voicing global optimum vs brute force on small cases; stable tie choice, narrow-range failures. |
| M9A-T04 | Roman/function/root-motion speech and display, complete duration boundaries and immutable seed fixtures. |
| M9A-T05 | Codec/preset/draft persistence, phone three-recipe playback/switching, hidden answers and failure cleanup. |

Run targeted music/training/data/app tests, assets/fixtures, check-fast and updated short isolated
phone tests before starting M9B. Record a separate M9A ledger entry; do not hide failures behind
the contextual-interval work.

## M9B.1 — Contextual interval settings and candidate model

| Field | Values / default |
| --- | --- |
| key/context/range | M8's choices; C major, tonic triad, C4–C5 |
| interval labels | Existing simple 0–12 semitone interval IDs; minor third, major third, perfect fifth |
| presentation | Ascending, descending, harmonic; ascending |
| answer | Interval, degree pair, interval+degree pair; interval |
| context gap/timing/recipe/seed/stop | M8/shared defaults |

Enumerate target notes in the selected major/natural-minor scale and range. Build finite ordered
pairs whose absolute semitone distance matches the selected interval. Ascending/descending enforce
their direction, permitting equal notes only for unison; harmonic orders the answer low→high.
Octave pairs share a degree with different MIDI values. Unison harmonic groups deduplicate the
same sounding pitch into one voice while preserving the two-target relationship in the answer.

Use interval labels as bag categories. Require every selected interval/presentation in every
selected key to have feasible context and target candidates. Context selection and pair selection
use M8's separate child streams with stable order `(firstMidi, secondMidi, degree1, degree2)`.
Do not classify an interval from a context pitch or equate degree distance with semitone distance.

## M9B.2 — Timeline, answers and screens

Compile full M8 context → context gap → two target notes. Sequential target duration is
`2*d + g`; harmonic target duration is d. The context repeats in every Example. Answer options
use existing interval phrase, two degree-number tokens in presented order, or interval then both
degrees with the defined 80 ms token gap. Store degree and semitone facts separately.

Expose keys/context, interval checklist, presentation and answer controls with complete range/
duration feedback. Add `builtin:intervals_in_context:starter:v1`, typed preset codec and five-slot
operations. A summary of selected allowed intervals is safe configuration; the actual current
interval/degree pair remains concealed until allowed. Reuse M8's context summary and validation.

| ID | Contextual-interval acceptance |
| --- | --- |
| M9B-T01 | Every selected interval/presentation in all feasible major/minor keys; impossible combinations rejected. |
| M9B-T02 | Context/target separation, unison/octave handling, descending pair order and correct semitone classification. |
| M9B-T03 | Minor context leading tone cannot enter natural-minor target pool; independent child-stream fixtures. |
| M9B-T04 | All answer formats/recipes, full context replays, token timing and below/at/above 60 s. |
| M9B-T05 | Storage/live edits, previous retained items, progression↔contextual-interval switches and hidden metadata/semantics. |
| M9B-T06 | Phone lifecycle/timer/Stop, missing asset/render failure and bounded repeated-cycle memory. |

## Integration commands and completion

Use these future commands for each slice, assigning evidence to M9A or M9B:

```bash
./scripts/check-music-fixtures.sh
./scripts/check-assets.sh
./gradlew --dependency-verification strict :core:music:test :core:training:test :core:data:testDebugUnitTest :app:testDebugUnitTest
./scripts/check-fast.sh
./scripts/device-smoke.sh --smoke-only --out .local/verification/mode-expansion/M9B/device-short
```

After both slices, run the integration gate:

```bash
EAR_NATIVE_EVIDENCE_DIR=.local/verification/mode-expansion/M9/native ./scripts/check-native-tests.sh
EAR_NATIVE_EVIDENCE_DIR=.local/verification/mode-expansion/M9/native EAR_REVIEW_OUT=.local/verification/mode-expansion/M9/device ./scripts/check-full.sh --automated-only
```

Complete with all M9A/M9B acceptance IDs, updated harmonic/context/provider/voice contracts,
candidate screenshots at 150%/200% text, and separate ledger results. Keep human pronunciation/
acoustic/visual/TalkBack rows open. Both providers must be production-wired before M10 begins.
