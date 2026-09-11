package luowei.refugee.spawn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;

import luowei.refugee.Refugee;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.config.RefugeeConfig.ImmigrationTier;
import luowei.refugee.config.RefugeePlayDifficulty;
import luowei.refugee.interact.RosterService;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.settle.StandableFinder;
import luowei.refugee.special.SpecialRefugeeService;

/**
 * 每天在 {@link RefugeeConfig#immigrationEventDayTime}（默认 daytime 4000）对每个领土主体触发一波入境。
 * 档位人数区间抽取后再乘难度并向上取整。指令 force 跳过抽签与白天限制，仍仅在白名单维度生效。
 */
public final class RefugeeImmigration {
	private static final String LOG_PREFIX = "[refugee immigration]";
	private static final long DAYTIME_END = 12000L;

	private RefugeeImmigration() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RefugeeImmigration::onServerTick);
	}

	private static void onServerTick(MinecraftServer server) {
		ServerLevel clock = server.overworld();
		if (clock == null) {
			return;
		}
		int eventTime = RefugeeConfig.immigrationEventDayTime;
		if (eventTime < 0) {
			return;
		}
		long dayTime = clock.getDayTime();
		long day = Math.floorDiv(dayTime, 24000L);
		long timeOfDay = Math.floorMod(dayTime, 24000L);
		if (timeOfDay < eventTime) {
			return;
		}
		ImmigrationEventData data = ImmigrationEventData.get(server);
		if (data.lastFiredDay() >= day) {
			return;
		}
		data.markFired(day);
		List<UUID> subjects = PbsAdapter.pollEntities(server);
		int subjectCount = 0;
		for (UUID subjectId : subjects) {
			if (subjectId != null) {
				subjectCount++;
			}
		}
		log("event serverTick=" + server.getTickCount()
				+ " eventDayTime=" + eventTime
				+ " " + timeCtx(clock)
				+ " subjects=" + subjectCount
				+ (subjectCount == 0 ? " skipped=no_subjects" : ""));
		for (UUID subjectId : subjects) {
			if (subjectId == null) {
				continue;
			}
			tickSubject(server, subjectId);
		}
	}

	private static void tickSubject(MinecraftServer server, UUID subjectId) {
		boolean sawWhitelist = false;
		for (ServerLevel level : server.getAllLevels()) {
			if (!RefugeeConfig.isImmigrationDimension(level)) {
				continue;
			}
			sawWhitelist = true;
			ImmigrationResult result = tryImmigrate(level, subjectId, false);
			if (result.status() == ImmigrationResult.Status.SUCCESS) {
				return;
			}
			if (result.status() == ImmigrationResult.Status.SKIPPED_CHANCE) {
				return;
			}
			if (result.status() == ImmigrationResult.Status.SKIPPED_NIGHT) {
				continue;
			}
		}
		if (!sawWhitelist) {
			ServerLevel probe = server.overworld();
			logSkip(probe, subjectId, false, "dimension_not_whitelisted",
					"whitelist=" + whitelistText() + " sawWhitelistDim=false");
		}
	}

	/**
	 * @param force 为 true 时跳过抽签，立即尝试刷 1 人（指令用）
	 */
	public static ImmigrationResult tryImmigrate(ServerLevel level, UUID subjectId, boolean force) {
		if (level == null || subjectId == null) {
			log("skip reason=invalid_args levelNull=" + (level == null)
					+ " subjectNull=" + (subjectId == null)
					+ " force=" + force);
			return ImmigrationResult.fail(ImmigrationResult.Status.NO_TERRITORY);
		}
		if (!RefugeeConfig.isImmigrationDimension(level)) {
			logSkip(level, subjectId, force, "dimension_not_whitelisted",
					"whitelist=" + whitelistText());
			return ImmigrationResult.fail(ImmigrationResult.Status.SKIPPED_DIMENSION);
		}
		if (!force && !isDaytime(level)) {
			logSkip(level, subjectId, false, "night", timeCtx(level));
			return ImmigrationResult.fail(ImmigrationResult.Status.SKIPPED_NIGHT);
		}
		int wanted = 1;
		if (force) {
			log("check force=true skipChance=true " + subjectCtx(level.getServer(), subjectId)
					+ " " + timeCtx(level));
		} else {
			int owned = PbsAdapter.territoryCounts(level, subjectId).owned();
			ImmigrationTier tier = RefugeeConfig.immigrationTier(owned);
			double chance = tier == null ? 0.0 : tier.chance();
			double roll = level.random.nextDouble();
			boolean hit = chance > 0.0 && roll < chance;
			log("roll " + subjectCtx(level.getServer(), subjectId)
					+ " " + timeCtx(level)
					+ " owned=" + owned
					+ " " + formatTier(tier)
					+ " roll=" + roll
					+ " chance=" + chance
					+ " hit=" + hit);
			if (!hit) {
				logSkip(level, subjectId, false, "chance_miss",
						"owned=" + owned + " " + formatTier(tier) + " roll=" + roll + " chance=" + chance);
				return ImmigrationResult.fail(ImmigrationResult.Status.SKIPPED_CHANCE);
			}
			RefugeePlayDifficulty difficulty = RefugeePlayDifficulty.of(level);
			int minCount = tier == null ? 1 : tier.minCount();
			int maxCount = tier == null ? 1 : tier.maxCount();
			wanted = difficulty.rollArrivalCount(level.random, minCount, maxCount);
			log("arrival " + subjectCtx(level.getServer(), subjectId)
					+ " " + formatTier(tier)
					+ " difficulty=" + difficulty.name()
					+ " multiplier=" + difficulty.arrivalMultiplier()
					+ " wanted=" + wanted);
			if (wanted <= 0) {
				logSkip(level, subjectId, false, "arrival_zero", formatTier(tier));
				return ImmigrationResult.fail(ImmigrationResult.Status.SKIPPED_CHANCE);
			}
		}
		ImmigrationResult spawned = spawnFromLoadedCandidates(level, subjectId, force, wanted);
		if (spawned.status() == ImmigrationResult.Status.SUCCESS) {
			log("spawn " + subjectCtx(level.getServer(), subjectId)
					+ " " + timeCtx(level)
					+ " chunk=" + formatChunk(spawned.chunk())
					+ " pos=" + formatPos(spawned.pos())
					+ " wanted=" + wanted
					+ " spawned=" + spawned.count()
					+ " force=" + force
					+ " specialNpc=" + spawned.specialNpc());
		}
		return spawned;
	}

	private static ImmigrationResult spawnFromLoadedCandidates(
			ServerLevel level,
			UUID subjectId,
			boolean force,
			int wanted
	) {
		List<ChunkPos> candidates = new ArrayList<>(PbsAdapter.immigrationChunks(level, subjectId));
		if (candidates.isEmpty()) {
			logSkip(level, subjectId, force, "no_territory", "candidates=0 wanted=" + wanted);
			return ImmigrationResult.fail(ImmigrationResult.Status.NO_TERRITORY);
		}
		candidates = shuffled(candidates, level);
		int loaded = 0;
		int unloaded = 0;
		for (ChunkPos chunk : candidates) {
			if (!level.hasChunk(chunk.x, chunk.z)) {
				unloaded++;
				continue;
			}
			loaded++;
			ImmigrationResult spawned = spawnInChunk(level, subjectId, chunk, force, wanted);
			if (spawned.status() == ImmigrationResult.Status.SUCCESS) {
				return spawned;
			}
			if (spawned.status() == ImmigrationResult.Status.CREATE_FAILED) {
				return spawned;
			}
		}
		if (loaded == 0) {
			logSkip(level, subjectId, force, "chunk_unloaded",
					"candidates=" + candidates.size() + " tried=" + unloaded + " allUnloaded=true wanted=" + wanted);
			return ImmigrationResult.fail(ImmigrationResult.Status.CHUNK_UNLOADED);
		}
		logSkip(level, subjectId, force, "no_standable",
				"candidates=" + candidates.size() + " loaded=" + loaded + " unloaded=" + unloaded + " wanted=" + wanted);
		return ImmigrationResult.fail(ImmigrationResult.Status.NO_STANDABLE);
	}

	private static List<ChunkPos> shuffled(List<ChunkPos> candidates, ServerLevel level) {
		List<ChunkPos> copy = new ArrayList<>(candidates);
		for (int i = copy.size(); i > 1; i--) {
			Collections.swap(copy, i - 1, level.random.nextInt(i));
		}
		return copy;
	}

	private static ImmigrationResult spawnInChunk(
			ServerLevel level,
			UUID subjectId,
			ChunkPos chunk,
			boolean force,
			int wanted
	) {
		List<BlockPos> spots = StandableFinder.findInChunk(level, chunk, null, Math.max(1, wanted));
		if (spots.isEmpty()) {
			return ImmigrationResult.fail(ImmigrationResult.Status.NO_STANDABLE);
		}
		Villager first = null;
		BlockPos firstFeet = null;
		int spawned = 0;
		for (BlockPos feet : spots) {
			Villager villager = spawnOwned(level, subjectId, feet);
			if (villager == null) {
				if (spawned == 0) {
					logSkip(level, subjectId, force, "create_failed",
							"chunk=" + formatChunk(chunk) + " pos=" + formatPos(feet) + " wanted=" + wanted);
					return ImmigrationResult.fail(ImmigrationResult.Status.CREATE_FAILED);
				}
				break;
			}
			if (first == null) {
				first = villager;
				firstFeet = feet;
			}
			spawned++;
		}
		notifyArrival(level.getServer(), subjectId, spawned);
		String specialNpc = SpecialRefugeeService.onImmigrationSuccess(level, subjectId, firstFeet);
		return new ImmigrationResult(ImmigrationResult.Status.SUCCESS, first, firstFeet, chunk, specialNpc, spawned);
	}

	public static Villager spawnOwned(ServerLevel level, UUID subjectId, BlockPos feet) {
		Villager villager = EntityType.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (villager == null) {
			return null;
		}
		StandableFinder.snapToStandable(villager, level, feet, level.random.nextFloat() * 360.0f, 0.0f);
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.setSubjectId(subjectId);
		RefugeeAttachments.markDirty(villager, data);
		if (!level.addFreshEntity(villager)) {
			return null;
		}
		RosterService.registerOwnedIfPlayer(level.getServer(), subjectId, villager);
		return villager;
	}

	private static void notifyArrival(MinecraftServer server, UUID subjectId, int count) {
		if (server == null || subjectId == null || count <= 0) {
			return;
		}
		Component message = Component.translatable("message.refugee.immigration.arrived", count);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (subjectId.equals(player.getUUID()) || subjectId.equals(PbsAdapter.resolveSubject(player))) {
				player.sendSystemMessage(message);
			}
		}
	}

	private static void logSkip(ServerLevel level, UUID subjectId, boolean force, String reason, String extras) {
		log("skip reason=" + reason
				+ " " + subjectCtx(level == null ? null : level.getServer(), subjectId)
				+ (level == null ? "" : " " + timeCtx(level))
				+ " force=" + force
				+ (extras == null || extras.isBlank() ? "" : " " + extras));
	}

	private static void log(String message) {
		Refugee.LOGGER.debug("{} {}", LOG_PREFIX, message);
	}

	private static String subjectCtx(MinecraftServer server, UUID subjectId) {
		return "subject=" + subjectName(server, subjectId)
				+ " uuid=" + (subjectId == null ? "-" : subjectId)
				+ " player=" + onlinePlayerNames(server, subjectId);
	}

	private static String timeCtx(ServerLevel level) {
		long dayTime = level.getDayTime();
		return "dim=" + level.dimension().location()
				+ " day=" + Math.floorDiv(dayTime, 24000L)
				+ " dayTick=" + Math.floorMod(dayTime, 24000L)
				+ " dayTime=" + dayTime
				+ " gameTime=" + level.getGameTime()
				+ " serverTick=" + (level.getServer() == null ? 0 : level.getServer().getTickCount());
	}

	private static String subjectName(MinecraftServer server, UUID subjectId) {
		if (server == null || subjectId == null) {
			return "-";
		}
		String name = PbsAdapter.displayName(server, subjectId);
		return name == null || name.isBlank() ? "-" : name;
	}

	private static String onlinePlayerNames(MinecraftServer server, UUID subjectId) {
		if (server == null || subjectId == null) {
			return "-";
		}
		List<String> names = new ArrayList<>();
		ServerPlayer direct = server.getPlayerList().getPlayer(subjectId);
		if (direct != null) {
			names.add(direct.getGameProfile().getName());
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player == direct) {
				continue;
			}
			if (subjectId.equals(player.getUUID()) || subjectId.equals(PbsAdapter.resolveSubject(player))) {
				names.add(player.getGameProfile().getName());
			}
		}
		return names.isEmpty() ? "-" : String.join(",", names);
	}

	private static boolean isDaytime(ServerLevel level) {
		return Math.floorMod(level.getDayTime(), 24000L) < DAYTIME_END;
	}

	private static String formatTier(ImmigrationTier tier) {
		if (tier == null) {
			return "tier=none maxOwned=- chance=- minCount=- maxCount=-";
		}
		String maxOwned = tier.maxOwned() == Integer.MAX_VALUE ? "unbounded" : Integer.toString(tier.maxOwned());
		return "maxOwned=" + maxOwned
				+ " chance=" + tier.chance()
				+ " minCount=" + tier.minCount()
				+ " maxCount=" + tier.maxCount();
	}

	private static String whitelistText() {
		return RefugeeConfig.immigrationDimensionWhitelist.toString();
	}

	private static String formatPos(BlockPos pos) {
		return pos == null ? "-" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static String formatChunk(ChunkPos chunk) {
		return chunk == null ? "-" : chunk.x + "," + chunk.z;
	}

	public record ImmigrationResult(
			Status status,
			Villager villager,
			BlockPos pos,
			ChunkPos chunk,
			String specialNpc,
			int count
	) {
		public enum Status {
			SUCCESS,
			SKIPPED_CHANCE,
			SKIPPED_NIGHT,
			SKIPPED_DIMENSION,
			NO_TERRITORY,
			CHUNK_UNLOADED,
			NO_STANDABLE,
			CREATE_FAILED
		}

		public static ImmigrationResult fail(Status status) {
			return new ImmigrationResult(status, null, null, null, "none", 0);
		}
	}
}
