"""Build GeckoLib Bedrock geo.json + 128x128 textures for the four special NPCs."""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ART = Path(__file__).resolve().parent
OUT_GEO = ART / "geo"
OUT_TEX = ART
ASSETS_GEO = ROOT / "src/main/resources/assets/refugee/geckolib/models/entity/special"
ASSETS_ANIM = ROOT / "src/main/resources/assets/refugee/geckolib/animations/entity/special"
ASSETS_TEX = ROOT / "src/main/resources/assets/refugee/textures/entity/special"
TW = TH = 128


def rgba(h: str) -> tuple[int, int, int, int]:
	h = h.lstrip("#")
	return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255


class Packer:
	def __init__(self, w: int = TW, h: int = TH) -> None:
		self.w = w
		self.h = h
		self.x = 0
		self.y = 0
		self.row_h = 0
		self.slots: dict[str, tuple[int, int, int, int, int, int]] = {}

	def alloc(self, name: str, sx: float, sy: float, sz: float) -> tuple[int, int]:
		bw = int(round(2 * (sx + sz)))
		bh = int(round(sy + sz))
		bw = max(bw, 2)
		bh = max(bh, 2)
		if self.x + bw > self.w:
			self.x = 0
			self.y += self.row_h
			self.row_h = 0
		if self.y + bh > self.h:
			raise RuntimeError(f"UV overflow packing {name} ({bw}x{bh}) at {self.x},{self.y}")
		u, v = self.x, self.y
		self.slots[name] = (u, v, int(round(sx)), int(round(sy)), int(round(sz)), bw)
		self.x += bw + 1
		self.row_h = max(self.row_h, bh + 1)
		return u, v


def cube(origin, size, uv, inflate=0, rot=None, pivot=None):
	c = {"origin": [float(x) for x in origin], "size": [float(x) for x in size], "uv": [int(uv[0]), int(uv[1])]}
	if inflate:
		c["inflate"] = float(inflate)
	if rot:
		c["rotation"] = [float(x) for x in rot]
		if pivot:
			c["pivot"] = [float(x) for x in pivot]
	return c


def bone(name, pivot, cubes=None, parent=None, rot=None):
	b = {"name": name, "pivot": [float(x) for x in pivot]}
	if parent:
		b["parent"] = parent
	if rot:
		b["rotation"] = [float(x) for x in rot]
	if cubes:
		b["cubes"] = cubes
	return b


def paint_box(img: Image.Image, pack: Packer, name: str, faces: dict[str, str | list[str]]) -> None:
	u, v, sx, sy, sz, _ = pack.slots[name]
	px = img.load()

	def fill(x0, y0, w, h, color):
		if isinstance(color, list):
			for yy, row in enumerate(color):
				for xx, ch in enumerate(row.replace(" ", "")):
					if 0 <= x0 + xx < img.width and 0 <= y0 + yy < img.height:
						if ch != ".":
							px[x0 + xx, y0 + yy] = rgba(ch) if len(ch) > 1 else faces.get(ch, (0, 0, 0, 0))
			return
		r, g, b, a = rgba(color) if isinstance(color, str) else color
		for yy in range(h):
			for xx in range(w):
				if 0 <= x0 + xx < img.width and 0 <= y0 + yy < img.height:
					px[x0 + xx, y0 + yy] = (r, g, b, a)

	# Minecraft/Bedrock box UV
	# top:    (u+sz, v) size sx x sz
	# down:   (u+sz+sx, v) size sx x sz
	# west:   (u, v+sz) size sz x sy
	# north:  (u+sz, v+sz) size sx x sy   (front, -Z)
	# east:   (u+sz+sx, v+sz) size sz x sy
	# south:  (u+sz+sx+sz, v+sz) size sx x sy
	fill(u + sz, v, sx, sz, faces.get("up", faces.get("n", "#000000")))
	fill(u + sz + sx, v, sx, sz, faces.get("down", faces.get("n", "#000000")))
	fill(u, v + sz, sz, sy, faces.get("west", faces.get("e", faces.get("n", "#000000"))))
	north = faces.get("north", faces.get("n"))
	if isinstance(north, list):
		draw_pixels(px, u + sz, v + sz, north)
	else:
		fill(u + sz, v + sz, sx, sy, north)
	fill(u + sz + sx, v + sz, sz, sy, faces.get("east", faces.get("w", faces.get("n", "#000000"))))
	south = faces.get("south", faces.get("s", faces.get("n")))
	if isinstance(south, list):
		draw_pixels(px, u + sz + sx + sz, v + sz, south)
	else:
		fill(u + sz + sx + sz, v + sz, sx, sy, south)


