package luowei.refugee.spawn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import luowei.refugee.interact.RosterService;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.settle.StandableFinder;
import luowei.refugee.special.SpecialRefugeeService;

/**
 * 黎明按持有区块定当日配额，白天每 {@link RefugeeConfig#immigrationIntervalTicks}
 * 在白名单维度滴入 1 人。指令 force 跳过白天与 P日，仍仅在白名单维度生效。
 */
public final class RefugeeImmigration {
	private static final String LOG_PREFIX = "[refugee immigration]";
	private static final Map<UUID, Long> quotaDayBySubject = new HashMap<>();
	private static final Map<UUID, Integer> remainingQuotaBySubject = new HashMap<>();
	private static final Map<UUID, Integer> dailyQuotaBySubject = new HashMap<>();

	private RefugeeImmigration() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RefugeeImmigration::onServerTick);
	}

	private static void onServerTick(MinecraftServer server) {
		int interval = RefugeeConfig.immigrationIntervalTicks;
		if (interval <= 0 || server.getTickCount() % interval != 0) {
			return;
		}
		List<UUID> subjects = PbsAdapter.pollEntities(server);
		int subjectCount = 0;
		for (UUID subjectId : subjects) {
			if (subjectId != null) {
				subjectCount++;
			}
		}
		log("interval serverTick=" + server.getTickCount()
				+ " intervalTicks=" + interval
				+ " subjects=" + subjectCount
				+ (subjectCount == 0 ? " skipped=no_subjects" : ""));
		for (UUID subjectId : subjects) {
			if (subjectId == null) {
				continue;
			}
			dripSubject(server, subjectId);
		}
	}

	private static void dripSubject(MinecraftServer server, UUID subjectId) {
		boolean sawWhitelist = false;
		for (ServerLevel level : server.getAllLevels()) {
			if (!RefugeeConfig.isImmigrationDimension(level)) {
				continue;
			}
			sawWhitelist = true;
			if (!level.isBrightOutside()) {
				logSkip(level, subjectId, false, "not_daytime", "isBrightOutside=false");
				continue;
			}
			ImmigrationResult result = tryImmigrate(level, subjectId, false);
			if (result.status() == ImmigrationResult.Status.SUCCESS) {
				return;
			}
			if (result.status() == ImmigrationResult.Status.SKIPPED_CHANCE) {
				return;
			}
		}
		if (!sawWhitelist) {
			ServerLevel probe = server.overworld();
			logSkip(probe, subjectId, false, "dimension_not_whitelisted",
					"whitelist=" + whitelistText() + " sawWhitelistDim=false");
		}
	}

	/**
	 * @param force 为 true 时跳过白天与当日 P日配额，立即尝试刷 1 人（指令用）
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
		if (force) {
			log("check force=true skipDay=true skipPDay=true " + subjectCtx(level.getServer(), subjectId)
					+ " " + timeCtx(level));
		} else {
			if (!level.isBrightOutside()) {
				logSkip(level, subjectId, false, "not_daytime", "isBrightOutside=false");
				return ImmigrationResult.fail(ImmigrationResult.Status.SKIPPED_CHANCE);
			}
			ensureDailyQuota(level, subjectId);
			int remaining = remainingQuotaBySubject.getOrDefault(subjectId, 0);
			int dailyQuota = dailyQuotaBySubject.getOrDefault(subjectId, 0);
			if (remaining <= 0) {
				String reason = dailyQuota <= 0 ? "quota_miss" : "quota_exhausted";
				logSkip(level, subjectId, false, reason,
						"remaining=" + remaining + " dailyQuota=" + dailyQuota);
				return ImmigrationResult.fail(ImmigrationResult.Status.SKIPPED_CHANCE);
			}
		}
		int remainingBefore = remainingQuotaBySubject.getOrDefault(subjectId, 0);
		int dailyQuota = dailyQuotaBySubject.getOrDefault(subjectId, remainingBefore);
		ImmigrationResult spawned = spawnFromLoadedCandidates(level, subjectId, force);
		if (spawned.status() == ImmigrationResult.Status.SUCCESS) {
			int remainingAfter = remainingBefore;
			if (!force) {
				remainingAfter = Math.max(0, remainingBefore - 1);
				remainingQuotaBySubject.put(subjectId, remainingAfter);
			}
			int dripIndex = (!force && dailyQuota > 0) ? (dailyQuota - remainingBefore + 1) : 0;
			log("spawn " + subjectCtx(level.getServer(), subjectId)
					+ " " + timeCtx(level)
					+ " chunk=" + formatChunk(spawned.chunk())
					+ " pos=" + formatPos(spawned.pos())
					+ " dailyQuota=" + dailyQuota
					+ " remainingBefore=" + remainingBefore
					+ " remainingAfter=" + remainingAfter
					+ " drip=" + (force ? "force" : dripIndex + "/" + dailyQuota)
					+ " force=" + force
					+ " specialNpc=" + spawned.specialNpc());
		}
		return spawned;
	}

	private static void ensureDailyQuota(ServerLevel level, UUID subjectId) {
		long dayId = Math.floorDiv(level.getDayTime(), 24000L);
		Long rolledDay = quotaDayBySubject.get(subjectId);
		if (rolledDay != null && rolledDay == dayId) {
			int remaining = remainingQuotaBySubject.getOrDefault(subjectId, 0);
			if (remaining > 0) {
				log("quota-existing " + subjectCtx(level.getServer(), subjectId)
						+ " " + timeCtx(level)
						+ " remaining=" + remaining
						+ " dailyQuota=" + dailyQuotaBySubject.getOrDefault(subjectId, 0));
			}
			return;
		}
		int owned = PbsAdapter.territoryCounts(level, subjectId).owned();
		ImmigrationTier tier = RefugeeConfig.immigrationTier(owned);
		double roll = level.random.nextDouble();
		double pDay = tier == null ? 0.0 : tier.dayChance();
		boolean hit = tier != null && pDay > 0.0 && roll < pDay;
		int quota = hit ? rollCount(tier, level) : 0;
		quotaDayBySubject.put(subjectId, dayId);
		remainingQuotaBySubject.put(subjectId, quota);
		dailyQuotaBySubject.put(subjectId, quota);
		log("quota-roll " + subjectCtx(level.getServer(), subjectId)
				+ " " + timeCtx(level)
				+ " owned=" + owned
				+ " " + formatTier(tier)
				+ " roll=" + roll
				+ " pDay=" + pDay
				+ " hit=" + hit
				+ " quota=" + quota);
	}

	private static int rollCount(ImmigrationTier tier, ServerLevel level) {
		int min = Math.min(tier.minCount(), tier.maxCount());
		int max = Math.max(tier.minCount(), tier.maxCount());
		if (max <= min) {
			return Math.max(0, min);
		}
		return min + level.random.nextInt(max - min + 1);
	}

	private static ImmigrationResult spawnFromLoadedCandidates(ServerLevel level, UUID subjectId, boolean force) {
		String quotaExtra = quotaExtra(subjectId);
		List<ChunkPos> candidates = new ArrayList<>(PbsAdapter.immigrationChunks(level, subjectId));
		if (candidates.isEmpty()) {
			logSkip(level, subjectId, force, "no_territory", "candidates=0 " + quotaExtra);
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
			ImmigrationResult spawned = spawnInChunk(level, subjectId, chunk, force);
			if (spawned.status() == ImmigrationResult.Status.SUCCESS) {
				return spawned;
			}
			if (spawned.status() == ImmigrationResult.Status.CREATE_FAILED) {
				return spawned;
			}
		}
		if (loaded == 0) {
			logSkip(level, subjectId, force, "chunk_unloaded",
					"candidates=" + candidates.size() + " tried=" + unloaded + " allUnloaded=true " + quotaExtra);
			return ImmigrationResult.fail(ImmigrationResult.Status.CHUNK_UNLOADED);
		}
		logSkip(level, subjectId, force, "no_standable",
				"candidates=" + candidates.size() + " loaded=" + loaded + " unloaded=" + unloaded + " " + quotaExtra);
		return ImmigrationResult.fail(ImmigrationResult.Status.NO_STANDABLE);
	}

	private static List<ChunkPos> shuffled(List<ChunkPos> candidates, ServerLevel level) {
		List<ChunkPos> copy = new ArrayList<>(candidates);
		for (int i = copy.size(); i > 1; i--) {
			Collections.swap(copy, i - 1, level.random.nextInt(i));
		}
		return copy;
	}

	private static ImmigrationResult spawnInChunk(ServerLevel level, UUID subjectId, ChunkPos chunk, boolean force) {
		BlockPos feet = StandableFinder.findInChunk(level, chunk, null);
		if (feet == null) {
			return ImmigrationResult.fail(ImmigrationResult.Status.NO_STANDABLE);
		}
		Villager villager = EntityType.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (villager == null) {
			logSkip(level, subjectId, force, "create_failed",
					"chunk=" + formatChunk(chunk) + " pos=" + formatPos(feet) + " " + quotaExtra(subjectId));
			return ImmigrationResult.fail(ImmigrationResult.Status.CREATE_FAILED);
		}
		StandableFinder.snapToStandable(villager, level, feet, level.random.nextFloat() * 360.0f, 0.0f);
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.setSubjectId(subjectId);
		RefugeeAttachments.markDirty(villager, data);
		level.addFreshEntity(villager);
		RosterService.registerOwnedIfPlayer(level.getServer(), subjectId, villager);
		notifyArrival(level.getServer(), subjectId, 1);
		String specialNpc = SpecialRefugeeService.onImmigrationSuccess(level, subjectId, feet);
		return new ImmigrationResult(ImmigrationResult.Status.SUCCESS, villager, feet, chunk, specialNpc);
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

	private static String formatTier(ImmigrationTier tier) {
		if (tier == null) {
			return "tier=none maxOwned=- dayChance=- minCount=- maxCount=-";
		}
		String maxOwned = tier.maxOwned() == Integer.MAX_VALUE ? "unbounded" : Integer.toString(tier.maxOwned());
		return "maxOwned=" + maxOwned
				+ " dayChance=" + tier.dayChance()
				+ " minCount=" + tier.minCount()
				+ " maxCount=" + tier.maxCount();
	}

	private static String quotaExtra(UUID subjectId) {
		return "dailyQuota=" + dailyQuotaBySubject.getOrDefault(subjectId, 0)
				+ " remaining=" + remainingQuotaBySubject.getOrDefault(subjectId, 0);
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

	public record ImmigrationResult(Status status, Villager villager, BlockPos pos, ChunkPos chunk, String specialNpc) {
		public enum Status {
			SUCCESS,
			SKIPPED_CHANCE,
			SKIPPED_DIMENSION,
			NO_TERRITORY,
			CHUNK_UNLOADED,
			NO_STANDABLE,
			CREATE_FAILED
		}

		public static ImmigrationResult fail(Status status) {
			return new ImmigrationResult(status, null, null, null, "none");
		}
	}
}
