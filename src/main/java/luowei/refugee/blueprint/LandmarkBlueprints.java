package luowei.refugee.blueprint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

/**
 * 世界地标蓝图：法师塔、地狱熔炉。由 {@link WorldgenBlueprints} 写出到世界生成目录。
 */
public final class LandmarkBlueprints {
	public static final String MAGE_TOWER = "mage_tower";
	public static final String HELL_FURNACE = "hell_furnace";

	public static final int MAGE_TOWER_X = 9;
	public static final int MAGE_TOWER_Y = 22;
	public static final int MAGE_TOWER_Z = 9;
	/** 附魔层地板在 y=10，村民站在 (4,11,6)，附魔台在 (4,11,4)。 */
	public static final BlockPos ENCHANTER_LOCAL_FEET = new BlockPos(4, 11, 6);

	public static final int HELL_FURNACE_X = 11;
	public static final int HELL_FURNACE_Y = 9;
	public static final int HELL_FURNACE_Z = 11;

	private static final String DEEPSLATE_BRICKS = "minecraft:deepslate_bricks";
	private static final String DEEPSLATE_TILES = "minecraft:deepslate_tiles";
	private static final String DEEPSLATE_STAIRS = "minecraft:deepslate_brick_stairs";
	private static final String DEEPSLATE_WALL = "minecraft:deepslate_brick_wall";
	private static final String CHISELED = "minecraft:chiseled_stone_bricks";
	private static final String POLISHED_DEEPSLATE = "minecraft:polished_deepslate";
	private static final String LAPIS = "minecraft:lapis_block";
	private static final String AMETHYST = "minecraft:amethyst_block";
	private static final String BOOKSHELF = "minecraft:bookshelf";
	private static final String TABLE = "minecraft:enchanting_table";
	private static final String BREWING = "minecraft:brewing_stand";
	private static final String LECTERN = "minecraft:lectern";
	private static final String DOOR = "minecraft:dark_oak_door";
	private static final String PANE = "minecraft:purple_stained_glass_pane";
	private static final String LADDER = "minecraft:ladder";
	private static final String LANTERN = "minecraft:lantern";
	private static final String CARPET = "minecraft:purple_carpet";
	private static final String ROD = "minecraft:lightning_rod";

	private static final String BLACKSTONE = "minecraft:blackstone";
	private static final String POLISHED_BLACKSTONE = "minecraft:polished_blackstone";
	private static final String NETHER_BRICKS = "minecraft:nether_bricks";
	private static final String RED_NETHER = "minecraft:red_nether_bricks";
	private static final String NETHER_FENCE = "minecraft:nether_brick_fence";
	private static final String NETHER_STAIRS = "minecraft:nether_brick_stairs";
	private static final String MAGMA = "minecraft:magma_block";
	private static final String BLAST = "minecraft:blast_furnace";
	private static final String FURNACE = "minecraft:furnace";
	private static final String CAMPFIRE = "minecraft:soul_campfire";
	private static final String BARS = "minecraft:iron_bars";
	private static final String SOUL_LANTERN = "minecraft:soul_lantern";
	private static final String CRIMSON = "minecraft:crimson_hyphae";
	private static final String NETHER_DOOR = "minecraft:crimson_door";

	private LandmarkBlueprints() {
	}

	static Map<String, String> displayNames() {
		Map<String, String> names = new LinkedHashMap<>();
		names.put(MAGE_TOWER, "法师塔");
		names.put(HELL_FURNACE, "地狱熔炉");
		return names;
	}

	static void writeAll(Path dir) throws IOException {
		writeMageTower(dir.resolve(MAGE_TOWER + ".nbt"));
		writeHellFurnace(dir.resolve(HELL_FURNACE + ".nbt"));
	}

	public static Vec3i mageTowerSize() {
		return new Vec3i(MAGE_TOWER_X, MAGE_TOWER_Y, MAGE_TOWER_Z);
	}

	public static Vec3i hellFurnaceSize() {
		return new Vec3i(HELL_FURNACE_X, HELL_FURNACE_Y, HELL_FURNACE_Z);
	}

