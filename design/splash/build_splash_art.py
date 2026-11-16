"""Build res/drawable-nodpi/splash_art.webp from the image25 key art.

The splash animates on a 1080x2400 canvas (9:20). The generated art is 9:16,
so we cover-crop: scale = max(1080/864, 2400/1536) = 1.5625 -> 1350x2400.
We bake that scale here with Lanczos so the device only ever needs 1:1 blits,
instead of a soft 1.56x GPU upscale on every frame.

  python design/splash/build_splash_art.py
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "design/splash/assets/gen-poster-vinyl-01.png"
DST = ROOT / "app/src/main/res/drawable-nodpi/splash_art.webp"

W, H = 1350, 2400

art = Image.open(SRC).convert("RGB")
s = max(W / art.width, H / art.height)
scaled = art.resize((round(art.width * s), round(art.height * s)), Image.LANCZOS)
x = (scaled.width - W) // 2
y = (scaled.height - H) // 2
out = scaled.crop((x, y, x + W, y + H))
out.save(DST, "WEBP", quality=86, method=6)
print("wrote", DST, out.size, DST.stat().st_size // 1024, "KB")
