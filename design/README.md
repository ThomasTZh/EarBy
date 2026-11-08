# EarBy icon sources

The current launcher and store icon is hand-drawn by **Dr Ham Hamster** and used with her
permission. The artwork is `icons/EarByIconV6.png`; it is not relicensed under CC BY 4.0 or
Apache-2.0, and the artist retains her rights. See `docs/legal/ASSET_SOURCES.md`.

`tools/icon/generate_launcher_icons.py` uses Pillow to produce the legacy and round launcher PNGs,
the adaptive-icon layers and the Play 512 icon from that drawing:

```bash
python3 -m pip install pillow
python3 tools/icon/generate_launcher_icons.py
```

The mark is centred by its drawing bounds, matching the artist's placement in the original tile.
Pass `--placement centroid` to centre its ink mass instead; that variant sits about 17 px lower at
512 px.

`earby-icon.svg`, `earby-icon-foreground.svg` and `earby-icon-round.svg` are earlier original EarBy
project artwork, licensed CC BY 4.0. They remain available as a fallback icon. `render-icons.py`
renders `icons/EarByIconV5.png` from the SVG source with cairosvg and Pillow; it does not touch the
live launcher assets:

```bash
python3 -m pip install cairosvg pillow
python3 design/render-icons.py
```