def draw_pixels(px, x0, y0, rows: list[str]) -> None:
	for yy, row in enumerate(rows):
		for xx, hexcol in enumerate(row.split()):
			if hexcol in (".", "--"):
				continue
			px[x0 + xx, y0 + yy] = rgba(hexcol)


def geo(identifier: str, bones: list[dict]) -> dict:
	return {
		"format_version": "1.12.0",
		"minecraft:geometry": [
			{
				"description": {
					"identifier": identifier,
					"texture_width": TW,
					"texture_height": TH,
					"visible_bounds_width": 3,
					"visible_bounds_height": 4,
					"visible_bounds_offset": [0, 1.5, 0],
				},
				"bones": bones,
			}
		],
	}


def humanoid_anim() -> dict:
	return {
		"format_version": "1.8.0",
		"animations": {
			"idle": {
				"loop": True,
				"animation_length": 3.0,
				"bones": {
					"body": {
						"position": {
							"0.0": [0, 0, 0],
							"1.5": [0, 0.4, 0],
							"3.0": [0, 0, 0],
						}
					},
					"right_arm": {"rotation": {"0.0": [0, 0, 6], "1.5": [0, 0, 10], "3.0": [0, 0, 6]}},
					"left_arm": {"rotation": {"0.0": [0, 0, -6], "1.5": [0, 0, -10], "3.0": [0, 0, -6]}},
					"head": {"rotation": {"0.0": [0, 0, 0], "1.5": [2, 0, 0], "3.0": [0, 0, 0]}},
				},
			},
			"walk": {
				"loop": True,
				"animation_length": 0.8,
				"bones": {
					"right_arm": {"rotation": {"0.0": [40, 0, 0], "0.4": [-40, 0, 0], "0.8": [40, 0, 0]}},
					"left_arm": {"rotation": {"0.0": [-40, 0, 0], "0.4": [40, 0, 0], "0.8": [-40, 0, 0]}},
					"right_leg": {"rotation": {"0.0": [-40, 0, 0], "0.4": [40, 0, 0], "0.8": [-40, 0, 0]}},
					"left_leg": {"rotation": {"0.0": [40, 0, 0], "0.4": [-40, 0, 0], "0.8": [40, 0, 0]}},
					"body": {"rotation": {"0.0": [0, 0, -1.5], "0.4": [0, 0, 1.5], "0.8": [0, 0, -1.5]}},
				},
			},
		},
	}


# ----- shared Steve-like core, extras differ -----
def core_uv(p: Packer) -> dict[str, tuple[int, int]]:
	return {
		"head": p.alloc("head", 8, 8, 8),
		"body": p.alloc("body", 8, 12, 4),
		"right_arm": p.alloc("right_arm", 4, 12, 4),
		"left_arm": p.alloc("left_arm", 4, 12, 4),
		"right_leg": p.alloc("right_leg", 4, 12, 4),
		"left_leg": p.alloc("left_leg", 4, 12, 4),
	}


