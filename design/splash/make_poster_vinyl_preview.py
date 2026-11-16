"""Build preview_poster_vinyl.py from preview_poster.py, swapping in the
image25-generated vinyl-poster art and a vinyl shard palette.
"""
from pathlib import Path

here = Path(__file__).resolve().parent
src = (here / "preview_poster.py").read_text(encoding="utf-8")

s = src.replace(
    'OUT = Path(__file__).resolve().parent / "preview-poster.png"',
    'OUT = Path(__file__).resolve().parent / "preview-poster-vinyl.png"',
)
s = s.replace(
    'art = Image.open(ASSETS / "splash-color-1080.png").convert("RGB")',
    'art = Image.open(ASSETS / "gen-poster-vinyl-01.png").convert("RGB")',
)
s = s.replace(
    "dd.polygon(pts, fill=(96, 86, 72, 240), outline=(24, 20, 16, 255))",
    "dd.polygon(pts, fill=(28, 26, 28, 245), outline=(6, 5, 7, 255))",
)
s = s.replace(
    "dd.line([pts[0], pts[1], pts[2]], fill=(232, 205, 160, 200), width=2)",
    "dd.line([pts[0], pts[1], pts[2]], fill=(250, 190, 110, 210), width=2)",
)
s = s.replace(
    "ps = [0.0, 0.22, 0.40, 0.62, 0.82, 1.0]",
    "ps = [0.0, 0.20, 0.36, 0.55, 0.75, 1.0]",
)

assert s != src
(here / "preview_poster_vinyl.py").write_text(s, encoding="utf-8")
print("wrote preview_poster_vinyl.py")
