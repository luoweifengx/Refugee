package luowei.refugee.blueprint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聚落生产与居住建筑：仓库、水井、农田、兵营、铁匠铺、谷仓。
 */
public final class SettlementBlueprints {
	private static final String COBBLE = BuiltinBlueprints.COBBLE;
	private static final String STONE_BRICKS = BuiltinBlueprints.STONE_BRICKS;
	private static final String LOG = BuiltinBlueprints.LOG;
	private static final String PLANKS = BuiltinBlueprints.PLANKS;
	private static final String STAIRS = BuiltinBlueprints.STAIRS;
	private static final String SLAB = BuiltinBlueprints.SLAB;
	private static final String FENCE = BuiltinBlueprints.FENCE;
	private static final String DOOR = BuiltinBlueprints.DOOR;
	private static final String PANE = BuiltinBlueprints.PANE;
	private static final String LADDER = BuiltinBlueprints.LADDER;
	private static final String WALL_TORCH = BuiltinBlueprints.WALL_TORCH;
	private static final String CHEST = "minecraft:chest";
	private static final String BED = "minecraft:red_bed";
	private static final String FURNACE = "minecraft:furnace";
	private static final String BLAST = "minecraft:blast_furnace";
	private static final String ANVIL = "minecraft:anvil";
	private static final String SMITHING = "minecraft:smithing_table";
	private static final String CRAFTING = "minecraft:crafting_table";
	private static final String HAY = "minecraft:hay_block";
	private static final String COMPOSTER = "minecraft:composter";
	private static final String FARMLAND = "minecraft:farmland";
	private static final String WATER = "minecraft:water";
	private static final String CAULDRON = "minecraft:water_cauldron";
	private static final String FENCE_GATE = "minecraft:oak_fence_gate";
	private static final String LANTERN = "minecraft:lantern";

	private SettlementBlueprints() {
	}

	static Map<String, String> displayNames() {
		Map<String, String> names = new LinkedHashMap<>();
		names.put("warehouse_oak", "橡木仓库");
		names.put("well_cobble", "圆石水井");
		names.put("farm_plot", "农田");
		names.put("barracks", "兵营");
		names.put("smithy", "铁匠铺");
		names.put("granary", "谷仓");
		return names;
	}

	static void writeAll(Path dir) throws IOException {
		writeWarehouse(dir.resolve("warehouse_oak.nbt"));
		writeWell(dir.resolve("well_cobble.nbt"));
		writeFarm(dir.resolve("farm_plot.nbt"));
		writeBarracks(dir.resolve("barracks.nbt"));
		writeSmithy(dir.resolve("smithy.nbt"));
		writeGranary(dir.resolve("granary.nbt"));
	}

	/** 7×6×7，圆石地基，两侧箱子，人字屋顶。 */
	private static void writeWarehouse(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(7, 6, 7);
		int max = 6;
		for (int x = 0; x <= max; x++) {
			for (int z = 0; z <= max; z++) {
				w.set(x, 0, z, COBBLE);
			}
		}
		placeLogCorners(w, max, 3);
		placeWalls(w, 7, 7, 3, PLANKS);
		w.set(3, 1, 0, DOOR, BlueprintNbtWriter.doorLower("north", "left"));
		w.set(3, 2, 0, DOOR, BlueprintNbtWriter.doorUpper("north", "left"));
		w.set(0, 2, 3, PANE);
		w.set(max, 2, 3, PANE);
		w.set(3, 2, max, PANE);
		for (int z = 2; z <= 5; z++) {
			if (z == 3) {
				continue;
			}
			w.set(1, 1, z, CHEST, BlueprintNbtWriter.chest("east"));
			w.set(5, 1, z, CHEST, BlueprintNbtWriter.chest("west"));
		}
		w.set(1, 2, 2, WALL_TORCH, BlueprintNbtWriter.wallTorch("east"));
		w.set(5, 2, 2, WALL_TORCH, BlueprintNbtWriter.wallTorch("west"));
		placeGableRoof(w, 7, 7, 4);
		w.write(path);
	}

