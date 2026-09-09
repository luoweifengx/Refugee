package luowei.refugee.special;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;

import luowei.refugee.Refugee;
import luowei.refugee.attachment.PlayerSelectionData;
import luowei.refugee.attachment.PlayerSelectionData.RosterEntry;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.compat.FoodCompat;
import luowei.refugee.config.RefugeePlayDifficulty;
import luowei.refugee.network.RefugeeNetworking;
import luowei.refugee.network.SpecialSplashAction;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.settle.StandableFinder;
import luowei.refugee.spawn.LandmarkData;
import luowei.refugee.talk.RefugeeBubble;

/**
 * 四个特殊难民的生成、绑定、交互与解锁。
 * 向导开局发放；护士/绘图师/附魔师/向导在满足条件后随入境到来，死后也走入境，每次只补一名。
 */
public final class SpecialRefugeeService {
	private static final String IMMIGRATION_LOG_PREFIX = "[refugee immigration]";
	private static final int UNLOCK_INTERVAL_TICKS = 20;
	private static final int CARTOGRAPHER_OWNED_THRESHOLD = 20;
	private static final List<RefugeeSpecialRole> IMMIGRATION_ROLES = List.of(
			RefugeeSpecialRole.NURSE,
			RefugeeSpecialRole.CARTOGRAPHER,
			RefugeeSpecialRole.ENCHANTER,
			RefugeeSpecialRole.GUIDE
	);

	private SpecialRefugeeService() {
	}

	public static void register() {
		GuideDialogueConfig.load();
		ServerTickEvents.END_SERVER_TICK.register(SpecialRefugeeService::onServerTick);
	}

	public static boolean handleInteract(ServerPlayer player, Villager villager, ItemStack held) {
		RefugeeSpecialRole role = RefugeeSpecialRole.of(villager);
		if (role == null) {
			return false;
		}
		boolean empty = held == null || held.isEmpty();
		return switch (role) {
			case GUIDE -> {
				if (!empty) {
					yield false;
				}
				yield GuideTutorialService.handleGuideInteract(player, villager);
			}
			case CARTOGRAPHER, ENCHANTER -> {
				if (!empty) {
					yield false;
				}
				RefugeeBubble.onTalk(villager);
				lookAtPlayer(villager, player);
				RefugeeNetworking.openSpecialSplash(player, villager);
				yield true;
			}
			case NURSE -> {
				if (!empty && !held.is(Items.EMERALD)) {
					yield false;
				}
				RefugeeBubble.onTalk(villager);
				lookAtPlayer(villager, player);
				if (!empty) {
					NurseService.HealResult result = NurseService.heal(player, villager);
					if (result == NurseService.HealResult.HEALED) {
						yield true;
					}
					RefugeeNetworking.openSpecialSplash(player, villager, nurseTalkKey(result));
					yield true;
				}
				RefugeeNetworking.openSpecialSplash(player, villager);
				yield true;
			}
		};
	}

	/** 开屏期间看向交互玩家，对照旅商 LookControl。 */
	public static void lookAtPlayer(Villager villager, ServerPlayer player) {
		if (villager == null || player == null) {
			return;
		}
		villager.getNavigation().stop();
		villager.setDeltaMovement(0.0, villager.getDeltaMovement().y, 0.0);
		villager.getLookControl().setLookAt(player, 30.0F, villager.getMaxHeadXRot());
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.startLookAt(player.getUUID(), villager.level().getGameTime() + 300);
	}

