"""Compose kit item icons from vanilla sprites and a 32x32 command staff."""
from __future__ import annotations

import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ART = Path(__file__).resolve().parent
VANILLA = ART / "vanilla"
GAME_ITEM = ROOT / "src" / "main" / "resources" / "assets" / "refugee" / "textures" / "item"
PREVIEW = ART / "preview"

MC_JAR = Path.home() / ".gradle" / "caches" / "fabric-loom" / "1.21.5" / "minecraft-client.jar"
LEATHER_TINT = (0xA0, 0x65, 0x40)
KIT_SIZE = 32

VANILLA_FILES = [
	"leather_helmet.png",
	"leather_helmet_overlay.png",
	"leather_chestplate.png",
	"leather_chestplate_overlay.png",
	"chainmail_helmet.png",
	"chainmail_chestplate.png",
	"iron_helmet.png",
	"iron_chestplate.png",
	"diamond_helmet.png",
	"diamond_chestplate.png",
]

KITS = [
	("leather_kit.png", "leather_helmet.png", "leather_chestplate.png", True),
	("chain_kit.png", "chainmail_helmet.png", "chainmail_chestplate.png", False),
	("iron_kit.png", "iron_helmet.png", "iron_chestplate.png", False),
	("diamond_kit.png", "diamond_helmet.png", "diamond_chestplate.png", False),
]

CONCEPT_STAFF = ART / "command_staff_concept.png"
STAFF_SIZE = 32

ARROW_FILL = (210, 40, 40, 255)
ARROW_HIGH = (232, 88, 72, 255)
ARROW_OUT = (112, 18, 18, 255)


def extract_vanilla() -> None:
	VANILLA.mkdir(parents=True, exist_ok=True)
	if not MC_JAR.is_file():
		raise FileNotFoundError(f"minecraft client jar not found: {MC_JAR}")
	with zipfile.ZipFile(MC_JAR) as zf:
		for name in VANILLA_FILES:
			src = f"assets/minecraft/textures/item/{name}"
			with zf.open(src) as inf, (VANILLA / name).open("wb") as out:
				out.write(inf.read())


def load_rgba(path: Path) -> Image.Image:
	return Image.open(path).convert("RGBA")