	/** 5×7×5，中央水缸、围栏井口、四柱棚顶。 */
	private static void writeWell(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(5, 7, 5);
		int max = 4;
		for (int x = 0; x <= max; x++) {
			for (int z = 0; z <= max; z++) {
				boolean pit = x >= 1 && x <= 3 && z >= 1 && z <= 3;
				w.set(x, 0, z, pit ? COBBLE : STONE_BRICKS);
			}
		}
		w.set(2, 0, 2, CAULDRON, BlueprintNbtWriter.cauldronLevel(3));
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				if (x == 2 && z == 2) {
					continue;
				}
				w.set(x, 1, z, COBBLE);
			}
		}
		w.set(2, 1, 1, COBBLE);
		w.set(1, 1, 2, COBBLE);
		w.set(3, 1, 2, COBBLE);
		w.set(2, 1, 3, COBBLE);
		int[][] posts = {{1, 1}, {1, 3}, {3, 1}, {3, 3}};
		for (int[] p : posts) {
			for (int y = 2; y <= 4; y++) {
				w.set(p[0], y, p[1], LOG, BlueprintNbtWriter.axisY());
			}
		}
		w.set(2, 2, 1, FENCE);
		w.set(2, 2, 3, FENCE);
		w.set(1, 2, 2, FENCE);
		w.set(3, 2, 2, FENCE);
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				w.set(x, 5, z, SLAB, BlueprintNbtWriter.slabBottom());
			}
		}
		w.set(2, 6, 2, LANTERN, BlueprintNbtWriter.lantern(false));
		w.write(path);
	}

	/** 9×2×9，湿耕地、中央水渠、围栏与北门。 */
	private static void writeFarm(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(9, 2, 9);
		int max = 8;
		for (int x = 0; x <= max; x++) {
			for (int z = 0; z <= max; z++) {
				boolean edge = x == 0 || z == 0 || x == max || z == max;
				boolean water = x == 4 && z == 4;
				if (edge) {
					w.set(x, 0, z, COBBLE);
					boolean gate = x == 4 && z == 0;
					if (gate) {
						w.set(x, 1, z, FENCE_GATE, BlueprintNbtWriter.fenceGate("north"));
					} else {
						w.set(x, 1, z, FENCE);
					}
				} else if (water) {
					w.set(x, 0, z, WATER, BlueprintNbtWriter.props("level", "0"));
				} else {
					w.set(x, 0, z, FARMLAND, BlueprintNbtWriter.farmland(7));
				}
			}
		}
		w.set(7, 1, 7, COMPOSTER);
		w.write(path);
	}

	/** 9×6×7，石屋兵营，四张床与人字屋顶。 */
	private static void writeBarracks(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(9, 6, 7);
		int maxX = 8;
		int maxZ = 6;
		for (int x = 0; x <= maxX; x++) {
			for (int z = 0; z <= maxZ; z++) {
				w.set(x, 0, z, STONE_BRICKS);
			}
		}
		placeWalls(w, 9, 7, 3, COBBLE);
		int[][] corners = {{0, 0}, {0, maxZ}, {maxX, 0}, {maxX, maxZ}};
		for (int[] c : corners) {
			for (int y = 1; y <= 3; y++) {
				w.set(c[0], y, c[1], COBBLE);
			}
		}
		w.set(4, 1, 0, DOOR, BlueprintNbtWriter.doorLower("north", "left"));
		w.set(4, 2, 0, DOOR, BlueprintNbtWriter.doorUpper("north", "left"));
		w.set(2, 2, 0, PANE);
		w.set(6, 2, 0, PANE);
		w.set(0, 2, 3, PANE);
		w.set(maxX, 2, 3, PANE);
		w.set(2, 2, maxZ, PANE);
		w.set(6, 2, maxZ, PANE);
		placeBed(w, 1, 1, 2, "east");
		placeBed(w, 1, 1, 4, "east");
		placeBed(w, 6, 1, 2, "west");
		placeBed(w, 6, 1, 4, "west");
		w.set(4, 1, 5, CRAFTING);
		w.set(3, 1, 5, CHEST, BlueprintNbtWriter.chest("south"));
		w.set(5, 1, 5, CHEST, BlueprintNbtWriter.chest("south"));
		w.set(1, 2, 3, WALL_TORCH, BlueprintNbtWriter.wallTorch("east"));
		w.set(7, 2, 3, WALL_TORCH, BlueprintNbtWriter.wallTorch("west"));
		placeGableRoof(w, 9, 7, 4);
		w.write(path);
	}

	/** 7×6×7，开敞南向工棚，炉、高炉、铁砧。 */
	private static void writeSmithy(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(7, 6, 7);
		int max = 6;
		for (int x = 0; x <= max; x++) {
			for (int z = 0; z <= max; z++) {
				w.set(x, 0, z, COBBLE);
			}
		}
		placeLogCorners(w, max, 3);
		for (int y = 1; y <= 3; y++) {
			for (int x = 0; x <= max; x++) {
				if (x == 0 || x == max) {
					continue;
				}
				w.set(x, y, max, COBBLE);
			}
			for (int z = 1; z < max; z++) {
				w.set(0, y, z, COBBLE);
				w.set(max, y, z, COBBLE);
			}
		}
		w.set(3, 1, max, FURNACE, BlueprintNbtWriter.furnace("north", true));
		w.set(2, 1, 5, BLAST, BlueprintNbtWriter.furnace("south", true));
		w.set(4, 1, 5, FURNACE, BlueprintNbtWriter.furnace("south", true));
		w.set(3, 1, 3, ANVIL, BlueprintNbtWriter.anvil("south"));
		w.set(1, 1, 4, SMITHING);
		w.set(5, 1, 4, CRAFTING);
		w.set(1, 2, 2, WALL_TORCH, BlueprintNbtWriter.wallTorch("east"));
		w.set(5, 2, 2, WALL_TORCH, BlueprintNbtWriter.wallTorch("west"));
		placeGableRoof(w, 7, 7, 4);
		w.write(path);
	}

	/** 5×8×5，干草堆叠、内侧梯子、人字屋顶。 */
	private static void writeGranary(Path path) throws IOException {
		BlueprintNbtWriter w = new BlueprintNbtWriter(5, 8, 5);
		int max = 4;
		for (int x = 0; x <= max; x++) {
			for (int z = 0; z <= max; z++) {
				w.set(x, 0, z, PLANKS);
			}
		}
		placeLogCorners(w, max, 5);
		placeWalls(w, 5, 5, 5, PLANKS);
		w.set(2, 1, 0, DOOR, BlueprintNbtWriter.doorLower("north", "left"));
		w.set(2, 2, 0, DOOR, BlueprintNbtWriter.doorUpper("north", "left"));
		w.set(0, 3, 2, PANE);
		w.set(max, 3, 2, PANE);
		w.set(2, 3, max, PANE);
		w.set(2, 1, 3, HAY, BlueprintNbtWriter.axisY());
		w.set(3, 1, 3, HAY, BlueprintNbtWriter.axisY());
		w.set(1, 1, 3, HAY, BlueprintNbtWriter.axisY());
		w.set(2, 2, 3, HAY, BlueprintNbtWriter.axisY());
		w.set(3, 2, 3, HAY, BlueprintNbtWriter.axisY());
		w.set(2, 3, 3, HAY, BlueprintNbtWriter.axisY());
		for (int y = 1; y <= 5; y++) {
			w.set(1, y, 2, LADDER, BlueprintNbtWriter.ladder("east"));
		}
		w.set(2, 4, 3, LANTERN, BlueprintNbtWriter.lantern(false));
		placeGableRoof(w, 5, 5, 6);
		w.write(path);
	}

	private static void placeBed(BlueprintNbtWriter w, int footX, int y, int z, String facing) {
		int headX = footX + ("east".equals(facing) ? 1 : -1);
		w.set(footX, y, z, BED, BlueprintNbtWriter.bed(facing, "foot"));
		w.set(headX, y, z, BED, BlueprintNbtWriter.bed(facing, "head"));
	}

	private static void placeLogCorners(BlueprintNbtWriter w, int max, int topY) {
		int[][] corners = {{0, 0}, {0, max}, {max, 0}, {max, max}};
		for (int[] c : corners) {
			for (int y = 0; y <= topY; y++) {
				w.set(c[0], y, c[1], LOG, BlueprintNbtWriter.axisY());
			}
		}
	}

	private static void placeWalls(BlueprintNbtWriter w, int sx, int sz, int wallTop, String wall) {
		int maxX = sx - 1;
		int maxZ = sz - 1;
		for (int y = 1; y <= wallTop; y++) {
			for (int x = 0; x < sx; x++) {
				if (!isCorner(x, 0, maxX, maxZ)) {
					w.set(x, y, 0, wall);
				}
				if (!isCorner(x, maxZ, maxX, maxZ)) {
					w.set(x, y, maxZ, wall);
				}
			}
			for (int z = 1; z < maxZ; z++) {
				w.set(0, y, z, wall);
				w.set(maxX, y, z, wall);
			}
		}
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

	private static boolean isCorner(int x, int z, int maxX, int maxZ) {
		return (x == 0 || x == maxX) && (z == 0 || z == maxZ);
	}
}
