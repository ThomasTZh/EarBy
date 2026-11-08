# Verification record

Project: string note synthesis, A0-C8. Date: 2026-10-24; V4 revision 2026-11-08.

Environment: MATLAB R2024b Update 9 (Linux x86_64), Python 3.12 standard library,
OpenJDK 25 with Gervill (`com.sun.media.sound`), GNU bash.

## Final artifact

| Field | Value |
|---|---|
| Path | `out/string_synth.sf2` |
| Size | 21,126,646 bytes |
| SHA-256 | `f3326f4c53c52a521bce7e9e2932edb9e34807f85a96b391eb224a5a0b499705` |
| EARP1 pack | `app/src/main/assets/soundfont/earby-string-pack.bin`, 21,126,760 bytes, SHA-256 `f2e14a32c252c5d94ec5f1b7f8cbf63da6ff7a2c08afb49470af591cc6c05462` |
| Preset | `String Synth A0-C8`, bank 0, program 0 |
| Instrument | `Bass<=59 / Guitar>59` |
| Zones / samples | 88 / 88 |
| Key range | MIDI 21-108 |
| Sample rate / frames | 48,000 Hz / 120,000 (2.5 s) |
| Root key / correction | key / 0 |
| Loop | none |

## 1. WAV audit

### V4 dark calibration (2026-11-08)

The owner chose V4 (the gentle-darkening variant: `p = 1.55`, LP 1.5 kHz guitar /
0.9 kHz bass at Q 0.5, body peak gains x0.8) after the sample-player listening
review. The 88 final notes, the SoundFont and the EARP1 pack were regenerated from
it; the 2026-10-24 figures below remain as the previous audit.

- 88 final WAVs and 176 review candidates, all mono / 48 kHz / 16-bit / 120,000
  frames.
- Final RMS exactly 0.12000; peak 0.4306-0.7315, no clipping.
- Candidate peak range 0.4064-0.7626.
- SF2 `f3326f4c…` validates: 88 samples, keys 21-108, 39 bass (21-59) / 49 guitar
  (60-108), 48 kHz, 120,000 frames, no loops; PCM byte-identical to the final WAVs.
- EARP1 pack `f2e14a32…`: 88 entries, MIDI 21-108, 120,000 frames each, payload
  SHA-256 `295476119e0db682e793d1f36ca277d264c6f6fc9623682d9ed07fd96759d908`;
  every entry matches the note WAV and carries the new SF2 hash.
- Metrics against the V4 targets (nine guitar notes): fundamental fraction 0.896
  full note and 0.737 in the first 150 ms, centroid 1031 Hz, 0.104 of the energy
  above 1.5 f0. Nine low notes (A0-C3): 0.713 / 0.238, centroid 86 Hz, 0.994 below
  300 Hz.
- The V4 package renders equal the new bank notes byte for byte (18 files).

### Volume revision (2026-10-24)

At the owner's request the final notes were rebuilt at **0.120 RMS**, twice the
original 0.060 level; measured peaks are 0.4398-0.8349 and remain below the 0.98
limit. The 0.060 figures in the original audit below are preserved as the
first-audition record. The final artifact hash and size in the table above are
updated for the rebuilt font.

176 candidates and 88 final notes:

- 264 files total, all mono, 48 kHz, 16-bit PCM, 120,000 frames.
- Candidate RMS: 0.06000 for every file; peak 0.2108-0.4686.
- Final RMS: 0.06000 for every file; peak 0.2199-0.4174.
- No clipping.

## 2. Structural SoundFont validation

Command:

```bash
python3 tools/validate_sf2.py out/string_synth.sf2 \
    --wav-dir out/audio/notes --mapping out/mapping.json
```

Result: exit 0.

- One preset, bank 0, program 0; one instrument.
- 88 zones, 88 samples, keys 21-108, contiguous.
- Every zone has a single-key range, `sampleModes = 0`,
  `overridingRootKey = key`, and `sampleID` last.
- Every sample has `originalPitch = key`, correction 0, rate 48 kHz, loop 0,
  mono type.
- Embedded PCM is byte-identical to the 88 final WAVs.
- Mapping source counts: bass 39, guitar 49; split MIDI 59.
- All 88 mapping SHA-256 values match the WAVs.

Second parser:

```bash
python3 tools/audio-analysis/sf2_tool.py out/string_synth.sf2 --list-presets
python3 tools/audio-analysis/sf2_tool.py out/string_synth.sf2 --bank 0 --program 0
```

Result: `bank 0 program 0 'String Synth A0-C8' instruments=1`, 88 samples,
21,120,000 sample bytes.

## 3. MATLAB tests

Command:

```matlab
runtests('tests')
```

Result: **103 passed, 0 failed, 0 incomplete, 30.3 s.**

Covered: note table endpoints and formula, preset validity, mono/48 kHz/RMS
format, attack energy, silent tail, determinism, preset difference, partial
frequencies against the inharmonic model, the high-band energy guard, inverse-FFT
versus additive equivalence (< -40 dB residual), WAV naming/format/summary
contract. The measured inverse-FFT versus additive residual is -67 to -73 dB.

Code Analyzer: no issues in any `.m` file.

## 4. Python tests

```bash
python3 -m unittest discover -s tools/tests
```

Result: **6 passed**, including the SF2 build/validate round trip, WAV contract
rejections, zone/sample mapping, and embedded PCM comparison.

`tools/check.sh` exit 0 after the cleanup.

## 5. Gervill playback

```bash
java -Djava.awt.headless=true \
  --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED \
  tools/Sf2RenderProbe.java out/string_synth.sf2
```

Result: **PASS**, exit 0.

- Soundbank loaded: 1 instrument, `String Synth A0-C8`.
- 88/88 keys rendered non-silent, output RMS range 0.0136-0.0257.
- Fundamentals: 27.44 Hz (-0.21%), 110.05 Hz (+0.04%), 440.00 Hz (0.00%),
  1046.50 Hz (0.00%), 4186.09 Hz (0.00%).
- Matched harmonics: 8, 8, 7, 4, 3.

## 6. Test samples

`out/test_samples/` contains 12 notes copied byte-identically from
`out/audio/notes` and a 32.2 s montage:

- Files: mono, 48 kHz, 16-bit, 120,000 frames, RMS 0.0600.
- Copies: per-file SHA-256 equals the final note and the manifest entry.
- Montage: 1,545,600 frames (32.20 s), peak 0.417, RMS 0.058 (RMS is lowered by
  the 0.2 s gaps).
- Page: 13 players (12 notes plus montage).

## 7. Reproducibility commands

```matlab
run_synthesis
build_final(59)
runtests('tests')
```

```bash
python3 -m unittest discover -s tools/tests
python3 tools/build_sf2.py --wav-dir out/audio/notes \
    --mapping out/mapping.json --out out/string_synth.sf2
python3 tools/validate_sf2.py out/string_synth.sf2 \
    --wav-dir out/audio/notes --mapping out/mapping.json
java --add-exports java.desktop/com.sun.media.sound=ALL-UNNAMED \
    tools/Sf2RenderProbe.java out/string_synth.sf2
```

## 8. Final workspace state

- `out/` is gitignored; it holds about 88 MB of generated audio, SF2, figures,
  reports and test samples.
- Experiment-only directories (`out/test_upright`, `out/test_timbre`) and their
  scripts were removed after the decisions.
- Calibration recordings (University of Iowa samples, full-GM-font renders) were
  deleted; nothing copyrighted is redistributed.
- No app, Kotlin or Gradle file was modified by the synthesis work. The generator
  source is tracked; `out/` remains ignored.
