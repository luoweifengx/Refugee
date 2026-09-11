package luowei.refugee.pbs;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.attachment.PlayerSelectionData;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.interact.SelectionService;
import luowei.refugee.logistics.OrgLogisticsData;
import luowei.refugee.network.RefugeeNetworking;
import luowei.refugee.special.RefugeeSpecialRole;
import luowei.refugee.staff.StaffService;
import luowei.refugee.warehouse.WarehouseLedger;

/**
 * 检测玩家 subject 从个人 UUID 变为组织 UUID（入组/组织合并），把难民与特殊 NPC 划归组织。
 */
public final class OrgMergeService {
	private static final Map<UUID, UUID> lastSubject = new ConcurrentHashMap<>();
	private static final Set<UUID> pendingDiscard = ConcurrentHashMap.newKeySet();

	private OrgMergeService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(OrgMergeService::onServerTick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler.getPlayer() != null) {
				lastSubject.remove(handler.getPlayer().getUUID());
			}
		});
	}

	public static boolean isPendingDiscard(UUID villagerId) {
		return villagerId != null && pendingDiscard.contains(villagerId);
	}

	public static void onLoadedVillager(Villager villager, ServerLevel level) {
		if (villager == null || level == null || level.getServer() == null) {
			return;
		}
		if (pendingDiscard.remove(villager.getUUID())) {
			discardSpecial(villager, level);
			return;
		}
		remapLoadedSubject(villager, level.getServer());
	}

	private static void onServerTick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(server, player);
		}
	}

	private static void tickPlayer(MinecraftServer server, ServerPlayer player) {
		UUID playerId = player.getUUID();
		UUID current = PbsAdapter.resolveSubject(player);
		if (current == null) {
			return;
		}
		UUID last = lastSubject.put(playerId, current);
		if (last == null) {
			if (!current.equals(playerId)) {
				migrateIntoOrg(server, playerId, current, player);
			}
			return;
		}
		if (last.equals(current)) {
			return;
		}
		if (current.equals(playerId)) {
			return;
		}
		migrateIntoOrg(server, last, current, player);
	}

	private static void migrateIntoOrg(MinecraftServer server, UUID fromSubject, UUID orgId, ServerPlayer trigger) {
		if (server == null || fromSubject == null || orgId == null || fromSubject.equals(orgId)) {
			return;
		}
		remapVillagerSubjects(server, fromSubject, orgId);
		OrgLogisticsData.get(server).mergeFrom(fromSubject, orgId);
		reindexWarehouses(server, orgId);
		StaffService.syncSubject(server, orgId);
		RefugeeNetworking.syncCatalogToAll(server);
		dedupeSpecialsAndRoster(server, orgId, trigger);
	}

	private static void remapLoadedSubject(Villager villager, MinecraftServer server) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		UUID subject = data.subjectId();
		if (subject == null) {
			return;
		}
		PbsAdapter.organizationOf(server, subject).ifPresent(orgId -> {
			if (!orgId.equals(subject)) {
				data.setSubjectId(orgId);
				RefugeeAttachments.markDirty(villager, data);
			}
		});
	}

	private static void remapVillagerSubjects(MinecraftServer server, UUID from, UUID to) {
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (!(entity instanceof Villager villager) || !villager.isAlive()) {
					continue;
				}
				RefugeeVillagerData data = RefugeeAttachments.get(villager);
				if (from.equals(data.subjectId())) {
					data.setSubjectId(to);
					RefugeeAttachments.markDirty(villager, data);
				}
			}
		}
	}

	private static void reindexWarehouses(MinecraftServer server, UUID orgId) {
		WarehouseLedger ledger = WarehouseLedger.instance();
		OrgLogisticsData logistics = OrgLogisticsData.get(server);
		for (OrgLogisticsData.ContainerRef ref : logistics.warehouses(orgId)) {
			ServerLevel level = null;
			for (ServerLevel candidate : server.getAllLevels()) {
				if (candidate.dimension().location().equals(ref.dimension())) {
					level = candidate;
					break;
				}
			}
			if (level == null) {
				continue;
			}
			var entity = level.getBlockEntity(ref.pos());
			if (entity instanceof net.minecraft.world.Container container) {
				ledger.rebuildChest(orgId, ref, container);
			}
		}
	}

	private static void dedupeSpecialsAndRoster(MinecraftServer server, UUID orgId, ServerPlayer trigger) {
		List<ServerPlayer> members = onlineMembers(server, orgId);
		ServerPlayer keeper = findKeeper(server, orgId, members, trigger);
		Map<RefugeeSpecialRole, UUID> keep = new EnumMap<>(RefugeeSpecialRole.class);
		if (keeper != null) {
			PlayerSelectionData keeperData = RefugeeAttachments.get(keeper);
			for (RefugeeSpecialRole role : RefugeeSpecialRole.values()) {
				UUID id = keeperData.specialId(role);
				if (id != null) {
					keep.put(role, id);
				}
			}
		}
		List<Villager> specials = loadedSpecials(server, orgId);
		for (Villager villager : specials) {
			RefugeeSpecialRole role = RefugeeSpecialRole.of(villager);
			if (role == null) {
				continue;
			}
			UUID existing = keep.get(role);
			if (existing == null) {
				keep.put(role, villager.getUUID());
			} else if (!existing.equals(villager.getUUID())) {
				discardSpecial(villager, (ServerLevel) villager.level());
			}
		}
		for (ServerPlayer member : members) {
			PlayerSelectionData data = RefugeeAttachments.get(member);
			for (RefugeeSpecialRole role : RefugeeSpecialRole.values()) {
				UUID bound = data.specialId(role);
				if (bound != null && !bound.equals(keep.get(role))) {
					queueDiscard(server, bound);
					data.clearSpecialBinding(bound);
				}
			}
		}
		PlayerSelectionData master = new PlayerSelectionData();
		for (ServerPlayer member : members) {
			master.absorbRoster(RefugeeAttachments.get(member));
		}
		if (keeper != null) {
			PlayerSelectionData keeperData = RefugeeAttachments.get(keeper);
			keeperData.copySpecialBindingsFrom(keeperData);
			master.copySpecialBindingsFrom(keeperData);
			for (Map.Entry<RefugeeSpecialRole, UUID> entry : keep.entrySet()) {
				if (master.specialId(entry.getKey()) == null) {
					master.bindSpecial(entry.getKey(), entry.getValue());
				}
			}
		} else {
			for (Map.Entry<RefugeeSpecialRole, UUID> entry : keep.entrySet()) {
				master.bindSpecial(entry.getKey(), entry.getValue());
			}
		}
		for (ServerPlayer member : members) {
			PlayerSelectionData data = RefugeeAttachments.get(member);
			data.absorbRoster(master);
			data.copySpecialBindingsFrom(master);
			RefugeeAttachments.markDirty(member, data);
		}
	}

	private static ServerPlayer findKeeper(
			MinecraftServer server,
			UUID orgId,
			List<ServerPlayer> members,
			ServerPlayer trigger
	) {
		UUID ownerId = PbsAdapter.organizationOwner(server, orgId).orElse(null);
		if (ownerId != null) {
			ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
			if (owner != null) {
				return owner;
			}
		}
		ServerPlayer best = null;
		int bestCount = -1;
		for (ServerPlayer member : members) {
			int count = RefugeeAttachments.get(member).specialBindingCount();
			if (count == 4) {
				return member;
			}
			if (count > bestCount) {
				bestCount = count;
				best = member;
			}
		}
		return best != null ? best : trigger;
	}

	private static List<ServerPlayer> onlineMembers(MinecraftServer server, UUID orgId) {
		List<ServerPlayer> result = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (orgId.equals(PbsAdapter.resolveSubject(player))) {
				result.add(player);
			}
		}
		return result;
	}

	private static List<Villager> loadedSpecials(MinecraftServer server, UUID orgId) {
		List<Villager> result = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (!(entity instanceof Villager villager) || !villager.isAlive()) {
					continue;
				}
				if (!RefugeeSpecialRole.isSpecial(villager)) {
					continue;
				}
				if (orgId.equals(RefugeeAttachments.get(villager).subjectId())) {
					result.add(villager);
				}
			}
		}
		return result;
	}

	private static void queueDiscard(MinecraftServer server, UUID villagerId) {
		if (villagerId == null) {
			return;
		}
		Entity entity = findLoaded(server, villagerId);
		if (entity instanceof Villager villager && villager.level() instanceof ServerLevel level) {
			discardSpecial(villager, level);
			return;
		}
		pendingDiscard.add(villagerId);
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

	private static void discardSpecial(Villager villager, ServerLevel level) {
		if (villager == null || level == null) {
			return;
		}
		pendingDiscard.remove(villager.getUUID());
		level.sendParticles(
				ParticleTypes.EXPLOSION,
				villager.getX(),
				villager.getY() + villager.getBbHeight() * 0.5,
				villager.getZ(),
				8,
				0.2,
				0.2,
				0.2,
				0.01
		);
		level.playSound(
				null,
				villager.getX(),
				villager.getY(),
				villager.getZ(),
				SoundEvents.GENERIC_EXPLODE.value(),
				SoundSource.NEUTRAL,
				0.6F,
				1.0F
		);
		UUID id = villager.getUUID();
		villager.discard();
		SelectionService.onVillagerRemoved(id, level);
	}
}