def build_guide():
	p = Packer()
	uv = core_uv(p)
	uv["hair_top"] = p.alloc("hair_top", 10, 2, 10)
	uv["hair_front"] = p.alloc("hair_front", 8, 4, 1)
	uv["hair_l"] = p.alloc("hair_l", 2, 9, 9)
	uv["hair_r"] = p.alloc("hair_r", 2, 9, 9)
	uv["hair_back"] = p.alloc("hair_back", 10, 10, 2)
	uv["beard"] = p.alloc("beard", 6, 3, 1)
	uv["collar"] = p.alloc("collar", 8, 3, 5)
	uv["coat_l"] = p.alloc("coat_l", 3, 20, 7)
	uv["coat_r"] = p.alloc("coat_r", 3, 20, 7)
	uv["coat_back"] = p.alloc("coat_back", 10, 20, 3)
	uv["belt"] = p.alloc("belt", 8, 1, 5)
	uv["strap"] = p.alloc("strap", 1, 16, 10)
	uv["satchel"] = p.alloc("satchel", 3, 5, 4)
	uv["boot_l"] = p.alloc("boot_l", 5, 4, 5)
	uv["boot_r"] = p.alloc("boot_r", 5, 4, 5)
	uv["cuff_l"] = p.alloc("cuff_l", 5, 3, 5)
	uv["cuff_r"] = p.alloc("cuff_r", 5, 3, 5)

	h, hk, hs = "#3a2a20", "#2a1c14", "#4c3828"
	sk, sks = "#c4a07a", "#a88864"
	eye = "#7ec8e8"
	coat, coats = "#4a4540", "#35302c"
	shirt, shirts = "#6a7a8a", "#586878"
	pant, pants = "#3d4a5c", "#2e3948"
	boot = "#5a3c28"
	strap = "#6b4a32"
	cuff = "#d8d4c8"
	belt = "#2a2420"
	gold = "#c4a050"

	img = Image.new("RGBA", (TW, TH), (0, 0, 0, 0))
	paint_box(img, p, "head", {
		"n": [
			f"{h} {h} {h} {h} {h} {h} {h} {h}",
			f"{h} {h} {h} {h} {h} {h} {hk} {h}",
			f"{h} {sk} {sk} {sk} {sk} {sk} {h} {hk}",
			f"{h} {sk} {eye} {sk} {sk} {eye} {sk} {h}",
			f"{sk} {sk} {sk} {sk} {sk} {sk} {sk} {h}",
			f"{sks} {sks} {sk} {sk} {sk} {sks} {sks} {h}",
			f"{sks} {sks} {sks} {sks} {sks} {sks} {sks} {hk}",
			f"{sks} {sks} {sks} {sks} {sks} {sks} {sks} {h}",
		],
		"s": h, "e": h, "w": h, "up": h, "down": sks,
	})
	paint_box(img, p, "body", {"n": shirt, "s": shirts, "e": shirt, "w": shirt, "up": shirt, "down": shirt})
	paint_box(img, p, "right_arm", {"n": coat, "s": coats, "e": coat, "w": coat, "up": coat, "down": cuff})
	paint_box(img, p, "left_arm", {"n": coat, "s": coats, "e": coat, "w": coat, "up": coat, "down": cuff})
	paint_box(img, p, "right_leg", {"n": pant, "s": pants, "e": pant, "w": pant, "up": pant, "down": boot})
	paint_box(img, p, "left_leg", {"n": pant, "s": pants, "e": pant, "w": pant, "up": pant, "down": boot})
	for key, col in {
		"hair_top": h, "hair_front": h, "hair_l": h, "hair_r": h, "hair_back": hk,
		"beard": sks, "collar": shirts, "coat_l": coat, "coat_r": coat, "coat_back": coats,
		"belt": belt, "strap": strap, "satchel": "#5a3a24", "boot_l": boot, "boot_r": boot,
		"cuff_l": cuff, "cuff_r": cuff,
	}.items():
		paint_box(img, p, key, {"n": col, "s": col, "e": col, "w": col, "up": col, "down": col})
	# gold buckle pixels on belt north
	u, v, sx, sy, sz, _ = p.slots["belt"]
	px = img.load()
	px[u + sz + 3, v + sz] = rgba(gold)
	px[u + sz + 4, v + sz] = rgba(gold)

	bones = [
		bone("root", [0, 0, 0]),
		bone("body", [0, 24, 0], [
			cube([-4, 12, -2], [8, 12, 4], uv["body"]),
			cube([-4, 22, -3], [8, 3, 5], uv["collar"]),
			cube([-4, 12, -2.5], [8, 1, 5], uv["belt"]),
			cube([-6, 4, -3], [3, 20, 7], uv["coat_r"]),
			cube([3, 4, -3], [3, 20, 7], uv["coat_l"]),
			cube([-5, 4, 2], [10, 20, 3], uv["coat_back"]),
			cube([-1, 10, 1], [1, 16, 10], uv["strap"], rot=[0, 35, 12], pivot=[0, 22, 0]),
			cube([-7, 10, 1], [3, 5, 4], uv["satchel"]),
		], parent="root"),
		bone("head", [0, 24, 0], [
			cube([-4, 24, -4], [8, 8, 8], uv["head"]),
			cube([-5, 31, -5], [10, 2, 10], uv["hair_top"]),
			cube([-4, 28, -5], [8, 4, 1], uv["hair_front"]),
			cube([4, 24, -5], [2, 9, 9], uv["hair_l"]),
			cube([-6, 24, -5], [2, 9, 9], uv["hair_r"]),
			cube([-5, 24, 4], [10, 10, 2], uv["hair_back"]),
			cube([-3, 23, -5], [6, 3, 1], uv["beard"]),
		], parent="body"),
		bone("right_arm", [-5, 22, 0], [
			cube([-8, 12, -2], [4, 12, 4], uv["right_arm"]),
			cube([-8.5, 12, -2.5], [5, 3, 5], uv["cuff_r"]),
		], parent="body"),
		bone("left_arm", [5, 22, 0], [
			cube([4, 12, -2], [4, 12, 4], uv["left_arm"]),
			cube([3.5, 12, -2.5], [5, 3, 5], uv["cuff_l"]),
		], parent="body"),
		bone("right_leg", [-2, 12, 0], [
			cube([-4, 0, -2], [4, 12, 4], uv["right_leg"]),
			cube([-4.5, -0.5, -2.5], [5, 4, 5], uv["boot_r"]),
		], parent="root"),
		bone("left_leg", [2, 12, 0], [
			cube([0, 0, -2], [4, 12, 4], uv["left_leg"]),
			cube([-0.5, -0.5, -2.5], [5, 4, 5], uv["boot_l"]),
		], parent="root"),
	]
	return geo("geometry.refugee.guide", bones), img


