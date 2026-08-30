package luowei.refugee.blueprint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

/**
 * 写出原版结构模板 NBT：size/pos 为 Int 列表（不是 IntArray）。
 */
public final class BlueprintNbtWriter {
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final String[][][] names;
	private final CompoundTag[][][] properties;

	public BlueprintNbtWriter(int sizeX, int sizeY, int sizeZ) {
		if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
			throw new IllegalArgumentException("size must be positive");
		}
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.names = new String[sizeX][sizeY][sizeZ];
		this.properties = new CompoundTag[sizeX][sizeY][sizeZ];
	}

	public boolean inBounds(int x, int y, int z) {
		return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
	}

	public void set(int x, int y, int z, String name) {
		set(x, y, z, name, null);
	}

	public void set(int x, int y, int z, String name, CompoundTag props) {
		if (!inBounds(x, y, z)) {
			throw new IllegalArgumentException("out of bounds: " + x + "," + y + "," + z);
		}
		this.names[x][y][z] = normalize(name);
		this.properties[x][y][z] = props == null || props.isEmpty() ? null : props.copy();
	}

	public void fill(int x0, int y0, int z0, int x1, int y1, int z1, String name) {
		fill(x0, y0, z0, x1, y1, z1, name, null);
	}

	public void fill(int x0, int y0, int z0, int x1, int y1, int z1, String name, CompoundTag props) {
		int minX = Math.min(x0, x1);
		int maxX = Math.max(x0, x1);
		int minY = Math.min(y0, y1);
		int maxY = Math.max(y0, y1);
		int minZ = Math.min(z0, z1);
		int maxZ = Math.max(z0, z1);
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					set(x, y, z, name, props);
				}
			}
		}
	}

	public boolean isEmpty() {
		for (int x = 0; x < sizeX; x++) {
			for (int y = 0; y < sizeY; y++) {
				for (int z = 0; z < sizeZ; z++) {
					if (names[x][y][z] != null) {
						return false;
					}
				}
			}
		}
		return true;
	}

	public void write(Path path) throws IOException {
		write(path, true);
	}

	public void write(Path path, boolean rewriteConnections) throws IOException {
		if (rewriteConnections) {
			fillConnections();
		}
		Map<String, Integer> indexByKey = new LinkedHashMap<>();
		ListTag palette = new ListTag();
		ListTag blocks = new ListTag();
		for (int y = 0; y < sizeY; y++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					String name = names[x][y][z];
					if (name == null) {
						continue;
					}
					CompoundTag props = properties[x][y][z];
					String key = paletteKey(name, props);
					Integer state = indexByKey.get(key);
					if (state == null) {
						state = palette.size();
						indexByKey.put(key, state);
						CompoundTag entry = new CompoundTag();
						entry.putString("Name", name);
						if (props != null && !props.isEmpty()) {
							entry.put("Properties", props.copy());
						}
						palette.add(entry);
					}
					CompoundTag block = new CompoundTag();
					block.put("pos", intList(x, y, z));
					block.putInt("state", state);
					blocks.add(block);
				}
			}
		}
		CompoundTag nbt = new CompoundTag();
		nbt.put("size", intList(sizeX, sizeY, sizeZ));
		nbt.put("palette", palette);
		nbt.put("blocks", blocks);
		nbt.put("entities", new ListTag());
		nbt.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		NbtIo.writeCompressed(nbt, path);
	}

	public static CompoundTag props(String... pairs) {
		if ((pairs.length & 1) != 0) {
			throw new IllegalArgumentException("properties must be key/value pairs");
		}
		CompoundTag tag = new CompoundTag();
		for (int i = 0; i < pairs.length; i += 2) {
			tag.putString(pairs[i], pairs[i + 1]);
		}
		return tag;
	}

	public static CompoundTag axis(String axis) {
		return props("axis", axis);
	}

	public static CompoundTag axisY() {
		return axis("y");
	}

	public static CompoundTag stairs(String facing) {
		return props("facing", facing, "half", "bottom", "shape", "straight", "waterlogged", "false");
	}

	public static CompoundTag slabBottom() {
		return props("type", "bottom", "waterlogged", "false");
	}

	public static CompoundTag doorLower(String facing, String hinge) {
		return props("facing", facing, "half", "lower", "hinge", hinge, "open", "false", "powered", "false");
	}

	public static CompoundTag doorUpper(String facing, String hinge) {
		return props("facing", facing, "half", "upper", "hinge", hinge, "open", "false", "powered", "false");
	}

	public static CompoundTag ladder(String facing) {
		return props("facing", facing, "waterlogged", "false");
	}

	public static CompoundTag wallTorch(String facing) {
		return props("facing", facing);
	}

	public static CompoundTag lantern(boolean hanging) {
		return props("hanging", hanging ? "true" : "false");
	}

	public static CompoundTag furnace(String facing, boolean lit) {
		return props("facing", facing, "lit", lit ? "true" : "false");
	}

	public static CompoundTag campfire(String facing, boolean lit) {
		return props("facing", facing, "lit", lit ? "true" : "false", "signal_fire", "false", "waterlogged", "false");
	}

	public static CompoundTag lectern(String facing) {
		return props("facing", facing, "has_book", "true", "powered", "false");
	}

	public static CompoundTag facing(String facing) {
		return props("facing", facing);
	}

	public static CompoundTag slab(String type) {
		return props("type", type, "waterlogged", "false");
	}

	/** Vanilla StructureTemplate 的 size/pos：List of Int，不是 IntArray。 */
	public static ListTag intList(int... values) {
		ListTag list = new ListTag();
		for (int v : values) {
			list.add(IntTag.valueOf(v));
		}
		return list;
	}

	private void fillConnections() {
		for (int x = 0; x < sizeX; x++) {
			for (int y = 0; y < sizeY; y++) {
				for (int z = 0; z < sizeZ; z++) {
					String name = names[x][y][z];
					if (name == null) {
						continue;
					}
					if (isPaneLike(name) || isFenceLike(name)) {
						CompoundTag p = properties[x][y][z] == null ? new CompoundTag() : properties[x][y][z].copy();
						p.putString("north", bool(connectsSide(x, y, z - 1, name)));
						p.putString("south", bool(connectsSide(x, y, z + 1, name)));
						p.putString("west", bool(connectsSide(x - 1, y, z, name)));
						p.putString("east", bool(connectsSide(x + 1, y, z, name)));
						p.putString("waterlogged", "false");
						properties[x][y][z] = p;
					} else if (isWallLike(name)) {
						CompoundTag p = properties[x][y][z] == null ? new CompoundTag() : properties[x][y][z].copy();
						p.putString("north", wallHeight(x, y, z - 1));
						p.putString("south", wallHeight(x, y, z + 1));
						p.putString("west", wallHeight(x - 1, y, z));
						p.putString("east", wallHeight(x + 1, y, z));
						p.putString("up", "true");
						p.putString("waterlogged", "false");
						properties[x][y][z] = p;
					}
				}
			}
		}
	}

	private boolean connectsSide(int x, int y, int z, String self) {
		if (!inBounds(x, y, z) || names[x][y][z] == null) {
			return false;
		}
		String n = names[x][y][z];
		if (n.equals(self)) {
			return true;
		}
		return isConnectSolid(n);
	}

	private String wallHeight(int x, int y, int z) {
		if (!inBounds(x, y, z) || names[x][y][z] == null) {
			return "none";
		}
		String n = names[x][y][z];
		if (isWallLike(n) || isConnectSolid(n)) {
			return "tall";
		}
		return "none";
	}

	private static boolean isPaneLike(String name) {
		return name.equals("minecraft:glass_pane")
				|| name.equals("minecraft:iron_bars")
				|| name.endsWith("_stained_glass_pane");
	}

	private static boolean isFenceLike(String name) {
		return name.endsWith("_fence") || name.equals("minecraft:nether_brick_fence");
	}

	private static boolean isWallLike(String name) {
		return name.endsWith("_wall");
	}

	private static boolean isConnectSolid(String name) {
		if (name.contains("torch")
				|| name.contains("lantern")
				|| name.contains("campfire")
				|| name.contains("carpet")
				|| name.contains("door")
				|| name.contains("trapdoor")
				|| name.contains("sign")
				|| name.equals("minecraft:ladder")
				|| name.equals("minecraft:chain")
				|| name.equals("minecraft:lectern")
				|| name.equals("minecraft:enchanting_table")
				|| name.equals("minecraft:brewing_stand")
				|| name.equals("minecraft:lightning_rod")
				|| isPaneLike(name)
				|| isFenceLike(name)
				|| name.endsWith("_slab")) {
			return false;
		}
		return true;
	}

	private static String bool(boolean value) {
		return value ? "true" : "false";
	}

	private static String normalize(String name) {
		return name.indexOf(':') >= 0 ? name : "minecraft:" + name;
	}

	private static String paletteKey(String name, CompoundTag props) {
		return props == null || props.isEmpty() ? name : name + props;
	}
}
