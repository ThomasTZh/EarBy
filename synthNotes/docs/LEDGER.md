# Ledger: string note synthesis, A0 to C8

Status: **complete** (2026-10-24; revised 2026-11-08 with the V4 calibration).
Final output: `out/string_synth.sf2`, SHA-256
`f3326f4c53c52a521bce7e9e2932edb9e34807f85a96b391eb224a5a0b499705`
(21,126,646 bytes), and the EARP1 sample pack
`app/src/main/assets/soundfont/earby-string-pack.bin`, SHA-256
`f2e14a32c252c5d94ec5f1b7f8cbf63da6ff7a2c08afb49470af591cc6c05462`
(21,126,760 bytes). The 2026-10-24 dark font was
`c3c3ff7da62bf0ad7d306657d168783f75bd8a894dfa7c2cdb27bb4b7a7cd27c`; the V4
revision supersedes it. The final level is 0.120 RMS; the 0.060 figures in the
historical measurements below record the first audition.

This project started standalone inside the Passive Ear Training repository. Since
the 2026-11-08 V4 revision the EARP1 sample pack is the shipped instrument asset in
`app/src/main/assets/soundfont/`; the SoundFont is the tracked generator input at
`synthNotes/StringSynth_A0-C8.sf2`. The MATLAB generators remain standalone and no
Android, Kotlin or Gradle source is changed by this project. The
generated `out/` tree remains ignored.

## Objective

Simulate single mono notes from A0 (MIDI 21) to C8 (MIDI 108) for an electric bass
and a steel-string guitar, using spectra researched from the literature and an
FFT-based synthesis, then export one bank-0/program-0 SoundFont with the bass on
the low notes and the guitar on the high notes.

## Timeline and decisions

