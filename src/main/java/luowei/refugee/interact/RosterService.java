package luowei.refugee.interact;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

import luowei.refugee.Refugee;
import luowei.refugee.attachment.PlayerSelectionData;
import luowei.refugee.attachment.PlayerSelectionData.RosterEntry;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.config.RefugeePlayDifficulty;
import luowei.refugee.settle.StandableFinder;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.pbs.OrgMergeService;
import luowei.refugee.special.RefugeeSpecialRole;
import luowei.refugee.special.SpecialRefugeeService;

/**
 * 玩家难民名册、按原版难度发放开局难民、死亡扣一人延迟击杀、空名册旁观失败。
 */
public final class RosterService {
	private static final Map<UUID, Integer> pendingStarters = new ConcurrentHashMap<>();
	private static final Set<UUID> queuedKills = ConcurrentHashMap.newKeySet();

	private RosterService() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> onRespawn(newPlayer));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				onPlayerDeath(player);
			}
		});
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, conversionContext) -> {
			if (previous instanceof Villager villager && previous.level() instanceof ServerLevel level) {
				if (RefugeeSpecialRole.isSpecial(villager)) {
					SpecialRefugeeService.markSpecialGone(level.getServer(), villager.getUUID());
				}
				SelectionService.onVillagerRemoved(previous.getUUID(), level);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(world instanceof ServerLevel level)) {
				return;
			}
			onEntityLoad(entity, level);
		});
		ServerTickEvents.END_SERVER_TICK.register(RosterService::onServerTick);
	}

	public static void registerOwned(ServerPlayer player, Villager villager) {
		if (player == null || villager == null) {
			return;
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		data.addRoster(villager);
		RefugeeAttachments.markDirty(player, data);
	}

	/**
	 * 入境/指令：记入该主体下所有在线成员的名册（组织 UUID 时每人一份同一批人）。
	 */
	public static void registerOwnedIfPlayer(MinecraftServer server, UUID subjectId, Villager villager) {
		if (server == null || subjectId == null || villager == null) {
			return;
		}
		boolean any = false;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (subjectId.equals(player.getUUID()) || subjectId.equals(PbsAdapter.resolveSubject(player))) {
				registerOwned(player, villager);
				any = true;
			}
		}
		if (any) {
			return;
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(subjectId);
		if (owner != null) {
			registerOwned(owner, villager);
		}
	}

	public static void onVillagerGone(UUID villagerId, ServerLevel level) {
		if (villagerId == null || level == null || level.getServer() == null) {
			return;
		}
		queuedKills.remove(villagerId);
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			PlayerSelectionData data = RefugeeAttachments.get(player);
			boolean changed = data.removeRoster(villagerId);
			changed |= data.removePendingKill(villagerId);
			changed |= data.clearSpecialBinding(villagerId);
			if (changed) {
				RefugeeAttachments.markDirty(player, data);
			}
		}
	}

	private static void onJoin(ServerPlayer player) {
		if (player == null) {
			return;
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (data.isDefeated()) {
			applySpectator(player, true);
		} else if (!data.isStarterGranted()) {
			pendingStarters.putIfAbsent(player.getUUID(), 2);
		}
		queueLoadedPendingKills(player);
	}

	private static void onRespawn(ServerPlayer player) {
		if (player == null) {
			return;
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (data.isDefeated()) {
			applySpectator(player, true);
		}
		queueLoadedPendingKills(player);
	}

	private static void onPlayerDeath(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		if (data.isDefeated()) {
			return;
		}
		if (data.isRosterEmpty()) {
			data.setDefeated(true);
			RefugeeAttachments.markDirty(player, data);
			player.sendSystemMessage(Component.translatable("message.refugee.defeated"));
			return;
		}
		if (!RefugeePlayDifficulty.of(level).playerDeathSacrifices()) {
			return;
		}
		RosterEntry sacrificed = data.pollSacrificeRoster();
		if (sacrificed == null) {
			return;
		}
		data.removeSelected(sacrificed.villagerId());
		data.addPendingKill(sacrificed.villagerId());
		RefugeeAttachments.markDirty(player, data);
		SelectionService.collectBannersIfEmpty(player);
		queuedKills.add(sacrificed.villagerId());
		player.sendSystemMessage(Component.translatable("message.refugee.death.sacrifice", data.rosterSize()));
	}

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (isPendingKill(level.getServer(), entity.getUUID())) {
			if (entity instanceof LivingEntity living && living.isAlive()) {
				queuedKills.add(entity.getUUID());
			} else {
				prunePending(level.getServer(), entity.getUUID());
			}
			return;
		}
		if (!(entity instanceof Villager villager) || !villager.isAlive()) {
			return;
		}
		OrgMergeService.onLoadedVillager(villager, level);
		if (!villager.isAlive() || villager.isRemoved()) {
			return;
		}
		UUID subjectId = RefugeeAttachments.get(villager).subjectId();
		if (subjectId == null) {
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(subjectId);
		if (owner == null) {
			return;
		}
		PlayerSelectionData data = RefugeeAttachments.get(owner);
		if (!data.hasRoster(villager.getUUID())) {
			return;
		}
		data.updateRosterLocation(villager.getUUID(), level.dimension().location(), villager.blockPosition());
		RefugeeAttachments.markDirty(owner, data);
	}

	private static void onServerTick(MinecraftServer server) {
		grantPendingStarters(server);
		processQueuedKills(server);
	}

	private static void grantPendingStarters(MinecraftServer server) {
		if (pendingStarters.isEmpty()) {
			return;
		}
		pendingStarters.replaceAll((id, ticks) -> ticks - 1);
		List<UUID> due = new ArrayList<>();
		for (Map.Entry<UUID, Integer> entry : pendingStarters.entrySet()) {
			if (entry.getValue() <= 0) {
				due.add(entry.getKey());
			}
		}
		for (UUID playerId : due) {
			pendingStarters.remove(playerId);
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null) {
				continue;
			}
			PlayerSelectionData data = RefugeeAttachments.get(player);
			if (data.isStarterGranted() || data.isDefeated()) {
				continue;
			}
			grantStarterTeam(player);
		}
	}

	private static void grantStarterTeam(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			pendingStarters.putIfAbsent(player.getUUID(), 1);
			Refugee.LOGGER.debug("[refugee starter] defer player={} reason=not-server-level", player.getGameProfile().getName());
			return;
		}
		long started = System.nanoTime();
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		selection.setStarterGranted(true);
		RefugeePlayDifficulty difficulty = RefugeePlayDifficulty.of(level);
		int wanted = difficulty.starterRefugeeCount();
		boolean wantGuide = difficulty.spawnGuide();
		int spawned = 0;
		BlockPos origin = player.blockPosition();
		Refugee.LOGGER.debug(
				"[refugee starter] grant player={} difficulty={} wanted={} guide={} origin={}",
				player.getGameProfile().getName(),
				difficulty,
				wanted,
				wantGuide,
				origin.toShortString()
		);
		int needed = wanted + (wantGuide ? 1 : 0);
		if (needed <= 0) {
			RefugeeAttachments.markDirty(player, selection);
			Refugee.LOGGER.debug(
					"[refugee starter] done player={} spawned=0 guide=false skipped=hardcore",
					player.getGameProfile().getName()
			);
			return;
		}
		List<BlockPos> spots = findStarterSpots(level, origin, needed);
		Refugee.LOGGER.debug(
				"[refugee starter] spots={} needed={} first={} last={}",
				spots.size(),
				needed,
				spots.isEmpty() ? "none" : spots.getFirst().toShortString(),
				spots.isEmpty() ? "none" : spots.getLast().toShortString()
		);
		int genericSpots = Math.min(wanted, spots.size());
		for (int i = 0; i < genericSpots; i++) {
			if (spawnStarter(player, level, selection, spots.get(i))) {
				spawned++;
			}
		}
		BlockPos guideFeet = null;
		if (wantGuide) {
			guideFeet = spots.size() > genericSpots ? spots.get(genericSpots) : null;
			if (guideFeet == null && !spots.isEmpty()) {
				Set<BlockPos> reserved = new HashSet<>(spots);
				reserved.add(origin);
				List<BlockPos> extra = StandableFinder.findStandable(level, spots.getLast(), reserved, 1);
				if (!extra.isEmpty()) {
					guideFeet = extra.getFirst();
				}
			}
		}
		boolean guideSpawned = false;
		if (wantGuide && SpecialRefugeeService.shareExistingSpecial(player, RefugeeSpecialRole.GUIDE)) {
			Refugee.LOGGER.debug(
					"[refugee starter] guide skipped=org_already_has player={}",
					player.getGameProfile().getName()
			);
		} else if (wantGuide && guideFeet != null) {
			Villager guide = SpecialRefugeeService.spawnBound(player, level, guideFeet, RefugeeSpecialRole.GUIDE, true);
			if (guide != null) {
				RefugeeVillagerData data = RefugeeAttachments.get(guide);
				data.setSubjectId(player.getUUID());
				RefugeeAttachments.markDirty(guide, data);
				spawned++;
				guideSpawned = true;
				Refugee.LOGGER.debug(
						"[refugee starter] guide id={} feet={} follow=true",
						guide.getUUID().toString().substring(0, 8),
						guideFeet.toShortString()
				);
			}
		}
		RefugeeAttachments.markDirty(player, selection);
		if (wantGuide && (guideSpawned || selection.guideId() != null) && !selection.isGuideIntroDone()) {
			luowei.refugee.special.GuideTutorialService.markEligible(player, level);
		}
		if (spawned > 0) {
			SelectionService.giveBanner(player);
		} else if (wanted > 0) {
			Refugee.LOGGER.warn("Starter refugees failed to spawn for {}", player.getGameProfile().getName());
		}
		Refugee.LOGGER.debug(
				"[refugee starter] done player={} spawned={} guide={} follow=true {}ns",
				player.getGameProfile().getName(),
				spawned,
				guideSpawned,
				System.nanoTime() - started
		);
	}

	private static List<BlockPos> findStarterSpots(ServerLevel level, BlockPos origin, int needed) {
		Set<BlockPos> reserved = new HashSet<>();
		reserved.add(origin);
		return StandableFinder.findStandable(level, origin, reserved, needed);
	}

	private static boolean spawnStarter(ServerPlayer player, ServerLevel level, PlayerSelectionData selection, BlockPos feet) {
		Villager villager = EntityType.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (villager == null) {
			return false;
		}
		StandableFinder.snapToStandable(villager, level, feet, level.random.nextFloat() * 360.0f, 0.0f);
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.setSubjectId(player.getUUID());
		data.startFollowing(player.getUUID());
		RefugeeAttachments.markDirty(villager, data);
		if (!level.addFreshEntity(villager)) {
			Refugee.LOGGER.debug(
					"[refugee starter] spawn-failed feet={}",
					feet.toShortString()
			);
			return false;
		}
		selection.addSelected(villager.getUUID());
		selection.addRoster(villager);
		Refugee.LOGGER.debug(
				"[refugee starter] spawn id={} feet={} follow=true",
				villager.getUUID().toString().substring(0, 8),
				feet.toShortString()
		);
		return true;
	}

	private static void queueLoadedPendingKills(ServerPlayer player) {
		PlayerSelectionData data = RefugeeAttachments.get(player);
		for (UUID villagerId : data.snapshotPendingKills()) {
			queuedKills.add(villagerId);
		}
	}

	private static void processQueuedKills(MinecraftServer server) {
		if (queuedKills.isEmpty()) {
			return;
		}
		List<UUID> batch = new ArrayList<>(queuedKills);
		queuedKills.clear();
		for (UUID villagerId : batch) {
			if (!isPendingKill(server, villagerId)) {
				continue;
			}
			Entity entity = findLoaded(server, villagerId);
			if (entity == null) {
				continue;
			}
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				prunePending(server, villagerId);
				continue;
			}
			if (living.level() instanceof ServerLevel level) {
				level.playSound(
						null,
						living.getX(),
						living.getY(),
						living.getZ(),
						SoundEvents.ENDERMAN_DEATH,
						SoundSource.HOSTILE,
						1.0F,
						1.0F
				);
				living.kill(level);
			}
			prunePending(server, villagerId);
		}
	}

	private static boolean isPendingKill(MinecraftServer server, UUID villagerId) {
		if (server == null || villagerId == null) {
			return false;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (RefugeeAttachments.get(player).hasPendingKill(villagerId)) {
				return true;
			}
		}
		return false;
	}

	private static void prunePending(MinecraftServer server, UUID villagerId) {
		queuedKills.remove(villagerId);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PlayerSelectionData data = RefugeeAttachments.get(player);
			boolean changed = data.removePendingKill(villagerId);
			changed |= data.removeRoster(villagerId);
			changed |= data.clearSpecialBinding(villagerId);
			if (data.removeSelected(villagerId)) {
				changed = true;
				SelectionService.collectBannersIfEmpty(player);
			}
			if (changed) {
				RefugeeAttachments.markDirty(player, data);
			}
		}
	}

	private static Entity findLoaded(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(id);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}

	private static void applySpectator(ServerPlayer player, boolean announce) {
		player.setGameMode(GameType.SPECTATOR);
		if (!announce) {
			return;
		}
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.refugee.defeated")));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("subtitle.refugee.defeated")));
		player.sendSystemMessage(Component.translatable("message.refugee.defeated"));
	}
}
