# String note synthesis, A0 to C8, and one SF2 export

Researched plucked-string spectra turned into 176 candidate one-shots and one
final bank-0/program-0 SoundFont: bass for MIDI 21-59 (A0-B3) and steel-string
guitar for MIDI 60-108 (C4-C8). Every note is a single mono one-shot rendered by
inverse FFT from a physical modal model, then verified with forward FFT and an
independent SoundFont player.

Status: **complete** (2026-10-24; V4 dark calibration 2026-11-08). Project record:
[docs/LEDGER.md](docs/LEDGER.md); verification evidence:
[docs/VERIFICATION.md](docs/VERIFICATION.md).

Revision 2026-10-24: the final notes and bundled font use **0.120 RMS**, twice the
original 0.060 level, at the owner's volume request. Historical measurements below
that record the first audition keep their original 0.060 figures.

Revision 2026-11-08: the owner chose the **V4 dark calibration** (stronger
fundamental, less mid/high energy) after the sample-player listening review. The
presets, the 88 notes, the SoundFont and the EARP1 sample pack were regenerated
from it. The V4 values are in the model table below; the 2026-10-24 dark values
remain in [docs/LEDGER.md](docs/LEDGER.md) and
[docs/VERIFICATION.md](docs/VERIFICATION.md).

## Deliverables

| Path | Contents |
|---|---|
| `out/string_synth.sf2` | Final instrument, 88 one-shots, bank 0 program 0, 20.15 MiB |
| `out/audio/notes/021.wav` ... `108.wav` | The 88 reviewed one-shots embedded in the SF2 |
| `out/audio/candidates/{bass,guitar}/021.wav` ... `108.wav` | Full 88-note range for both models, used for the crossover review |
| `out/review/index.html` | A/B players for every note, with the chosen split marked |
| `out/review/pairs.csv` | Same information as a table |
| `out/summary.csv`, `out/partials.csv` | Per-note metrics and modelled partial tables |
| `out/mapping.json` | Split, per-key source, SHA-256 of every final WAV |
| `out/final_notes.csv` | Final set with source instrument per key |
| `out/figures/*.png` | Spectra, partial templates, decay spectrograms, range coverage |
| `out/test_samples/` | 12-note excerpt set, montage and listening page |
| `docs/LEDGER.md` | Decisions, final parameters and discarded directions |
| `docs/VERIFICATION.md` | Commands and exact verification results |

## Export contract (verified)

| Requirement | Implementation |
|---|---|
| Mono, 48,000 Hz, 16-bit signed PCM | `audiowrite(..., BitsPerSample=16)`; all 176 candidates audited |
| One file per MIDI note, `021.wav` to `108.wav` | 88 files in `out/audio/notes`, named by MIDI number |
| RMS about 0.120 | Exactly 0.120 in every final candidate; decoded WAV RMS 0.120 +/- 0.0002 |
| No extra leading silence | 1.5 ms raised-cosine attack; sound starts at sample 1 |
| Release tail included | 2.5 s note with natural decay and a 50 ms raised-cosine release |
| One SF2, one bank 0 / program 0 preset | `String Synth A0-C8`, instrument `Bass<=59 / Guitar>59` |
| Root key = that note, correction 0 | `originalPitch = key`, `pitchCorrection = 0`, `overridingRootKey = key` |
| Sample names traceable by MIDI | `shdr` names `021` ... `108`, same as the WAV files |

## Model

For MIDI note `k`, `f0 = 440 * 2^((k-69)/12)`, and partial `n`:

```
f_n   = n * f0 * sqrt(1 + B * n^2)                  (string bending stiffness)
A_n   = |sin(pi*n*beta)| / n^p * |sin(pi*n*u)| * |H_body(f_n)|
phase = 0 or pi, from sign(sin(pi*n*beta) * sin(pi*n*u))
tau_n = tau1 / n^q                                  (higher partials decay faster)
```