def tint(im: Image.Image, color: tuple[int, int, int]) -> Image.Image:
	px = im.load()
	tr, tg, tb = color
	out = Image.new("RGBA", im.size)
	op = out.load()
	for y in range(im.height):
		for x in range(im.width):
			r, g, b, a = px[x, y]
			if a == 0:
				continue
			op[x, y] = ((r * tr) // 255, (g * tg) // 255, (b * tb) // 255, a)
	return out


def overlay(base: Image.Image, top: Image.Image) -> Image.Image:
	out = base.copy()
	out.alpha_composite(top)
	return out


def crop_opaque(im: Image.Image) -> Image.Image:
	px = im.load()
	xs: list[int] = []
	ys: list[int] = []
	for y in range(im.height):
		for x in range(im.width):
			if px[x, y][3] > 0:
				xs.append(x)
				ys.append(y)
	return im.crop((min(xs), min(ys), max(xs) + 1, max(ys) + 1))


def piece(name: str, is_leather: bool) -> Image.Image:
	im = load_rgba(VANILLA / name)
	if is_leather:
		im = tint(im, LEATHER_TINT)
		over = VANILLA / name.replace(".png", "_overlay.png")
		if over.is_file():
			ov = load_rgba(over)
			if ov.getextrema()[3][1] > 0:
				im = overlay(im, ov)
	im = crop_opaque(im)
	doubled = im.resize((im.width * 2, im.height * 2), Image.Resampling.NEAREST)
	w = max(1, round(doubled.width * 2 / 3))
	h = max(1, round(doubled.height * 2 / 3))
	return doubled.resize((w, h), Image.Resampling.NEAREST)


def draw_enter_arrow(
	canvas: Image.Image,
	origin: tuple[int, int],
	corner_x: int | None = None,
) -> None:
	"""Return-key arrow: from origin right to corner_x, then down."""
	size = canvas.size[0]
	px = canvas.load()
	ox, oy = origin
	if corner_x is None:
		corner_x = ox + 9

	def put(x: int, y: int, color: tuple[int, int, int, int]) -> None:
		if 0 <= x < size and 0 <= y < size:
			px[x, y] = color

	fill: set[tuple[int, int]] = set()
	high: set[tuple[int, int]] = set()

	for x in range(ox, corner_x + 2):
		fill.update({(x, oy), (x, oy + 1)})
	for y in range(oy, oy + 16):
		fill.update({(corner_x, y), (corner_x + 1, y)})
	for x in range(corner_x - 3, corner_x + 5):
		fill.add((x, oy + 15))
	for x in range(corner_x - 2, corner_x + 4):
		fill.add((x, oy + 16))
	for x in range(corner_x - 1, corner_x + 3):
		fill.add((x, oy + 17))
	fill.add((corner_x, oy + 18))
	fill.add((corner_x + 1, oy + 18))

	high.update({(x, oy) for x in range(ox, corner_x + 1)})
	high.add((corner_x, oy + 1))

	outline: set[tuple[int, int]] = set()
	for x, y in fill:
		for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
			if (nx, ny) not in fill:
				outline.add((nx, ny))

	for x, y in outline:
		put(x, y, ARROW_OUT)
	for x, y in fill:
		put(x, y, ARROW_FILL)
	for x, y in high:
		put(x, y, ARROW_HIGH)


def compose_kit(helmet: str, chest: str, is_leather: bool) -> Image.Image:
	canvas = Image.new("RGBA", (KIT_SIZE, KIT_SIZE), (0, 0, 0, 0))
	helm_im = piece(helmet, is_leather)
	chest_im = piece(chest, is_leather)
	gap = 2
	group_w = max(helm_im.width, chest_im.width)
	helm_x = (group_w - helm_im.width) // 2
	chest_x = (group_w - chest_im.width) // 2
	canvas.paste(helm_im, (helm_x, 0), helm_im)
	canvas.paste(chest_im, (chest_x, helm_im.height + gap), chest_im)
	# From the gap at the right of the helmet, pointing out then down.
	arrow_x = helm_x + helm_im.width
	draw_enter_arrow(canvas, (arrow_x, helm_im.height), min(arrow_x + 8, KIT_SIZE - 5))
	return canvas


def is_concept_background(r: int, g: int, b: int) -> bool:
	mx, mn = max(r, g, b), min(r, g, b)
	return mx - mn < 14 and mn > 198


def compose_staff() -> Image.Image:
	src = load_rgba(CONCEPT_STAFF)
	px = src.load()
	for y in range(src.height):
		for x in range(src.width):
			r, g, b, _a = px[x, y]
			if is_concept_background(r, g, b):
				px[x, y] = (0, 0, 0, 0)
	xs: list[int] = []
	ys: list[int] = []
	for y in range(src.height):
		for x in range(src.width):
			if px[x, y][3] > 0:
				xs.append(x)
				ys.append(y)
	crop = src.crop((min(xs), min(ys), max(xs) + 1, max(ys) + 1))
	w, h = crop.size
	side = max(w, h)
	square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
	square.paste(crop, ((side - w) // 2, (side - h) // 2), crop)
	inner = STAFF_SIZE - 2
	small = square.resize((inner, inner), Image.Resampling.BOX)
	out = Image.new("RGBA", (STAFF_SIZE, STAFF_SIZE), (0, 0, 0, 0))
	out.paste(small, (1, 1), small)
	op = out.load()
	for y in range(STAFF_SIZE):
		for x in range(STAFF_SIZE):
			r, g, b, a = op[x, y]
			if a < 96 or is_concept_background(r, g, b):
				op[x, y] = (0, 0, 0, 0)
			else:
				op[x, y] = (r, g, b, 255)
	return out


def save_game(im: Image.Image, name: str) -> None:
	GAME_ITEM.mkdir(parents=True, exist_ok=True)
	im.save(GAME_ITEM / name)
	PREVIEW.mkdir(parents=True, exist_ok=True)
	im.resize((256, 256), Image.Resampling.NEAREST).save(PREVIEW / name)


def main() -> None:
	extract_vanilla()
	for out_name, helmet, chest, leather in KITS:
		save_game(compose_kit(helmet, chest, leather), out_name)
	save_game(compose_staff(), "command_staff.png")
	print("wrote", GAME_ITEM)


if __name__ == "__main__":
	main()