	/**
	 * 9×22×9 深板岩法师塔：三层房间、内部梯子、顶层附魔台、尖顶避雷针。
	 */
	private static void writeMageTower(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(MAGE_TOWER_X, MAGE_TOWER_Y, MAGE_TOWER_Z);
		int max = 8;
		int[] floors = {0, 5, 10, 15};
		int holeX = 1;
		int holeZ = 4;

		for (int fy : floors) {
			placeFloor(w, fy, max, DEEPSLATE_TILES, fy == 0 ? -1 : holeX, holeZ);
		}

		int[][] corners = {{0, 0}, {0, max}, {max, 0}, {max, max}};
		for (int[] c : corners) {
			for (int y = 0; y <= 16; y++) {
				w.set(c[0], y, c[1], CHISELED);
			}
		}

		fillPerimeter(w, 1, 4, max, DEEPSLATE_BRICKS);
		fillPerimeter(w, 6, 9, max, DEEPSLATE_BRICKS);
		fillPerimeter(w, 11, 14, max, DEEPSLATE_BRICKS);
		fillBelt(w, 4, max, LAPIS);
		fillBelt(w, 9, max, LAPIS);
		fillBelt(w, 14, max, LAPIS);
		for (int[] c : corners) {
			for (int y = 0; y <= 16; y++) {
				w.set(c[0], y, c[1], CHISELED);
			}
		}

		w.set(4, 1, 0, DOOR, BlueprintNbtWriter.doorLower("north", "left"));
		w.set(4, 2, 0, DOOR, BlueprintNbtWriter.doorUpper("north", "left"));

		placeWindow(w, 2, 4, max, true);
		placeWindow(w, 7, 4, max, false);
		placeWindow(w, 12, 4, max, false);

		for (int y = 1; y <= 15; y++) {
			w.set(0, y, holeZ, POLISHED_DEEPSLATE);
			w.set(holeX, y, holeZ, LADDER, BlueprintNbtWriter.ladder("east"));
		}

		placeGroundInterior(w);
		placeLibraryInterior(w);
		placeEnchantInterior(w);

		for (int i = 0; i <= max; i++) {
			boolean merlon = i % 2 == 0;
			placeCrenel(w, i, 16, 0, merlon);
			placeCrenel(w, i, 16, max, merlon);
			if (i != 0 && i != max) {
				placeCrenel(w, 0, 16, i, merlon);
				placeCrenel(w, max, 16, i, merlon);
			}
		}
		w.set(1, 16, 1, LANTERN, BlueprintNbtWriter.lantern(false));
		w.set(1, 16, 7, LANTERN, BlueprintNbtWriter.lantern(false));
		w.set(7, 16, 1, LANTERN, BlueprintNbtWriter.lantern(false));
		w.set(7, 16, 7, LANTERN, BlueprintNbtWriter.lantern(false));

		placePyramidRoof(w, max);
		w.write(path);
	}

	private static void placeGroundInterior(BlueprintNbtWriter w) {
		w.set(4, 1, 4, CARPET);
		w.set(2, 1, 1, BOOKSHELF);
		w.set(3, 1, 1, BOOKSHELF);
		w.set(5, 1, 1, BOOKSHELF);
		w.set(6, 1, 1, BOOKSHELF);
		w.set(1, 1, 2, BOOKSHELF);
		w.set(1, 1, 6, BOOKSHELF);
		w.set(7, 1, 2, BOOKSHELF);
		w.set(7, 1, 6, BOOKSHELF);
		w.set(2, 4, 2, LANTERN, BlueprintNbtWriter.lantern(true));
		w.set(6, 4, 2, LANTERN, BlueprintNbtWriter.lantern(true));
		w.set(2, 4, 6, LANTERN, BlueprintNbtWriter.lantern(true));
		w.set(6, 4, 6, LANTERN, BlueprintNbtWriter.lantern(true));
	}

	private static void placeLibraryInterior(BlueprintNbtWriter w) {
		for (int x = 2; x <= 6; x++) {
			if (x == 4) {
				continue;
			}
			w.set(x, 6, 7, BOOKSHELF);
			w.set(x, 7, 7, BOOKSHELF);
		}
		w.set(7, 6, 3, BOOKSHELF);
		w.set(7, 6, 5, BOOKSHELF);
		w.set(7, 7, 3, BOOKSHELF);
		w.set(7, 7, 5, BOOKSHELF);
		w.set(4, 6, 6, LECTERN, BlueprintNbtWriter.lectern("north"));
		w.set(4, 6, 4, CARPET);
		w.set(2, 9, 4, LANTERN, BlueprintNbtWriter.lantern(true));
		w.set(6, 9, 4, LANTERN, BlueprintNbtWriter.lantern(true));
	}

	private static void placeEnchantInterior(BlueprintNbtWriter w) {
		w.set(4, 11, 4, TABLE);
		w.set(2, 11, 2, BOOKSHELF);
		w.set(3, 11, 2, BOOKSHELF);
		w.set(5, 11, 2, BOOKSHELF);
		w.set(6, 11, 2, BOOKSHELF);
		w.set(2, 11, 3, BOOKSHELF);
		w.set(6, 11, 3, BOOKSHELF);
		w.set(2, 11, 5, BOOKSHELF);
		w.set(6, 11, 5, BOOKSHELF);
		w.set(7, 11, 4, BREWING);
		w.set(4, 11, 7, LECTERN, BlueprintNbtWriter.lectern("north"));
		w.set(4, 11, 5, CARPET);
		w.set(2, 14, 2, LANTERN, BlueprintNbtWriter.lantern(true));
		w.set(6, 14, 2, LANTERN, BlueprintNbtWriter.lantern(true));
		w.set(2, 14, 6, LANTERN, BlueprintNbtWriter.lantern(true));
		w.set(6, 14, 6, LANTERN, BlueprintNbtWriter.lantern(true));
	}

