# Google Play checklist

## Account and identity

- [ ] Personal developer account verified and fee paid.
- [ ] Legal developer name, contact email and address complete.
- [ ] App created for `com.earby.app` and Play App Signing enabled.

## Legal release blockers

- [x] Separator clip provenance confirmed: a C6/G6 render of the MIT MuseScore General v0.2 font,
      reproduced bit-exactly from the hash-verified upstream font; recorded in
      `docs/legal/ASSET_SOURCES.md` with the upstream acknowledgements in
      `THIRD_PARTY_NOTICES.md`. The clip itself is unchanged and the owner accepted it by ear on
      2026-11-08 (see `docs/audio/ASSETS.md`).
- [ ] Publisher contact email and public HTTPS privacy-policy URL present in the listing and policy.
- [x] Hand-drawn EarBy icon by Dr Ham Hamster used with her permission; no Qwen-Image 2.1 V4
      asset or credit remains.

## Signed artifacts (2026-11-08)

- `dist/play/EarBy-1.0.0.aab` — 30,240,377 bytes, SHA-256
  `63e458398eb87e32785b0d2e932c2050c60f0421d9eefae622cae0a90e5b65bf`, built from commit
  `f634502` with the JNI keep rules, upload-key signed.
- `dist/release/EarBy-1.0.0.apk` — 35,465,948 bytes, SHA-256
  `eb983c34c4dc804a793799d587eb76753c1a818aa08c0c4c043cc2d868bdec36`, built from commit
  `f634502` with the JNI keep rules, upload-key signed (the minified hash can vary per run; this
  is the built artifact).
- Upload certificate SHA-256:
  `1d48d8d0bc148a1314f377dfcadfd9a2ec01feb530db2bb57dc98b6e6f5bb9d5`.
- `scripts/check-release.sh` PASS on both artifacts.
- Upload to Play Console is still pending (see below).

## Technical release

- [ ] Signed AAB uploaded to internal testing.
- [ ] Pre-launch report has no unresolved crash, ANR or policy issue.
- [ ] Closed test: at least 12 testers opted in for 14 continuous days.
- [ ] Production access granted.
- [ ] Staged rollout 10% → 50% → 100%.

## Store listing

- [x] 512×512 icon and 1024×500 feature graphic are in `store/`.
- [x] 2–8 Play phone screenshots from the 1.0.0 build: eight captures in `store/screenshots/`.
- [x] Screenshots are JPEG or 24-bit PNG without alpha and within Play's 2:1 aspect limit:
      1080x2160 24-bit RGB PNG at exactly 2:1 (rules in `docs/release/STORE_LISTING.md`).
- [x] Title, short description and full description (`store/listing.md`).
- [x] Education category and contact email.
- [x] Privacy-policy URL reachable over HTTPS.

## App content

- [ ] Data safety: no collection and no sharing.
- [ ] No ads, no in-app purchases, no login.
- [ ] Target audience 13+; content rating completed.
- [ ] No news, health, financial, government or COVID declarations.
- [ ] Foreground media-playback service and wake-lock usage explained if requested.

## Post-release

- [ ] Android vitals monitored.
- [ ] Previous AAB retained for rollback.
- [ ] GitHub release and privacy site match the Play listing.
