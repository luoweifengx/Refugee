package luowei.refugee.blueprint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;

/**
 * 被摧毁聚落的痕迹：废弃箭塔、破损城墙、坍塌城门、废墟小屋。
 */
public final class RuinBlueprints {
	public static final String ARROW_TOWER = "ruin_arrow_tower";
	public static final String WALL = "ruin_wall";
	public static final String GATE = "ruin_gate";
	public static final String HUT = "ruin_hut";

	public static final int ARROW_TOWER_X = 5;
	public static final int ARROW_TOWER_Y = 12;
	public static final int ARROW_TOWER_Z = 5;
	public static final int WALL_X = 7;
	public static final int WALL_Y = 6;
	public static final int WALL_Z = 3;
	public static final int GATE_X = 7;
	public static final int GATE_Y = 6;
	public static final int GATE_Z = 4;
	public static final int HUT_X = 7;
	public static final int HUT_Y = 6;
	public static final int HUT_Z = 7;

	private static final String COBBLE = BuiltinBlueprints.COBBLE;
	private static final String STONE_BRICKS = BuiltinBlueprints.STONE_BRICKS;
	private static final String COBBLE_WALL = BuiltinBlueprints.COBBLE_WALL;
	private static final String LOG = BuiltinBlueprints.LOG;
	private static final String PLANKS = BuiltinBlueprints.PLANKS;
	private static final String STAIRS = BuiltinBlueprints.STAIRS;
	private static final String SLAB = BuiltinBlueprints.SLAB;
	private static final String FENCE = BuiltinBlueprints.FENCE;
	private static final String LADDER = BuiltinBlueprints.LADDER;
	private static final String MOSSY_COBBLE = "minecraft:mossy_cobblestone";
	private static final String MOSSY_BRICKS = "minecraft:mossy_stone_bricks";
	private static final String CRACKED = "minecraft:cracked_stone_bricks";
	private static final String COBWEB = "minecraft:cobweb";
	private static final String VINE = "minecraft:vine";
	private static final String MOSS_CARPET = "minecraft:moss_carpet";
	private static final String CAMPFIRE = "minecraft:campfire";
	private static final String BARS = "minecraft:iron_bars";

	private RuinBlueprints() {
	}

	public record Spec(String id, int sizeX, int sizeY, int sizeZ) {
	}

	public static List<Spec> spawnables() {
		return List.of(
				new Spec(ARROW_TOWER, ARROW_TOWER_X, ARROW_TOWER_Y, ARROW_TOWER_Z),
				new Spec(WALL, WALL_X, WALL_Y, WALL_Z),
				new Spec(GATE, GATE_X, GATE_Y, GATE_Z),
				new Spec(HUT, HUT_X, HUT_Y, HUT_Z)
		);
	}

	public static Spec spec(String id) {
		for (Spec spec : spawnables()) {
			if (spec.id().equals(id)) {
				return spec;
			}
		}
		return null;
	}

	public static CompoundTag templateNbt(String id) {
		return writer(id).toNbt();
	}

	private static BlueprintNbtWriter writer(String id) {
		return switch (id) {
			case ARROW_TOWER -> arrowTower();
			case WALL -> wall();
			case GATE -> gate();
			case HUT -> hut();
			default -> throw new IllegalArgumentException(id);
		};
	}

	static Map<String, String> displayNames() {
		Map<String, String> names = new LinkedHashMap<>();
		names.put(ARROW_TOWER, "废弃箭塔");
		names.put(WALL, "破损城墙");
		names.put(GATE, "坍塌城门");
		names.put(HUT, "废墟小屋");
		return names;
	}

	static void writeAll(Path dir) throws IOException {
		arrowTower().write(dir.resolve(ARROW_TOWER + ".nbt"));
		wall().write(dir.resolve(WALL + ".nbt"));
		gate().write(dir.resolve(GATE + ".nbt"));
		hut().write(dir.resolve(HUT + ".nbt"));
	}

