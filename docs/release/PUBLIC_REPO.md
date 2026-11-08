# Public repository snapshot

The public GitHub repository is a distributable snapshot with a single initial commit; the
internal development history is not published. `scripts/export-public-snapshot.sh` is the **only**
supported export path: do not assemble the tree by hand and do not push the private `main` branch
to the public repository.

Include only:

- Gradle build files, wrapper and pinned catalogs;
- `app`, `core`, `gradle`, `scripts` and the source needed to build;
- `app/src/main/assets` shipped instrument pack (EARP1), voice pack and separator;
- `design`, `icons` (V5 fallback and V6 hand-drawn only) and `store` release art;
- root license/notices, README, community files and curated docs;
- privacy site.

The export removes:

- `.local`, `dist`, `.gradle`, `build`, `.cxx`, IDE files and `local.properties`;
- keystores, passwords, signing properties and device serials;
- `synthNotes/out`, `tools/**/raw`, candidate/superseded voice WAVs and private evidence;
- `AGENTS.md`, `planDraft.md`, `docs/testing/notes4human.txt` and historical release evidence
  unless separately reviewed;
- the private icon drafts `icons/EarByIconV1.png` to `icons/EarByIconV4.png` (V4 is Qwen-Image
  2.1-derived and non-commercial); the snapshot ships only `EarByIconV5.png` (CC BY 4.0) and
  `EarByIconV6.png` (hand-drawn, used with permission);
- private filesystem locations: home-directory paths are replaced with a generic phrase.

## Procedure

1. Commit the private tree first; the export archives `HEAD` (`git archive HEAD`), so an
   uncommitted change is silently left out.
2. Run `scripts/export-public-snapshot.sh <empty-directory>`. The script fails on secret-like
   files, candidate audio, a V1-V4 icon, a missing V5/V6 icon, a reserved placeholder domain or a
   remaining home path.
3. Diff the export against the published tree. Every difference must be intentional; record it in
   the close-out note.
4. Confirm no Qwen-derived art: `store/feature-graphic.png` is the pre-Qwen project art
   (SHA-256 `b2fa9b6fb2b8349132920f3e9825896873fc8ae838a0fb571dac932657ced888`) and every
   launcher/store icon regenerates byte-identically from `icons/EarByIconV6.png` with
   `tools/icon/generate_launcher_icons.py`. Do not run `design/render-icons.py` against the live
   launcher resources; it renders only the V5 fallback.
5. Run `python3 scripts/check-docs.py`, `python3 scripts/check-architecture.py` and
   `python3 -m unittest discover -s scripts/tests`. Run `scripts/check-public-tree.sh` inside a
   git checkout of the export (it scans `git ls-files`); the private tree tracks candidate voice
   material that the export removes, so the private tree is expected to fail that scan.
6. Build from a fresh clone of the export with `scripts/fetch-native-sources.sh` first; the app
   build never downloads the native sources itself.
7. Publish as one initial commit, tag the reviewed source `v1.0.0`, and create the GitHub release
   from that tag. Do not attach an APK: only a debug-key test build exists, so there is no
   upload-key-signed artifact.
8. Enable GitHub Pages for `site/` (privacy policy). The contact address is
   `thomas.t.zhang@outlook.com`.

## Remote safety

The private repository keeps the public snapshot remote **read-only**: `public-snapshot` points at
the public repository for inspection, but its push URL is disabled so `git push` cannot publish
private `main` by accident. The public repository is recreated and pushed by the owner as a single
commit; re-enable push deliberately for that step only, then disable it again.
