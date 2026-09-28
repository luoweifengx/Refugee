package luowei.refugee.special;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

import luowei.refugee.attachment.PlayerSelectionData;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.block.AltarBlockEntity;
import luowei.refugee.block.ModBlocks;
import luowei.refugee.compat.GloryCompat;
import luowei.refugee.network.RefugeeNetworking;
import luowei.refugee.pbs.PbsAdapter;

/**
 * 特殊 NPC 主动剧情：对话锁、头顶感叹号、组织聊天、制图师往返与终局。
 */
public final class SpecialStoryService {
	public static final int TICK_INTERVAL = 10;
	public static final long CARTOGRAPHER_LEAVE_TIME = 1000L;
	public static final long ENCHANTER_SILENT_TICKS = 30L * 20L;
	public static final long GUIDE_STORY_SILENT_TICKS = 4L * 20L;
	public static final String NURSE_INTERRUPT_0 = "screen.refugee.splash.story.interrupt.nurse.0";
	public static final String NURSE_INTERRUPT_1 = "screen.refugee.splash.story.interrupt.nurse.1";
	public static final String NURSE_BUSY_KEY = "screen.refugee.splash.story.interrupt.nurse.busy";
	public static final String CARTO_INTERRUPT_KEY = "screen.refugee.splash.story.interrupt.cartographer";
	public static final String ENCHANTER_INTERRUPT_KEY = "screen.refugee.splash.story.interrupt.enchanter";
	private static final Map<UUID, DialogueLock> LOCKS = new ConcurrentHashMap<>();
	private static final Set<UUID> STORY_ALERTS = ConcurrentHashMap.newKeySet();
	private static final Map<String, Set<UUID>> ANNOUNCED = new ConcurrentHashMap<>();

	private record DialogueLock(int entityId, SpecialStoryKind story, int step) {
	}