`A_n` includes the pluck-position comb (`beta`), the pickup-position comb (`u`,
bass only) and the body/pickup magnitude response `H_body`. Partials at or above
`0.45*fs` are dropped; zero-amplitude comb nulls are skipped without ending the
series.

| Parameter | Guitar | Bass | Basis |
|---|---|---|---|
| `fs`, duration, RMS | 48 kHz, 2.5 s, 0.120 | same | export contract |
| `p` | 1.55 | 1.55 | bridge force is `1/n`; the calibrated extra slope carries the radiated tilt (V4; the 2026-10-24 dark calibration used 1.3) |
| `beta` (pluck) | 0.20 | 0.18 | pluck-position comb, nulls at `n = kL/d` |
| `u` (pickup) | none | 0.20 | magnetic pickup senses the mode shape at its position |
| `B` | 4.0e-5 | 3.0e-5 | from `B = pi^3 E d^4 / (64 T L^2)` for typical string/scale values |
| `tau1`, `q` | 1.10 s, 0.70 | 1.50 s, 0.75 | Wright's measured decay tables, shortened for a 2.5 s one-shot |
| Body | modes 106/216/268/431/553/1010 Hz, Q 30/25/25/20/20/15, gains 0.40/0.64/0.48/0.28/0.16/0.04; HP 80 Hz; LP 1.5 kHz Q 0.5 | modes 95 Hz Q 1.2 and 700 Hz Q 2, gains 0.28/0.04; LP 0.9 kHz Q 0.5 | Wright measured guitar body modes; the V4 rolloff is calibrated to reference recordings and the owner's 2026-11-08 choice |
| Fades | 1.5 ms attack, 50 ms release | same | click-free one-shot boundaries |

## Research basis

- Howard Wright's guitar thesis supplies the inharmonicity form
  `f_n = nF(1+Bn^2)^(1/2)` (eq. 2.1, after Fletcher), the measured body modes
  106/216/268/431/553/1010 Hz, the pluck-position spectral discussion, and the
  measured decay tables (steel B string 3.118 s at mode 1 and 1.333 s at mode 5;
  wound A string 6.89 s at mode 1 falling to 0.43 s at mode 20).
- Penn State Acoustics derives the triangular-pluck Fourier coefficient
  `A_n = 2h/(n^2 pi^2) * L^2/(d(L-d)) * sin(n pi d/L)`, whose comb nulls are the
  `|sin(pi n beta)|` factor used here. The bridge force and magnetic pickup each
  add a factor `n`, giving the `1/n` force slope; the final model uses `1/n^1.3`
  plus a radiated low-pass to account for the loss between bridge force and
  radiated pressure (see the calibration note below).
- Euphonics (Joe Wolfe, UNSW) explains bending-stiffness inharmonicity and the
  frequency-dependent damping of real strings (a U-shaped loss curve), which
  motivates `tau_n = tau1 / n^q`.
- Wikipedia supplies the equal-tempered frequencies (A0 = 27.50 Hz, A4 = 440 Hz,
  C8 = 4186.01 Hz), the bass range (E1 = 41.2 Hz), and the pickup model
  (winding inductance and cable capacitance form a damped second-order low-pass
  with a resonance).

## Calibration correction

The first export used the bridge-force `1/n` slope with a 7 kHz guitar low-pass
and a resonant 3.2 kHz bass low-pass. Listening and a reference comparison showed
the upper mids were too strong. In the first version the bass A2 carried 52.5
percent of its energy in 300 Hz-1 kHz and the onset carried 3 to 6.5 percent of
its energy above 1 kHz (depending on the note), against 0.0-0.3 percent in
natural bass recordings.
Real radiated spectra lose energy faster than the bridge force, and the first
model also applied the measured body modes as narrow Q 20-30 magnitude peaks.

The 2026-10-24 dark calibration used `p = 1.3`, a 2 kHz (guitar) or 1.2 kHz (bass) radiated
roll-off, pluck positions 0.20/0.18, a bass pickup position of 0.20, and lower
body peak gains. The user chose this "dark" calibration after listening to
current, smooth, dark and smooth-dark variants. Frequencies, combs, damping and
the inverse-FFT method are unchanged. The reference material (isolated guitar and
double-bass notes, and renders of a full GM soundfont) was used for calibration
only and is not redistributed.

