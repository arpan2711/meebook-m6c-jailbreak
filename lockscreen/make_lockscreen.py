# Resize/crop a photo to the M6C screen (1072x1448). Run: calibre-debug -e make_lockscreen.py -- in.png out.png
import sys
from qt.core import QImage, Qt
W, H = 1072, 1448
img = QImage(sys.argv[1])
s = img.scaled(W, H, Qt.AspectRatioMode.KeepAspectRatioByExpanding, Qt.TransformationMode.SmoothTransformation)
x, y = (s.width() - W) // 2, (s.height() - H) // 2
out = s.copy(x, y, W, H)
print(img.width(), img.height(), '->', out.width(), out.height(), 'saved', out.save(sys.argv[2], 'PNG'))