	private static void placePyramidRoof(BlueprintNbtWriter w, int max) {
		int[] insets = {1, 2, 3, 4};
		int[] ys = {17, 18, 19, 20};
		for (int i = 0; i < insets.length; i++) {
			int inset = insets[i];
			int y = ys[i];
			int x0 = inset;
			int x1 = max - inset;
			int z0 = inset;
			int z1 = max - inset;
			if (x0 == x1) {
				w.set(x0, y, z0, AMETHYST);
				continue;
			}
			for (int x = x0; x <= x1; x++) {
				w.set(x, y, z0, DEEPSLATE_STAIRS, BlueprintNbtWriter.stairs("south"));
				w.set(x, y, z1, DEEPSLATE_STAIRS, BlueprintNbtWriter.stairs("north"));
			}
			for (int z = z0 + 1; z < z1; z++) {
				w.set(x0, y, z, DEEPSLATE_STAIRS, BlueprintNbtWriter.stairs("east"));
				w.set(x1, y, z, DEEPSLATE_STAIRS, BlueprintNbtWriter.stairs("west"));
			}
			for (int x = x0 + 1; x < x1; x++) {
				for (int z = z0 + 1; z < z1; z++) {
					w.set(x, y, z, DEEPSLATE_BRICKS);
				}
			}
		}
		w.set(4, 21, 4, ROD, BlueprintNbtWriter.facing("up"));
	}

	/**
	 * 11×9×11 黑石/地狱砖熔炉：中央岩浆池、一圈高炉、灵魂营火烟囱。
	 */
	private static void writeHellFurnace(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(HELL_FURNACE_X, HELL_FURNACE_Y, HELL_FURNACE_Z);
		int max = 10;

		for (int x = 0; x <= max; x++) {
			for (int z = 0; z <= max; z++) {
				boolean pit = x >= 4 && x <= 6 && z >= 4 && z <= 6;
				w.set(x, 0, z, pit ? MAGMA : BLACKSTONE);
			}
		}
		for (int x = 4; x <= 6; x++) {
			for (int z = 4; z <= 6; z++) {
				if (x == 5 && z == 5) {
					continue;
				}
				w.set(x, 0, z, MAGMA);
			}
		}

		int[][] corners = {{0, 0}, {0, max}, {max, 0}, {max, max}};
		for (int[] c : corners) {
			for (int y = 0; y <= 5; y++) {
				w.set(c[0], y, c[1], POLISHED_BLACKSTONE);
			}
		}

		fillPerimeter(w, 1, 4, max, NETHER_BRICKS);
		fillBelt(w, 3, max, RED_NETHER);
		for (int[] c : corners) {
			for (int y = 0; y <= 5; y++) {
				w.set(c[0], y, c[1], POLISHED_BLACKSTONE);
			}
		}
		w.set(5, 1, 0, NETHER_DOOR, BlueprintNbtWriter.doorLower("north", "left"));
		w.set(5, 2, 0, NETHER_DOOR, BlueprintNbtWriter.doorUpper("north", "left"));

		placeBarsWindow(w, 2, max);
		placeBarsWindow(w, 4, max);

		w.set(5, 1, 3, BLAST, BlueprintNbtWriter.furnace("south", true));
		w.set(5, 1, 7, BLAST, BlueprintNbtWriter.furnace("north", true));
		w.set(3, 1, 5, BLAST, BlueprintNbtWriter.furnace("east", true));
		w.set(7, 1, 5, BLAST, BlueprintNbtWriter.furnace("west", true));
		w.set(3, 1, 3, FURNACE, BlueprintNbtWriter.furnace("south", true));
		w.set(7, 1, 3, FURNACE, BlueprintNbtWriter.furnace("south", true));
		w.set(3, 1, 7, FURNACE, BlueprintNbtWriter.furnace("north", true));
		w.set(7, 1, 7, FURNACE, BlueprintNbtWriter.furnace("north", true));

		w.set(1, 1, 2, CRIMSON, BlueprintNbtWriter.axisY());
		w.set(1, 1, 8, CRIMSON, BlueprintNbtWriter.axisY());
		w.set(9, 1, 2, CRIMSON, BlueprintNbtWriter.axisY());
		w.set(9, 1, 8, CRIMSON, BlueprintNbtWriter.axisY());
		w.set(2, 1, 1, SOUL_LANTERN, BlueprintNbtWriter.lantern(false));
		w.set(8, 1, 1, SOUL_LANTERN, BlueprintNbtWriter.lantern(false));
		w.set(2, 1, 9, SOUL_LANTERN, BlueprintNbtWriter.lantern(false));
		w.set(8, 1, 9, SOUL_LANTERN, BlueprintNbtWriter.lantern(false));

		for (int x = 1; x <= 9; x++) {
			for (int z = 1; z <= 9; z++) {
				if (x >= 4 && x <= 6 && z >= 4 && z <= 6) {
					continue;
				}
				if (x == 1 || x == 9 || z == 1 || z == 9) {
					w.set(x, 5, z, NETHER_STAIRS, stairsTowardCenter(x, z));
				} else {
					w.set(x, 5, z, BLACKSTONE);
				}
			}
		}

		for (int y = 5; y <= 8; y++) {
			for (int x = 4; x <= 6; x++) {
				for (int z = 4; z <= 6; z++) {
					boolean hollow = x == 5 && z == 5 && y >= 6;
					if (hollow) {
						continue;
					}
					boolean shell = x == 4 || x == 6 || z == 4 || z == 6;
					if (shell || y == 5) {
						w.set(x, y, z, y == 8 ? RED_NETHER : NETHER_BRICKS);
					}
				}
			}
		}
		w.set(5, 6, 5, CAMPFIRE, BlueprintNbtWriter.campfire("north", true));
		w.set(4, 8, 5, NETHER_FENCE);
		w.set(6, 8, 5, NETHER_FENCE);
		w.set(5, 8, 4, NETHER_FENCE);
		w.set(5, 8, 6, NETHER_FENCE);
		w.set(5, 5, 2, SOUL_LANTERN, BlueprintNbtWriter.lantern(false));
		w.set(5, 5, 8, SOUL_LANTERN, BlueprintNbtWriter.lantern(false));
		w.write(path);
	}

