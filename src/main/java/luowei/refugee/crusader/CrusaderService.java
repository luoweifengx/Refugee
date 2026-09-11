package luowei.refugee.crusader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;

import luowei.refugee.Refugee;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.compat.SiegeCompat;
import luowei.refugee.compat.SiegeCompat.ClusterView;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.settle.StandableFinder;

/**
 * 入侵集群数超过在线玩家 × 8 时，在组织领土中心刷十字军；白天 4000 消失。
 */
public final class CrusaderService {
	public static final int CLUSTER_PER_PLAYER = 8;
	public static final int DESPAWN_TIME_OF_DAY = 4000;

	private CrusaderService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CrusaderService::onServerTick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof Villager villager && world instanceof ServerLevel) {
				onCrusaderLoaded(villager);
			}
		});
	}

	private static void onServerTick(MinecraftServer server) {
		if (!SiegeCompat.isLoaded() || server.overworld() == null) {
			return;
		}
		ServerLevel overworld = server.overworld();
		long dayTime = overworld.getDayTime();
		long dayNumber = Math.floorDiv(dayTime, 24000L);
		CrusaderSavedData data = CrusaderSavedData.get(server);
		if (data.nextDespawnAt() > 0L && dayTime >= data.nextDespawnAt()) {
			discardAll(server);
			data.markDespawned(dayNumber);
		}
		if (data.lastDespawnDay() == dayNumber) {
			return;
		}
		trySpawn(server, data, dayTime);
	}

	public static long nextMorning4000(long dayTime) {
		long day = Math.floorDiv(dayTime, 24000L);
		long timeOfDay = Math.floorMod(dayTime, 24000L);
		if (timeOfDay < DESPAWN_TIME_OF_DAY) {
			return day * 24000L + DESPAWN_TIME_OF_DAY;
		}
		return (day + 1L) * 24000L + DESPAWN_TIME_OF_DAY;
	}

	private static void trySpawn(MinecraftServer server, CrusaderSavedData data, long dayTime) {
		Map<SpawnKey, Integer> counts = new HashMap<>();
		for (ClusterView cluster : SiegeCompat.clusters()) {
			if (cluster.level() == null || cluster.targetId() == null) {
				continue;
			}
			SpawnKey key = new SpawnKey(cluster.level(), cluster.targetId());
			counts.merge(key, 1, Integer::sum);
		}
		for (Map.Entry<SpawnKey, Integer> entry : counts.entrySet()) {
			SpawnKey key = entry.getKey();
			int clusters = entry.getValue();
			int players = PbsAdapter.onlinePlayerCount(server, key.subjectId());
			if (players <= 0 || clusters <= players * CLUSTER_PER_PLAYER) {
				continue;
			}
			if (data.hasSquad(key.level().dimension(), key.subjectId())) {
				continue;
			}
			int spawned = spawnSquad(key.level(), key.subjectId());
			if (spawned > 0) {
				data.markSquad(key.level().dimension(), key.subjectId(), dayTime);
				notify(server, key.subjectId(), spawned);
			}
		}
	}

	private static int spawnSquad(ServerLevel level, UUID subjectId) {
		ChunkPos center = PbsAdapter.territoryCenter(level, subjectId).orElse(null);
		if (center == null) {
			return 0;
		}
		level.getChunk(center.x, center.z);
		MinecraftServer server = level.getServer();
		SiegeCompat.refreshProgression(server, subjectId);
		CrusaderLoadout.Stage stage = CrusaderLoadout.resolve(
				SiegeCompat.hasIron(server, subjectId),
				SiegeCompat.hasDiamond(server, subjectId),
				PbsAdapter.hasDemonChunks(level)
		);
		List<CrusaderLoadout.Kit> kits = CrusaderLoadout.kits(stage);
		List<BlockPos> spots = StandableFinder.findInChunk(level, center, null, kits.size());
		int spawned = 0;
		int limit = Math.min(kits.size(), spots.size());
		for (int i = 0; i < limit; i++) {
			if (spawnOne(level, subjectId, spots.get(i), kits.get(i))) {
				spawned++;
			}
		}
		if (spawned == 0) {
			Refugee.LOGGER.debug(
					"[refugee crusader] spawn failed subject={} dim={} chunk={} wanted={}",
					subjectId,
					level.dimension().location(),
					center.x + "," + center.z,
					kits.size()
			);
		}
		return spawned;
	}

	private static boolean spawnOne(ServerLevel level, UUID subjectId, BlockPos feet, CrusaderLoadout.Kit kit) {
		Villager villager = EntityType.VILLAGER.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (villager == null) {
			return false;
		}
		StandableFinder.snapToStandable(villager, level, feet, level.random.nextFloat() * 360.0f, 0.0f);
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.setSubjectId(subjectId);
		data.setCrusader(true);
		data.setGuardCenter(feet);
		RefugeeAttachments.markDirty(villager, data);
		equip(villager, kit);
		villager.setPersistenceRequired();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			villager.setDropChance(slot, 0.0f);
		}
		return level.addFreshEntity(villager);
	}

	private static void equip(Villager villager, CrusaderLoadout.Kit kit) {
		switch (kit.armor()) {
			case IRON -> {
				villager.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				villager.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				villager.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
				villager.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			}
			case DIAMOND -> {
				villager.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
				villager.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
				villager.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
				villager.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
			}
			case NONE -> {
			}
		}
		villager.setItemSlot(EquipmentSlot.MAINHAND, stackOf(kit.mainHand()));
		villager.setItemSlot(EquipmentSlot.OFFHAND, stackOf(kit.offhand()));
	}

	private static ItemStack stackOf(Item item) {
		if (item == null || item == Items.AIR) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(item);
	}

	private static void onCrusaderLoaded(Villager villager) {
		if (!RefugeeAttachments.get(villager).isCrusader() || !(villager.level() instanceof ServerLevel)) {
			return;
		}
		MinecraftServer server = villager.level().getServer();
		if (server == null || server.overworld() == null) {
			return;
		}
		CrusaderSavedData data = CrusaderSavedData.get(server);
		long dayTime = server.overworld().getDayTime();
		long dayNumber = Math.floorDiv(dayTime, 24000L);
		if (data.lastDespawnDay() == dayNumber) {
			clearGear(villager);
			villager.discard();
		}
	}

	private static void discardAll(MinecraftServer server) {
		List<Villager> leftover = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			leftover.addAll(level.getEntities(
					EntityTypeTest.forClass(Villager.class),
					villager -> villager.isAlive() && RefugeeAttachments.get(villager).isCrusader()));
		}
		for (Villager villager : leftover) {
			clearGear(villager);
			villager.discard();
		}
	}

	private static void clearGear(Villager villager) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			villager.setItemSlot(slot, ItemStack.EMPTY);
		}
	}

	private static void notify(MinecraftServer server, UUID subjectId, int count) {
		if (server == null || subjectId == null || count <= 0) {
			return;
		}
		Component message = Component.translatable("message.refugee.crusader.arrived", count);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (subjectId.equals(player.getUUID()) || subjectId.equals(PbsAdapter.resolveSubject(player))) {
				player.sendSystemMessage(message);
			}
		}
	}

	private record SpawnKey(ServerLevel level, UUID subjectId) {
	}
}
