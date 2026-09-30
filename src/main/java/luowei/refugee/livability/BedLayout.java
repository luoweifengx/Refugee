package luowei.refugee.livability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import luowei.refugee.ai.BedClaim;

/**
 * 每张床头记下密度和间距。只在放下或拆掉时改范围内的床，不向外连锁。
 * 间距是最近另一张床的切比雪夫距离减 1；范围内没有别的床时记为 3，舒适按「3 以上」算。
 */
public final class BedLayout extends SavedData {
	private static final String DATA_ID = "refugee_bed_layout";
	/** 没有邻居时的间距，舒适表里走「3 以上」。 */
	public static final int GAP_ALONE = 3;

	private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("dimension").forGetter(Entry::dimension),
			Codec.INT.fieldOf("x").forGetter(Entry::x),
			Codec.INT.fieldOf("y").forGetter(Entry::y),
			Codec.INT.fieldOf("z").forGetter(Entry::z),
			Codec.INT.fieldOf("density").forGetter(Entry::density),
			Codec.INT.fieldOf("gap").forGetter(Entry::gap)
	).apply(instance, Entry::new));

	public static final Codec<BedLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ENTRY_CODEC.listOf().optionalFieldOf("beds", List.of()).forGetter(BedLayout::entries)
	).apply(instance, BedLayout::fromCodec));

	public static final SavedDataType<BedLayout> TYPE = new SavedDataType<>(
			DATA_ID,
			context -> new BedLayout(),
			context -> BedLayout.CODEC,
			null
	);

	private final Map<String, Stats> beds = new HashMap<>();

	public BedLayout() {
	}

	public static BedLayout get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public record Stats(int density, int gap) {
	}

	public static void onBlockReplaced(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
		if (level == null || pos == null || oldState == null || newState == null) {
			return;
		}
		boolean wasHead = isHead(oldState);
		boolean nowHead = isHead(newState);
		if (!wasHead && nowHead) {
			place(level, pos.immutable());
		} else if (wasHead && !nowHead) {
			remove(level, pos.immutable());
		}
	}

	/** 没有记录时按当前周围的床量一次再记下。 */
	public static Stats stats(ServerLevel level, BlockPos head) {
		if (level == null || head == null || !isHead(level.getBlockState(head))) {
			return new Stats(1, GAP_ALONE);
		}
		BlockPos bed = head.immutable();
		BedLayout data = get(level.getServer());
		Stats stored = data.beds.get(key(level.dimension(), bed));
		if (stored != null) {
			return stored;
		}
		Stats measured = measure(level, bed, null);
		data.beds.put(key(level.dimension(), bed), measured);
		data.setDirty();
		return measured;
	}

	private static void place(ServerLevel level, BlockPos head) {
		BedLayout data = get(level.getServer());
		String mapKey = key(level.dimension(), head);
		if (data.beds.containsKey(mapKey)) {
			return;
		}
		int radius = radius();
		List<BlockPos> neighbors = headsWithin(level, head, radius);
		int density = 1;
		int nearest = Integer.MAX_VALUE;
		for (BlockPos neighbor : neighbors) {
			Stats stats = data.ensure(level, neighbor, head);
			int dist = chebyshev(head, neighbor);
			density++;
			int neighborGap = Math.max(0, dist - 1);
			data.beds.put(key(level.dimension(), neighbor), new Stats(stats.density + 1, Math.min(stats.gap, neighborGap)));
			nearest = Math.min(nearest, dist);
		}
		int gap = nearest == Integer.MAX_VALUE ? GAP_ALONE : Math.max(0, nearest - 1);
		data.beds.put(mapKey, new Stats(Math.max(1, density), gap));
		data.setDirty();
	}

	private static void remove(ServerLevel level, BlockPos head) {
		BedLayout data = get(level.getServer());
		data.beds.remove(key(level.dimension(), head));
		int radius = radius();
		for (BlockPos neighbor : headsWithin(level, head, radius)) {
			String neighborKey = key(level.dimension(), neighbor);
			Stats stats = data.beds.get(neighborKey);
			if (stats == null) {
				data.beds.put(neighborKey, measure(level, neighbor, null));
				continue;
			}
			int density = Math.max(1, stats.density - 1);
			data.beds.put(neighborKey, new Stats(density, nearestGap(level, neighbor, radius)));
		}
		data.setDirty();
	}

	/** 周围已有记录就用记录；没有就按除开 {@code ignore} 之外的床量一次。 */
	private Stats ensure(ServerLevel level, BlockPos head, BlockPos ignore) {
		String mapKey = key(level.dimension(), head);
		Stats stored = beds.get(mapKey);
		if (stored != null) {
			return stored;
		}
		Stats measured = measure(level, head, ignore);
		beds.put(mapKey, measured);
		return measured;
	}

	private static Stats measure(ServerLevel level, BlockPos head, BlockPos ignore) {
		int radius = radius();
		int density = 1;
		int nearest = Integer.MAX_VALUE;
		for (BlockPos neighbor : headsWithin(level, head, radius)) {
			if (neighbor.equals(ignore)) {
				continue;
			}
			density++;
			nearest = Math.min(nearest, chebyshev(head, neighbor));
		}
		int gap = nearest == Integer.MAX_VALUE ? GAP_ALONE : Math.max(0, nearest - 1);
		return new Stats(Math.max(1, density), gap);
	}

	private static int nearestGap(ServerLevel level, BlockPos head, int radius) {
		int nearest = Integer.MAX_VALUE;
		for (BlockPos neighbor : headsWithin(level, head, radius)) {
			nearest = Math.min(nearest, chebyshev(head, neighbor));
		}
		return nearest == Integer.MAX_VALUE ? GAP_ALONE : Math.max(0, nearest - 1);
	}

	private static List<BlockPos> headsWithin(ServerLevel level, BlockPos origin, int radius) {
		List<BlockPos> found = new ArrayList<>();
		BlockPos.betweenClosedStream(
				origin.offset(-radius, -radius, -radius),
				origin.offset(radius, radius, radius)
		).forEach(pos -> {
			if (pos.equals(origin) || chebyshev(origin, pos) > radius) {
				return;
			}
			if (!level.isLoaded(pos)) {
				return;
			}
			if (isHead(level.getBlockState(pos))) {
				found.add(pos.immutable());
			}
		});
		return found;
	}

	private static boolean isHead(BlockState state) {
		return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
	}

	private static int radius() {
		return Math.max(1, LivabilityRules.CURRENT.bedLinkRadius);
	}

	private static int chebyshev(BlockPos a, BlockPos b) {
		return Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
	}

	private static BedLayout fromCodec(List<Entry> entries) {
		BedLayout data = new BedLayout();
		if (entries == null) {
			return data;
		}
		for (Entry entry : entries) {
			if (entry == null || entry.dimension == null || entry.dimension.isBlank()) {
				continue;
			}
			data.beds.put(
					entry.dimension + "|" + entry.x + "|" + entry.y + "|" + entry.z,
					new Stats(Math.max(1, entry.density), Math.max(0, entry.gap))
			);
		}
		return data;
	}

	private List<Entry> entries() {
		List<Entry> list = new ArrayList<>();
		for (Map.Entry<String, Stats> entry : beds.entrySet()) {
			String[] parts = entry.getKey().split("\\|");
			if (parts.length < 4) {
				continue;
			}
			Stats stats = entry.getValue();
			list.add(new Entry(
					parts[0],
					Integer.parseInt(parts[1]),
					Integer.parseInt(parts[2]),
					Integer.parseInt(parts[3]),
					stats.density,
					stats.gap
			));
		}
		return list;
	}

	private static String key(ResourceKey<Level> dimension, BlockPos pos) {
		BlockPos head = pos.immutable();
		return dimension.location() + "|" + head.getX() + "|" + head.getY() + "|" + head.getZ();
	}

	/** 调用方传入的位置可能是床尾，先归一到床头再查。 */
	public static Stats statsAt(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return new Stats(1, GAP_ALONE);
		}
		return stats(level, BedClaim.head(level, pos));
	}

	private record Entry(String dimension, int x, int y, int z, int density, int gap) {
	}
}
