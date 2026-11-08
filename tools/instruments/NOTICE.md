# Instrument notices

The historical 0.9.22 hybrid piano was a local instrument built from two sources:

- **Osiris Piano**, Versilian Studios and Karoryfer Samples, CC0 1.0. Source:
  https://github.com/sfzinstruments/Osiris_Piano, commit
  `18c6afccb60cff458edbf7c394571783e074e1e9`. Used for MIDI 21-49 (B0-C#3), Mic A natural samples.
- **MuseScore General v0.2**, MIT. Source:
  https://ftp.osuosl.org/pub/musescore/soundfont/MuseScore_General/MuseScore_General.sf2.
  Used for the original separator cue and for MIDI 50-108 (D3-C8), rendered as one-shot samples.

The current V4 instrument is the owner-generated custom additive string synth: the tracked
generator input is `synthNotes/StringSynth_A0-C8.sf2` (21,126,646 bytes, SHA-256
`f3326f4c53c52a521bce7e9e2932edb9e34807f85a96b391eb224a5a0b499705`, V4 dark calibration selected
2026-11-08). It contains no MuseScore or Osiris sample content and is not packaged. The app ships
the matching EARP1 pack `app/src/main/assets/soundfont/earby-string-pack.bin` (21,126,760 bytes,
SHA-256 `f2e14a32c252c5d94ec5f1b7f8cbf63da6ff7a2c08afb49470af591cc6c05462`), derived from that font
and containing the same project-generated samples only. The earlier
0.9.22 hybrid font was `app/src/main/assets/soundfont/MuseScore_General.sf2` (39,688,036 bytes,
SHA-256 `4f2c932bcb89218e206491e9fe5c10ac2bd52de9c4804096d901a29adec00a6d`). The separator
clip is `app/src/main/assets/separator/original-separator.wav` (30,798 bytes, SHA-256
`ed935306de57c8466d7d4a096817624d945ac0858771251adbc2d741c8ac96a6`).


## Release note

The current 1.0.0 release ships the EARP1 pack derived from the owner-generated
`StringSynth_A0-C8.sf2` (the SF2 itself is a tracked generator input, not packaged) and the
separator clip.
The separator clip's source, licence and rendering process are recorded in
`docs/legal/ASSET_SOURCES.md`: the project owner rendered it on 2026-10-18 as a C6/G6 cue from
MuseScore General v0.2 (MIT, upstream SHA-256
`ee51d2c4b1525e70f19a45909c4fd7a2e26d91d115fa89dbf5a6bc413d8b9bf3`) with the development harness
`separator_render.cpp`, and the shipped bytes were reproduced bit-exactly from the hash-verified
upstream font on 2026-11-08. The upstream MIT acknowledgements are carried in
`THIRD_PARTY_NOTICES.md` and `LICENSES/MuseScore_General-MIT.txt`. The clip is unchanged; no
replacement is needed. The historical MuseScore/Osiris hybrid font is not shipped.
