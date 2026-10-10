# Crop a photo to the M6C screen shape (1072x1448) and scale it, never stretching it.
# The crop keeps the given left, right and bottom edges; the top edge follows from the screen's
# aspect ratio. Coordinates are in the source photo's pixels.
# Run: calibre-debug -e crop_lockscreen.py -- in.png out.png LEFT RIGHT BOTTOM
import sys
from qt.core import QImage, Qt

W, H = 1072, 1448
src, dst = sys.argv[1], sys.argv[2]
left, right, bottom = (int(v) for v in sys.argv[3:6])
img = QImage(src)
width = right - left
height = round(width * H / W)
top = bottom - height
if top < 0:
    sys.exit(f"crop needs {height}px of height but only {bottom} above the bottom edge")
out = img.copy(left, top, width, height).scaled(
    W, H, Qt.AspectRatioMode.IgnoreAspectRatio, Qt.TransformationMode.SmoothTransformation)
# width/height already has the screen's ratio, so this scale is uniform (no stretch)
print(f"crop x {left}-{right} y {top}-{bottom} ({width}x{height}) scale {W / width:.3f}",
      "saved", out.save(dst, "PNG"))
