#!/usr/bin/env python3
"""Generate the launcher assets from the hand-drawn EarBy icon.

The artwork is `icons/EarByIconV6.png`, hand-drawn by Dr Ham Hamster and used with
her permission. The tile around the drawing is replaced by a flat mint field so the
same mark can be cut by the adaptive-icon masks without a white halo; the original
tile stays untouched in `icons/`.

Requires Pillow. Run from the repository root or any directory:

    python3 tools/icon/generate_launcher_icons.py
    python3 tools/icon/generate_launcher_icons.py --placement centroid
"""
import argparse
from collections import deque
from functools import lru_cache
from pathlib import Path
from struct import iter_unpack

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'icons' / 'EarByIconV6.png'

# Sampled from the artwork's flat tile. It is the adaptive-icon background colour and
# the fill behind the legacy and store icons.
BACKGROUND = (126, 224, 211)
LEGACY = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
ADAPTIVE = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}

# Share of the icon edge the drawing's bounding box should occupy. Adaptive icons keep
# a smaller mark so it stays inside the 66 dp safe circle of the 108 dp canvas.
LEGACY_FILL = 0.63
ADAPTIVE_FILL = 0.60
CORNER_RADIUS = 0.20
# Shapes and the mark are drawn oversized and scaled down, which anti-aliases the
# rounded tile and the circular icon without a mask step.
SUPERSAMPLE = 4


def _pixels(image: Image.Image) -> list[tuple[int, int, int, int]]:
    return [tuple(channel) for channel in iter_unpack('4B', image.tobytes())]


def _luminance(pixel: tuple[int, int, int, int]) -> float:
    red, green, blue, _ = pixel
    return 0.299 * red + 0.587 * green + 0.114 * blue


def _outside_mask(image: Image.Image) -> bytearray:
    """Mark the white page around the drawn tile, grown slightly over its soft edge."""
    width, height = image.size
    pixels = _pixels(image)
    mask = bytearray(width * height)
    queue: deque[tuple[int, int]] = deque()
    for x in range(width):
        for y in (0, height - 1):
            queue.append((x, y))
    for y in range(height):
        for x in (0, width - 1):
            queue.append((x, y))
    while queue:
        x, y = queue.popleft()
        index = y * width + x
        if mask[index] or _luminance(pixels[index]) < 235:
            continue
        mask[index] = 1
        if x > 0:
            queue.append((x - 1, y))
        if x < width - 1:
            queue.append((x + 1, y))
        if y > 0:
            queue.append((x, y - 1))
        if y < height - 1:
            queue.append((x, y + 1))
    # The tile edge anti-aliases into the page; drop that soft ring as well.
    for _ in range(3):
        grown = bytearray(mask)
        for y in range(height):
            row = y * width
            for x in range(width):
                if not mask[row + x]:
                    continue
                if x > 0:
                    grown[row + x - 1] = 1
                if x < width - 1:
                    grown[row + x + 1] = 1
                if y > 0:
                    grown[row - width + x] = 1
                if y < height - 1:
                    grown[row + width + x] = 1
        mask = grown
    return mask


@lru_cache(maxsize=1)
def art_layer() -> Image.Image:
    """Return the ear-and-note mark with the mint tile and white page made transparent.

    Pixels close to the sampled mint colour become transparent, fully saturated
    pixels stay opaque, and the anti-aliased drawing boundary keeps partial alpha.
    """
    image = Image.open(SOURCE).convert('RGBA')
    outside = _outside_mask(image)
    red, green, blue = BACKGROUND
    kept = []
    for index, (r, g, b, a) in enumerate(_pixels(image)):
        if outside[index]:
            kept.append((r, g, b, 0))
            continue
        distance = max(abs(r - red), abs(g - green), abs(b - blue))
        if distance <= 8:
            kept.append((r, g, b, 0))
        elif distance >= 24:
            kept.append((r, g, b, a))
        else:
            kept.append((r, g, b, int(a * (distance - 8) / 16)))
    layer = Image.new('RGBA', image.size)
    layer.putdata(kept)
    return layer


@lru_cache(maxsize=1)
def art_mark() -> Image.Image:
    """The mark cropped to its visible bounds.

    The crop uses the drawing's own ink (the white strokes and the black note), not the
    faint anti-aliased ring where the drawn tile met the page. That ring survives the
    mint keying with a very low alpha and would otherwise pull the crop off centre.
    """
    layer = art_layer()
    width, height = layer.size
    min_x, min_y, max_x, max_y = width, height, -1, -1
    for index, (r, g, b, a) in enumerate(_pixels(layer)):
        if a < 200:
            continue
        luminance = 0.299 * r + 0.587 * g + 0.114 * b
        if 70 < luminance < 200:
            continue
        x, y = index % width, index // width
        min_x, max_x = min(min_x, x), max(max_x, x)
        min_y, max_y = min(min_y, y), max(max_y, y)
    if max_x < 0:
        box = layer.getbbox()
        if box is None:
            raise SystemExit('The art layer is empty; check the mint colour')
        return layer.crop(box)
    margin = 2
    return layer.crop(
        (
            max(0, min_x - margin),
            max(0, min_y - margin),
            min(width, max_x + margin + 1),
            min(height, max_y + margin + 1),
        )
    )


