"""Re-crop body/head views using width+height filters so borders are ignored."""
from __future__ import annotations

from pathlib import Path

from PIL import Image

CROPS = Path(__file__).resolve().parent / "crops"
BODY = ["front", "left", "back", "right"]
HEAD = ["head_front", "head_left", "head_back", "head_top"]


def is_bg(p: tuple[int, ...]) -> bool:
	r, g, b = p[:3]
	return r < 50 and g < 60 and b < 80


def find_blobs(im: Image.Image, min_area: int = 400) -> list[dict]:
	w, h = im.size
	px = im.load()
	vis = [[False] * w for _ in range(h)]
	blobs: list[dict] = []
	for y in range(h):
		for x in range(w):
			if vis[y][x] or is_bg(px[x, y]):
				continue
			stack = [(x, y)]
			vis[y][x] = True
			minx = maxx = x
			miny = maxy = y
			count = 0
			while stack:
				cx, cy = stack.pop()
				count += 1
				minx = min(minx, cx)
				maxx = max(maxx, cx)
				miny = min(miny, cy)
				maxy = max(maxy, cy)
				for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
					if 0 <= nx < w and 0 <= ny < h and not vis[ny][nx] and not is_bg(px[nx, ny]):
						vis[ny][nx] = True
						stack.append((nx, ny))
			if count >= min_area:
				blobs.append(
					{"x": minx, "y": miny, "w": maxx - minx + 1, "h": maxy - miny + 1, "area": count}
				)
	return blobs


def crop_pad(im: Image.Image, b: dict, pad: int = 8) -> Image.Image:
	x0 = max(0, b["x"] - pad)
	y0 = max(0, b["y"] - pad)
	x1 = min(im.width, b["x"] + b["w"] + pad)
	y1 = min(im.height, b["y"] + b["h"] + pad)
	return im.crop((x0, y0, x1, y1))


def main() -> None:
	for name in ("guide", "physician", "cartographer", "enchanter"):
		im = Image.open(CROPS / f"{name}_panel.png").convert("RGB")
		blobs = find_blobs(im, 350)
		# Body: reasonably wide, tall, not the whole panel.
		bodies = [
			b
			for b in blobs
			if 55 <= b["w"] <= 200 and 170 <= b["h"] <= 380 and b["x"] < 520
		]
		bodies.sort(key=lambda b: b["x"])
		print(f"{name} bodies={len(bodies)}")
		for i, b in enumerate(bodies[:4]):
			print(f"  body {BODY[i]}: {b}")
			crop_pad(im, b, 10).save(CROPS / f"{name}_{BODY[i]}.png")
		heads = [
			b
			for b in blobs
			if 50 <= b["w"] <= 110 and 50 <= b["h"] <= 130 and b["x"] >= 500
		]
		heads.sort(key=lambda b: (b["y"], b["x"]))
		print(f"{name} heads={len(heads)}")
		for i, b in enumerate(heads[:4]):
			hn = HEAD[i] if i < 4 else f"head_{i}"
			print(f"  {hn}: {b}")
			crop_pad(im, b, 6).save(CROPS / f"{name}_{hn}.png")


if __name__ == "__main__":
	main()
