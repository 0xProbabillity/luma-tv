# reproject.py pano.jpg yaw pitch hfov out.jpg [w h] — a rectilinear camera view of an equirectangular panorama
import sys, numpy as np, cv2
cv2.setNumThreads(8)
src, yaw, pitch, hfov, out = sys.argv[1], *map(float, sys.argv[2:5]), sys.argv[5]
W, H = (int(sys.argv[6]), int(sys.argv[7])) if len(sys.argv) > 7 else (1920, 1080)
pano = cv2.imread(src, cv2.IMREAD_COLOR); ph, pw = pano.shape[:2]
f = (W / 2) / np.tan(np.radians(hfov) / 2)
x, y = np.meshgrid(np.arange(W) - W / 2 + .5, np.arange(H) - H / 2 + .5)
d = np.stack([x, -y, np.full_like(x, f)], -1); d /= np.linalg.norm(d, axis=-1, keepdims=True)
p, q = np.radians(pitch), np.radians(yaw)
Rx = np.array([[1, 0, 0], [0, np.cos(p), np.sin(p)], [0, -np.sin(p), np.cos(p)]])
Ry = np.array([[np.cos(q), 0, np.sin(q)], [0, 1, 0], [-np.sin(q), 0, np.cos(q)]])
d = d @ (Ry @ Rx).T
lon = np.arctan2(d[..., 0], d[..., 2]); lat = np.arcsin(np.clip(d[..., 1], -1, 1))
mx = ((lon / (2 * np.pi) + .5) * pw).astype(np.float32); my = ((.5 - lat / np.pi) * ph).astype(np.float32)
img = cv2.remap(pano, mx, my, cv2.INTER_LANCZOS4, borderMode=cv2.BORDER_WRAP)
cv2.imwrite(out, img, [cv2.IMWRITE_JPEG_QUALITY, 92])