def build_physician():
	p = Packer()
	uv = core_uv(p)
	uv["bang"] = p.alloc("bang", 8, 5, 1)
	uv["hair_l"] = p.alloc("hair_l", 2, 10, 9)
	uv["hair_r"] = p.alloc("hair_r", 2, 10, 9)
	uv["hair_back"] = p.alloc("hair_back", 10, 22, 3)
	uv["hair_tuft_l"] = p.alloc("hair_tuft_l", 3, 3, 3)
	uv["hair_tuft_r"] = p.alloc("hair_tuft_r", 3, 3, 3)
	uv["coat_l"] = p.alloc("coat_l", 3, 21, 8)
	uv["coat_r"] = p.alloc("coat_r", 3, 21, 8)
	uv["coat_back"] = p.alloc("coat_back", 10, 21, 3)
	uv["collar"] = p.alloc("collar", 8, 2, 5)
	uv["steth_n"] = p.alloc("steth_n", 6, 5, 1)
	uv["shoe_l"] = p.alloc("shoe_l", 4, 2, 5)
	uv["shoe_r"] = p.alloc("shoe_r", 4, 2, 5)

	h, hk = "#16161c", "#0c0c12"
	sk = "#f0d8c8"
	eye = "#2e2e3a"
	coat, coats = "#f4f1ea", "#e2ddd4"
	inner = "#3a4550"
	pant = "#5a6068"
	shoe = "#6b4a32"
	steth = "#c8d0d8"

	img = Image.new("RGBA", (TW, TH), (0, 0, 0, 0))
	paint_box(img, p, "head", {
		"n": [
			f"{h} {h} {h} {h} {h} {h} {h} {h}",
			f"{h} {h} {h} {h} {h} {h} {h} {h}",
			f"{h} {h} {sk} {sk} {sk} {h} {h} {h}",
			f"{h} {sk} {eye} {sk} {sk} {eye} {h} {h}",
			f"{h} {sk} {sk} {sk} {sk} {sk} {sk} {h}",
			f"{h} {sk} {sk} {sk} {sk} {sk} {sk} {h}",
			f"{h} {sk} {sk} {sk} {sk} {sk} {sk} {h}",
			f"{h} {h} {sk} {sk} {sk} {sk} {h} {h}",
		],
		"s": h, "e": h, "w": h, "up": h, "down": sk,
	})
	paint_box(img, p, "body", {"n": inner, "s": inner, "e": inner, "w": inner, "up": inner, "down": inner})
	paint_box(img, p, "right_arm", {"n": coat, "s": coats, "e": coat, "w": coat, "up": coat, "down": coat})
	paint_box(img, p, "left_arm", {"n": coat, "s": coats, "e": coat, "w": coat, "up": coat, "down": coat})
	paint_box(img, p, "right_leg", {"n": pant, "s": pant, "e": pant, "w": pant, "up": pant, "down": shoe})
	paint_box(img, p, "left_leg", {"n": pant, "s": pant, "e": pant, "w": pant, "up": pant, "down": shoe})
	for key, col in {
		"bang": h, "hair_l": h, "hair_r": h, "hair_back": hk, "hair_tuft_l": h, "hair_tuft_r": h,
		"coat_l": coat, "coat_r": coat, "coat_back": coats, "collar": "#eef0f2",
		"steth_n": steth, "shoe_l": shoe, "shoe_r": shoe,
	}.items():
		paint_box(img, p, key, {"n": col, "s": col, "e": col, "w": col, "up": col, "down": col})
	u, v, sx, sy, sz, _ = p.slots["steth_n"]
	px = img.load()
	px[u + sz, v + sz + 4] = rgba("#4a90c8")
	px[u + sz + 5, v + sz + 4] = rgba("#4a90c8")

	bones = [
		bone("root", [0, 0, 0]),
		bone("body", [0, 24, 0], [
			cube([-4, 12, -2], [8, 12, 4], uv["body"]),
			cube([-4, 23, -3], [8, 2, 5], uv["collar"]),
			cube([-6, 4, -3], [3, 21, 8], uv["coat_r"]),
			cube([3, 4, -3], [3, 21, 8], uv["coat_l"]),
			cube([-5, 4, 2], [10, 21, 3], uv["coat_back"]),
			cube([-3, 16, -3.2], [6, 5, 1], uv["steth_n"]),
		], parent="root"),
		bone("head", [0, 24, 0], [
			cube([-4, 24, -4], [8, 8, 8], uv["head"]),
			cube([-4, 28, -5], [8, 5, 1], uv["bang"]),
			cube([4, 24, -5], [2, 10, 9], uv["hair_l"]),
			cube([-6, 24, -5], [2, 10, 9], uv["hair_r"]),
			cube([-5, 10, 3], [10, 22, 3], uv["hair_back"]),
			cube([-5, 32, -2], [3, 3, 3], uv["hair_tuft_r"]),
			cube([2, 32, -2], [3, 3, 3], uv["hair_tuft_l"]),
		], parent="body"),
		bone("right_arm", [-5, 22, 0], [cube([-8, 12, -2], [4, 12, 4], uv["right_arm"])], parent="body"),
		bone("left_arm", [5, 22, 0], [cube([4, 12, -2], [4, 12, 4], uv["left_arm"])], parent="body"),
		bone("right_leg", [-2, 12, 0], [
			cube([-4, 0, -2], [4, 12, 4], uv["right_leg"]),
			cube([-4, 0, -3], [4, 2, 5], uv["shoe_r"]),
		], parent="root"),
		bone("left_leg", [2, 12, 0], [
			cube([0, 0, -2], [4, 12, 4], uv["left_leg"]),
			cube([0, 0, -3], [4, 2, 5], uv["shoe_l"]),
		], parent="root"),
	]
	return geo("geometry.refugee.physician", bones), img


