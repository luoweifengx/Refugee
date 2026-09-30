package luowei.refugee.livability;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.pbs.PbsAdapter;

/**
 * 按同一主体下已加载居民的人数和平均舒适度发成就。
 * 人均舒适度降到 0.05 及以下视为 0。
 */
public final class LivabilityAchievements {
	private static final ResourceLocation HAMLET = ResourceLocation.fromNamespaceAndPath("refugee", "settlement/hamlet");
	private static final ResourceLocation ORDERED = ResourceLocation.fromNamespaceAndPath("refugee", "settlement/ordered");
	private static final ResourceLocation TYRANNY = ResourceLocation.fromNamespaceAndPath("refugee", "settlement/tyranny");
	private static final double ZERO_COMFORT = 0.05;

	private LivabilityAchievements() {
	}

	public static void check(MinecraftServer server) {
		if (server == null) {
			return;
		}
		Map<UUID, Acc> groups = new HashMap<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (Villager villager : level.getEntities(EntityType.VILLAGER, Entity::isAlive)) {
				if (villager.isBaby() || !RefugeeAttachments.isRefugee(villager)) {
					continue;
				}
				UUID subject = RefugeeAttachments.get(villager).subjectId();
				if (subject == null) {
					continue;
				}
				Acc acc = groups.computeIfAbsent(subject, ignored -> new Acc());
				acc.count++;
				acc.comfort += LivabilityService.get(villager).comfort();
			}
		}
		for (Map.Entry<UUID, Acc> entry : groups.entrySet()) {
			Acc acc = entry.getValue();
			if (acc.count <= 0) {
				continue;
			}
			double average = acc.comfort / acc.count;
			if (acc.count >= 20 && average >= 15.0) {
				grant(server, entry.getKey(), HAMLET);
			}
			if (acc.count >= 200 && average >= 15.0) {
				grant(server, entry.getKey(), ORDERED);
			}
			if (average <= ZERO_COMFORT) {
				grant(server, entry.getKey(), TYRANNY);
			}
		}
	}

	private static void grant(MinecraftServer server, UUID subject, ResourceLocation id) {
		AdvancementHolder holder = server.getAdvancements().get(id);
		if (holder == null) {
			return;
		}
		Set<UUID> players = new HashSet<>(PbsAdapter.organizationMembers(server, subject));
		PbsAdapter.organizationOwner(server, subject).ifPresent(players::add);
		if (players.isEmpty()) {
			players.add(subject);
		}
		for (UUID playerId : players) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null) {
				continue;
			}
			AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
			if (progress.isDone()) {
				continue;
			}
			for (String criterion : progress.getRemainingCriteria()) {
				player.getAdvancements().award(holder, criterion);
			}
		}
	}

	private static final class Acc {
		private int count;
		private double comfort;
	}
}