On 2026-11-08 the owner found the result still had too much mid/high energy and
chose **V4**: `p = 1.55`, a 1.5 kHz (guitar) or 0.9 kHz (bass) radiated roll-off
with Q 0.5, pluck positions 0.20/0.18, a bass pickup position of 0.20, and body
peak gains scaled to 0.8 of the dark values. V4 is the production calibration;
the V1-V4 listening evidence is in
`.local/verification/sample-player/tone/` and `tone-low/`. Frequencies, combs,
damping and the inverse-FFT method are unchanged.

## FFT synthesis and why the spectrum has two terms

Each note is built as a one-sided spectrum and transformed once:

```
S(f) = sum_n phase_n * A_n * ( 1/(1/tau_n + j*2*pi*(f - f_n))
                              + 1/(1/tau_n + j*2*pi*(f + f_n)) )
x(t) = real(ifft([S; conj(S(end-1:-1:2))]))
```

The pair of Lorentzians is the transform of a causal damped sinusoid
`A_n exp(-t/tau_n) cos(2*pi*f_n*t)`. Both terms are required: dropping the
negative-frequency image leaves out a broad tail worth about 3 percent of the
waveform at A0 (verified by comparing against a direct additive synthesis).
With both terms, the inverse-FFT note matches the additive model by -67 to
-73 dB for A0, A2, A4, A7 and C8 on both instruments. The transform is 2^20
samples (21.8 s at 48 kHz), so long decays are resolved before the note is
cropped to 2.5 s.

A nearest-bin block FFT was rejected: at A0 its frequency quantization is up to
about 5 cents, while the Lorentzian form keeps exact partial frequencies (FFT
measurement error is below 0.03 cents).

## Commands

Inside MATLAB R2024b, from this directory:

```matlab
run_synthesis          % render 176 candidates, CSVs, review page, figures
build_final(59)        % copy the chosen 88 notes, write mapping.json
runtests('tests')      % 103 tests
```

From a shell:

```bash
python3 -m unittest discover -s tools/tests
python3 tools/build_sf2.py --wav-dir out/audio/notes \
    --mapping out/mapping.json --out out/string_synth.sf2
python3 tools/validate_sf2.py out/string_synth.sf2 \
    --wav-dir out/audio/notes --mapping out/mapping.json
java --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED \
    tools/Sf2RenderProbe.java out/string_synth.sf2
```

`run_synthesis` replaces `out/audio/`; `build_final` replaces
`out/audio/notes/`. The Python writer and validator use only the standard
library. The Java probe needs a JDK with `jdk.compiler` (single-file source mode)
and the Gervill internal API export.

## Verified results in this run

- Candidates: 176 files, all mono/48 kHz/16-bit, 120,000 frames, RMS 0.0600,
  peak 0.2108 to 0.4686, no clipping.
- Final set: 88 files, 39 bass (MIDI 21-59) and 49 guitar (MIDI 60-108).
- `matlab.unittest`: 103 passed, 0 failed, 30.3 s.
- Python `unittest`: 6 passed.
- Code Analyzer: no issues in any `.m` file.
- SF2: 21,126,646 bytes, SHA-256
  `fcef5e8869cd42f6b3bc4df495a02fc021c04b3b6d4fa71276fd838380f8efee`;
  1 preset, 1 instrument, 88 zones and 88 samples, keys 21-108, 48 kHz, no loop.
- Structural validator: exit 0; PCM in the SF2 is byte-identical to the 88 WAVs;
  the repository's `tools/audio-analysis/sf2_tool.py` also parses one preset
  with 88 samples.
- Gervill playback: all 88 keys non-silent; measured fundamentals 27.44 Hz
  (-0.21 percent), 110.05 Hz (+0.04), 440.00 Hz, 1046.50 Hz, 4186.09 Hz; 8, 8,
  7, 4 and 3 matched harmonics respectively.