def build_cartographer():
	p = Packer()
	uv = core_uv(p)
	uv["hair_top"] = p.alloc("hair_top", 9, 2, 9)
	uv["bang"] = p.alloc("bang", 8, 3, 1)
	uv["hair_l"] = p.alloc("hair_l", 2, 8, 8)
	uv["hair_r"] = p.alloc("hair_r", 2, 8, 8)
	uv["hair_back"] = p.alloc("hair_back", 9, 8, 2)
	uv["goggle"] = p.alloc("goggle", 3, 3, 2)
	uv["scarf"] = p.alloc("scarf", 10, 4, 7)
	uv["pouch_l"] = p.alloc("pouch_l", 3, 4, 3)
	uv["pouch_r"] = p.alloc("pouch_r", 3, 4, 3)
	uv["belt"] = p.alloc("belt", 8, 1, 5)
	uv["strap"] = p.alloc("strap", 1, 12, 8)
	uv["boot_l"] = p.alloc("boot_l", 5, 5, 5)
	uv["boot_r"] = p.alloc("boot_r", 5, 5, 5)

	h, hk = "#6b4423", "#4a2e14"
	sk, sks = "#e0b890", "#c49870"
	eye = "#5a3a20"
	gog = "#4a90d8"
	scarf = "#a03038"
	shirt, shirts = "#e8dcc8", "#d4c4a8"
	pant = "#4a5c3a"
	boot = "#6b4a28"
	leath = "#8a5a32"
	gold = "#c4a050"

	img = Image.new("RGBA", (TW, TH), (0, 0, 0, 0))
	paint_box(img, p, "head", {
		"n": [
			f"{h} {h} {h} {h} {h} {h} {h} {h}",
			f"{h} {h} {h} {h} {h} {h} {h} {hk}",
			f"{h} {sk} {sk} {sk} {sk} {sk} {h} {h}",
			f"{h} {sk} {eye} {sk} {sk} {eye} {sk} {h}",
			f"{sk} {sk} {sk} {sk} {sk} {sk} {sk} {h}",
			f"{sk} {sk} {sk} {sk} {sk} {sk} {sk} {h}",
			f"{sks} {sk} {sk} {sk} {sk} {sk} {sks} {h}",
			f"{sks} {sks} {sks} {sks} {sks} {sks} {sks} {h}",
		],
		"s": h, "e": h, "w": h, "up": h, "down": sks,
	})
	paint_box(img, p, "body", {"n": shirt, "s": shirts, "e": shirt, "w": shirt, "up": shirt, "down": shirt})
	paint_box(img, p, "right_arm", {"n": shirt, "s": shirts, "e": shirt, "w": shirt, "up": shirt, "down": sks})
	paint_box(img, p, "left_arm", {"n": shirt, "s": shirts, "e": shirt, "w": shirt, "up": shirt, "down": sks})
	paint_box(img, p, "right_leg", {"n": pant, "s": pant, "e": pant, "w": pant, "up": pant, "down": boot})
	paint_box(img, p, "left_leg", {"n": pant, "s": pant, "e": pant, "w": pant, "up": pant, "down": boot})
	for key, col in {
		"hair_top": h, "bang": h, "hair_l": h, "hair_r": hk, "hair_back": hk,
		"goggle": gog, "scarf": scarf, "pouch_l": leath, "pouch_r": leath, "belt": "#3a2a18",
		"strap": leath, "boot_l": boot, "boot_r": boot,
	}.items():
		paint_box(img, p, key, {"n": col, "s": col, "e": col, "w": col, "up": col, "down": col})
	u, v, sx, sy, sz, _ = p.slots["belt"]
	px = img.load()
	px[u + sz + 3, v + sz] = rgba(gold)
	px[u + sz + 4, v + sz] = rgba(gold)

	bones = [
		bone("root", [0, 0, 0]),
		bone("body", [0, 24, 0], [
			cube([-4, 12, -2], [8, 12, 4], uv["body"]),
			cube([-5, 20, -4], [10, 4, 7], uv["scarf"]),
			cube([-4, 12, -2.5], [8, 1, 5], uv["belt"]),
			cube([-7, 11, -1], [3, 4, 3], uv["pouch_r"]),
			cube([4, 11, -1], [3, 4, 3], uv["pouch_l"]),
			cube([-0.5, 12, 0], [1, 12, 8], uv["strap"], rot=[0, -28, -10], pivot=[0, 22, 0]),
		], parent="root"),
		bone("head", [0, 24, 0], [
			cube([-4, 24, -4], [8, 8, 8], uv["head"]),
			cube([-4.5, 31, -4.5], [9, 2, 9], uv["hair_top"]),
			cube([-4, 29, -5], [8, 3, 1], uv["bang"]),
			cube([4, 24, -4], [2, 8, 8], uv["hair_l"]),
			cube([-6, 24, -4], [2, 8, 8], uv["hair_r"]),
			cube([-4.5, 24, 4], [9, 8, 2], uv["hair_back"]),
			cube([3.5, 26, -6], [3, 3, 2], uv["goggle"]),
		], parent="body"),
		bone("right_arm", [-5, 22, 0], [cube([-8, 12, -2], [4, 12, 4], uv["right_arm"])], parent="body"),
		bone("left_arm", [5, 22, 0], [cube([4, 12, -2], [4, 12, 4], uv["left_arm"])], parent="body"),
		bone("right_leg", [-2, 12, 0], [
			cube([-4, 0, -2], [4, 12, 4], uv["right_leg"]),
			cube([-4.5, -0.5, -2.5], [5, 5, 5], uv["boot_r"]),
		], parent="root"),
		bone("left_leg", [2, 12, 0], [
			cube([0, 0, -2], [4, 12, 4], uv["left_leg"]),
			cube([-0.5, -0.5, -2.5], [5, 5, 5], uv["boot_l"]),
		], parent="root"),
	]
	return geo("geometry.refugee.cartographer", bones), img


