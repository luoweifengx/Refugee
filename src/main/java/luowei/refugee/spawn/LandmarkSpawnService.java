package luowei.refugee.spawn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import luowei.player_block_status.lib.chunk.ChunkState;
import luowei.refugee.Refugee;
import luowei.refugee.blueprint.BlueprintRegistry;
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
		ensureLoaded(
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
			BlockPos origin = placeIfSuitable(
					level,
					chunk,
					Refugee.id(LandmarkBlueprints.MAGE_TOWER),
					LandmarkBlueprints.MAGE_TOWER_X,
					LandmarkBlueprints.MAGE_TOWER_Z
			);
			if (origin != null) {
				data.markMageTower(origin);
				announce(level, "message.refugee.landmark.mage_tower", origin);
				Refugee.LOGGER.info("Placed mage tower at {} (chunk {})", format(origin), chunk.x + "," + chunk.z);
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
			BlockPos origin = placeIfSuitable(
					level,
					chunk,
					Refugee.id(LandmarkBlueprints.HELL_FURNACE),
					LandmarkBlueprints.HELL_FURNACE_X,
					LandmarkBlueprints.HELL_FURNACE_Z
			);
			if (origin != null) {
				data.markHellFurnace(origin);
				announce(level, "message.refugee.landmark.hell_furnace", origin);
				Refugee.LOGGER.info(
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

	private static BlockPos placeIfSuitable(
			ServerLevel level,
			ChunkPos chunk,
			ResourceLocation structureId,
			int sizeX,
			int sizeZ
	) {
		int originX = chunk.getMinBlockX() + 8 - sizeX / 2;
		int originZ = chunk.getMinBlockZ() + 8 - sizeZ / 2;
		ensureLoaded(level, new BlockPos(originX, 64, originZ), sizeX, sizeZ);
		int midX = originX + sizeX / 2;
		int midZ = originZ + sizeZ / 2;
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, midX, midZ);
		int originY = surface - 1;
		if (originY <= level.getMinY() + 1 || originY + 24 >= level.getMinY() + level.getHeight() - 2) {
			return null;
		}
		BlockPos origin = new BlockPos(originX, originY, originZ);
		if (!surfaceOk(level, origin, sizeX, sizeZ, surface)) {
			return null;
		}
		StructureTemplate template = BlueprintRegistry.get(structureId);
		if (template == null) {
			return null;
		}
		StructurePlaceSettings settings = new StructurePlaceSettings()
				.setIgnoreEntities(true)
				.setKnownShape(true);
		template.placeInWorld(level, origin, origin, settings, level.random, Block.UPDATE_CLIENTS);
		return origin;
	}

	private static boolean surfaceOk(ServerLevel level, BlockPos origin, int sizeX, int sizeZ, int centerY) {
		int[][] samples = {
				{sizeX / 2, sizeZ / 2},
				{0, 0},
				{sizeX - 1, 0},
				{0, sizeZ - 1},
				{sizeX - 1, sizeZ - 1}
		};
		for (int[] sample : samples) {
			int x = origin.getX() + sample[0];
			int z = origin.getZ() + sample[1];
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (Math.abs(y - centerY) > 3) {
				return false;
			}
			BlockPos top = new BlockPos(x, y, z);
			BlockPos ground = top.below();
			if (!level.getFluidState(top).isEmpty() || !level.getFluidState(ground).isEmpty()) {
				return false;
			}
			BlockState floor = level.getBlockState(ground);
			if (floor.isAir() || !floor.isFaceSturdy(level, ground, Direction.UP)) {
				return false;
			}
		}
		return true;
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

	private static void ensureLoaded(ServerLevel level, BlockPos origin, int sizeX, int sizeZ) {
		int minCx = origin.getX() >> 4;
		int maxCx = (origin.getX() + sizeX - 1) >> 4;
		int minCz = origin.getZ() >> 4;
		int maxCz = (origin.getZ() + sizeZ - 1) >> 4;
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				level.getChunk(cx, cz);
			}
		}
	}

	private static void announce(ServerLevel level, String key, BlockPos origin) {
		Component message = Component.translatable(key, origin.getX(), origin.getY(), origin.getZ());
		level.getServer().getPlayerList().broadcastSystemMessage(message, false);
	}

	private static String format(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}
}