	/**
	 * 5×12×5 石箭塔残体：缺角、缺垛口、青苔与蛛网。
	 */
	private static BlueprintNbtWriter arrowTower() {
		BlueprintNbtWriter w = new BlueprintNbtWriter(ARROW_TOWER_X, ARROW_TOWER_Y, ARROW_TOWER_Z);
		int max = 4;
		for (int x = 0; x <= max; x++) {
			for (int z = 0; z <= max; z++) {
				w.set(x, 0, z, weathered(x, 0, z));
			}
		}
		int[][] corners = {{0, 0}, {0, max}, {max, 0}, {max, max}};
		for (int[] c : corners) {
			int top = (c[0] == max && c[1] == 0) ? 6 : 11;
			for (int y = 1; y <= top; y++) {
				w.set(c[0], y, c[1], weathered(c[0], y, c[1]));
			}
		}
		int[] floors = {5, 10};
		for (int fy : floors) {
			for (int x = 1; x < max; x++) {
				for (int z = 1; z < max; z++) {
					if (x == 1 && z == 2) {
						continue;
					}
					if (fy == 10 && (x + z) % 2 == 0) {
						continue;
					}
					if (fy == 10) {
						w.set(x, fy, z, SLAB, BlueprintNbtWriter.slabBottom());
					} else {
						w.set(x, fy, z, STONE_BRICKS);
					}
				}
			}
		}
		for (int y = 1; y <= 9; y++) {
			fillTowerRing(w, y, max);
		}
		for (int y = 1; y <= 6; y++) {
			w.set(0, y, 2, weathered(0, y, 2));
			w.set(1, y, 2, LADDER, BlueprintNbtWriter.ladder("east"));
		}
		w.set(3, 6, 1, COBWEB);
		w.set(1, 7, 3, COBWEB);
		w.set(2, 10, 1, COBWEB);
		w.set(1, 3, 1, VINE, BlueprintNbtWriter.vine("west"));
		w.set(1, 4, 1, VINE, BlueprintNbtWriter.vine("west"));
		w.set(3, 2, 1, MOSS_CARPET);
		w.set(2, 6, 3, MOSS_CARPET);
		placeBrokenCrenel(w, max);
		return w;
	}

	private static void fillTowerRing(BlueprintNbtWriter w, int y, int max) {
		for (int x = 0; x <= max; x++) {
			for (int z : new int[] {0, max}) {
				if (isCorner(x, z, max, max)) {
					continue;
				}
				if (missingTower(x, y, z)) {
					continue;
				}
				w.set(x, y, z, weathered(x, y, z));
			}
		}
		for (int z = 1; z < max; z++) {
			for (int x : new int[] {0, max}) {
				if (isCorner(x, z, max, max)) {
					continue;
				}
				if (missingTower(x, y, z)) {
					continue;
				}
				if (x == 0 && z == 2 && y <= 6) {
					continue;
				}
				w.set(x, y, z, weathered(x, y, z));
			}
		}
	}

	private static boolean missingTower(int x, int y, int z) {
		if (x == 4 && y >= 3 && y <= 8 && z >= 1 && z <= 3) {
			return true;
		}
		if (y == 7 && z == 0 && x >= 1 && x <= 3) {
			return true;
		}
		if (y >= 9 && x == 0 && z == 2) {
			return true;
		}
		return false;
	}

	private static void placeBrokenCrenel(BlueprintNbtWriter w, int max) {
		for (int i = 0; i <= max; i++) {
			if (i % 2 == 0 && i != 4) {
				w.set(i, 11, 0, i == 2 ? COBBLE_WALL : STONE_BRICKS);
			}
			if (i == 0 || i == 2) {
				w.set(i, 11, max, STONE_BRICKS);
			}
			if (i == 1 || i == 3) {
				w.set(0, 11, i, COBBLE_WALL);
			}
		}
		w.set(2, 8, 0, BARS);
		w.set(4, 2, 2, BARS);
	}

	/**
	 * 7×6×3，与直城墙同模数：中段塌陷、青苔与缺口。
	 */
	private static BlueprintNbtWriter wall() {
		BlueprintNbtWriter w = new BlueprintNbtWriter(WALL_X, WALL_Y, WALL_Z);
		for (int x = 0; x < 7; x++) {
			int top = wallHeight(x);
			for (int y = 0; y <= top; y++) {
				for (int z = 0; z < 3; z++) {
					if (wallGap(x, y, z)) {
						continue;
					}
					w.set(x, y, z, weathered(x, y, z));
				}
			}
			if (top >= 4 && x % 2 == 0 && x != 2) {
				w.set(x, 5, 0, x == 6 ? COBBLE_WALL : weathered(x, 5, 0));
				if (x != 4) {
					w.set(x, 5, 2, weathered(x, 5, 2));
				}
			}
		}
		w.set(3, 2, 1, COBWEB);
		w.set(4, 1, 0, MOSS_CARPET);
		w.set(2, 3, 1, VINE, BlueprintNbtWriter.vine("north"));
		return w;
	}

	private static int wallHeight(int x) {
		if (x == 2 || x == 3) {
			return 1;
		}
		if (x == 4) {
			return 3;
		}
		return 4;
	}

	private static boolean wallGap(int x, int y, int z) {
		if (x == 5 && y >= 1 && y <= 2 && z == 1) {
			return true;
		}
		return x == 1 && y == 2 && z == 0;
	}

