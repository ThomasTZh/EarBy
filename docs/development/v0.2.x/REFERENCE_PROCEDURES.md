# ToneDear procedure reference

Checked 2026-10-11 in response to the owner's answers to D08 and D09. This is a behavioral
reference, not a dependency, code license, asset source or permission to copy implementation.
Implement the specification independently using this project's existing Kotlin/audio pipeline.

## Observed behavior

The [chord exercise](https://tonedear.com/ear-training/chord-identification) offers selectable
qualities, inversions and fixed-root practice. Its [public chord script](https://tonedear.com/js/ear-training/chords.js)
compares sorted pitches after subtracting the bass. It accepts matching interpretations
among selected qualities/inversions, including equivalent labels. It plays the chord directly;
the inspected playback path adds no separate root cue.

The [scale exercise](https://tonedear.com/ear-training/scale-identification) offers direction
and fixed-root controls. Its [public scale script](https://tonedear.com/js/ear-training/scales.js)
plays an octave including the repeated tonic, reversing the sequence for descent. Neither
the inspected page nor script defines fragment selection.

The page reads succeeded through the web tool. JavaScript fetches through that tool failed;
the same public scripts were then read directly over HTTPS. Local snapshots and hashes are
under `.local/verification/v0.2.x-planning/2026-10-11T102753Z/tonedear/`:

| Script | SHA-256 |
| --- | --- |
| chords.js | b421206912389e8e859548819d60a6c44897313a313d2c3d7df2c2eb47b43568 |
| scales.js | a429674158b88ed18ab2a264c02e1c3af278b66b24af1b8693d47e5fee508763 |

These are observations of the retrieved version, not a promise of future website behavior.

## Application of the owner's instruction

- D08: preserve valid equivalent labels. In this passive app, speak the complete alternative
  set with “or”; do not invent interactive scoring, answer buttons or a root cue.
- D09: complete scales only in v0.2.x. Defer the previously proposed fragments.
- Preserve the project's eleven chord qualities, thirteen scale formulas, seeded generators,
  range validation, arpeggios, three learning recipes and both-direction scale extension.
- Keep the project's explicit invalid-inversion validation; do not imitate a site's fallback
  from a triad's nonexistent third inversion to root position.
- Do not add ToneDear's accounts, links, assets, analytics, runtime network calls or quiz UI.

The owner referred to ToneDear to resolve two procedure questions. This scoped interpretation
does not expand the entire app to every ToneDear option. Future changes to these decisions
belong in [DECISIONS](DECISIONS.md), with their effect on generation and voice duration.
