#!/usr/bin/env python3
"""Render F-Droid repo and app icons from the Android launcher artwork."""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
FDROID = ROOT / "fdroid"
BG = "#EC0016"
# ic_launcher_foreground path (108dp viewport)
PATH_108 = [(20, 70), (40, 40), (55, 55), (75, 30), (88, 45), (70, 75), (55, 60), (40, 80)]


def render(size: int) -> Image.Image:
    img = Image.new("RGBA", (size, size), BG)
    draw = ImageDraw.Draw(img)
    scale = size / 108
    pts = [(x * scale, y * scale) for x, y in PATH_108]
    draw.polygon(pts, fill="#FFFFFF")
    return img


def main() -> None:
    icon = render(512)
    icon.save(FDROID / "icon.png", "PNG", optimize=True)
    (FDROID / "icons").mkdir(parents=True, exist_ok=True)
    icon.save(FDROID / "icons" / "icon.png", "PNG", optimize=True)

    app_icon = FDROID / "metadata" / "de.openbahn.navigator.debug" / "en-US" / "images" / "icon.png"
    app_icon.parent.mkdir(parents=True, exist_ok=True)
    icon.save(app_icon, "PNG", optimize=True)

    graphics = FDROID / "graphics"
    graphics.mkdir(parents=True, exist_ok=True)
    render(48).save(graphics / "icon-48.png", "PNG", optimize=True)
    icon.save(graphics / "icon-512.png", "PNG", optimize=True)


if __name__ == "__main__":
    main()
