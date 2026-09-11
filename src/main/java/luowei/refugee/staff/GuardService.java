package luowei.refugee.staff;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import luowei.refugee.attachment.PlayerSelectionData;
import luowei.refugee.attachment.PlayerSelectionData.RosterEntry;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.interact.SelectionService;

/**
 * 玩家卫队编制：跟随者打标/除名，范围召集与按名册强制加载的全部召集。
 * 不改变普通集结过滤。
 */
public final class GuardService {
	private static final int LOAD_TIMEOUT_TICKS = 200;
	private static final int LOAD_RADIUS = 2;
	private static final int LOCATION_INTERVAL = 80;
	private static final Map<UUID, PendingRally> PENDING = new ConcurrentHashMap<>();

	private GuardService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GuardService::onServerTick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof Villager villager && world instanceof ServerLevel level) {
				onVillagerLoaded(villager, level);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> releasePending(handler.getPlayer().getUUID(), server));
	}

	public static int addFromFollowing(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return -1;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		List<UUID> selected = selection.snapshotSelected();
		if (selected.isEmpty()) {
			return -1;
		}
		int added = 0;
		boolean anyFollow = false;
		for (UUID villagerId : selected) {
			Entity entity = level.getEntity(villagerId);
			if (!(entity instanceof Villager villager) || !villager.isAlive()) {
				continue;
			}
			RefugeeVillagerData data = RefugeeAttachments.get(villager);
			if (!data.isFollowing() || !player.getUUID().equals(data.followPlayerId())) {
				continue;
			}
			if (!SelectionService.canCommand(player, villager)) {
				continue;
			}
			anyFollow = true;
			if (data.isGuardOf(player.getUUID())) {
				selection.addGuard(villager);
				RefugeeAttachments.markDirty(player, selection);
				continue;
			}
			stealMark(player, villager, data);
			data.setGuardMark(player.getUUID());
			selection.addGuard(villager);
			RefugeeAttachments.markDirty(villager, data);
			RefugeeAttachments.markDirty(player, selection);
			added++;
		}
		if (!anyFollow) {
			return -1;
		}
		return added;
	}

	public static int removeFromFollowing(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return -1;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		List<UUID> selected = selection.snapshotSelected();
		if (selected.isEmpty()) {
			return -1;
		}
		int removed = 0;
		boolean anyFollow = false;
		for (UUID villagerId : selected) {
			Entity entity = level.getEntity(villagerId);
			if (!(entity instanceof Villager villager) || !villager.isAlive()) {
				continue;
			}
			RefugeeVillagerData data = RefugeeAttachments.get(villager);
			if (!data.isFollowing() || !player.getUUID().equals(data.followPlayerId())) {
				continue;
			}
			anyFollow = true;
			if (!data.isGuardOf(player.getUUID())) {
				continue;
			}
			data.clearGuardMark();
			selection.removeGuard(villagerId);
			RefugeeAttachments.markDirty(villager, data);
			RefugeeAttachments.markDirty(player, selection);
			removed++;
		}
		if (!anyFollow) {
			return -1;
		}
		return removed;
	}

	public static int rallyNear(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}
		double radius = RefugeeConfig.hornBellRadius;
		AABB box = player.getBoundingBox().inflate(radius);
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, box, Villager::isAlive);
		int count = 0;
		for (Villager villager : villagers) {
			if (villager.distanceTo(player) > radius) {
				continue;
			}
			RefugeeVillagerData data = RefugeeAttachments.get(villager);
			if (!data.isGuardOf(player.getUUID())) {
				continue;
			}
			if (!SelectionService.canCommand(player, villager)) {
				continue;
			}
			if (SelectionService.follow(player, villager)) {
				count++;
			}
		}
		return count;
	}

	public static RallyAllStart rallyAll(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return RallyAllStart.EMPTY;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		List<RosterEntry> members = new ArrayList<>(selection.guardEntries());
		if (members.isEmpty()) {
			return RallyAllStart.EMPTY;
		}
		releasePending(player.getUUID(), server);
		int immediate = 0;
		List<PendingLoad> pending = new ArrayList<>();
		boolean rosterChanged = false;
		for (RosterEntry entry : members) {
			if (entry == null || entry.villagerId() == null) {
				continue;
			}
			Villager villager = findLoaded(server, entry.villagerId());
			if (villager != null && villager.isAlive()) {
				if (!RefugeeAttachments.get(villager).isGuardOf(player.getUUID())) {
					selection.removeGuard(entry.villagerId());
					rosterChanged = true;
					continue;
				}
				if (SelectionService.follow(player, villager)) {
					selection.updateGuardLocation(
							villager.getUUID(),
							villager.level().dimension().location(),
							villager.blockPosition()
					);
					rosterChanged = true;
					immediate++;
				}
				continue;
			}
			if (entry.pos() == null) {
				continue;
			}
			pending.add(new PendingLoad(entry.villagerId(), entry.dimension(), entry.pos(), LOAD_TIMEOUT_TICKS));
		}
		if (rosterChanged) {
			RefugeeAttachments.markDirty(player, selection);
		}
		if (!pending.isEmpty()) {
			PENDING.put(player.getUUID(), new PendingRally(player.getUUID(), immediate, pending));
		}
		return new RallyAllStart(immediate, pending.size(), false);
	}

	public static void onVillagerRemoved(UUID villagerId, MinecraftServer server) {
		if (villagerId == null || server == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PlayerSelectionData data = RefugeeAttachments.get(player);
			if (data.removeGuard(villagerId)) {
				RefugeeAttachments.markDirty(player, data);
			}
		}
		for (PendingRally rally : PENDING.values()) {
			rally.pending.removeIf(load -> villagerId.equals(load.villagerId));
		}
	}

	public static void tickLocation(Villager villager) {
		if (villager == null || villager.tickCount % LOCATION_INTERVAL != 0 || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		UUID ownerId = data.guardMarkPlayerId();
		if (ownerId == null) {
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
		if (owner == null) {
			return;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(owner);
		if (!selection.hasGuard(villager.getUUID())) {
			selection.addGuard(villager);
		} else {
			selection.updateGuardLocation(villager.getUUID(), level.dimension().location(), villager.blockPosition());
		}
		RefugeeAttachments.markDirty(owner, selection);
	}

	private static void onVillagerLoaded(Villager villager, ServerLevel level) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		UUID ownerId = data.guardMarkPlayerId();
		if (ownerId == null) {
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
		if (owner != null) {
			PlayerSelectionData selection = RefugeeAttachments.get(owner);
			selection.addGuard(villager);
			RefugeeAttachments.markDirty(owner, selection);
		}
		PendingRally rally = PENDING.get(ownerId);
		if (rally == null) {
			return;
		}
		Iterator<PendingLoad> iterator = rally.pending.iterator();
		while (iterator.hasNext()) {
			PendingLoad load = iterator.next();
			if (!villager.getUUID().equals(load.villagerId)) {
				continue;
			}
			releaseTicket(level.getServer(), load);
			ServerPlayer rallyPlayer = owner != null ? owner : level.getServer().getPlayerList().getPlayer(rally.playerId);
			if (tryFollow(rallyPlayer, villager)) {
				rally.found++;
			} else {
				rally.missing++;
			}
			iterator.remove();
			return;
		}
	}

	private static void onServerTick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		List<UUID> finished = new ArrayList<>();
		for (PendingRally rally : PENDING.values()) {
			tickPending(server, rally);
			if (rally.pending.isEmpty()) {
				finished.add(rally.playerId);
			}
		}
		for (UUID playerId : finished) {
			PendingRally rally = PENDING.remove(playerId);
			if (rally == null) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null) {
				continue;
			}
			if (rally.missing > 0) {
				player.displayClientMessage(Component.translatable(
						"message.refugee.staff.guard.all.partial",
						rally.found,
						rally.missing
				), true);
			} else {
				player.displayClientMessage(Component.translatable("message.refugee.staff.guard.all.done", rally.found), true);
			}
		}
	}

	private static void tickPending(MinecraftServer server, PendingRally rally) {
		ServerPlayer player = server.getPlayerList().getPlayer(rally.playerId);
		Iterator<PendingLoad> iterator = rally.pending.iterator();
		while (iterator.hasNext()) {
			PendingLoad load = iterator.next();
			load.ticksLeft--;
			ServerLevel level = levelOf(server, load.dimension);
			if (level == null) {
				rally.missing++;
				iterator.remove();
				continue;
			}
			ensureTicket(level, load);
			level.getChunk(load.chunk().x, load.chunk().z);
			Villager villager = findIn(level, load.villagerId);
			if (villager == null) {
				villager = findLoaded(server, load.villagerId);
			}
			if (villager != null && villager.isAlive()) {
				releaseTicket(server, load);
				if (tryFollow(player, villager)) {
					rally.found++;
				} else {
					rally.missing++;
				}
				iterator.remove();
				continue;
			}
			if (load.ticksLeft <= 0) {
				releaseTicket(server, load);
				rally.missing++;
				iterator.remove();
			}
		}
	}

	private static boolean tryFollow(ServerPlayer player, Villager villager) {
		if (player == null || villager == null || !villager.isAlive()) {
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (!data.isGuardOf(player.getUUID())) {
			PlayerSelectionData selection = RefugeeAttachments.get(player);
			if (selection.removeGuard(villager.getUUID())) {
				RefugeeAttachments.markDirty(player, selection);
			}
			return false;
		}
		return SelectionService.follow(player, villager);
	}

	private static void stealMark(ServerPlayer newOwner, Villager villager, RefugeeVillagerData data) {
		UUID previous = data.guardMarkPlayerId();
		if (previous == null || previous.equals(newOwner.getUUID())) {
			return;
		}
		MinecraftServer server = newOwner.level().getServer();
		if (server == null) {
			return;
		}
		ServerPlayer other = server.getPlayerList().getPlayer(previous);
		if (other == null) {
			return;
		}
		PlayerSelectionData otherSelection = RefugeeAttachments.get(other);
		if (otherSelection.removeGuard(villager.getUUID())) {
			RefugeeAttachments.markDirty(other, otherSelection);
		}
	}

	private static void ensureTicket(ServerLevel level, PendingLoad load) {
		if (load.ticketed) {
			return;
		}
		level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, load.chunk(), LOAD_RADIUS);
		load.ticketed = true;
		load.ticketDimension = level.dimension().location();
	}

	private static void releaseTicket(MinecraftServer server, PendingLoad load) {
		if (!load.ticketed || server == null) {
			return;
		}
		ServerLevel level = levelOf(server, load.ticketDimension == null ? load.dimension : load.ticketDimension);
		if (level != null) {
			level.getChunkSource().removeTicketWithRadius(TicketType.PORTAL, load.chunk(), LOAD_RADIUS);
		}
		load.ticketed = false;
	}

	private static void releasePending(UUID playerId, MinecraftServer server) {
		PendingRally rally = PENDING.remove(playerId);
		if (rally == null) {
			return;
		}
		for (PendingLoad load : rally.pending) {
			releaseTicket(server, load);
		}
	}

	private static Villager findLoaded(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			Villager villager = findIn(level, id);
			if (villager != null) {
				return villager;
			}
		}
		return null;
	}

	private static Villager findIn(ServerLevel level, UUID id) {
		Entity entity = level.getEntity(id);
		if (entity instanceof Villager villager && villager.isAlive()) {
			return villager;
		}
		return null;
	}

	private static ServerLevel levelOf(MinecraftServer server, ResourceLocation dimension) {
		if (dimension == null) {
			return server.overworld();
		}
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
		ServerLevel level = server.getLevel(key);
		return level == null ? server.overworld() : level;
	}

	public record RallyAllStart(int immediate, int pending, boolean empty) {
		public static final RallyAllStart EMPTY = new RallyAllStart(0, 0, true);

		public boolean hasWork() {
			return !empty && (immediate > 0 || pending > 0);
		}
	}

	private static final class PendingRally {
		private final UUID playerId;
		private int found;
		private int missing;
		private final List<PendingLoad> pending;

		private PendingRally(UUID playerId, int found, List<PendingLoad> pending) {
			this.playerId = playerId;
			this.found = found;
			this.pending = pending;
		}
	}

	private static final class PendingLoad {
		private final UUID villagerId;
		private final ResourceLocation dimension;
		private final BlockPos pos;
		private int ticksLeft;
		private boolean ticketed;
		private ResourceLocation ticketDimension;

		private PendingLoad(UUID villagerId, ResourceLocation dimension, BlockPos pos, int ticksLeft) {
			this.villagerId = villagerId;
			this.dimension = dimension;
			this.pos = pos.immutable();
			this.ticksLeft = ticksLeft;
		}

		private ChunkPos chunk() {
			return new ChunkPos(pos);
		}
	}
}
