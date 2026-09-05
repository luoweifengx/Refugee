package luowei.refugee.special;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.compat.FoodCompat;
import luowei.refugee.interact.RosterService;
import luowei.refugee.network.RefugeeNetworking;
import luowei.refugee.network.SpecialSplashAction;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.settle.StandableFinder;
import luowei.refugee.spawn.LandmarkSpawnService;
import luowei.refugee.talk.RefugeeBubble;

/**
 * 四个特殊难民的生成、绑定、交互与解锁。
 */
public final class SpecialRefugeeService {
	private static final String IMMIGRATION_LOG_PREFIX = "[refugee immigration]";
	private static final int UNLOCK_INTERVAL_TICKS = 20;
	private static final int CARTOGRAPHER_OWNED_THRESHOLD = 20;
	private static final Set<UUID> nurseAttemptLoggedPlayers = new HashSet<>();

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
			case GUIDE, CARTOGRAPHER, ENCHANTER -> {
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
		return luowei.refugee.interact.RefugeeRoles.isGiveableTool(stack);
	}

	/**
	 * @return {@code nurse} 本次刷出护士；{@code nurse_failed} 第一次尝试失败；{@code none} 已有/已发放或无需尝试
	 */
	public static String onImmigrationSuccess(ServerLevel level, UUID subjectId, BlockPos around) {
		if (level == null || subjectId == null) {
			return "none";
		}
		MinecraftServer server = level.getServer();
		String subjectName = PbsAdapter.displayName(server, subjectId);
		boolean attempted = false;
		boolean spawned = false;
		boolean failed = false;
		for (ServerPlayer player : playersOfSubject(server, subjectId)) {
			PlayerSelectionData data = RefugeeAttachments.get(player);
			if (data.isNurseGranted() || data.nurseId() != null) {
				continue;
			}
			attempted = true;
			TrySpawnOutcome outcome = trySpawnResult(
					player,
					RefugeeSpecialRole.NURSE,
					around,
					"message.refugee.special.nurse.joined"
			);
			String playerName = player.getGameProfile().getName();
			if (outcome.status() == TrySpawnResult.SPAWNED) {
				spawned = true;
				Refugee.LOGGER.debug(
						"{} nurse result=spawned subject={} uuid={} player={} around={} pos={}",
						IMMIGRATION_LOG_PREFIX,
						subjectName == null || subjectName.isBlank() ? "-" : subjectName,
						subjectId,
						playerName,
						formatPos(around),
						formatPos(outcome.feet())
				);
			} else {
				failed = true;
				if (nurseAttemptLoggedPlayers.add(player.getUUID())) {
					Refugee.LOGGER.debug(
							"{} nurse result=failed reason={} subject={} uuid={} player={} around={}",
							IMMIGRATION_LOG_PREFIX,
							outcome.status().reasonKey(),
							subjectName == null || subjectName.isBlank() ? "-" : subjectName,
							subjectId,
							playerName,
							formatPos(around)
					);
				}
			}
		}
		if (spawned) {
			return "nurse";
		}
		if (attempted && failed) {
			return "nurse_failed";
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
	 * 指令强制生成：忽略入境解锁标记，但同角色已绑定且仍在名册时拒绝重复。
	 */
	public static CommandSpawnResult spawnForCommand(ServerPlayer player, RefugeeSpecialRole role) {
		if (player == null || role == null || !(player.level() instanceof ServerLevel level)) {
			return CommandSpawnResult.fail(CommandSpawnResult.Status.FAILED);
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
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		if (follow) {
			selection.addSelected(villager.getUUID());
		}
		selection.addRoster(villager);
		selection.bindSpecial(role, villager.getUUID());
		RefugeeAttachments.markDirty(player, selection);
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
		if (!data.hadLapis() && hasLapis(player)) {
			data.setHadLapis(true);
			RefugeeAttachments.markDirty(player, data);
		}
		tryUnlockEnchanter(player);
		tryUnlockCartographer(player);
	}

	private static void tryUnlockEnchanter(ServerPlayer player) {
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (data.isEnchanterGranted() || data.enchanterId() != null) {
			return;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (FoodCompat.isLoaded()) {
			if (!FoodCompat.isRitualCompleted(player.getServer())) {
				return;
			}
			ServerLevel overworld = player.getServer().overworld();
			BlockPos around = LandmarkSpawnService.enchanterFeet(overworld);
			if (around == null) {
				return;
			}
			trySpawn(player, overworld, RefugeeSpecialRole.ENCHANTER, around, "message.refugee.special.enchanter.joined");
			return;
		}
		if (data.hadLapis()) {
			trySpawn(player, level, RefugeeSpecialRole.ENCHANTER, player.blockPosition(), "message.refugee.special.enchanter.joined");
		}
	}

	private static void tryUnlockCartographer(ServerPlayer player) {
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (data.isCartographerGranted() || data.cartographerId() != null) {
			return;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		UUID subjectId = PbsAdapter.resolveSubject(player);
		if (PbsAdapter.territoryCounts(level, subjectId).owned() <= CARTOGRAPHER_OWNED_THRESHOLD) {
			return;
		}
		trySpawn(player, level, RefugeeSpecialRole.CARTOGRAPHER, player.blockPosition(), "message.refugee.special.cartographer.joined");
	}

	private static boolean trySpawn(
			ServerPlayer player,
			ServerLevel level,
			RefugeeSpecialRole role,
			BlockPos around,
			String messageKey
	) {
		return trySpawnResult(player, level, role, around, messageKey).status() == TrySpawnResult.SPAWNED;
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
		if (player == null || level == null) {
			return TrySpawnOutcome.fail(TrySpawnResult.SKIPPED);
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (data.specialId(role) != null) {
			return TrySpawnOutcome.fail(TrySpawnResult.SKIPPED);
		}
		if (role == RefugeeSpecialRole.NURSE && data.isNurseGranted()) {
			return TrySpawnOutcome.fail(TrySpawnResult.SKIPPED);
		}
		if (role == RefugeeSpecialRole.CARTOGRAPHER && data.isCartographerGranted()) {
			return TrySpawnOutcome.fail(TrySpawnResult.SKIPPED);
		}
		if (role == RefugeeSpecialRole.ENCHANTER && data.isEnchanterGranted()) {
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
		player.sendSystemMessage(Component.translatable(messageKey));
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
}