- Example modelled spectra: guitar A2 has 72 partials and a 150 Hz centroid,
  bass A1 has 77 partials and an 87 Hz centroid; guitar A0 has 190 partials,
  bass A0 has 114; C8 has 4 partials on both.
- The fundamental is the strongest measured peak in the A0, A1, A2 and C4
  exports, so the darker calibration did not sacrifice pitch clarity.

## Crossover review

The candidate page at `out/review/index.html` remains as the review artifact.
The user listened and selected the split at B3/C4: bass through MIDI 59, guitar
from MIDI 60. That choice is recorded in `out/mapping.json`, `out/review/index.html`
and `out/final_notes.csv`. After a separate timbre comparison the user selected
the dark calibration for both instruments, then the **V4** dark calibration on
2026-11-08 (see above); V4 is now the production model.

## Project record

- [docs/LEDGER.md](docs/LEDGER.md) records the timeline, final parameters, final
  export, commands and the discarded directions: the bright first model, the
  smooth and smooth+dark timbre variants, and the upright double-bass experiment.
- [docs/VERIFICATION.md](docs/VERIFICATION.md) records the exact WAV audit,
  SoundFont validation, test results and Gervill playback results.

The upright double-bass experiment for A0-B0 was rejected; the final export uses
the electric bass for MIDI 21-59. The first, brighter model was replaced by the
dark calibration, which the 2026-11-08 V4 revision then darkened further for a
stronger fundamental.

## Limitations

- The models are synthetic approximations, not recordings. There is no pick
  noise, no string-body coupling, no two-stage decay, no beating between
  polarizations, no stereo and no room.
- One inharmonicity coefficient is used per instrument; real strings differ by
  gauge and by string.
- Notes outside the native ranges (bass E1-G4, guitar E2-E6) are extrapolations
  required by the A0-C8 request. A0-C8 spans 7.2 octaves.
- The bridge-side pluck and pickup combs can weaken individual partials, but the
  fundamental is the strongest measured peak in the low-note exports, and the
  Gervill check measures it at 27.44 Hz for A0. A fuller or brighter low end is
  a matter of the pluck/pickup positions and the roll-off parameters.
- The SF2 samples are one-shots with no loop. Note-off behaviour is up to the
  player; the sample already contains its release tail.
- Cavity/plate behaviour below about 80 Hz is not measured; the guitar low
  notes are therefore more approximate than the mid range.

## Sources

- H. Wright, *The Acoustics and Psychoacoustics of the Guitar*, PhD thesis, 1996:
  <https://www.cglib.org/wp-content/uploads/cglib.org/Musicology/Wright%20Howard%20The%20Acoustics%20and%20Psychoacoustics%20of%20the%20Guitar%201996.pdf>
- D. A. Russell, *The Plucked Fixed-Fixed String*, Penn State Acoustics:
  <https://www.acs.psu.edu/drussell/Demos/Pluck-Fourier/Pluck-Fourier.html>
- J. Wolfe, *Euphonics section 7.2, Choosing strings*:
  <https://euphonics.org/7-2-choosing-strings/>
- *Scientific pitch notation*, Wikipedia:
  <https://en.wikipedia.org/wiki/Scientific_pitch_notation>
- *Bass guitar*, Wikipedia: <https://en.wikipedia.org/wiki/Bass_guitar>
- *Pickup (music technology)*, Wikipedia:
  <https://en.wikipedia.org/wiki/Pickup_(music_technology)>
- University of Iowa Musical Instrument Samples, used for calibration only:
  <https://theremin.music.uiowa.edu/MIS.html>
- A full GM soundfont from the repository's local verification backup, rendered
  through Gervill for steel-guitar and electric-bass references, used for
  calibration only.
- SoundFont writer conventions reused from the repository's
  `tools/audio-analysis/sf2_tool.py`.

This directory is standalone. No Android, Kotlin or Gradle file was changed, and
`out/` is kept out of version control.