def build_enchanter():
	p = Packer()
	uv = core_uv(p)
	uv["brim"] = p.alloc("brim", 16, 1, 16)
	uv["hat1"] = p.alloc("hat1", 10, 4, 10)
	uv["hat2"] = p.alloc("hat2", 7, 4, 8)
	uv["hat3"] = p.alloc("hat3", 5, 4, 6)
	uv["hat4"] = p.alloc("hat4", 3, 3, 4)
	uv["band"] = p.alloc("band", 11, 2, 11)
	uv["hair_front"] = p.alloc("hair_front", 8, 4, 1)
	uv["hair_l"] = p.alloc("hair_l", 2, 18, 8)
	uv["hair_r"] = p.alloc("hair_r", 2, 18, 8)
	uv["hair_back"] = p.alloc("hair_back", 10, 20, 3)
	uv["robe_l"] = p.alloc("robe_l", 3, 24, 8)
	uv["robe_r"] = p.alloc("robe_r", 3, 24, 8)
	uv["robe_back"] = p.alloc("robe_back", 10, 24, 4)
	uv["star"] = p.alloc("star", 6, 6, 1)
	uv["sleeve_l"] = p.alloc("sleeve_l", 5, 12, 5)
	uv["sleeve_r"] = p.alloc("sleeve_r", 5, 12, 5)

	h, hk = "#c8c0d8", "#b0a8c8"
	sk = "#f0d8d0"
	eye = "#a070e0"
	hat, hats = "#2a1a40", "#1c102e"
	gold = "#d4b44a"
	robe, robes = "#2a1848", "#3d2460"
	trim = "#c4a040"

	img = Image.new("RGBA", (TW, TH), (0, 0, 0, 0))
	paint_box(img, p, "head", {
		"n": [
			f"{h} {h} {h} {h} {h} {h} {h} {h}",
			f"{h} {h} {h} {h} {h} {h} {h} {h}",
			f"{h} {sk} {sk} {sk} {sk} {sk} {h} {h}",
			f"{h} {sk} {eye} {sk} {sk} {eye} {sk} {h}",
			f"{sk} {sk} {sk} {sk} {sk} {sk} {sk} {h}",
			f"{sk} {sk} {sk} {sk} {sk} {sk} {sk} {h}",
			f"{sk} {sk} {sk} {sk} {sk} {sk} {sk} {h}",
			f"{h} {sk} {sk} {sk} {sk} {sk} {h} {h}",
		],
		"s": h, "e": h, "w": h, "up": h, "down": sk,
	})
	paint_box(img, p, "body", {"n": robe, "s": robes, "e": robe, "w": robe, "up": robe, "down": robe})
	paint_box(img, p, "right_arm", {"n": robe, "s": robes, "e": robe, "w": robe, "up": robe, "down": "#e8dcc8"})
	paint_box(img, p, "left_arm", {"n": robe, "s": robes, "e": robe, "w": robe, "up": robe, "down": "#e8dcc8"})
	paint_box(img, p, "right_leg", {"n": robe, "s": robe, "e": robe, "w": robe, "up": robe, "down": hats})
	paint_box(img, p, "left_leg", {"n": robe, "s": robe, "e": robe, "w": robe, "up": robe, "down": hats})
	for key, col in {
		"brim": hat, "hat1": hat, "hat2": hats, "hat3": hat, "hat4": hats, "band": gold,
		"hair_front": h, "hair_l": h, "hair_r": hk, "hair_back": h,
		"robe_l": robe, "robe_r": robe, "robe_back": robes, "star": gold,
		"sleeve_l": robe, "sleeve_r": robe,
	}.items():
		paint_box(img, p, key, {"n": col, "s": col, "e": col, "w": col, "up": col, "down": col})
	# hexagram-ish on star north
	u, v, sx, sy, sz, _ = p.slots["star"]
	px = img.load()
	for xx in range(6):
		px[u + sz + xx, v + sz + 2] = rgba(gold)
		px[u + sz + xx, v + sz + 3] = rgba(trim)
	px[u + sz + 2, v + sz] = rgba(gold)
	px[u + sz + 3, v + sz] = rgba(gold)
	px[u + sz + 2, v + sz + 5] = rgba(gold)
	px[u + sz + 3, v + sz + 5] = rgba(gold)

	bones = [
		bone("root", [0, 0, 0]),
		bone("body", [0, 24, 0], [
			cube([-4, 12, -2], [8, 12, 4], uv["body"]),
			cube([-6, 0, -3], [3, 24, 8], uv["robe_r"]),
			cube([3, 0, -3], [3, 24, 8], uv["robe_l"]),
			cube([-5, 0, 2], [10, 24, 4], uv["robe_back"]),
			cube([-3, 16, -3.2], [6, 6, 1], uv["star"]),
		], parent="root"),
		bone("head", [0, 24, 0], [
			cube([-4, 24, -4], [8, 8, 8], uv["head"]),
			cube([-4, 28, -5], [8, 4, 1], uv["hair_front"]),
			cube([4, 12, -4], [2, 18, 8], uv["hair_l"]),
			cube([-6, 12, -4], [2, 18, 8], uv["hair_r"]),
			cube([-5, 12, 3], [10, 20, 3], uv["hair_back"]),
			cube([-8, 31, -8], [16, 1, 16], uv["brim"]),
			cube([-5.5, 31.5, -5.5], [11, 2, 11], uv["band"]),
			cube([-5, 32, -5], [10, 4, 10], uv["hat1"]),
			cube([-3, 36, -4], [7, 4, 8], uv["hat2"], rot=[-8, 0, 8], pivot=[0, 36, 0]),
			cube([-2, 40, -2], [5, 4, 6], uv["hat3"], rot=[-12, 0, 12], pivot=[0, 40, 0]),
			cube([-1, 43, 0], [3, 3, 4], uv["hat4"], rot=[-18, 0, 16], pivot=[0, 43, 0]),
		], parent="body"),
		bone("right_arm", [-5, 22, 0], [
			cube([-8, 12, -2], [4, 12, 4], uv["right_arm"]),
			cube([-8.5, 12, -2.5], [5, 12, 5], uv["sleeve_r"]),
		], parent="body"),
		bone("left_arm", [5, 22, 0], [
			cube([4, 12, -2], [4, 12, 4], uv["left_arm"]),
			cube([3.5, 12, -2.5], [5, 12, 5], uv["sleeve_l"]),
		], parent="body"),
		bone("right_leg", [-2, 12, 0], [cube([-4, 0, -2], [4, 12, 4], uv["right_leg"])], parent="root"),
		bone("left_leg", [2, 12, 0], [cube([0, 0, -2], [4, 12, 4], uv["left_leg"])], parent="root"),
	]
	return geo("geometry.refugee.enchanter", bones), img


