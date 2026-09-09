package luowei.refugee.special;

import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import luowei.refugee.attachment.PlayerSelectionData;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.compat.FoodCompat;
import luowei.refugee.interact.SelectionService;
import luowei.refugee.item.ModItems;
import luowei.refugee.network.RefugeeNetworking;
import luowei.refugee.talk.RefugeeBubble;

/**
 * 向导开局教程：1 分钟后靠近触发，或提前右键；每人一次；打断续播与沉默一天。
 * 靠近触发会先抱怨玩家没来找，再续打断句与 intro.0–3。
 */
public final class GuideTutorialService {
	public static final int INTRO_LINES = 4;
	public static final int INTERRUPT_LIMIT = 5;
	public static final int ELIGIBLE_DELAY_TICKS = 20 * 60;
	public static final long SILENT_TICKS = 24000L;
	public static final double TRIGGER_DISTANCE = 2.75;
	public static final int TICK_INTERVAL = 10;

	public static final String INTRO_KEY_PREFIX = "screen.refugee.splash.guide.intro.";
	public static final String SEEK_KEY = "screen.refugee.splash.guide.intro.seek";
	public static final String INTERRUPT_KEY_PREFIX = "screen.refugee.splash.guide.interrupt.";
	public static final String ABANDON_KEY = "screen.refugee.splash.guide.abandon";
	private static final String[] INTERRUPT_KEYS = {
			INTERRUPT_KEY_PREFIX + "0",
			INTERRUPT_KEY_PREFIX + "1",
			INTERRUPT_KEY_PREFIX + "2"
	};