	public static void handleSplashAction(ServerPlayer player, int entityId, SpecialSplashAction action) {
		if (player == null || action == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (action == SpecialSplashAction.INTRO_ADVANCE
				|| action == SpecialSplashAction.INTRO_FINISH
				|| action == SpecialSplashAction.INTRO_INTERRUPT) {
			switch (action) {
				case INTRO_ADVANCE -> GuideTutorialService.onIntroAdvance(player, entityId);
				case INTRO_FINISH -> GuideTutorialService.onIntroFinish(player, entityId);
				case INTRO_INTERRUPT -> GuideTutorialService.onIntroInterrupt(player, entityId);
				default -> {
				}
			}
			return;
		}
		Entity entity = level.getEntity(entityId);
		if (!(entity instanceof Villager villager)) {
			return;
		}
		RefugeeSpecialRole role = RefugeeSpecialRole.of(villager);
		if (role == null || villager.distanceTo(player) > 16.0f) {
			return;
		}
		lookAtPlayer(villager, player);
		switch (action) {
			case TALK -> {
				RefugeeBubble.onTalk(villager);
				if (role == RefugeeSpecialRole.ENCHANTER) {
					EnchanterGiftData.tryGiveTable(player);
				}
			}
			case HEAL -> {
				if (role == RefugeeSpecialRole.NURSE) {
					RefugeeBubble.onTalk(villager);
					NurseService.HealResult result = NurseService.heal(player, villager);
					RefugeeNetworking.updateSpecialSplashTalk(player, villager.getId(), nurseTalkKey(result));
				}
			}
			case MAP -> {
				if (role == RefugeeSpecialRole.CARTOGRAPHER) {
					RefugeeNetworking.openTerritoryMap(player, villager, TerritoryMapService.DEFAULT_RADIUS);
				}
			}
			case TRADE -> {
				if (role == RefugeeSpecialRole.ENCHANTER) {
					RefugeeBubble.onTalk(villager);
					EnchanterTrades.open(player, villager);
				}
			}
		}
	}

	private static String nurseTalkKey(NurseService.HealResult result) {
		return switch (result) {
			case FULL -> "screen.refugee.splash.nurse.full";
			case NOT_ENOUGH -> "screen.refugee.splash.nurse.not_enough";
			case HEALED -> "screen.refugee.splash.nurse.healed";
		};
	}

	public static boolean blocksEquipment(Villager villager, ItemStack stack) {
		if (!RefugeeSpecialRole.isSpecial(villager) || stack == null || stack.isEmpty()) {
			return false;
		}
		return luowei.refugee.interact.RefugeeRoles.isGiveableTool(stack)
				|| luowei.refugee.item.ArmorKitItem.isKit(stack);
	}

	/**
	 * 组织已有该角色时，把绑定抄给该玩家及在线成员。
	 *
	 * @return 组织里已经有这个角色（含刚共享的）
	 */
	public static boolean shareExistingSpecial(ServerPlayer player, RefugeeSpecialRole role) {
		if (player == null || role == null) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		UUID subjectId = PbsAdapter.resolveSubject(player);
		pruneSubject(server, subjectId);
		if (adoptOrgSpecial(server, subjectId, role)) {
			return true;
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		return data.specialId(role) != null;
	}

	public static void markSpecialGone(MinecraftServer server, UUID villagerId) {
		if (server == null || villagerId == null) {
			return;
		}
		SpecialGoneData.get(server).markGone(villagerId);
	}

	/**
	 * 入境成功时最多补一名已解锁且缺员的特殊难民。
	 *
	 * @return 本次刷出的角色 id；失败为 {@code <id>_failed}；无需尝试为 {@code none}
	 */
	public static String onImmigrationSuccess(ServerLevel level, UUID subjectId, BlockPos around) {
		if (level == null || subjectId == null) {
			return "none";
		}
		MinecraftServer server = level.getServer();
		pruneSubject(server, subjectId);
		List<ServerPlayer> members = playersOfSubject(server, subjectId);
		if (members.isEmpty()) {
			return "none";
		}
		String subjectName = PbsAdapter.displayName(server, subjectId);
		for (RefugeeSpecialRole role : IMMIGRATION_ROLES) {
			if (adoptOrgSpecial(server, subjectId, role) || orgRoleTaken(server, subjectId, role)) {
				continue;
			}
			if (!immigrationEligible(level, subjectId, members, role)) {
				continue;
			}
			for (ServerPlayer player : members) {
				if (RefugeeAttachments.get(player).specialId(role) != null) {
					continue;
				}
				TrySpawnOutcome outcome = trySpawnResult(
						player,
						level,
						role,
						around,
						"message.refugee.special." + role.id() + ".joined"
				);
				String playerName = player.getGameProfile().getName();
				if (outcome.status() == TrySpawnResult.SPAWNED) {
					Refugee.LOGGER.debug(
							"{} {} result=spawned subject={} uuid={} player={} around={} pos={}",
							IMMIGRATION_LOG_PREFIX,
							role.id(),
							subjectName == null || subjectName.isBlank() ? "-" : subjectName,
							subjectId,
							playerName,
							formatPos(around),
							formatPos(outcome.feet())
					);
					return role.id();
				}
				if (outcome.status() == TrySpawnResult.SKIPPED) {
					break;
				}
				Refugee.LOGGER.debug(
						"{} {} result=failed reason={} subject={} uuid={} player={} around={}",
						IMMIGRATION_LOG_PREFIX,
						role.id(),
						outcome.status().reasonKey(),
						subjectName == null || subjectName.isBlank() ? "-" : subjectName,
						subjectId,
						playerName,
						formatPos(around)
				);
				return role.id() + "_failed";
			}
		}
		return "none";
	}

	public static void onSpecialRemoved(MinecraftServer server, UUID villagerId) {
		if (server == null || villagerId == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PlayerSelectionData data = RefugeeAttachments.get(player);
			if (data.clearSpecialBinding(villagerId)) {
				RefugeeAttachments.markDirty(player, data);
			}
		}
	}

	/**
	 * 指令强制生成：忽略入境解锁标记，但组织内同角色已存在时拒绝重复。
	 */
	public static CommandSpawnResult spawnForCommand(ServerPlayer player, RefugeeSpecialRole role) {
		if (player == null || role == null || !(player.level() instanceof ServerLevel level)) {
			return CommandSpawnResult.fail(CommandSpawnResult.Status.FAILED);
		}
		MinecraftServer server = level.getServer();
		UUID subjectId = PbsAdapter.resolveSubject(player);
		pruneSubject(server, subjectId);
		UUID orgExisting = findOrgSpecialId(server, subjectId, role);
		if (orgExisting != null) {
			shareSpecial(server, subjectId, role, orgExisting);
			return CommandSpawnResult.fail(CommandSpawnResult.Status.ALREADY_HAS);
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		UUID existing = data.specialId(role);
		if (existing != null && data.hasRoster(existing)) {
			return CommandSpawnResult.fail(CommandSpawnResult.Status.ALREADY_HAS);
		}
		if (existing != null) {
			data.clearSpecialBinding(existing);
			RefugeeAttachments.markDirty(player, data);
		}
		BlockPos feet = findSpot(level, player.blockPosition());
		if (feet == null) {
			return CommandSpawnResult.fail(CommandSpawnResult.Status.NO_STANDABLE);
		}
		Villager villager = spawnBound(player, level, feet, role, false);
		if (villager == null) {
			return CommandSpawnResult.fail(CommandSpawnResult.Status.FAILED);
		}
		return new CommandSpawnResult(CommandSpawnResult.Status.SUCCESS, villager, feet);
	}

	public record CommandSpawnResult(Status status, Villager villager, BlockPos pos) {
		public enum Status {
			SUCCESS,
			ALREADY_HAS,
			NO_STANDABLE,
			FAILED
		}

		private static CommandSpawnResult fail(Status status) {
			return new CommandSpawnResult(status, null, null);
		}
	}

	public static Villager spawnBound(
			ServerPlayer player,
			ServerLevel level,
			BlockPos feet,
			RefugeeSpecialRole role,
			boolean follow
	) {
		if (player == null || level == null || feet == null || role == null) {
			return null;
		}
		Villager villager = EntityType.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (villager == null) {
			return null;
		}
		StandableFinder.snapToStandable(villager, level, feet, level.random.nextFloat() * 360.0f, 0.0f);
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.setSubjectId(PbsAdapter.resolveSubject(player));
		if (follow) {
			data.startFollowing(player.getUUID());
		}
		RefugeeAttachments.markDirty(villager, data);
		RefugeeSpecialRole.apply(villager, role);
		if (!level.addFreshEntity(villager)) {
			return null;
		}
		if (follow) {
			PlayerSelectionData selection = RefugeeAttachments.get(player);
			selection.addSelected(villager.getUUID());
			RefugeeAttachments.markDirty(player, selection);
		}
		shareSpecial(level.getServer(), data.subjectId(), role, villager.getUUID());
		return villager;
	}

	private static void onServerTick(MinecraftServer server) {
		if (server.getTickCount() % UNLOCK_INTERVAL_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayerUnlocks(player);
		}
	}

	private static void tickPlayerUnlocks(ServerPlayer player) {
		if (player == null || player.isSpectator() || !(player.level() instanceof ServerLevel)) {
			return;
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (data.isDefeated()) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		pruneStaleSpecials(server, player);
		if (!data.hadLapis() && hasLapis(player)) {
			data.setHadLapis(true);
			RefugeeAttachments.markDirty(player, data);
		}
		adoptMissingOrgSpecials(player);
	}

	private static void adoptMissingOrgSpecials(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		UUID subjectId = PbsAdapter.resolveSubject(player);
		PlayerSelectionData data = RefugeeAttachments.get(player);
		for (RefugeeSpecialRole role : RefugeeSpecialRole.values()) {
			if (data.specialId(role) != null) {
				continue;
			}
			adoptOrgSpecial(server, subjectId, role);
		}
	}

	private static boolean immigrationEligible(
			ServerLevel level,
			UUID subjectId,
			List<ServerPlayer> members,
			RefugeeSpecialRole role
	) {
		return switch (role) {
			case NURSE -> true;
			case CARTOGRAPHER -> PbsAdapter.territoryCounts(level, subjectId).owned() > CARTOGRAPHER_OWNED_THRESHOLD;
			case ENCHANTER -> isEnchanterEligible(level, members);
			case GUIDE -> isGuideEligible(level, members);
		};
	}

	private static boolean isEnchanterEligible(ServerLevel level, List<ServerPlayer> members) {
		if (FoodCompat.isLoaded()) {
			MinecraftServer server = level.getServer();
			return FoodCompat.isRitualCompleted(server) && LandmarkData.get(server).isMageTowerPlaced();
		}
		for (ServerPlayer member : members) {
			if (RefugeeAttachments.get(member).hadLapis()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isGuideEligible(ServerLevel level, List<ServerPlayer> members) {
		if (!RefugeePlayDifficulty.of(level).spawnGuide()) {
			return false;
		}
		for (ServerPlayer member : members) {
			if (RefugeeAttachments.get(member).isStarterGranted()) {
				return true;
			}
		}
		return false;
	}

	private static TrySpawnOutcome trySpawnResult(
			ServerPlayer player,
			RefugeeSpecialRole role,
			BlockPos around,
			String messageKey
	) {
		if (!(player.level() instanceof ServerLevel level)) {
			return TrySpawnOutcome.fail(TrySpawnResult.SKIPPED);
		}
		return trySpawnResult(player, level, role, around, messageKey);
	}

	private static TrySpawnOutcome trySpawnResult(
			ServerPlayer player,
			ServerLevel level,
			RefugeeSpecialRole role,
			BlockPos around,
			String messageKey
	) {
		if (player == null || level == null || role == null) {
			return TrySpawnOutcome.fail(TrySpawnResult.SKIPPED);
		}
		MinecraftServer server = level.getServer();
		UUID subjectId = PbsAdapter.resolveSubject(player);
		pruneSubject(server, subjectId);
		if (adoptOrgSpecial(server, subjectId, role) || orgRoleTaken(server, subjectId, role)) {
			return TrySpawnOutcome.fail(TrySpawnResult.SKIPPED);
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (data.specialId(role) != null) {
			return TrySpawnOutcome.fail(TrySpawnResult.SKIPPED);
		}
		BlockPos preferred = around == null ? player.blockPosition() : around;
		BlockPos feet = StandableFinder.isStandable(level, preferred) ? preferred : findSpot(level, preferred);
		if (feet == null) {
			Refugee.LOGGER.warn("Failed to find standable spot for {} near {}", role.id(), player.getGameProfile().getName());
			return TrySpawnOutcome.fail(TrySpawnResult.NO_STANDABLE);
		}
		Villager villager = spawnBound(player, level, feet, role, false);
		if (villager == null) {
			return TrySpawnOutcome.fail(TrySpawnResult.CREATE_FAILED);
		}
		if (role == RefugeeSpecialRole.ENCHANTER) {
			RefugeeVillagerData villagerData = RefugeeAttachments.get(villager);
			villagerData.setGuardCenter(feet);
			RefugeeAttachments.markDirty(villager, villagerData);
		}
		if (role == RefugeeSpecialRole.GUIDE) {
			for (ServerPlayer member : playersOfSubject(server, subjectId)) {
				GuideTutorialService.markEligible(member, level);
			}
		}
		notifyMembers(server, subjectId, messageKey);
		return new TrySpawnOutcome(TrySpawnResult.SPAWNED, feet);
	}

	private static String formatPos(BlockPos pos) {
		return pos == null ? "-" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private record TrySpawnOutcome(TrySpawnResult status, BlockPos feet) {
		private static TrySpawnOutcome fail(TrySpawnResult status) {
			return new TrySpawnOutcome(status, null);
		}
	}

	private enum TrySpawnResult {
		SPAWNED("spawned"),
		SKIPPED("skipped"),
		NO_STANDABLE("no_standable"),
		CREATE_FAILED("create_failed");

		private final String reasonKey;

		TrySpawnResult(String reasonKey) {
			this.reasonKey = reasonKey;
		}

		String reasonKey() {
			return reasonKey;
		}
	}

	private static BlockPos findSpot(ServerLevel level, BlockPos origin) {
		Set<BlockPos> reserved = new HashSet<>();
		reserved.add(origin);
		List<BlockPos> spots = StandableFinder.findStandable(level, origin, reserved, 1);
		if (!spots.isEmpty()) {
			return spots.getFirst();
		}
		return StandableFinder.findInChunk(level, new ChunkPos(origin), reserved);
	}

	private static boolean hasLapis(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(Items.LAPIS_LAZULI)) {
				return true;
			}
		}
		return false;
	}

	private static void pruneSubject(MinecraftServer server, UUID subjectId) {
		for (ServerPlayer member : playersOfSubject(server, subjectId)) {
			pruneStaleSpecials(server, member);
		}
	}

	private static void pruneStaleSpecials(MinecraftServer server, ServerPlayer player) {
		if (server == null || player == null) {
			return;
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		boolean changed = false;
		for (RefugeeSpecialRole role : RefugeeSpecialRole.values()) {
			UUID id = data.specialId(role);
			if (id == null || isSpecialPresent(server, data, id)) {
				continue;
			}
			changed |= data.clearSpecialBinding(id);
			changed |= data.removeRoster(id);
		}
		if (changed) {
			RefugeeAttachments.markDirty(player, data);
		}
	}

	private static boolean isSpecialPresent(MinecraftServer server, PlayerSelectionData data, UUID villagerId) {
		if (server == null || villagerId == null) {
			return false;
		}
		if (SpecialGoneData.get(server).isGone(villagerId)) {
			return false;
		}
		Villager loaded = findLoadedVillager(server, villagerId);
		if (loaded != null && loaded.isAlive()) {
			return true;
		}
		if (data == null || !data.hasRoster(villagerId)) {
			return false;
		}
		RosterEntry entry = data.rosterEntry(villagerId);
		if (entry == null || entry.pos() == null) {
			return true;
		}
		ServerLevel dim = levelOf(server, entry.dimension());
		if (dim == null) {
			return true;
		}
		ChunkPos chunk = new ChunkPos(entry.pos());
		if (!dim.hasChunk(chunk.x, chunk.z)) {
			return true;
		}
		Entity entity = dim.getEntity(villagerId);
		return entity instanceof Villager villager && villager.isAlive();
	}

	private static ServerLevel levelOf(MinecraftServer server, ResourceLocation dimension) {
		if (server == null || dimension == null) {
			return null;
		}
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
	}

	private static List<ServerPlayer> playersOfSubject(MinecraftServer server, UUID subjectId) {
		List<ServerPlayer> result = new ArrayList<>();
		if (server == null || subjectId == null) {
			return result;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (subjectId.equals(player.getUUID()) || subjectId.equals(PbsAdapter.resolveSubject(player))) {
				result.add(player);
			}
		}
		return result;
	}

	private static boolean adoptOrgSpecial(MinecraftServer server, UUID subjectId, RefugeeSpecialRole role) {
		UUID existing = findOrgSpecialId(server, subjectId, role);
		if (existing == null) {
			return false;
		}
		shareSpecial(server, subjectId, role, existing);
		return true;
	}

	private static boolean orgRoleTaken(MinecraftServer server, UUID subjectId, RefugeeSpecialRole role) {
		return findOrgSpecialId(server, subjectId, role) != null;
	}

	private static UUID findOrgSpecialId(MinecraftServer server, UUID subjectId, RefugeeSpecialRole role) {
		if (server == null || subjectId == null || role == null) {
			return null;
		}
		for (ServerLevel world : server.getAllLevels()) {
			for (Entity entity : world.getAllEntities()) {
				if (!(entity instanceof Villager villager) || !villager.isAlive()) {
					continue;
				}
				if (!RefugeeSpecialRole.is(villager, role)) {
					continue;
				}
				UUID villagerSubject = RefugeeAttachments.get(villager).subjectId();
				if (subjectId.equals(villagerSubject) && !SpecialGoneData.get(server).isGone(villager.getUUID())) {
					return villager.getUUID();
				}
			}
		}
		for (ServerPlayer member : playersOfSubject(server, subjectId)) {
			PlayerSelectionData data = RefugeeAttachments.get(member);
			UUID bound = data.specialId(role);
			if (bound != null && isSpecialPresent(server, data, bound)) {
				return bound;
			}
		}
		return null;
	}

	private static void shareSpecial(MinecraftServer server, UUID subjectId, RefugeeSpecialRole role, UUID villagerId) {
		if (server == null || subjectId == null || role == null || villagerId == null) {
			return;
		}
		Villager loaded = findLoadedVillager(server, villagerId);
		RosterEntry copied = null;
		if (loaded == null) {
			for (ServerPlayer member : playersOfSubject(server, subjectId)) {
				for (RosterEntry entry : RefugeeAttachments.get(member).rosterEntries()) {
					if (villagerId.equals(entry.villagerId())) {
						copied = entry;
						break;
					}
				}
				if (copied != null) {
					break;
				}
			}
		}
		for (ServerPlayer member : playersOfSubject(server, subjectId)) {
			PlayerSelectionData data = RefugeeAttachments.get(member);
			if (loaded != null) {
				data.addRoster(loaded);
			} else if (copied != null) {
				data.addRoster(copied.villagerId(), copied.dimension(), copied.pos());
			}
			data.bindSpecial(role, villagerId);
			RefugeeAttachments.markDirty(member, data);
		}
	}

	private static Villager findLoadedVillager(MinecraftServer server, UUID villagerId) {
		if (server == null || villagerId == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(villagerId);
			if (entity instanceof Villager villager && villager.isAlive()) {
				return villager;
			}
		}
		return null;
	}

	private static void notifyMembers(MinecraftServer server, UUID subjectId, String messageKey) {
		if (server == null || subjectId == null || messageKey == null || messageKey.isBlank()) {
			return;
		}
		Component message = Component.translatable(messageKey);
		for (ServerPlayer player : playersOfSubject(server, subjectId)) {
			player.sendSystemMessage(message);
		}
	}
}
