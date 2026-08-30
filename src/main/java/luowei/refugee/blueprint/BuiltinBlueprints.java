package luowei.refugee.blueprint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 启动时写入的一批自有基础结构（无 jigsaw / 无珍贵物）。
 */
public final class BuiltinBlueprints {
	static final String COBBLE = "minecraft:cobblestone";
	static final String STONE_BRICKS = "minecraft:stone_bricks";
	static final String COBBLE_WALL = "minecraft:cobblestone_wall";
	static final String LOG = "minecraft:oak_log";
	static final String PLANKS = "minecraft:oak_planks";
	static final String STAIRS = "minecraft:oak_stairs";
	static final String SLAB = "minecraft:oak_slab";
	static final String FENCE = "minecraft:oak_fence";
	static final String DOOR = "minecraft:oak_door";
	static final String PANE = "minecraft:glass_pane";
	static final String LADDER = "minecraft:ladder";
	static final String TORCH = "minecraft:torch";
	static final String WALL_TORCH = "minecraft:wall_torch";

	private static final Map<String, String> DISPLAY_NAMES = new LinkedHashMap<>();

	static {
		DISPLAY_NAMES.put("wall_straight", "直城墙");
		DISPLAY_NAMES.put("wall_corner", "城墙转角");
		DISPLAY_NAMES.put("wall_gate", "城门");
		DISPLAY_NAMES.put("palisade", "木栅栏墙");
		DISPLAY_NAMES.put("hut_oak", "橡木小屋");
		DISPLAY_NAMES.put("house_oak", "橡木民居");
		DISPLAY_NAMES.put("house_stone", "石屋");
		DISPLAY_NAMES.put("road_straight", "直路");
		DISPLAY_NAMES.put("road_corner", "弯道");
		DISPLAY_NAMES.put("road_t", "T字路口");
		DISPLAY_NAMES.put("road_cross", "十字路口");
		DISPLAY_NAMES.put("watchtower_wood", "木哨塔");
		DISPLAY_NAMES.put("watchtower_stone", "石哨塔");
		DISPLAY_NAMES.putAll(LandmarkBlueprints.displayNames());
	}

	private BuiltinBlueprints() {
	}

	static Map<String, String> displayNames() {
		return DISPLAY_NAMES;
	}

	static void writeAll(Path dir) throws IOException {
		writeWallStraight(dir.resolve("wall_straight.nbt"));
		writeWallCorner(dir.resolve("wall_corner.nbt"));
		writeWallGate(dir.resolve("wall_gate.nbt"));
		writePalisade(dir.resolve("palisade.nbt"));
		writeHouse(dir.resolve("hut_oak.nbt"), 5, 5, 5, false, false);
		writeHouse(dir.resolve("house_oak.nbt"), 7, 6, 7, false, true);
		writeHouse(dir.resolve("house_stone.nbt"), 7, 6, 7, true, true);
		writeRoadStraight(dir.resolve("road_straight.nbt"));
		writeRoadCorner(dir.resolve("road_corner.nbt"));
		writeRoadT(dir.resolve("road_t.nbt"));
		writeRoadCross(dir.resolve("road_cross.nbt"));
		writeWatchtowerWood(dir.resolve("watchtower_wood.nbt"));
		writeWatchtowerStone(dir.resolve("watchtower_stone.nbt"));
		LandmarkBlueprints.writeAll(dir);
	}

	/** 7×6×3，沿 +X。下层实心圆石，Y=4 走道，Y=5 垛口隔一空一。 */
	private static void writeWallStraight(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(7, 6, 3);
		w.fill(0, 0, 0, 6, 4, 2, COBBLE);
		for (int x = 0; x < 7; x++) {
			if (x % 2 != 0) {
				continue;
			}
			w.set(x, 5, 0, COBBLE);
			w.set(x, 5, 2, COBBLE);
		}
		w.write(path);
	}

