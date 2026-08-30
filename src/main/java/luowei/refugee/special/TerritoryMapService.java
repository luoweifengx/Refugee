package luowei.refugee.special;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import luowei.player_block_status.lib.api.TerritoryQueries.OrgTerritoryChunks;
import luowei.player_block_status.lib.chunk.ChunkState;
import luowei.refugee.pbs.PbsAdapter;

/**
 * 绘图师领地网格：用 PBS 占领/边界索引填己方格，外来格按区块缓存，避免每次开界面全图 queryChunk。
 */
public final class TerritoryMapService {
	public static final int DEFAULT_RADIUS = 10;
	public static final int MAX_RADIUS = 20;
	public static final byte UNCACHED = 0;
	public static final byte EMPTY = 1;
	public static final byte OCCUPIED = 2;
	public static final byte BORDER = 3;
	public static final byte OTHER = 4;

	private static final int CACHE_TTL_TICKS = 200;
	private static final Map<Long, CachedCell> FOREIGN_CELLS = new ConcurrentHashMap<>();

	private TerritoryMapService() {
	}

	public static int clampRadius(int radius) {
		if (radius <= 5) {
			return 5;
		}
		if (radius <= 10) {
			return 10;
		}
		if (radius <= 15) {
			return 15;
		}
		return MAX_RADIUS;
	}

	public static byte[] buildGrid(ServerLevel level, UUID subjectId, ChunkPos center, int radius) {
		radius = clampRadius(radius);
		int side = radius * 2 + 1;
		byte[] cells = new byte[side * side];
		if (level == null || subjectId == null || center == null) {
			return cells;
		}
		OrgTerritoryChunks ours = PbsAdapter.territory(level, subjectId);
		java.util.Set<Long> occupied = new java.util.HashSet<>();
		java.util.Set<Long> border = new java.util.HashSet<>();
		for (ChunkPos pos : ours.occupied()) {
			occupied.add(pos.toLong());
		}
		for (ChunkPos pos : ours.border()) {
			border.add(pos.toLong());
		}
		int i = 0;
		for (int dz = -radius; dz <= radius; dz++) {
			for (int dx = -radius; dx <= radius; dx++) {
				ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
				long key = pos.toLong();
				if (occupied.contains(key)) {
					cells[i++] = OCCUPIED;
				} else if (border.contains(key)) {
					cells[i++] = BORDER;
				} else {
					cells[i++] = foreignOrEmpty(level, pos, subjectId);
				}
			}
		}
		return cells;
	}

	private static byte foreignOrEmpty(ServerLevel level, ChunkPos pos, UUID subjectId) {
		long cacheKey = cellKey(level, pos);
		CachedCell cached = FOREIGN_CELLS.get(cacheKey);
		long now = level.getGameTime();
		if (cached != null && now - cached.gameTime() < CACHE_TTL_TICKS) {
			return cached.kind();
		}
		byte kind = queryForeign(level, pos, subjectId);
		FOREIGN_CELLS.put(cacheKey, new CachedCell(kind, now));
		if (FOREIGN_CELLS.size() > 8192) {
			FOREIGN_CELLS.entrySet().removeIf(entry -> now - entry.getValue().gameTime() >= CACHE_TTL_TICKS);
		}
		return kind;
	}

	private static byte queryForeign(ServerLevel level, ChunkPos pos, UUID subjectId) {
		var occupying = PbsAdapter.occupyingSubject(level, pos);
		if (occupying.isPresent()) {
			return occupying.get().equals(subjectId) ? OCCUPIED : OTHER;
		}
		ChunkState state = PbsAdapter.chunkState(level, pos).orElse(ChunkState.NATURAL);
		if (state.isOccupiedFamily()) {
			return OCCUPIED;
		}
		if (state.isDemon()) {
			return OTHER;
		}
		return EMPTY;
	}

	private static long cellKey(ServerLevel level, ChunkPos pos) {
		ResourceLocation dim = level.dimension().location();
		long dimHash = 31L * dim.getNamespace().hashCode() + dim.getPath().hashCode();
		return dimHash * 31L + pos.toLong();
	}

	private record CachedCell(byte kind, long gameTime) {
	}
}
