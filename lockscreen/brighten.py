# Brighten a photo for the e-ink sleep screen (it looks dark once the front light is off).
# Run: calibre-debug -e brighten.py -- in.png out.png [gamma] [lift]
import sys
from qt.core import QImage

src, dst = sys.argv[1], sys.argv[2]
gamma = float(sys.argv[3]) if len(sys.argv) > 3 else 0.72   # < 1 lifts mid-tones and shadows
lift = float(sys.argv[4]) if len(sys.argv) > 4 else 18      # raises the black point a little

lut = bytes(min(255, round(lift + (255 - lift) * ((i / 255) ** gamma))) for i in range(256))
img = QImage(src).convertToFormat(QImage.Format.Format_RGB888)
w, h, bpl = img.width(), img.height(), img.bytesPerLine()
data = bytes(img.constBits().asarray(bpl * h)).translate(lut)
out = QImage(data, w, h, bpl, QImage.Format.Format_RGB888).copy()
print(w, h, "gamma", gamma, "lift", lift, "saved", out.save(dst, "PNG"))