| Date | Decision or event |
|---|---|
| 2026-10-24 | Researched plucked-string spectra (Wright thesis, Penn State pluck Fourier series, Euphonics, Askenfelt's double-bass chapter) |
| 2026-10-24 | Built the modal model and inverse-FFT synthesis; rendered 176 candidates |
| 2026-10-24 | User reviewed A/B candidates and chose the crossover at B3/C4 (bass MIDI 21-59, guitar 60-108) |
| 2026-10-24 | First SF2 built and validated (later superseded) |
| 2026-10-24 | User reported the mids/highs were too strong; reference comparison confirmed the issue |
| 2026-10-24 | Four timbre variants (current, smooth, dark, smooth_dark) rendered for listening |
| 2026-10-24 | User chose **dark** for both instruments; production presets updated |
| 2026-10-24 | Regenerated 176 candidates, 88 final notes and the final SF2; all checks repeated |
| 2026-10-24 | Upright double bass tested for A0-B0 with the dark calibration; **user rejected the direction** |
| 2026-10-24 | Owner requested twice the output level; final notes and SF2 rebuilt at 0.120 RMS, gain defaults unchanged |
| 2026-10-24 | Project closed with the electric bass retained for all bass notes; experiments removed |
| 2026-11-08 | Owner heard too much mid/high energy in the sample player; the SF2 carries no filter generator, so the balance is the calibration itself |
| 2026-11-08 | Four darker variants measured (V1 moderate, V2 deep, V3 fundamental-forward, V4 gentle) plus low-register evidence for A0-C3; owner chose **V4** |
| 2026-11-08 | 88 notes, SF2 and EARP1 pack regenerated from V4 (SF2 `f3326f4c…`, pack `f2e14a32…`); all checks repeated |
| 2026-11-08 | Fixed the stale 0.060 RMS expectations in `InstrumentPresetTest` and `NoteLibraryExportTest` (pre-existing since the 0.120 revision); 103/103 MATLAB tests pass |

## Final model

For MIDI note `k`, `f0 = 440 * 2^((k-69)/12)`. Partials:

```
f_n   = n * f0 * sqrt(1 + B * n^2)
A_n   = |sin(pi*n*beta) * sin(pi*n*u)| / n^p * |H_body(f_n)|
phase = sign(sin(pi*n*beta) * sin(pi*n*u))
tau_n = tau1 / n^q
```

| Parameter | Guitar | Bass |
|---|---|---|
| `p` | 1.55 | 1.55 |
| `beta` | 0.20 | 0.18 |
| `u` | none | 0.20 |
| `B` | 4.0e-5 | 3.0e-5 |
| `tau1`, `q` | 1.10 s, 0.70 | 1.50 s, 0.75 |
| Body modes | 106/216/268/431/553/1010 Hz, Q 30/25/25/20/20/15, gains 0.40/0.64/0.48/0.28/0.16/0.04 | 95/700 Hz, Q 1.2/2.0, gains 0.28/0.04 |
| Filters | HP 80 Hz Q 0.7, LP 1.5 kHz Q 0.5 | LP 0.9 kHz Q 0.5 |
| Native range | E2-E6 (MIDI 40-88) | E1-G4 (MIDI 28-67) |

Common: 48 kHz, 2.5 s, RMS 0.120, peak limit 0.98, 1.5 ms raised-cosine attack,
50 ms release, 2^20-point inverse FFT, floor at -80 dB, partials capped at
0.45*fs.

The `p = 1.55` exponent and the 1.5 kHz / 0.9 kHz low-passes carry the difference
between bridge force and radiated pressure. This is the **V4 dark** calibration
(2026-11-08). The 2026-10-24 dark calibration used `p = 1.3`, LP 2 kHz (guitar) /
1.2 kHz (bass) at Q 0.7 and the higher body peak gains 0.5/0.8/0.6/0.35/0.2/0.05
and 0.35/0.05.

## Final export

- Path: `out/string_synth.sf2`
- Size: 21,126,646 bytes
- SHA-256: `f3326f4c53c52a521bce7e9e2932edb9e34807f85a96b391eb224a5a0b499705`
- Preset: `String Synth A0-C8`, bank 0, program 0
- Instrument: `Bass<=59 / Guitar>59`
- 88 zones and 88 samples, keys 21-108
- 48 kHz, 120,000 frames, 16-bit mono, root key = key, correction 0, no loop
- 39 bass notes (MIDI 21-59), 49 guitar notes (MIDI 60-108)
- 88 final WAVs in `out/audio/notes`, RMS 0.1200, peak 0.4306-0.7315
- 176 review candidates in `out/audio/candidates`, peak 0.4064-0.7626
- EARP1 sample pack: `app/src/main/assets/soundfont/earby-string-pack.bin`,
  21,126,760 bytes, SHA-256
  `f2e14a32c252c5d94ec5f1b7f8cbf63da6ff7a2c08afb49470af591cc6c05462`

## Discarded directions

- **Bright v1**: `p = 1.0`, pluck 0.14/0.13, pickup 0.10, guitar low-pass 7 kHz,
  bass resonant low-pass 3.2 kHz Q 1.5. It put too much energy in the upper mids
  (bass A2: 52.5 percent in 300 Hz-1 kHz). Superseded by the dark calibration.
- **Smooth variant**: sine/velocity onset, 5 ms attack, broader body peaks,
  shallower pickup comb. User did not select it.
- **Smooth + dark variant**: combined the smooth onset/resonances with the dark
  rolloff. User preferred plain dark.
- **Upright double bass**: tested A0, A#0, B0 with body modes
  65/95/200/420/750/1200 Hz, no pickup comb, 40 Hz high-pass, 1.5 kHz low-pass,
  `p = 1.3`, `tau1 = 1.0 s`, `q = 0.55`, `B = 2.5e-5`. User rejected the
  direction; the electric bass remains for MIDI 21-59. Test script and renders
  were removed.
- **Reference recordings**: University of Iowa samples and rendered full-GM-font
  steel-guitar / electric-bass notes were used for calibration and deleted
  afterwards; none are redistributed.

## Verification summary

- MATLAB unit tests: 103 passed, 0 failed, 30.3 s.
- Python unit tests: 14 passed (6 SF2 builder, 8 pack builder) for the V4 build;
  `tools/check.sh` exit 0.
- SF2 structural validator: exit 0; 88 samples; PCM byte-identical to the final
  WAVs; mapping hashes match.
- Repository `tools/audio-analysis/sf2_tool.py`: parses one preset with 88
  samples.
- Gervill playback (2026-10-24 font, not repeated for V4): 88/88 keys non-silent;
  fundamentals 27.44 Hz (-0.21%),
  110.05 Hz (+0.04%), 440.00 Hz, 1046.50 Hz, 4186.09 Hz; 8/8/7/4/3 matched
  harmonics.
- Code Analyzer: no issues in any `.m` file.
- Final-note RMS exactly 0.1200 in the V4 build; all files mono / 48 kHz /
  16-bit / 120,000 frames; no clipping (peak 0.4306-0.7315). V4 bank check:
  SF2 samples, pack entries and note WAVs byte-identical, 0 problems.

Detailed evidence and commands: `docs/VERIFICATION.md`.

## Commands

Inside MATLAB R2024b, from this directory:

```matlab
run_synthesis
build_final(59)
runtests('tests')
```

From a shell:

```bash
python3 -m unittest discover -s tools/tests
python3 tools/build_sf2.py --wav-dir out/audio/notes \
    --mapping out/mapping.json --out out/string_synth.sf2
python3 tools/validate_sf2.py out/string_synth.sf2 \
    --wav-dir out/audio/notes --mapping out/mapping.json
python3 tools/build_sample_pack.py   # after installing the new SF2 in the app assets
java --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED \
    tools/Sf2RenderProbe.java out/string_synth.sf2
```

## Files

```
synthNotes/
  README.md
  docs/LEDGER.md, docs/VERIFICATION.md
  run_synthesis.m, build_final.m
  src/                 note table, presets, partial model, FFT synthesis, analysis
  tests/               five matlab.unittest classes, 103 tests
  tools/               SF2 writer/validator, unit tests, Java render probe, check.sh
  out/                 generated, gitignored
    string_synth.sf2
    audio/notes/       88 final one-shots
    audio/candidates/  176 review renders
    test_samples/      12-note excerpt set plus montage
    review/            crossover review page
    figures/           four diagnostic figures
    summary.csv, partials.csv, final_notes.csv, mapping.json
```

## Limitations

- Synthetic model, no pick noise, string-body coupling, two-stage decay, beating,
  stereo or room.
- One inharmonicity coefficient per instrument; real strings vary by gauge.
- Notes outside the native ranges are extrapolated.
- The dark calibration is a deliberate simplification; it is not a measured
  instrument impulse response.
- One-shot SF2 samples, no loop; note-off behaviour depends on the player.
- Cavity/plate behaviour below about 80 Hz is approximate.
