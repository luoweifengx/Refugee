package luowei.refugee.pbs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import luowei.player_block_status.lib.api.PlayerBlockStatusLib;
import luowei.player_block_status.lib.api.TerritoryQueries;
import luowei.player_block_status.lib.chunk.ChunkState;
import luowei.player_block_status.lib.org.OrganizationRecord;

/**
 * PBS 公开 API 适配：计分入口、领土查询、玩家/组织主体解析。
 * <p>
 * 村庄加分入口是 {@link PlayerBlockStatusLib#notifyTrackedBlockPlaced}（村民代放必须走此方法）。
 * 旗帜 / 号角 / 钟：PBS 无现成交互钩子，由本模组自行处理。
 */
public final class PbsAdapter {
	private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private PbsAdapter() {
	}

	/**
	 * 计分账户：在组织中则用组织 UUID，否则用玩家 UUID。
	 */
	public static UUID resolveSubject(MinecraftServer server, UUID playerId) {
		if (server == null || playerId == null) {
			return playerId;
		}
		return PlayerBlockStatusLib.queryPlayerOrganization(server, playerId).orElse(playerId);
	}

	public static UUID resolveSubject(ServerPlayer player) {
		return resolveSubject(player.level().getServer(), player.getUUID());
	}

	public static List<UUID> pollEntities(MinecraftServer server) {
		return PlayerBlockStatusLib.getEntityPollOrder(server);
	}

	public static TerritoryQueries.EntityTerritoryCounts territoryCounts(ServerLevel level, UUID subjectId) {
		return PlayerBlockStatusLib.queryEntityTerritoryCounts(level, subjectId);
	}

	public static TerritoryQueries.OrgTerritoryChunks territory(ServerLevel level, UUID subjectId) {
		return PlayerBlockStatusLib.queryOrgTerritory(level, subjectId);
	}

	public static Optional<UUID> occupyingSubject(ServerLevel level, ChunkPos chunkPos) {
		return PlayerBlockStatusLib.queryChunk(level, chunkPos).flatMap(view -> {
			if (view.getState() == null || !view.getState().isOccupiedFamily()) {
				return Optional.empty();
			}
			return Optional.ofNullable(view.getOccupyingOrg());
		});
	}

	public static Optional<ChunkState> chunkState(ServerLevel level, ChunkPos chunkPos) {
		return PlayerBlockStatusLib.queryChunkState(level, chunkPos);
	}

	public static List<ChunkPos> erodedChunks(ServerLevel level, UUID subjectId) {
		return PlayerBlockStatusLib.queryErodedChunks(level, subjectId);
	}

	public static int erodedChunkCount(ServerLevel level, UUID subjectId) {
		return PlayerBlockStatusLib.queryErodedChunkCount(level, subjectId);
	}

	public static List<BlockPos> erosionBlocks(ServerLevel level, ChunkPos chunkPos) {
		return PlayerBlockStatusLib.queryErosionBlocks(level, chunkPos);
	}

	public static boolean isErosionBlock(ServerLevel level, BlockPos pos) {
		return PlayerBlockStatusLib.isErosionBlock(level, pos);
	}

	public static List<ChunkPos> demonChunks(ServerLevel level) {
		return PlayerBlockStatusLib.queryDemonChunks(level);
	}

	public static boolean hasDemonChunks(ServerLevel level) {
		List<ChunkPos> chunks = demonChunks(level);
		return chunks != null && !chunks.isEmpty();
	}

	/**
	 * 占领+边界的平均中心区块；无所属格时 empty。
	 */
	public static Optional<ChunkPos> territoryCenter(ServerLevel level, UUID subjectId) {
		if (level == null || subjectId == null) {
			return Optional.empty();
		}
		return PlayerBlockStatusLib.queryTerritoryCentroid(level, subjectId)
				.map(TerritoryQueries.TerritoryCentroid::center);
	}