def write_all(install_assets: bool = True) -> None:
	OUT_GEO.mkdir(parents=True, exist_ok=True)
	builders = {
		"guide": build_guide,
		"physician": build_physician,
		"cartographer": build_cartographer,
		"enchanter": build_enchanter,
	}
	if install_assets:
		ASSETS_GEO.mkdir(parents=True, exist_ok=True)
		ASSETS_ANIM.mkdir(parents=True, exist_ok=True)
		ASSETS_TEX.mkdir(parents=True, exist_ok=True)
		anim = humanoid_anim()
		(ASSETS_ANIM / "humanoid.animation.json").write_text(json.dumps(anim, indent="\t"), encoding="utf-8")
		(ART / "humanoid.animation.json").write_text(json.dumps(anim, indent="\t"), encoding="utf-8")
	for name, fn in builders.items():
		g, img = fn()
		(OUT_GEO / f"{name}.geo.json").write_text(json.dumps(g, indent="\t"), encoding="utf-8")
		img.save(OUT_TEX / f"{name}.png")
		if install_assets:
			(ASSETS_GEO / f"{name}.geo.json").write_text(json.dumps(g, indent="\t"), encoding="utf-8")
			img.save(ASSETS_TEX / f"{name}.png")
		print("wrote", name)


if __name__ == "__main__":
	write_all(True)
