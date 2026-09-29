"""Render the existing FlowFrame vector brand as an opaque iOS app icon.

Requires Pillow; no generated imagery or downloaded artwork is used.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "ios/FlowFrame/Assets.xcassets"
SIZE = 2048
SCALE = SIZE / 108


def box(values):
    return tuple(round(x * SCALE) for x in values)


def main():
    image = Image.new("RGB", (SIZE, SIZE), "#5956DB")
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle(box((30, 19, 86, 61)), radius=round(11 * SCALE), fill="#B9B9FF")
    draw.rounded_rectangle(box((19, 29, 81, 83)), radius=round(13 * SCALE), fill="white")
    draw.polygon([box((40, 43)), box((40, 68)), box((60, 55.5))], fill="#5956DB")
    draw.ellipse(box((51, 57, 89, 95)), fill="#5956DB")
    draw.ellipse(box((54, 60, 86, 92)), fill="#8DE5D4")
    width = round(3.4 * SCALE)
    draw.line([box((70, 67)), box((70, 83))], fill="#28286B", width=width)
    draw.line([box((63.5, 76.5)), box((70, 83)), box((76.5, 76.5))], fill="#28286B", width=width, joint="curve")
    icon = ROOT / "AppIcon.appiconset"
    icon.mkdir(parents=True, exist_ok=True)
    image.resize((1024, 1024), Image.Resampling.LANCZOS).save(icon / "AppIcon.png")
    (icon / "Contents.json").write_text(json.dumps({
        "images": [{"filename": "AppIcon.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"}],
        "info": {"author": "xcode", "version": 1}
    }, indent=2) + "\n", encoding="utf-8")
    (ROOT / "Contents.json").write_text('{"info":{"author":"xcode","version":1}}\n', encoding="utf-8")


if __name__ == "__main__":
    main()
