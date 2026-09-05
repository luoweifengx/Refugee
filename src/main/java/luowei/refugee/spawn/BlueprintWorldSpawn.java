package luowei.refugee.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import luowei.refugee.Refugee;
import luowei.refugee.blueprint.WorldgenBlueprints;

/**
 * 在地表放置蓝图模板：平坦、无液体、可站立地面。
 */
final class BlueprintWorldSpawn {
	private BlueprintWorldSpawn() {
	}

	static BlockPos placeIfSuitable(
			ServerLevel level,
			ChunkPos chunk,
			ResourceLocation structureId,
			int sizeX,
			int sizeZ
	) {
		return placeIfSuitable(level, chunk, structureId, sizeX, sizeZ, Rotation.NONE);
	}

	static BlockPos placeIfSuitable(
			ServerLevel level,
			ChunkPos chunk,
			ResourceLocation structureId,
			int sizeX,
			int sizeZ,
			Rotation rotation
	) {
		int originX = chunk.getMinBlockX() + 8 - sizeX / 2;
		int originZ = chunk.getMinBlockZ() + 8 - sizeZ / 2;
		long started = debugNanos();
		int forced = ensureLoaded(level, new BlockPos(originX, 64, originZ), sizeX, sizeZ);
		int midX = originX + sizeX / 2;
		int midZ = originZ + sizeZ / 2;
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, midX, midZ);
		int originY = surface - 1;
		if (originY <= level.getMinY() + 1 || originY + 24 >= level.getMinY() + level.getHeight() - 2) {
			logPlace(structureId, chunk, "skip-y", forced, started, null);
			return null;
		}
		BlockPos origin = new BlockPos(originX, originY, originZ);
		if (!surfaceOk(level, origin, sizeX, sizeZ, surface)) {
			logPlace(structureId, chunk, "skip-surface", forced, started, origin);
			return null;
		}
		StructureTemplate template = WorldgenBlueprints.get(
				structureId,
				level.registryAccess().lookupOrThrow(Registries.BLOCK)
		);
		if (template == null) {
			logPlace(structureId, chunk, "skip-template", forced, started, origin);
			return null;
		}
		StructurePlaceSettings settings = new StructurePlaceSettings()
				.setIgnoreEntities(true)
				.setKnownShape(true)
				.setRotation(rotation == null ? Rotation.NONE : rotation);
		template.placeInWorld(level, origin, origin, settings, level.random, Block.UPDATE_CLIENTS);
		logPlace(structureId, chunk, "placed", forced, started, origin);
		return origin;
	}

	static int ensureLoaded(ServerLevel level, BlockPos origin, int sizeX, int sizeZ) {
		int minCx = origin.getX() >> 4;
		int maxCx = (origin.getX() + sizeX - 1) >> 4;
		int minCz = origin.getZ() >> 4;
		int maxCz = (origin.getZ() + sizeZ - 1) >> 4;
		int forced = 0;
		long started = debugNanos();
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				boolean loaded = level.hasChunk(cx, cz);
				if (!loaded) {
					forced++;
				}
				level.getChunk(cx, cz);
			}
		}
		if (Refugee.LOGGER.isDebugEnabled() && forced > 0) {
			Refugee.LOGGER.debug(
					"[refugee blueprint-spawn] ensureLoaded origin={} size={}x{} forced={} {}ns",
					origin.toShortString(),
					sizeX,
					sizeZ,
					forced,
					elapsedNanos(started)
			);
		}
		return forced;
	}

	private static void logPlace(
			ResourceLocation structureId,
			ChunkPos chunk,
			String result,
			int forced,
			long started,
			BlockPos origin
	) {
		if (!Refugee.LOGGER.isDebugEnabled()) {
			return;
		}
		Refugee.LOGGER.debug(
				"[refugee blueprint-spawn] {} id={} chunk={} origin={} forced={} {}ns",
				result,
				structureId,
				chunk.x + "," + chunk.z,
				origin == null ? "none" : origin.toShortString(),
				forced,
				elapsedNanos(started)
		);
	}

	private static long debugNanos() {
		return Refugee.LOGGER.isDebugEnabled() ? System.nanoTime() : 0L;
	}

	private static long elapsedNanos(long started) {
		return started == 0L ? 0L : System.nanoTime() - started;
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
}