	/** 5×6×5，内侧直角朝 +X+Z，走道高度与直墙对齐。 */
	private static void writeWallCorner(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(5, 6, 5);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				if (!onWallL(x, z)) {
					continue;
				}
				for (int y = 0; y <= 4; y++) {
					w.set(x, y, z, COBBLE);
				}
				if (!isWallLEdge(x, z)) {
					continue;
				}
				boolean merlon = (z == 0 || (z == 2 && x >= 2)) ? (x % 2 == 0) : (z % 2 == 0);
				if (merlon) {
					w.set(x, 5, z, COBBLE);
				}
			}
		}
		w.write(path);
	}

	/** 7×6×4，中间 2 宽×3 高门洞，两侧接直墙，走道齐平。 */
	private static void writeWallGate(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(7, 6, 4);
		for (int x = 0; x < 7; x++) {
			for (int y = 0; y <= 4; y++) {
				for (int z = 0; z < 4; z++) {
					boolean doorway = (x == 2 || x == 3) && y <= 2;
					if (!doorway) {
						w.set(x, y, z, COBBLE);
					}
				}
			}
			if (x % 2 == 0) {
				w.set(x, 5, 0, COBBLE);
				w.set(x, 5, 3, COBBLE);
			}
		}
		w.write(path);
	}

	/** 7×4×2，原木立柱 + 木板/栅栏，沿 +X。 */
	private static void writePalisade(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(7, 4, 2);
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 2; z++) {
				if (x % 2 == 0) {
					for (int y = 0; y < 4; y++) {
						w.set(x, y, z, LOG, BlueprintNbtWriter.axisY());
					}
				} else {
					for (int y = 0; y <= 2; y++) {
						w.set(x, y, z, PLANKS);
					}
					w.set(x, 3, z, FENCE);
				}
			}
		}
		w.write(path);
	}

	private static void writeHouse(Path path, int sx, int sy, int sz, boolean stone, boolean loft) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(sx, sy, sz);
		String wall = stone ? COBBLE : PLANKS;
		String floor = stone ? COBBLE : PLANKS;
		int maxX = sx - 1;
		int maxZ = sz - 1;
		int wallTop = sy - 3;
		int cx = sx / 2;
		int midZ = sz / 2;

		for (int x = 0; x < sx; x++) {
			for (int z = 0; z < sz; z++) {
				w.set(x, 0, z, floor);
			}
		}

		if (!stone) {
			int[][] corners = {{0, 0}, {0, maxZ}, {maxX, 0}, {maxX, maxZ}};
			for (int[] c : corners) {
				for (int y = 0; y <= wallTop; y++) {
					w.set(c[0], y, c[1], LOG, BlueprintNbtWriter.axisY());
				}
			}
		}

		for (int y = 1; y <= wallTop; y++) {
			for (int x = 0; x < sx; x++) {
				if (stone || !isCorner(x, 0, maxX, maxZ)) {
					w.set(x, y, 0, wall);
				}
				if (stone || !isCorner(x, maxZ, maxX, maxZ)) {
					w.set(x, y, maxZ, wall);
				}
			}
			for (int z = 1; z < maxZ; z++) {
				w.set(0, y, z, wall);
				w.set(maxX, y, z, wall);
			}
		}

		w.set(cx, 1, 0, DOOR, BlueprintNbtWriter.doorLower("north", "left"));
		w.set(cx, 2, 0, DOOR, BlueprintNbtWriter.doorUpper("north", "left"));

		w.set(0, 1, midZ, PANE);
		w.set(maxX, 1, midZ, PANE);
		if (sx >= 7) {
			w.set(0, 2, midZ, PANE);
			w.set(maxX, 2, midZ, PANE);
			w.set(1, 2, 0, PANE);
			w.set(maxX - 1, 2, 0, PANE);
		}

		if (loft) {
			w.set(cx, 1, 2, STAIRS, BlueprintNbtWriter.stairs("south"));
			w.set(cx, 2, 3, STAIRS, BlueprintNbtWriter.stairs("south"));
			for (int x = 1; x < maxX; x++) {
				for (int z = 3; z < maxZ; z++) {
					if (x == cx && z == 3) {
						continue;
					}
					w.set(x, 3, z, SLAB, BlueprintNbtWriter.slabBottom());
				}
			}
		}

		placeGableRoof(w, sx, sz, wallTop + 1);
		int torchZ = sx >= 7 ? Math.max(1, midZ - 1) : midZ;
		w.set(1, 2, torchZ, WALL_TORCH, BlueprintNbtWriter.wallTorch("east"));
		w.set(maxX - 1, 2, torchZ, WALL_TORCH, BlueprintNbtWriter.wallTorch("west"));
		w.write(path);
	}

	private static void placeGableRoof(BlueprintNbtWriter w, int sx, int sz, int roofY) {
		int maxX = sx - 1;
		int peakY = roofY + 1;
		for (int z = 0; z < sz; z++) {
			w.set(0, roofY, z, STAIRS, BlueprintNbtWriter.stairs("east"));
			w.set(maxX, roofY, z, STAIRS, BlueprintNbtWriter.stairs("west"));
			for (int x = 1; x < maxX; x++) {
				w.set(x, roofY, z, PLANKS);
			}
			if (sx <= 5) {
				w.set(1, peakY, z, STAIRS, BlueprintNbtWriter.stairs("east"));
				w.set(maxX - 1, peakY, z, STAIRS, BlueprintNbtWriter.stairs("west"));
				w.set(sx / 2, peakY, z, PLANKS);
			} else {
				w.set(1, peakY, z, STAIRS, BlueprintNbtWriter.stairs("east"));
				w.set(2, peakY, z, STAIRS, BlueprintNbtWriter.stairs("east"));
				w.set(sx / 2, peakY, z, PLANKS);
				w.set(maxX - 2, peakY, z, STAIRS, BlueprintNbtWriter.stairs("west"));
				w.set(maxX - 1, peakY, z, STAIRS, BlueprintNbtWriter.stairs("west"));
			}
		}
	}

	/** 5×1×3，中间圆石、两侧石砖，沿 +X。 */
	private static void writeRoadStraight(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(5, 1, 3);
		for (int x = 0; x < 5; x++) {
			w.set(x, 0, 0, STONE_BRICKS);
			w.set(x, 0, 1, COBBLE);
			w.set(x, 0, 2, STONE_BRICKS);
		}
		w.write(path);
	}

	/** 5×1×5，L 形，5 格模数。 */
	private static void writeRoadCorner(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(5, 1, 5);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				if (!(z <= 2 || x <= 2)) {
					continue;
				}
				boolean cobble = z == 1 || x == 1;
				w.set(x, 0, z, cobble ? COBBLE : STONE_BRICKS);
			}
		}
		w.write(path);
	}

	/** 5×1×5，横条沿 +X，竖枝居中朝 +Z。 */
	private static void writeRoadT(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(5, 1, 5);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				boolean bar = z <= 2;
				boolean stem = z >= 2 && x >= 1 && x <= 3;
				if (!bar && !stem) {
					continue;
				}
				boolean cobble = z == 1 || x == 2;
				w.set(x, 0, z, cobble ? COBBLE : STONE_BRICKS);
			}
		}
		w.write(path);
	}

	/** 5×1×5，居中 3 格宽十字。 */
	private static void writeRoadCross(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(5, 1, 5);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				boolean on = (z >= 1 && z <= 3) || (x >= 1 && x <= 3);
				if (!on) {
					continue;
				}
				boolean cobble = x == 2 || z == 2;
				w.set(x, 0, z, cobble ? COBBLE : STONE_BRICKS);
			}
		}
		w.write(path);
	}

	/** 5×10×5，四角原木到顶，Y=0/4/8 木板层，内侧梯子，顶层栅栏+火把。 */
	private static void writeWatchtowerWood(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(5, 10, 5);
		int max = 4;
		int[] floors = {0, 4, 8};
		for (int fy : floors) {
			for (int x = 0; x < 5; x++) {
				for (int z = 0; z < 5; z++) {
					if (fy > 0 && x == 1 && z == 2) {
						continue;
					}
					w.set(x, fy, z, PLANKS);
				}
			}
		}
		int[][] corners = {{0, 0}, {0, max}, {max, 0}, {max, max}};
		for (int[] c : corners) {
			for (int y = 0; y < 10; y++) {
				w.set(c[0], y, c[1], LOG, BlueprintNbtWriter.axisY());
			}
		}
		for (int y = 1; y <= 8; y++) {
			if (y != 4 && y != 8) {
				w.set(0, y, 2, PLANKS);
			}
			w.set(1, y, 2, LADDER, BlueprintNbtWriter.ladder("east"));
		}
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				if (!onPerimeter(x, z, max, max) || isCorner(x, z, max, max)) {
					continue;
				}
				w.set(x, 9, z, FENCE);
			}
		}
		w.set(1, 9, 1, TORCH);
		w.set(1, 9, 3, TORCH);
		w.set(3, 9, 1, TORCH);
		w.set(3, 9, 3, TORCH);
		w.write(path);
	}

	/** 5×12×5，圆石筒、石砖垛口、内部梯子。 */
	private static void writeWatchtowerStone(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(5, 12, 5);
		int max = 4;
		int[][] corners = {{0, 0}, {0, max}, {max, 0}, {max, max}};
		for (int[] c : corners) {
			for (int y = 0; y < 12; y++) {
				w.set(c[0], y, c[1], COBBLE);
			}
		}
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				w.set(x, 0, z, COBBLE);
			}
		}
		int[] brickFloors = {5, 10};
		for (int fy : brickFloors) {
			for (int x = 0; x < 5; x++) {
				for (int z = 0; z < 5; z++) {
					if (x == 1 && z == 2) {
						continue;
					}
					if (isCorner(x, z, max, max)) {
						continue;
					}
					w.set(x, fy, z, STONE_BRICKS);
				}
			}
		}
		int[] belts = {1, 2, 6, 7, 9};
		for (int y : belts) {
			fillPerimeterCobble(w, y, max);
		}
		for (int y = 1; y <= 10; y++) {
			w.set(0, y, 2, COBBLE);
			w.set(1, y, 2, LADDER, BlueprintNbtWriter.ladder("east"));
		}
		for (int i = 0; i < 5; i++) {
			placeTowerCrenel(w, i, 11, 0, i % 2 == 0);
			placeTowerCrenel(w, i, 11, max, i % 2 == 0);
			if (i != 0 && i != max) {
				placeTowerCrenel(w, 0, 11, i, i % 2 == 0);
				placeTowerCrenel(w, max, 11, i, i % 2 == 0);
			}
		}
		w.write(path);
	}

	private static void fillPerimeterCobble(BlueprintNbtWriter w, int y, int max) {
		for (int x = 0; x <= max; x++) {
			if (!isWindow(x, y, 0, max)) {
				w.set(x, y, 0, COBBLE);
			}
			if (!isWindow(x, y, max, max)) {
				w.set(x, y, max, COBBLE);
			}
		}
		for (int z = 1; z < max; z++) {
			if (!isWindow(0, y, z, max)) {
				w.set(0, y, z, COBBLE);
			}
			if (!isWindow(max, y, z, max)) {
				w.set(max, y, z, COBBLE);
			}
		}
	}

	private static boolean isWindow(int x, int y, int z, int max) {
		if (y != 2 && y != 7) {
			return false;
		}
		if (x == 2 && (z == 0 || z == max)) {
			return true;
		}
		return z == 2 && x == max;
	}

	private static void placeTowerCrenel(BlueprintNbtWriter w, int x, int y, int z, boolean merlon) {
		if (merlon) {
			w.set(x, y, z, STONE_BRICKS);
		} else {
			w.set(x, y, z, COBBLE_WALL);
		}
	}

	private static boolean onWallL(int x, int z) {
		return z <= 2 || x <= 2;
	}

	private static boolean isWallLEdge(int x, int z) {
		return z == 0 || x == 0 || (z == 2 && x >= 2) || (x == 2 && z >= 2);
	}

	private static boolean isCorner(int x, int z, int maxX, int maxZ) {
		return (x == 0 || x == maxX) && (z == 0 || z == maxZ);
	}

	private static boolean onPerimeter(int x, int z, int maxX, int maxZ) {
		return x == 0 || z == 0 || x == maxX || z == maxZ;
	}
}
