package luowei.refugee.livability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.interact.RefugeeRoles;

/**
 * 饥饿低于三成时，向半径 16 格内手上食物超过 8 个的最近村民要一半。
 */
public final class MutualAidService {
	public static final double HUNGER_LINE = 6.0;
	public static final int CHECK_INTERVAL = 100;
	public static final double RADIUS = 16.0;
	public static final int FOOD_MORE_THAN = 8;

	private MutualAidService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tickPlayer(player);
			}
		});
	}

	public static void tickResident(Villager villager) {
		if (villager == null || villager.isBaby() || !RefugeeAttachments.isRefugee(villager)) {
			return;
		}
		if (RefugeeAttachments.get(villager).isCrusader()) {
			return;
		}
		if (villager.tickCount % CHECK_INTERVAL != 0) {
			return;
		}
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		if (LivabilityService.get(villager).satiety() >= HUNGER_LINE) {
			return;
		}
		shareFromNearest(level, villager, villager);
	}

	private static void tickPlayer(ServerPlayer player) {
		if (player == null || !player.isAlive() || player.isSpectator()) {
			return;
		}
		if (player.tickCount % CHECK_INTERVAL != 0) {
			return;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (player.getFoodData().getFoodLevel() >= HUNGER_LINE) {
			return;
		}
		shareFromNearest(level, player, null);
	}

	private static void shareFromNearest(ServerLevel level, net.minecraft.world.entity.Entity hungry, Villager exclude) {
		AABB box = hungry.getBoundingBox().inflate(RADIUS);
		Villager donor = null;
		double best = RADIUS * RADIUS;
		for (Villager candidate : level.getEntitiesOfClass(Villager.class, box, Villager::isAlive)) {
			if (candidate == exclude || candidate.isRemoved()) {
				continue;
			}
			ItemStack food = RefugeeRoles.logicalFood(candidate);
			if (!RefugeeRoles.isFood(food) || food.getCount() <= FOOD_MORE_THAN) {
				continue;
			}
			double distance = candidate.distanceToSqr(hungry);
			if (distance <= best) {
				best = distance;
				donor = candidate;
			}
		}
		if (donor == null) {
			return;
		}
		ItemStack held = RefugeeRoles.logicalFood(donor).copy();
		int give = held.getCount() / 2;
		if (give <= 0) {
			return;
		}
		ItemStack gift = held.split(give);
		RefugeeRoles.setLogicalFood(donor, held);
		if (hungry instanceof ServerPlayer player) {
			giveToPlayer(level, player, gift);
			return;
		}
		if (hungry instanceof Villager villager) {
			giveToVillager(level, villager, gift);
		}
	}

	private static void giveToPlayer(ServerLevel level, ServerPlayer player, ItemStack gift) {
		player.getInventory().add(gift);
		if (!gift.isEmpty()) {
			player.spawnAtLocation(level, gift);
		}
	}

	private static void giveToVillager(ServerLevel level, Villager villager, ItemStack gift) {
		ItemStack held = RefugeeRoles.logicalFood(villager).copy();
		if (held.isEmpty()) {
			RefugeeRoles.setLogicalFood(villager, gift);
			return;
		}
		if (ItemStack.isSameItemSameComponents(held, gift)) {
			int room = held.getMaxStackSize() - held.getCount();
			if (room > 0) {
				int move = Math.min(room, gift.getCount());
				held.grow(move);
				gift.shrink(move);
				RefugeeRoles.setLogicalFood(villager, held);
			}
		}
		if (gift.isEmpty()) {
			return;
		}
		SimpleContainer inventory = villager.getInventory();
		ItemStack leftover = inventory.addItem(gift);
		if (!leftover.isEmpty()) {
			villager.spawnAtLocation(level, leftover);
		}
	}
}
