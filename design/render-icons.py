#!/usr/bin/env python3
"""Render the fallback EarBy icon artwork from its SVG sources.

Requires cairosvg and Pillow. This writes `icons/EarByIconV5.png` only; the live
launcher and store assets are generated from the current artwork by
`tools/icon/generate_launcher_icons.py`.
"""
from io import BytesIO
from pathlib import Path

import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'design' / 'earby-icon.svg'


def render(svg: Path, size: int) -> Image.Image:
    png = cairosvg.svg2png(url=str(svg), output_width=size, output_height=size)
    return Image.open(BytesIO(png)).convert('RGBA')


def main() -> None:
    render(SOURCE, 1024).save(ROOT / 'icons/EarByIconV5.png')


if __name__ == '__main__':
    main()