def _centroid_offset(mark: Image.Image) -> tuple[float, float]:
    """Alpha-weighted centre of the mark, in the mark's own pixel coordinates."""
    total = 0.0
    weighted_x = 0.0
    weighted_y = 0.0
    width = mark.width
    for index, (_, _, _, alpha) in enumerate(_pixels(mark)):
        if not alpha:
            continue
        total += alpha
        weighted_x += (index % width) * alpha
        weighted_y += (index // width) * alpha
    if not total:
        return mark.width / 2, mark.height / 2
    return weighted_x / total, weighted_y / total


def place_mark(canvas: Image.Image, fill: float, placement: str) -> None:
    """Place the mark at `fill` of the canvas edge.

    `box` centres the drawing's bounding box; `centroid` centres its ink mass, which
    compensates for the open ear curve on the right and the note in the lower left.
    """
    size = canvas.width
    mark = art_mark()
    target_height = max(1, round(size * fill))
    target_width = max(1, round(mark.width * target_height / mark.height))
    resized = mark.resize((target_width, target_height), Image.LANCZOS)
    if placement == 'centroid':
        centre_x, centre_y = _centroid_offset(resized)
        left = round(size / 2 - centre_x)
        top = round(size / 2 - centre_y)
    elif placement == 'box':
        left = (size - target_width) // 2
        top = (size - target_height) // 2
    else:
        raise SystemExit(f'Unknown placement {placement!r}; use box or centroid')
    canvas.alpha_composite(resized, (left, top))


def _transparent_mint(size: int) -> Image.Image:
    """A canvas whose fully transparent pixels still carry the mint colour.

    Straight-alpha scaling then blends mint with mint at the tile edge instead of
    fading towards black, which would leave a dark fringe.
    """
    return Image.new('RGBA', (size, size), BACKGROUND + (0,))


def tile(size: int, round_icon: bool, placement: str) -> Image.Image:
    """A mint tile with the mark placed inside it."""
    large = size * SUPERSAMPLE
    canvas = _transparent_mint(large)
    draw = ImageDraw.Draw(canvas)
    if round_icon:
        draw.ellipse((0, 0, large - 1, large - 1), fill=BACKGROUND + (255,))
    else:
        draw.rounded_rectangle(
            (0, 0, large - 1, large - 1),
            radius=int(large * CORNER_RADIUS),
            fill=BACKGROUND + (255,),
        )
    place_mark(canvas, LEGACY_FILL, placement)
    return canvas.resize((size, size), Image.LANCZOS)


def store_icon(size: int, placement: str) -> Image.Image:
    """Play listing icon: a full-bleed mint square, since Play applies its own mask."""
    canvas = Image.new('RGBA', (size, size), BACKGROUND + (255,))
    place_mark(canvas, LEGACY_FILL, placement)
    return canvas.convert('RGB')


def adaptive_foreground(size: int, placement: str) -> Image.Image:
    """The mark alone on a transparent 108 dp canvas."""
    canvas = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    place_mark(canvas, ADAPTIVE_FILL, placement)
    return canvas


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        '--placement',
        choices=('box', 'centroid'),
        default='box',
        help='box centres the drawing bounds (default), centroid centres the ink mass',
    )
    placement = parser.parse_args().placement
    for density, size in LEGACY.items():
        base = ROOT / 'app/src/main/res' / f'mipmap-{density}'
        tile(size, round_icon=False, placement=placement).save(base / 'ic_launcher.png')
        tile(size, round_icon=True, placement=placement).save(
            base / 'ic_launcher_round.png'
        )
    for density, size in ADAPTIVE.items():
        base = ROOT / 'app/src/main/res' / f'mipmap-{density}'
        Image.new('RGBA', (size, size), BACKGROUND + (255,)).save(
            base / 'ic_launcher_background.png'
        )
        adaptive_foreground(size, placement).save(base / 'ic_launcher_foreground.png')

    store_icon(512, placement).save(ROOT / 'store' / 'icon-512.png')
    print(f'Rendered launcher icons, adaptive layers and store/icon-512.png ({placement})')


if __name__ == '__main__':
    main()
