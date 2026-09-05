package luowei.refugee.spawn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import luowei.player_block_status.lib.chunk.ChunkState;
import luowei.refugee.Refugee;
import luowei.refugee.blueprint.LandmarkBlueprints;
import luowei.refugee.compat.FoodCompat;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.settle.StandableFinder;

/**
 * 世界地标：Food 仪式完成后在出生点 10–20 区块地表刷法师塔；
 * 恶魔区块出现后在其 10 区块内刷地狱熔炉（优先自然区块）。
 */
public final class LandmarkSpawnService {
	private static final int INTERVAL_TICKS = 20;
	private static final int MAGE_MIN_CHUNKS = 10;
	private static final int MAGE_MAX_CHUNKS = 20;
	private static final int FURNACE_RADIUS_CHUNKS = 10;
	private static final int ATTEMPTS_PER_TICK = 12;

	private LandmarkSpawnService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LandmarkSpawnService::onServerTick);
	}

	public static BlockPos enchanterFeet(ServerLevel level) {
		if (level == null) {
			return null;
		}
		LandmarkData data = LandmarkData.get(level.getServer());
		if (!data.isMageTowerPlaced() || data.mageTowerOrigin() == null) {
			return null;
		}
		BlockPos origin = data.mageTowerOrigin();
		BlueprintWorldSpawn.ensureLoaded(
				level,
				origin,
				LandmarkBlueprints.MAGE_TOWER_X,
				LandmarkBlueprints.MAGE_TOWER_Z
		);
		BlockPos feet = origin.offset(LandmarkBlueprints.ENCHANTER_LOCAL_FEET);
		if (StandableFinder.isStandable(level, feet)) {
			return feet;
		}
		List<BlockPos> nearby = StandableFinder.findStandable(level, feet, Set.of(), 1);
		return nearby.isEmpty() ? null : nearby.getFirst();
	}

	private static void onServerTick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL_TICKS != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return;
		}
		LandmarkData data = LandmarkData.get(server);
		tryPlaceMageTower(overworld, data);
		tryPlaceHellFurnace(overworld, data);
	}

	private static void tryPlaceMageTower(ServerLevel level, LandmarkData data) {
		if (data.isMageTowerPlaced() || !FoodCompat.isLoaded() || !FoodCompat.isRitualCompleted(level.getServer())) {
			return;
		}
		ChunkPos spawn = new ChunkPos(level.getSharedSpawnPos());
		List<ChunkPos> ring = chebyshevRing(spawn, MAGE_MIN_CHUNKS, MAGE_MAX_CHUNKS);
		Collections.shuffle(ring, new java.util.Random(level.random.nextLong()));
		int tried = 0;
		for (ChunkPos chunk : ring) {
			if (tried++ >= ATTEMPTS_PER_TICK) {
				return;
			}
			BlockPos origin = BlueprintWorldSpawn.placeIfSuitable(
					level,
					chunk,
					Refugee.id(LandmarkBlueprints.MAGE_TOWER),
					LandmarkBlueprints.MAGE_TOWER_X,
					LandmarkBlueprints.MAGE_TOWER_Z
			);
			if (origin != null) {
				data.markMageTower(origin);
				announce(level, "message.refugee.landmark.mage_tower", origin);
				Refugee.LOGGER.debug("Placed mage tower at {} (chunk {})", format(origin), chunk.x + "," + chunk.z);
				return;
			}
		}
	}

	private static void tryPlaceHellFurnace(ServerLevel level, LandmarkData data) {
		if (data.isHellFurnacePlaced()) {
			return;
		}
		List<ChunkPos> demons = PbsAdapter.demonChunks(level);
		if (demons.isEmpty()) {
			return;
		}
		ChunkPos anchor = demons.getFirst();
		List<ChunkPos> candidates = furnaceCandidates(level, anchor);
		int tried = 0;
		for (ChunkPos chunk : candidates) {
			if (tried++ >= ATTEMPTS_PER_TICK) {
				return;
			}
			BlockPos origin = BlueprintWorldSpawn.placeIfSuitable(
					level,
					chunk,
					Refugee.id(LandmarkBlueprints.HELL_FURNACE),
					LandmarkBlueprints.HELL_FURNACE_X,
					LandmarkBlueprints.HELL_FURNACE_Z
			);
			if (origin != null) {
				data.markHellFurnace(origin);
				announce(level, "message.refugee.landmark.hell_furnace", origin);
				Refugee.LOGGER.debug(
						"Placed hell furnace at {} near demon chunk {}",
						format(origin),
						anchor.x + "," + anchor.z
				);
				return;
			}
		}
	}

	private static List<ChunkPos> furnaceCandidates(ServerLevel level, ChunkPos demon) {
		Map<ChunkState, List<ChunkPos>> grouped = PbsAdapter.chunksInRadius(
				level,
				demon,
				FURNACE_RADIUS_CHUNKS,
				ChunkState.NATURAL,
				ChunkState.HOSTILE_BORDER,
				ChunkState.SAFE,
				ChunkState.BORDER,
				ChunkState.OCCUPIED
		);
		List<ChunkPos> ordered = new ArrayList<>();
		addFiltered(ordered, grouped.get(ChunkState.NATURAL), demon);
		addFiltered(ordered, grouped.get(ChunkState.HOSTILE_BORDER), demon);
		addFiltered(ordered, grouped.get(ChunkState.SAFE), demon);
		addFiltered(ordered, grouped.get(ChunkState.BORDER), demon);
		addFiltered(ordered, grouped.get(ChunkState.OCCUPIED), demon);
		return ordered;
	}

	private static void addFiltered(List<ChunkPos> into, List<ChunkPos> source, ChunkPos skip) {
		if (source == null) {
			return;
		}
		for (ChunkPos chunk : source) {
			if (chunk != null && !chunk.equals(skip)) {
				into.add(chunk);
			}
		}
	}

	private static List<ChunkPos> chebyshevRing(ChunkPos center, int min, int max) {
		List<ChunkPos> result = new ArrayList<>();
		for (int dx = -max; dx <= max; dx++) {
			for (int dz = -max; dz <= max; dz++) {
				int cheb = Math.max(Math.abs(dx), Math.abs(dz));
				if (cheb >= min && cheb <= max) {
					result.add(new ChunkPos(center.x + dx, center.z + dz));
				}
			}
		}
		return result;
	}

	private static void announce(ServerLevel level, String key, BlockPos origin) {
		Component message = Component.translatable(key, origin.getX(), origin.getY(), origin.getZ());
		level.getServer().getPlayerList().broadcastSystemMessage(message, false);
	}

	private static String format(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}
}