	private SpecialStoryService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SpecialStoryService::onServerTick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.getPlayer();
			DialogueLock lock = player == null ? null : LOCKS.get(player.getUUID());
			if (lock != null && lock.story() != null && lock.story() != SpecialStoryKind.DRAGON_GLORY) {
				onStoryInterrupt(player, lock.entityId());
			} else {
				unlock(player);
			}
		});
	}

	public static boolean isLocked(ServerPlayer player) {
		return player != null && LOCKS.containsKey(player.getUUID());
	}

	public static SpecialStoryKind lockedStory(ServerPlayer player) {
		if (player == null) {
			return null;
		}
		DialogueLock lock = LOCKS.get(player.getUUID());
		return lock == null ? null : lock.story();
	}

	public static boolean tryLock(ServerPlayer player, Villager villager, SpecialStoryKind story) {
		if (player == null || villager == null) {
			return false;
		}
		DialogueLock current = LOCKS.get(player.getUUID());
		if (current != null && current.entityId() != villager.getId()) {
			return false;
		}
		int step = current == null ? 0 : current.step();
		LOCKS.put(player.getUUID(), new DialogueLock(villager.getId(), story, step));
		return true;
	}

	public static void unlock(ServerPlayer player) {
		if (player != null) {
			LOCKS.remove(player.getUUID());
		}
	}

	public static void queue(MinecraftServer server, UUID subjectId, SpecialStoryKind kind) {
		if (server == null || subjectId == null || kind == null) {
			return;
		}
		SpecialStoryData data = SpecialStoryData.get(server);
		SpecialStoryData.SubjectStory story = data.of(subjectId);
		if (story.banished || (kind != SpecialStoryKind.NURSE_HEAL && story.isTalked(kind))) {
			return;
		}
		story.queue(kind);
		data.setDirty();
	}

	public static void onIronKitGiven(ServerPlayer player) {
		if (player == null || player.getServer() == null) {
			return;
		}
		queue(player.getServer(), player.getUUID(), SpecialStoryKind.GUIDE_IRON);
	}

	public static void onPlayerHurt(ServerPlayer player) {
		if (player == null || player.getServer() == null || player.getHealth() >= player.getMaxHealth() || orgBanished(player)) {
			return;
		}
		SpecialStoryData data = SpecialStoryData.get(player.getServer());
		SpecialStoryData.SubjectStory story = progress(player);
		if (!story.nurseInjuryTalked) {
			story.queue(SpecialStoryKind.NURSE_INJURY);
		} else {
			story.pendingNurseHeal = true;
		}
		data.setDirty();
	}

	public static void onPlayerDeath(ServerPlayer player) {
		if (player == null || player.getServer() == null || orgBanished(player)) {
			return;
		}
		SpecialStoryData data = SpecialStoryData.get(player.getServer());
		SpecialStoryData.SubjectStory story = progress(player);
		if (!story.nurseDeathTalked) {
			story.pendingNurseDeath = true;
			story.queue(SpecialStoryKind.NURSE_DEATH);
		} else {
			story.pendingNurseHeal = true;
		}
		data.setDirty();
	}

	public static void onDragonKilled(MinecraftServer server) {
		if (server == null || GloryCompat.isLoaded()) {
			return;
		}
		SpecialStoryData data = SpecialStoryData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (orgBanished(player)) {
				continue;
			}
			SpecialStoryData.SubjectStory story = progress(player);
			if (story.dragonVanillaDone) {
				continue;
			}
			story.queue(SpecialStoryKind.DRAGON_VANILLA);
		}
		data.setDirty();
	}

	public static void onCartographerSpawned(ServerPlayer player, boolean returning) {
		if (player == null || player.getServer() == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		UUID subjectId = PbsAdapter.resolveSubject(player);
		SpecialStoryData data = SpecialStoryData.get(player.getServer());
		SpecialStoryData.SubjectStory org = data.of(subjectId);
		org.cartographerAppeared = true;
		org.cartographerAway = false;
		org.cartographerSpawnDay = Math.floorDiv(level.getDayTime(), 24000L);
		SpecialStoryData.SubjectStory story = progress(player);
		if (returning) {
			if (story.ironTalked && !story.cartographerAncientMap) {
				story.queue(SpecialStoryKind.CARTO_ANCIENT);
			} else if (!story.cartographerFirstReturn) {
				story.queue(SpecialStoryKind.CARTO_RETURN);
			}
		} else if (!story.cartographerFirstVisit) {
			story.queue(SpecialStoryKind.CARTO_FIRST);
		}
		data.setDirty();
	}

	public static void onEnchanterSpawned(ServerPlayer player) {
		if (player == null || player.getServer() == null) {
			return;
		}
		queue(player.getServer(), player.getUUID(), SpecialStoryKind.ENCHANTER_ARRIVAL);
	}

	private static boolean orgBanished(ServerPlayer player) {
		return player != null && player.getServer() != null
				&& isBanished(player.getServer(), PbsAdapter.resolveSubject(player));
	}

	private static SpecialStoryData.SubjectStory progress(ServerPlayer player) {
		return SpecialStoryData.get(player.getServer()).of(player.getUUID());
	}

	public static boolean isBanished(MinecraftServer server, UUID subjectId) {
		if (server == null || subjectId == null) {
			return false;
		}
		return SpecialStoryData.get(server).of(subjectId).banished;
	}

	public static boolean cartographerAppeared(MinecraftServer server, UUID subjectId) {
		if (server == null || subjectId == null) {
			return false;
		}
		return SpecialStoryData.get(server).of(subjectId).cartographerAppeared;
	}

	public static List<SpecialStoryData.AltarRecord> altars(MinecraftServer server, UUID subjectId) {
		if (server == null || subjectId == null) {
			return List.of();
		}
		return List.copyOf(SpecialStoryData.get(server).of(subjectId).altars);
	}

	public static void registerAltar(ServerLevel level, BlockPos pos, UUID subjectId) {
		if (level == null || pos == null || subjectId == null) {
			return;
		}
		SpecialStoryData data = SpecialStoryData.get(level.getServer());
		SpecialStoryData.SubjectStory story = data.of(subjectId);
		ResourceLocation dim = level.dimension().location();
		for (SpecialStoryData.AltarRecord record : story.altars) {
			if (dim.equals(record.dimension()) && pos.equals(record.pos())) {
				return;
			}
		}
		story.altars.add(new SpecialStoryData.AltarRecord(dim, pos.immutable(), level.getGameTime()));
		data.setDirty();
		if (story.finaleDone) {
			tryWriteFinale(level.getServer(), subjectId);
		}
	}

	public static void unregisterAltar(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return;
		}
		SpecialStoryData data = SpecialStoryData.get(level.getServer());
		ResourceLocation dim = level.dimension().location();
		boolean changed = false;
		for (SpecialStoryData.SubjectStory story : data.subjects().values()) {
			changed |= story.altars.removeIf(record -> dim.equals(record.dimension()) && pos.equals(record.pos()));
		}
		if (changed) {
			data.setDirty();
		}
	}

	public static void onStoryAdvance(ServerPlayer player, int entityId) {
		DialogueLock lock = player == null ? null : LOCKS.get(player.getUUID());
		if (lock == null || lock.story() == null || lock.entityId() != entityId) {
			return;
		}
		Villager villager = villagerOf(player, entityId);
		if (villager == null) {
			return;
		}
		int next = lock.step() + 1;
		if (lock.story().loop()) {
			next = next % Math.max(1, lock.story().lines());
		} else {
			next = Math.min(lock.story().lines() - 1, next);
		}
		LOCKS.put(player.getUUID(), new DialogueLock(entityId, lock.story(), next));
		applyStoryLine(player, villager, lock.story(), next);
		broadcastLine(player, villager, lock.story().lineKey(next));
	}

	public static void onStoryFinish(ServerPlayer player, int entityId) {
		DialogueLock lock = player == null ? null : LOCKS.get(player.getUUID());
		if (lock == null || lock.story() == null) {
			unlock(player);
			return;
		}
		Villager villager = villagerOf(player, entityId);
		finishStory(player, villager, lock.story());
	}

	public static void onStoryInterrupt(ServerPlayer player, int entityId) {
		DialogueLock lock = player == null ? null : LOCKS.get(player.getUUID());
		if (lock == null || lock.story() == null) {
			unlock(player);
			return;
		}
		if (lock.story() == SpecialStoryKind.DRAGON_GLORY) {
			finishStory(player, villagerOf(player, entityId), lock.story());
			return;
		}
		if (player.getServer() == null) {
			unlock(player);
			return;
		}
		SpecialStoryKind kind = lock.story();
		SpecialStoryData data = SpecialStoryData.get(player.getServer());
		if (orgBanished(player)) {
			unlock(player);
			return;
		}
		SpecialStoryData.SubjectStory story = progress(player);
		story.setResumeStep(kind, lock.step());
		int count = story.bumpInterrupt(kind);
		Villager villager = villagerOf(player, entityId);
		releaseStoryApproach(villager);
		RefugeeSpecialRole role = kind.role() != null ? kind.role() : RefugeeSpecialRole.of(villager);
		long now = player.level() instanceof ServerLevel level ? level.getGameTime() : 0L;
		if (role == RefugeeSpecialRole.NURSE) {
			if (count >= 2) {
				story.skipNurseProactive(kind);
				story.nurseApology = true;
			}
		} else if (role == RefugeeSpecialRole.CARTOGRAPHER) {
			story.cartoPaused = true;
			story.cartoLine = true;
		} else if (role == RefugeeSpecialRole.ENCHANTER) {
			story.enchanterSilentUntil = now + ENCHANTER_SILENT_TICKS;
			story.enchanterEllipsis = true;
			if (villager != null) {
				GuideTutorialService.walkAway(player, villager);
			}
		} else if (role == RefugeeSpecialRole.GUIDE) {
			story.guideSilentUntil = now + GUIDE_STORY_SILENT_TICKS;
			if (villager != null) {
				GuideTutorialService.walkAway(player, villager);
			}
		}
		data.setDirty();
		unlock(player);
	}

	public static boolean tryResumeOnInteract(ServerPlayer player, Villager villager) {
		if (player == null || villager == null || player.getServer() == null || isLocked(player)) {
			return false;
		}
		RefugeeSpecialRole role = RefugeeSpecialRole.of(villager);
		if (role == null) {
			return false;
		}
		SpecialStoryData data = SpecialStoryData.get(player.getServer());
		if (orgBanished(player)) {
			return false;
		}
		SpecialStoryData.SubjectStory story = progress(player);
		long now = player.level() instanceof ServerLevel level ? level.getGameTime() : 0L;
		SpecialStoryKind kind = story.nextPending();
		if (kind == null || isStorySilent(story, kind, now)) {
			return false;
		}
		if (roleFor(player, kind, story) != role) {
			return false;
		}
		if (role == RefugeeSpecialRole.CARTOGRAPHER) {
			story.cartoPaused = false;
			data.setDirty();
		}
		openPendingStory(player, villager, kind, story, data);
		return true;
	}

	public static void onSplashClose(ServerPlayer player, int entityId) {
		Villager talking = villagerOf(player, entityId);
		if (talking != null) {
			RefugeeAttachments.get(talking).clearLookAt();
			releaseStoryApproach(talking);
		}
		DialogueLock lock = player == null ? null : LOCKS.get(player.getUUID());
		if (lock != null && lock.story() != null && lock.story().loop() && lock.story() == SpecialStoryKind.DRAGON_GLORY) {
			finishStory(player, villagerOf(player, entityId), lock.story());
			return;
		}
		unlock(player);
	}

	public static boolean consumePendingDivine(ServerPlayer player) {
		if (player == null || player.getServer() == null) {
			return false;
		}
		SpecialStoryData data = SpecialStoryData.get(player.getServer());
		SpecialStoryData.SubjectStory story = progress(player);
		if (!story.pendingDivine) {
			return false;
		}
		story.pendingDivine = false;
		data.setDirty();
		return true;
	}

	public static boolean needsBookTalk(ServerPlayer player) {
		if (player == null || player.getServer() == null) {
			return false;
		}
		return !progress(player).enchanterBookTalked;
	}

	public static void requestBookTalkThenDivine(ServerPlayer player) {
		if (player == null || player.getServer() == null) {
			return;
		}
		SpecialStoryData data = SpecialStoryData.get(player.getServer());
		SpecialStoryData.SubjectStory story = progress(player);
		story.pendingDivine = true;
		story.queue(SpecialStoryKind.ENCHANTER_BOOK);
		data.setDirty();
		Villager villager = findSpecial(player, RefugeeSpecialRole.ENCHANTER);
		if (villager == null) {
			return;
		}
		SpecialStoryKind locked = lockedStory(player);
		if (locked != null && locked != SpecialStoryKind.ENCHANTER_BOOK) {
			return;
		}
		openPendingStory(player, villager, SpecialStoryKind.ENCHANTER_BOOK, story, data);
	}

	private static void onServerTick(MinecraftServer server) {
		if (server.getTickCount() % TICK_INTERVAL != 0) {
			return;
		}
		tickGlory(server);
		tickCartographerLeave(server);
		tickFinale(server);
		Set<UUID> nextAlerts = new HashSet<>();
		Set<String> activeNotices = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player, nextAlerts, activeNotices);
		}
		for (UUID id : STORY_ALERTS) {
			if (nextAlerts.contains(id)) {
				continue;
			}
			Villager villager = findVillager(server, id);
			if (villager != null) {
				RefugeeAttachments.get(villager).setStoryAlert(false);
			}
		}
		STORY_ALERTS.clear();
		STORY_ALERTS.addAll(nextAlerts);
		ANNOUNCED.keySet().retainAll(activeNotices);
	}

	private static void tickPlayer(ServerPlayer player, Set<UUID> nextAlerts, Set<String> activeNotices) {
		if (player == null || player.isSpectator() || player.getServer() == null) {
			return;
		}
		if (!(player.level() instanceof ServerLevel)) {
			return;
		}
		if (orgBanished(player)) {
			return;
		}
		SpecialStoryData data = SpecialStoryData.get(player.getServer());
		SpecialStoryData.SubjectStory story = progress(player);
		scanInventory(player, story, data);
		if (story.pendingNurseDeath && !story.nurseDeathTalked) {
			story.queue(SpecialStoryKind.NURSE_DEATH);
		} else if (story.pendingNurseHeal && story.nurseInjuryTalked) {
			story.queue(SpecialStoryKind.NURSE_HEAL);
		}
		if (isLocked(player)) {
			keepLook(player);
			holdDialogueAlert(player, nextAlerts);
		}
		holdHeadNotice(player, story, nextAlerts, activeNotices, !isLocked(player));
	}

	private static void scanInventory(ServerPlayer player, SpecialStoryData.SubjectStory story, SpecialStoryData data) {
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		if (!selection.isGuideIntroDone()) {
			return;
		}
		boolean logs = false;
		boolean copper = false;
		boolean nether = false;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(ItemTags.LOGS)) {
				logs = true;
			}
			if (stack.is(Items.COPPER_INGOT) || stack.is(Items.COPPER_BLOCK) || stack.is(Items.RAW_COPPER)) {
				copper = true;
			}
			if (stack.is(Items.OBSIDIAN) || stack.is(Items.FLINT_AND_STEEL)) {
				nether = true;
			}
		}
		boolean changed = false;
		if (logs && !story.woodTalked) {
			story.queue(SpecialStoryKind.GUIDE_WOOD);
			changed = true;
		}
		if (copper && !story.copperTalked) {
			story.queue(SpecialStoryKind.GUIDE_COPPER);
			changed = true;
		}
		if (nether && !story.netherTalked) {
			story.queue(SpecialStoryKind.GUIDE_NETHER);
			changed = true;
		}
		if (changed) {
			data.setDirty();
		}
	}

	private static void holdHeadNotice(
			ServerPlayer player,
			SpecialStoryData.SubjectStory story,
			Set<UUID> nextAlerts,
			Set<String> activeNotices,
			boolean announce
	) {
		SpecialStoryKind kind = story.nextPending();
		if (kind == null) {
			return;
		}
		UUID subjectId = PbsAdapter.resolveSubject(player);
		String prefix = noticePrefix(subjectId, kind);
		RefugeeSpecialRole role = roleFor(player, kind, story);
		Villager villager = role == null ? null : findSpecial(player, role);
		if (villager == null) {
			retainNotice(prefix, activeNotices);
			return;
		}
		releaseStoryApproach(villager);
		RefugeeAttachments.get(villager).setStoryAlert(true);
		nextAlerts.add(villager.getUUID());
		String key = prefix + villager.getUUID();
		activeNotices.add(key);
		if (announce) {
			announcePending(player, villager, role, key);
		}
	}

	private static void holdDialogueAlert(ServerPlayer player, Set<UUID> nextAlerts) {
		DialogueLock lock = LOCKS.get(player.getUUID());
		if (lock == null || lock.story() == null) {
			return;
		}
		Villager villager = villagerOf(player, lock.entityId());
		if (villager == null) {
			return;
		}
		releaseStoryApproach(villager);
		RefugeeAttachments.get(villager).setStoryAlert(true);
		nextAlerts.add(villager.getUUID());
	}

	private static void retainNotice(String prefix, Set<String> activeNotices) {
		for (String key : ANNOUNCED.keySet()) {
			if (key.startsWith(prefix)) {
				activeNotices.add(key);
			}
		}
	}

	private static String noticePrefix(UUID subjectId, SpecialStoryKind kind) {
		return subjectId + "\0" + kind.id() + "\0";
	}

	private static void announcePending(ServerPlayer player, Villager villager, RefugeeSpecialRole role, String key) {
		if (player == null || player.getServer() == null || villager == null || role == null || key == null) {
			return;
		}
		Set<UUID> told = ANNOUNCED.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet());
		Component message = Component.translatable(
				"chat.refugee.story.pending",
				whereText(villager),
				Component.translatable("role.refugee." + role.id())
		);
		for (ServerPlayer member : playersOf(player.getServer(), PbsAdapter.resolveSubject(player))) {
			if (told.add(member.getUUID())) {
				member.sendSystemMessage(message);
			}
		}
	}

	private static Component whereText(Villager villager) {
		BlockPos pos = villager.blockPosition();
		ResourceKey<Level> dimension = villager.level().dimension();
		if (Level.NETHER.equals(dimension)) {
			return Component.translatable("chat.refugee.story.where.nether", pos.getX(), pos.getY(), pos.getZ());
		}
		if (Level.END.equals(dimension)) {
			return Component.translatable("chat.refugee.story.where.end", pos.getX(), pos.getY(), pos.getZ());
		}
		if (Level.OVERWORLD.equals(dimension)) {
			return Component.translatable("chat.refugee.story.where.overworld", pos.getX(), pos.getY(), pos.getZ());
		}
		String name = dimension == null ? "" : dimension.location().toString();
		return Component.literal(name + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
	}

	private static void openPendingStory(
			ServerPlayer player,
			Villager villager,
			SpecialStoryKind kind,
			SpecialStoryData.SubjectStory story,
			SpecialStoryData data
	) {
		if (!tryLock(player, villager, kind)) {
			return;
		}
		if (kind == SpecialStoryKind.NURSE_INJURY || kind == SpecialStoryKind.NURSE_DEATH || kind == SpecialStoryKind.NURSE_HEAL) {
			NurseService.heal(player, villager, true);
			story.pendingNurseHeal = false;
			if (kind == SpecialStoryKind.NURSE_DEATH) {
				story.pendingNurseDeath = false;
			}
		}
		int step = Math.min(Math.max(0, kind.lines() - 1), story.resumeStep(kind));
		String prepend = consumePrepend(player, kind, story);
		data.setDirty();
		openStory(player, villager, kind, step, prepend);
		broadcastLine(player, villager, prepend != null ? prepend : lineAt(player, kind, step));
	}

	private static String consumePrepend(ServerPlayer player, SpecialStoryKind kind, SpecialStoryData.SubjectStory story) {
		RefugeeSpecialRole role = kind == null ? null : kind.role();
		if (role == RefugeeSpecialRole.NURSE) {
			if (story.nurseApology) {
				story.nurseApology = false;
				return NURSE_BUSY_KEY;
			}
			if (story.interruptCount(kind) == 1) {
				return player.getRandom().nextBoolean() ? NURSE_INTERRUPT_0 : NURSE_INTERRUPT_1;
			}
		}
		if (role == RefugeeSpecialRole.CARTOGRAPHER && story.cartoLine) {
			story.cartoLine = false;
			return CARTO_INTERRUPT_KEY;
		}
		if (role == RefugeeSpecialRole.ENCHANTER && story.enchanterEllipsis) {
			story.enchanterEllipsis = false;
			return ENCHANTER_INTERRUPT_KEY;
		}
		if (role == RefugeeSpecialRole.GUIDE && story.interruptCount(kind) > 0) {
			return GuideTutorialService.interruptKey(player);
		}
		return null;
	}

	private static String lineAt(ServerPlayer player, SpecialStoryKind kind, int step) {
		if (kind == SpecialStoryKind.NURSE_HEAL) {
			return "screen.refugee.splash.nurse.healed";
		}
		return kind.lineKey(step);
	}

	private static boolean isStorySilent(SpecialStoryData.SubjectStory story, SpecialStoryKind kind, long now) {
		if (kind == null || kind.role() == null) {
			return false;
		}
		if (kind.role() == RefugeeSpecialRole.ENCHANTER) {
			return now < story.enchanterSilentUntil;
		}
		if (kind.role() == RefugeeSpecialRole.GUIDE) {
			return now < story.guideSilentUntil;
		}
		return false;
	}

	private static RefugeeSpecialRole roleFor(ServerPlayer player, SpecialStoryKind kind, SpecialStoryData.SubjectStory story) {
		if (kind == SpecialStoryKind.DRAGON_GLORY) {
			return firstLoadedSpecial(player);
		}
		if (kind == SpecialStoryKind.DRAGON_VANILLA) {
			return nextVanillaRole(player, story);
		}
		return kind.role();
	}

	private static RefugeeSpecialRole nextVanillaRole(ServerPlayer player, SpecialStoryData.SubjectStory story) {
		for (RefugeeSpecialRole role : RefugeeSpecialRole.values()) {
			if ((story.vanillaDragonMask & (1 << role.ordinal())) != 0) {
				continue;
			}
			if (findSpecial(player, role) != null) {
				return role;
			}
		}
		return null;
	}

	private static RefugeeSpecialRole firstLoadedSpecial(ServerPlayer player) {
		for (RefugeeSpecialRole role : RefugeeSpecialRole.values()) {
			if (findSpecial(player, role) != null) {
				return role;
			}
		}
		return null;
	}

	private static void openStory(ServerPlayer player, Villager villager, SpecialStoryKind kind, int step, String prepend) {
		releaseStoryApproach(villager);
		SpecialRefugeeService.lookAtPlayer(villager, player);
		LOCKS.put(player.getUUID(), new DialogueLock(villager.getId(), kind, step));
		RefugeeNetworking.openStorySplash(player, villager, kind, step, lineAt(player, kind, step), prepend);
	}

	private static void applyStoryLine(ServerPlayer player, Villager villager, SpecialStoryKind kind, int step) {
		if (kind == SpecialStoryKind.CARTO_FIRST && step == 4) {
			RefugeeNetworking.openTerritoryMap(player, villager, TerritoryMapService.DEFAULT_RADIUS);
		}
		if (kind == SpecialStoryKind.CARTO_RETURN && step == 3) {
			ExplorerMapService.giveRandom(player);
		}
		if (kind == SpecialStoryKind.CARTO_ANCIENT && step == 2) {
			ExplorerMapService.giveAncientCity(player);
		}
	}

	private static void finishStory(ServerPlayer player, Villager villager, SpecialStoryKind kind) {
		releaseStoryApproach(villager);
		if (player == null || player.getServer() == null) {
			unlock(player);
			return;
		}
		UUID subjectId = PbsAdapter.resolveSubject(player);
		SpecialStoryData data = SpecialStoryData.get(player.getServer());
		SpecialStoryData.SubjectStory story = progress(player);
		story.clearInterrupt(kind);
		if (kind == SpecialStoryKind.DRAGON_VANILLA) {
			RefugeeSpecialRole role = villager == null ? null : RefugeeSpecialRole.of(villager);
			if (role != null) {
				story.vanillaDragonMask |= 1 << role.ordinal();
			}
			boolean all = true;
			for (RefugeeSpecialRole special : RefugeeSpecialRole.values()) {
				if ((story.vanillaDragonMask & (1 << special.ordinal())) == 0 && findSpecial(player.getServer(), subjectId, special) != null) {
					all = false;
					break;
				}
			}
			if (all) {
				story.markTalked(kind);
				story.dequeue(kind);
			}
		} else {
			story.markTalked(kind);
			story.dequeue(kind);
		}
		if (kind == SpecialStoryKind.ENCHANTER_BOOK && story.pendingDivine) {
			story.pendingDivine = false;
			data.setDirty();
			unlock(player);
			if (villager != null) {
				EnchanterTrades.divine(player, villager);
			}
			return;
		}
		if (kind == SpecialStoryKind.ENCHANTER_ARRIVAL && !story.enchanterBookTalked) {
			story.queue(SpecialStoryKind.ENCHANTER_BOOK);
			data.setDirty();
			unlock(player);
			return;
		}
		if (kind == SpecialStoryKind.DRAGON_GLORY) {
			banish(player.getServer(), subjectId);
		}
		data.setDirty();
		unlock(player);
		if (villager != null && kind != SpecialStoryKind.DRAGON_GLORY && kind != SpecialStoryKind.NURSE_HEAL) {
			RefugeeNetworking.openSpecialSplash(player, villager);
			tryLock(player, villager, null);
		}
	}

	private static void tickGlory(MinecraftServer server) {
		if (!GloryCompat.isBossCleared(server)) {
			return;
		}
		SpecialStoryData data = SpecialStoryData.get(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (orgBanished(player)) {
				continue;
			}
			SpecialStoryData.SubjectStory story = progress(player);
			if (story.dragonGloryDone) {
				continue;
			}
			story.queue(SpecialStoryKind.DRAGON_GLORY);
		}
		data.setDirty();
	}

	private static void tickCartographerLeave(MinecraftServer server) {
		ServerLevel clock = server.overworld();
		if (clock == null) {
			return;
		}
		long day = Math.floorDiv(clock.getDayTime(), 24000L);
		long time = Math.floorMod(clock.getDayTime(), 24000L);
		if (time < CARTOGRAPHER_LEAVE_TIME) {
			return;
		}
		SpecialStoryData data = SpecialStoryData.get(server);
		boolean changed = false;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID subjectId = PbsAdapter.resolveSubject(player);
			SpecialStoryData.SubjectStory story = data.of(subjectId);
			if (story.banished || story.cartographerAway || !story.cartographerAppeared) {
				continue;
			}
			if (story.cartographerSpawnDay >= day) {
				continue;
			}
			Villager cartographer = findSpecial(server, subjectId, RefugeeSpecialRole.CARTOGRAPHER);
			if (cartographer == null) {
				continue;
			}
			leaveCartographer(server, subjectId, cartographer, story);
			changed = true;
		}
		if (changed) {
			data.setDirty();
		}
	}

	private static void tickFinale(MinecraftServer server) {
		SpecialStoryData data = SpecialStoryData.get(server);
		for (UUID subjectId : List.copyOf(data.subjects().keySet())) {
			SpecialStoryData.SubjectStory story = data.of(subjectId);
			if (story.banished && !story.finaleDone) {
				tryWriteFinale(server, subjectId);
			}
		}
	}

	private static void leaveCartographer(
			MinecraftServer server,
			UUID subjectId,
			Villager cartographer,
			SpecialStoryData.SubjectStory story
	) {
		story.cartographerAway = true;
		SpecialRefugeeService.markSpecialGone(server, cartographer.getUUID());
		SpecialRefugeeService.onSpecialRemoved(server, cartographer.getUUID());
		cartographer.discard();
	}

	public static void banish(MinecraftServer server, UUID subjectId) {
		if (server == null || subjectId == null) {
			return;
		}
		SpecialStoryData data = SpecialStoryData.get(server);
		SpecialStoryData.SubjectStory story = data.of(subjectId);
		story.banished = true;
		story.dragonGloryDone = true;
		story.dequeue(SpecialStoryKind.DRAGON_GLORY);
		data.setDirty();
		for (RefugeeSpecialRole role : RefugeeSpecialRole.values()) {
			Villager villager = findSpecial(server, subjectId, role);
			if (villager == null) {
				continue;
			}
			SpecialRefugeeService.markSpecialGone(server, villager.getUUID());
			SpecialRefugeeService.onSpecialRemoved(server, villager.getUUID());
			villager.discard();
		}
		tryWriteFinale(server, subjectId);
	}

	private static void tryWriteFinale(MinecraftServer server, UUID subjectId) {
		SpecialStoryData data = SpecialStoryData.get(server);
		SpecialStoryData.SubjectStory story = data.of(subjectId);
		if (story.finaleDone && hasFinaleBook(server, story)) {
			return;
		}
		SpecialStoryData.AltarRecord chosen = earliestAltar(server, story);
		if (chosen == null) {
			return;
		}
		ServerLevel level = server.getLevel(chosen.dimensionKey());
		if (level == null || !level.isLoaded(chosen.pos())) {
			return;
		}
		if (!(level.getBlockEntity(chosen.pos()) instanceof AltarBlockEntity altar)) {
			return;
		}
		ItemStack book = finaleBook();
		boolean placed = false;
		for (int slot = 0; slot < altar.getContainerSize(); slot++) {
			if (altar.getItem(slot).isEmpty()) {
				altar.setItem(slot, book);
				placed = true;
				break;
			}
		}
		if (!placed) {
			altar.setItem(0, book);
		}
		altar.setFinaleBeam(true);
		altar.setChanged();
		level.sendBlockUpdated(chosen.pos(), altar.getBlockState(), altar.getBlockState(), 3);
		story.finaleDone = true;
		data.setDirty();
	}

	private static boolean hasFinaleBook(MinecraftServer server, SpecialStoryData.SubjectStory story) {
		for (SpecialStoryData.AltarRecord record : story.altars) {
			ServerLevel level = server.getLevel(record.dimensionKey());
			if (level == null || !level.isLoaded(record.pos())) {
				continue;
			}
			if (level.getBlockEntity(record.pos()) instanceof AltarBlockEntity altar && altar.hasFinaleBeam()) {
				return true;
			}
		}
		return false;
	}

	private static SpecialStoryData.AltarRecord earliestAltar(MinecraftServer server, SpecialStoryData.SubjectStory story) {
		SpecialStoryData.AltarRecord best = null;
		Iterator<SpecialStoryData.AltarRecord> iterator = story.altars.iterator();
		while (iterator.hasNext()) {
			SpecialStoryData.AltarRecord record = iterator.next();
			ServerLevel level = server.getLevel(record.dimensionKey());
			if (level == null) {
				continue;
			}
			if (level.isLoaded(record.pos()) && !level.getBlockState(record.pos()).is(ModBlocks.ALTAR)) {
				iterator.remove();
				continue;
			}
			if (best == null || record.placedAt() < best.placedAt()) {
				best = record;
			}
		}
		return best;
	}

	private static ItemStack finaleBook() {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> pages = List.of(
				Filterable.passThrough(Component.translatable("book.refugee.finale.page.0")),
				Filterable.passThrough(Component.translatable("book.refugee.finale.page.1"))
		);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
				Filterable.passThrough(Component.translatable("book.refugee.finale.title").getString()),
				Component.translatable("book.refugee.finale.author").getString(),
				0,
				pages,
				true
		));
		return book;
	}

	private static void broadcastLine(ServerPlayer player, Villager villager, String key) {
		if (player == null || player.getServer() == null || key == null || key.isBlank()) {
			return;
		}
		RefugeeSpecialRole role = villager == null ? null : RefugeeSpecialRole.of(villager);
		Component line = Component.translatable(key);
		Component message = Component.translatable(
				"chat.refugee.story.line",
				Component.translatable("role.refugee." + (role == null ? "guide" : role.id())),
				line
		);
		player.sendSystemMessage(message);
	}

	private static void releaseStoryApproach(Villager villager) {
		if (villager == null) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (!data.isStoryApproach()) {
			return;
		}
		data.clearStoryApproach();
		data.stopFollowing();
		villager.getNavigation().stop();
		RefugeeAttachments.markDirty(villager, data);
	}

	private static void keepLook(ServerPlayer player) {
		DialogueLock lock = LOCKS.get(player.getUUID());
		if (lock == null) {
			return;
		}
		Villager villager = villagerOf(player, lock.entityId());
		if (villager != null) {
			SpecialRefugeeService.lookAtPlayer(villager, player);
		}
	}

	private static Villager villagerOf(ServerPlayer player, int entityId) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return null;
		}
		Entity entity = level.getEntity(entityId);
		return entity instanceof Villager villager ? villager : null;
	}

	private static Villager findVillager(MinecraftServer server, UUID id) {
		if (server == null || id == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(id);
			if (entity instanceof Villager villager && villager.isAlive()) {
				return villager;
			}
		}
		return null;
	}

	private static Villager findSpecial(ServerPlayer player, RefugeeSpecialRole role) {
		if (player == null || role == null || player.getServer() == null) {
			return null;
		}
		return findSpecial(player.getServer(), PbsAdapter.resolveSubject(player), role);
	}

	private static Villager findSpecial(MinecraftServer server, UUID subjectId, RefugeeSpecialRole role) {
		if (server == null || subjectId == null || role == null) {
			return null;
		}
		for (ServerPlayer member : playersOf(server, subjectId)) {
			UUID id = RefugeeAttachments.get(member).specialId(role);
			if (id == null) {
				continue;
			}
			for (ServerLevel level : server.getAllLevels()) {
				Entity entity = level.getEntity(id);
				if (entity instanceof Villager villager && villager.isAlive() && RefugeeSpecialRole.is(villager, role)) {
					return villager;
				}
			}
		}
		return null;
	}

	private static List<ServerPlayer> playersOf(MinecraftServer server, UUID subjectId) {
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

	private static void giveStack(ServerPlayer player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
		player.containerMenu.broadcastChanges();
	}

}
