"""Silver wallpaper resources from the two renders of silver_silk.py:
res/drawable-nodpi/wp_silver_day.jpg, wp_silver_night.jpg (1920x1080) and thumb_7.jpg, split diagonally day | night.
python3 artwork/silver_assets.py day.png night.png"""
import pathlib, sys
from PIL import Image, ImageDraw, ImageFilter
res = pathlib.Path(__file__).resolve().parent.parent / 'res' / 'drawable-nodpi'
day, night = (Image.open(p).convert('RGB').resize((1920, 1080), Image.LANCZOS) for p in sys.argv[1:3])
day.save(res / 'wp_silver_day.jpg', quality=90, optimize=True, progressive=True)
night.save(res / 'wp_silver_night.jpg', quality=90, optimize=True, progressive=True)
w, h = 384, 216
mask = Image.new('L', (w * 4, h * 4), 0)
ImageDraw.Draw(mask).polygon([(0, 0), (w * 4 * .62, 0), (w * 4 * .38, h * 4), (0, h * 4)], fill=255)
mask = mask.resize((w, h), Image.LANCZOS).filter(ImageFilter.GaussianBlur(.6))
thumb = Image.composite(day.resize((w, h), Image.LANCZOS), night.resize((w, h), Image.LANCZOS), mask)
thumb.save(res / 'thumb_7.jpg', quality=88, optimize=True)
print('wrote', *(res / n for n in ('wp_silver_day.jpg', 'wp_silver_night.jpg', 'thumb_7.jpg')))
