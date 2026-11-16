"""Extract frames from the emulator screen recording and build contact sheets.

  python design/splash/capture_frames.py            # whole clip, 10 fps, 6x4 sheet
  python design/splash/capture_frames.py 1.2 3.4 0.06   # window + step (seconds)
"""
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw

import imageio_ffmpeg

HERE = Path(__file__).resolve().parent
CLIP = HERE / "capture.mp4"
OUT = HERE / "capture-frames"

FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()

args = sys.argv[1:]
t0 = float(args[0]) if len(args) > 0 else 0.0
t1 = float(args[1]) if len(args) > 1 else 0.0
step = float(args[2]) if len(args) > 2 else 0.10

OUT.mkdir(exist_ok=True)
for old in OUT.glob("*.png"):
    old.unlink()

if t1 > t0:
    # 逐个时间点精确取帧（-ss 放在 -i 前面 = 快速定位）
    times = []
    t = t0
    while t <= t1 + 1e-6:
        times.append(round(t, 3))
        t += step
    for i, ts in enumerate(times):
        dst = OUT / f"f{i:03d}.png"
        subprocess.run(
            [FFMPEG, "-y", "-loglevel", "error", "-ss", f"{ts}", "-i", str(CLIP),
             "-frames:v", "1", str(dst)],
            check=True,
        )
    labels = [f"{ts:.2f}s" for ts in times]
else:
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", str(CLIP),
                    "-vf", "fps=10", str(OUT / "f%03d.png")], check=True)
    labels = [f"{i / 10:.1f}s" for i in range(len(list(OUT.glob('*.png'))))]

files = sorted(OUT.glob("*.png"))
if not files:
    raise SystemExit("no frames")
print(len(files), "frames")

COLS = 6
TH = 420
tw = int(Image.open(files[0]).width * TH / Image.open(files[0]).height)
rows = (len(files) + COLS - 1) // COLS
PAD, LBL, GAP = 10, 20, 6
sheet = Image.new("RGB", (PAD * 2 + tw * COLS + GAP * (COLS - 1),
                          PAD * 2 + rows * (TH + LBL + GAP)), (245, 245, 248))
d = ImageDraw.Draw(sheet)
for i, f in enumerate(files):
    r, c = divmod(i, COLS)
    x = PAD + c * (tw + GAP)
    y = PAD + r * (TH + LBL + GAP)
    sheet.paste(Image.open(f).resize((tw, TH), Image.LANCZOS), (x, y))
    d.text((x, y + TH + 4), labels[i] if i < len(labels) else "", fill=(30, 30, 36))

dst = HERE / ("capture-sheet.png" if t1 <= t0 else "capture-window.png")
sheet.save(dst)
print("wrote", dst, sheet.size)