	private static net.minecraft.nbt.CompoundTag stairsTowardCenter(int x, int z) {
		if (z == 1) {
			return BlueprintNbtWriter.stairs("south");
		}
		if (z == 9) {
			return BlueprintNbtWriter.stairs("north");
		}
		if (x == 1) {
			return BlueprintNbtWriter.stairs("east");
		}
		return BlueprintNbtWriter.stairs("west");
	}

	private static void placeFloor(BlueprintNbtWriter w, int y, int max, String block, int holeX, int holeZ) {
		for (int x = 0; x <= max; x++) {
			for (int z = 0; z <= max; z++) {
				if (x == holeX && z == holeZ) {
					continue;
				}
				w.set(x, y, z, block);
			}
		}
	}

	private static void fillPerimeter(BlueprintNbtWriter w, int y0, int y1, int max, String block) {
		for (int y = y0; y <= y1; y++) {
			for (int x = 0; x <= max; x++) {
				w.set(x, y, 0, block);
				w.set(x, y, max, block);
			}
			for (int z = 1; z < max; z++) {
				w.set(0, y, z, block);
				w.set(max, y, z, block);
			}
		}
	}

	private static void fillBelt(BlueprintNbtWriter w, int y, int max, String block) {
		for (int x = 1; x < max; x++) {
			if (x == 4 && y <= 2) {
				continue;
			}
			w.set(x, y, 0, block);
			w.set(x, y, max, block);
		}
		for (int z = 1; z < max; z++) {
			w.set(0, y, z, block);
			w.set(max, y, z, block);
		}
	}

	private static void placeWindow(BlueprintNbtWriter w, int y, int mid, int max, boolean skipDoorWall) {
		if (!skipDoorWall) {
			w.set(mid, y, 0, PANE);
		}
		w.set(mid, y, max, PANE);
		w.set(0, y, mid, PANE);
		w.set(max, y, mid, PANE);
	}

	private static void placeBarsWindow(BlueprintNbtWriter w, int y, int max) {
		w.set(2, y, 0, BARS);
		w.set(8, y, 0, BARS);
		w.set(2, y, max, BARS);
		w.set(8, y, max, BARS);
		w.set(0, y, 2, BARS);
		w.set(0, y, 8, BARS);
		w.set(max, y, 2, BARS);
		w.set(max, y, 8, BARS);
	}

	private static void placeCrenel(BlueprintNbtWriter w, int x, int y, int z, boolean merlon) {
		if (merlon) {
			w.set(x, y, z, POLISHED_DEEPSLATE);
		} else {
			w.set(x, y, z, DEEPSLATE_WALL);
		}
	}
}
