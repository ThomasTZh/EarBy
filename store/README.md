# Store assets

These are Play Store upload assets, not screenshot test baselines.

- `icon-512.png` — 512×512, RGB, no alpha. Full-bleed version of the hand-drawn icon by
  Dr Ham Hamster, used with her permission; Play applies its own corner mask.
- `feature-graphic.png` — 1024×500. Original project art.
- `screenshots/` — the eight Play phone captures of the upload-key-signed 1.0.0 release
  (2026-11-08), in order: `01-play-idle`, `02-play-answer`, `03-setup`, `04-presets`,
  `05-settings`, `06-help-credits`, `07-interval-walk`, `08-dark-theme`. Every file is a
  1080x2160 24-bit RGB PNG without alpha and exactly 2:1: the status bar is removed, the rest of
  the 1080x2400 capture is scaled to 2160 px tall and letterboxed with the sampled background
  colour, so the full app UI stays visible; most screens show the bottom navigation. No personal
  data, notifications, device serials or developer diagnostics are visible. See
  `docs/release/STORE_LISTING.md` for the rules.

The icon is not CC BY 4.0; see `docs/legal/ASSET_SOURCES.md`. All other store art is original
project work under CC BY 4.0.