	private GuideTutorialService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GuideTutorialService::onServerTick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.getPlayer()));
	}

	public static void markEligible(ServerPlayer player, ServerLevel level) {
		if (player == null || level == null) {
			return;
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (data.guideIntroEligibleAt() > 0L || data.isGuideIntroDone()) {
			return;
		}
		data.setGuideIntroEligibleAt(level.getGameTime() + ELIGIBLE_DELAY_TICKS);
		RefugeeAttachments.markDirty(player, data);
	}

	public static boolean isSilent(ServerPlayer player, ServerLevel level) {
		if (player == null || level == null) {
			return false;
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		long until = data.guideSilentUntil();
		return until > 0L && level.getGameTime() < until;
	}

	public static boolean handleGuideInteract(ServerPlayer player, Villager villager) {
		if (player == null || villager == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (isSilent(player, level)) {
			return true;
		}
		openGuide(player, villager);
		return true;
	}

	public static void openGuide(ServerPlayer player, Villager villager) {
		if (player == null || villager == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		skipLegacyWorlds(player);
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (isSilent(player, level)) {
			return;
		}
		RefugeeBubble.onTalk(villager);
		SpecialRefugeeService.lookAtPlayer(villager, player);
		if (data.isGuideIntroDone()) {
			RefugeeNetworking.openSpecialSplash(player, villager);
			return;
		}
		openIntro(player, villager, false, false);
	}

	public static void onIntroAdvance(ServerPlayer player, int entityId) {
		Villager villager = guideOf(player, entityId);
		if (villager == null || !(player.level() instanceof ServerLevel level) || isSilent(player, level)) {
			return;
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (data.isGuideIntroDone()) {
			return;
		}
		int next = Math.min(INTRO_LINES - 1, data.guideIntroStep() + 1);
		data.setGuideIntroStep(next);
		if (next >= 1) {
			grantStaff(player, data);
		}
		RefugeeAttachments.markDirty(player, data);
		SpecialRefugeeService.lookAtPlayer(villager, player);
	}

	public static void onIntroFinish(ServerPlayer player, int entityId) {
		Villager villager = guideOf(player, entityId);
		if (villager == null || !(player.level() instanceof ServerLevel)) {
			return;
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		grantStaff(player, data);
		data.setGuideIntroDone(true);
		data.setGuideIntroOpen(false);
		data.setGuideIntroStep(INTRO_LINES);
		RefugeeAttachments.markDirty(player, data);
		RefugeeNetworking.openSpecialSplash(player, villager);
	}

	public static void onIntroInterrupt(ServerPlayer player, int entityId) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (data.isGuideIntroDone() || !data.isGuideIntroOpen()) {
			return;
		}
		data.setGuideIntroOpen(false);
		int count = data.guideInterruptCount() + 1;
		data.setGuideInterruptCount(count);
		if (count >= INTERRUPT_LIMIT) {
			abandonIntro(player, data, guideOf(player, entityId), true);
			return;
		}
		RefugeeAttachments.markDirty(player, data);
	}

	public static boolean foodSecretVisible() {
		return FoodCompat.isLoaded();
	}

	public static String interruptKey(ServerPlayer player) {
		if (player == null || player.getRandom() == null) {
			return INTERRUPT_KEYS[0];
		}
		return INTERRUPT_KEYS[player.getRandom().nextInt(INTERRUPT_KEYS.length)];
	}

	private static void onJoin(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel)) {
			return;
		}
		skipLegacyWorlds(player);
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (data.isGuideIntroOpen() && !data.isGuideIntroDone()) {
			data.setGuideIntroOpen(false);
			data.setGuideInterruptCount(data.guideInterruptCount() + 1);
			if (data.guideInterruptCount() >= INTERRUPT_LIMIT) {
				abandonIntro(player, data, findGuide(player), false);
				return;
			}
			RefugeeAttachments.markDirty(player, data);
		}
	}

	private static void onDisconnect(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel)) {
			return;
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (!data.isGuideIntroOpen() || data.isGuideIntroDone()) {
			return;
		}
		data.setGuideIntroOpen(false);
		data.setGuideInterruptCount(data.guideInterruptCount() + 1);
		if (data.guideInterruptCount() >= INTERRUPT_LIMIT) {
			abandonIntro(player, data, findGuide(player), false);
			return;
		}
		RefugeeAttachments.markDirty(player, data);
	}

	private static void onServerTick(MinecraftServer server) {
		if (server.getTickCount() % TICK_INTERVAL != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player);
		}
	}

	private static void tickPlayer(ServerPlayer player) {
		if (player == null || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		skipLegacyWorlds(player);
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (data.isGuideIntroDone() || data.isGuideIntroOpen() || isSilent(player, level)) {
			return;
		}
		if (data.guideIntroEligibleAt() <= 0L || level.getGameTime() < data.guideIntroEligibleAt()) {
			return;
		}
		Villager guide = findGuide(player);
		if (guide == null || guide.distanceTo(player) > TRIGGER_DISTANCE) {
			return;
		}
		openIntro(player, guide, false, true);
	}

	private static void openIntro(ServerPlayer player, Villager villager, boolean abandon, boolean seek) {
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (data.guideIntroStep() >= 1) {
			grantStaff(player, data);
		}
		data.setGuideIntroOpen(true);
		RefugeeAttachments.markDirty(player, data);
		String interrupt = "";
		if (!abandon && data.guideInterruptCount() > 0 && data.guideInterruptCount() < INTERRUPT_LIMIT) {
			interrupt = interruptKey(player);
		}
		RefugeeNetworking.openGuideIntro(player, villager, data.guideIntroStep(), interrupt, abandon, seek && !abandon);
	}

	private static void abandonIntro(ServerPlayer player, PlayerSelectionData data, Villager guide, boolean reopen) {
		data.setGuideIntroDone(true);
		data.setGuideIntroOpen(false);
		if (player.level() instanceof ServerLevel level) {
			data.setGuideSilentUntil(level.getGameTime() + SILENT_TICKS);
		}
		RefugeeAttachments.markDirty(player, data);
		if (guide != null) {
			walkAway(player, guide);
			if (reopen) {
				RefugeeNetworking.openGuideIntro(player, guide, data.guideIntroStep(), "", true, false);
			}
		}
	}

	private static void walkAway(ServerPlayer player, Villager villager) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.stopFollowing();
		RefugeeAttachments.markDirty(villager, data);
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		if (selection.removeSelected(villager.getUUID())) {
			RefugeeAttachments.markDirty(player, selection);
			SelectionService.collectBannersIfEmpty(player);
		}
		Vec3 fromPlayer = villager.position().subtract(player.position());
		Vec3 dir = fromPlayer.horizontalDistanceSqr() < 1.0E-4
				? new Vec3(villager.getLookAngle().x, 0.0, villager.getLookAngle().z)
				: new Vec3(fromPlayer.x, 0.0, fromPlayer.z);
		if (dir.horizontalDistanceSqr() < 1.0E-4) {
			dir = new Vec3(1.0, 0.0, 0.0);
		}
		Vec3 dest = villager.position().add(dir.normalize().scale(12.0));
		villager.getNavigation().moveTo(dest.x, dest.y, dest.z, 0.7);
	}

	private static void grantStaff(ServerPlayer player, PlayerSelectionData data) {
		if (data.isGuideStaffGranted() || ModItems.COMMAND_STAFF == null) {
			return;
		}
		ItemStack staff = new ItemStack(ModItems.COMMAND_STAFF);
		if (!player.getInventory().add(staff)) {
			player.drop(staff, false);
		}
		data.setGuideStaffGranted(true);
	}

	private static void skipLegacyWorlds(ServerPlayer player) {
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (!data.isStarterGranted() || data.isGuideIntroDone() || data.guideIntroEligibleAt() > 0L) {
			return;
		}
		data.setGuideIntroDone(true);
		RefugeeAttachments.markDirty(player, data);
	}

	private static Villager findGuide(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		UUID id = RefugeeAttachments.get(player).guideId();
		if (id == null) {
			return null;
		}
		Entity entity = level.getEntity(id);
		if (entity instanceof Villager villager && villager.isAlive() && RefugeeSpecialRole.is(villager, RefugeeSpecialRole.GUIDE)) {
			return villager;
		}
		return null;
	}

	private static Villager guideOf(ServerPlayer player, int entityId) {
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		Entity entity = level.getEntity(entityId);
		if (entity instanceof Villager villager
				&& RefugeeSpecialRole.is(villager, RefugeeSpecialRole.GUIDE)
				&& villager.distanceTo(player) <= 16.0f) {
			return villager;
		}
		return findGuide(player);
	}

	public static List<String> introKeys() {
		return List.of(
				INTRO_KEY_PREFIX + "0",
				INTRO_KEY_PREFIX + "1",
				INTRO_KEY_PREFIX + "2",
				INTRO_KEY_PREFIX + "3"
		);
	}
}