	/**
	 * 组织在线成员数；未入组则该玩家在线为 1。
	 */
	public static int onlinePlayerCount(MinecraftServer server, UUID subjectId) {
		if (server == null || subjectId == null) {
			return 0;
		}
		Set<UUID> members = new HashSet<>(organizationMembers(server, subjectId));
		organizationOwner(server, subjectId).ifPresent(members::add);
		if (members.isEmpty()) {
			return server.getPlayerList().getPlayer(subjectId) != null ? 1 : 0;
		}
		int count = 0;
		for (UUID memberId : members) {
			if (server.getPlayerList().getPlayer(memberId) != null) {
				count++;
			}
		}
		return count;
	}

	public static Map<ChunkState, List<ChunkPos>> chunksInRadius(
			ServerLevel level,
			ChunkPos center,
			int radiusChunks,
			ChunkState... states
	) {
		return PlayerBlockStatusLib.queryChunksInRadius(level, center, radiusChunks, states);
	}

	/**
	 * 与边界区块四邻接的占领区块（入境落点）。若没有内部占领，回退到边界区块。
	 */
	public static List<ChunkPos> immigrationChunks(ServerLevel level, UUID subjectId) {
		TerritoryQueries.OrgTerritoryChunks chunks = territory(level, subjectId);
		Set<Long> borders = new HashSet<>();
		for (ChunkPos border : chunks.border()) {
			borders.add(border.toLong());
		}
		List<ChunkPos> adjacentOccupied = new ArrayList<>();
		for (ChunkPos occupied : chunks.occupied()) {
			if (hasCardinalNeighbor(occupied, borders)) {
				adjacentOccupied.add(occupied);
			}
		}
		if (!adjacentOccupied.isEmpty()) {
			return adjacentOccupied;
		}
		return chunks.border();
	}

	/**
	 * 村民建筑加分：把该格记到主体名下。传入玩家 UUID 时 PBS 会解析组织；组织 UUID 原样入账。
	 */
	public static void notifyVillagerPlaced(ServerLevel level, BlockPos pos, UUID subjectId) {
		if (level == null || pos == null || subjectId == null) {
			return;
		}
		PlayerBlockStatusLib.notifyTrackedBlockPlaced(level, pos, subjectId);
	}

	public static String displayName(MinecraftServer server, UUID subjectId) {
		return PlayerBlockStatusLib.resolveEntityDisplayName(server, subjectId);
	}

	public static Optional<UUID> organizationOf(MinecraftServer server, UUID playerId) {
		if (server == null || playerId == null) {
			return Optional.empty();
		}
		return PlayerBlockStatusLib.queryPlayerOrganization(server, playerId);
	}

	/**
	 * 蓝图共享范围：本人 + 所在组织的主人与成员。
	 */
	public static Set<UUID> shareGroup(MinecraftServer server, UUID playerId) {
		Set<UUID> ids = new LinkedHashSet<>();
		if (playerId != null) {
			ids.add(playerId);
		}
		if (server == null || playerId == null) {
			return ids;
		}
		UUID orgId = organizationOf(server, playerId).orElse(null);
		if (orgId == null) {
			return ids;
		}
		ids.add(orgId);
		ids.addAll(organizationMembers(server, orgId));
		organizationOwner(server, orgId).ifPresent(ids::add);
		return ids;
	}

	public static Optional<UUID> organizationOwner(MinecraftServer server, UUID orgId) {
		if (server == null || orgId == null) {
			return Optional.empty();
		}
		return PlayerBlockStatusLib.queryOrganization(server, orgId).map(OrganizationRecord::owner);
	}

	public static Set<UUID> organizationMembers(MinecraftServer server, UUID orgId) {
		if (server == null || orgId == null) {
			return Set.of();
		}
		return PlayerBlockStatusLib.queryOrganization(server, orgId)
				.map(record -> Set.copyOf(record.members()))
				.orElse(Set.of());
	}

	private static boolean hasCardinalNeighbor(ChunkPos pos, Set<Long> keys) {
		for (int[] delta : CARDINAL) {
			if (keys.contains(ChunkPos.asLong(pos.x + delta[0], pos.z + delta[1]))) {
				return true;
			}
		}
		return false;
	}
}