	/**
	 * 7×6×4，与城门同模数：一侧门垛坍塌。
	 */
	private static BlueprintNbtWriter gate() {
		BlueprintNbtWriter w = new BlueprintNbtWriter(GATE_X, GATE_Y, GATE_Z);
		for (int x = 0; x < 7; x++) {
			boolean collapsed = x >= 4;
			int top = collapsed ? (x == 6 ? 2 : 1) : 4;
			for (int y = 0; y <= top; y++) {
				for (int z = 0; z < 4; z++) {
					boolean doorway = (x == 2 || x == 3) && y <= 2;
					if (doorway || (collapsed && y >= 1 && z == 1)) {
						continue;
					}
					w.set(x, y, z, weathered(x, y, z));
				}
			}
			if (!collapsed && x % 2 == 0) {
				w.set(x, 5, 0, weathered(x, 5, 0));
				if (x != 2) {
					w.set(x, 5, 3, COBBLE_WALL);
				}
			}
		}
		w.set(5, 0, 1, MOSSY_COBBLE);
		w.set(5, 0, 2, CRACKED);
		w.set(6, 1, 0, COBBLE_WALL);
		w.set(3, 3, 1, COBWEB);
		w.set(1, 2, 1, VINE, BlueprintNbtWriter.vine("west"));
		w.set(4, 1, 0, MOSS_CARPET);
		w.set(2, 3, 0, BARS);
		return w;
	}

	/**
	 * 7×6×7，屋顶掀开、墙缺一面、熄灭营火。
	 */
	private static BlueprintNbtWriter hut() {
		BlueprintNbtWriter w = new BlueprintNbtWriter(HUT_X, HUT_Y, HUT_Z);
		int maxX = 6;
		int maxZ = 6;
		for (int x = 0; x <= maxX; x++) {
			for (int z = 0; z <= maxZ; z++) {
				if ((x == 5 && z == 5) || (x == 4 && z == 6)) {
					continue;
				}
				w.set(x, 0, z, (x + z) % 3 == 0 ? MOSSY_COBBLE : PLANKS);
			}
		}
		int[][] corners = {{0, 0}, {0, maxZ}, {maxX, 0}};
		for (int[] c : corners) {
			int top = c[0] == 0 && c[1] == maxZ ? 2 : 3;
			for (int y = 1; y <= top; y++) {
				w.set(c[0], y, c[1], LOG, BlueprintNbtWriter.axisY());
			}
		}
		for (int y = 1; y <= 3; y++) {
			for (int x = 1; x < maxX; x++) {
				if (y == 3 && x >= 4) {
					continue;
				}
				if (x == 3 && y <= 2) {
					continue;
				}
				w.set(x, y, 0, PLANKS);
			}
			for (int z = 1; z < maxZ; z++) {
				w.set(0, y, z, PLANKS);
				if (y == 1 && z >= 4) {
					continue;
				}
				if (z != 5) {
					w.set(maxX, y, z, y >= 2 && z >= 3 ? weathered(maxX, y, z) : PLANKS);
				}
			}
			for (int x = 1; x <= 3; x++) {
				w.set(x, y, maxZ, PLANKS);
			}
		}
		for (int z = 0; z <= 3; z++) {
			w.set(0, 4, z, STAIRS, BlueprintNbtWriter.stairs("east"));
			w.set(1, 4, z, PLANKS);
			if (z <= 2) {
				w.set(1, 5, z, STAIRS, BlueprintNbtWriter.stairs("east"));
				w.set(2, 5, z, PLANKS);
			}
		}
		w.set(2, 4, 0, STAIRS, BlueprintNbtWriter.stairs("east", "top"));
		w.set(5, 4, 1, STAIRS, BlueprintNbtWriter.stairs("west"));
		w.set(3, 1, 3, CAMPFIRE, BlueprintNbtWriter.campfire("north", false));
		w.set(1, 1, 1, COBWEB);
		w.set(5, 2, 2, COBWEB);
		w.set(2, 3, 5, COBWEB);
		w.set(1, 1, 5, MOSS_CARPET);
		w.set(4, 1, 2, MOSS_CARPET);
		w.set(1, 2, 4, VINE, BlueprintNbtWriter.vine("west"));
		w.set(1, 3, 4, VINE, BlueprintNbtWriter.vine("west"));
		w.set(4, 1, 1, FENCE);
		w.set(5, 1, 1, FENCE);
		return w;
	}

	private static String weathered(int x, int y, int z) {
		int h = Math.floorMod(x * 17 + y * 31 + z * 13, 5);
		return switch (h) {
			case 0 -> MOSSY_COBBLE;
			case 1 -> CRACKED;
			case 2 -> MOSSY_BRICKS;
			case 3 -> STONE_BRICKS;
			default -> COBBLE;
		};
	}

	private static boolean isCorner(int x, int z, int maxX, int maxZ) {
		return (x == 0 || x == maxX) && (z == 0 || z == maxZ);
	}
}
