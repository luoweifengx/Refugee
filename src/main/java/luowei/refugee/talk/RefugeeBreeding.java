package luowei.refugee.talk;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;

/**
 * 难民繁殖：爱恋/流汗走自定义气泡，拦截原版心形与失败粒子；可在两人间丢食物。
 */
public final class RefugeeBreeding {
	private RefugeeBreeding() {
	}

	public static boolean isRefugee(Villager villager) {
		return villager != null && RefugeeAttachments.isRefugee(villager);
	}

	public static void onLoveStart(ServerLevel level, Villager villager) {
		if (!isRefugee(villager)) {
			return;
		}
		RefugeeBubble.startLove(villager);
		villager.getBrain().getMemory(MemoryModuleType.BREED_TARGET).ifPresent(partner -> {
			if (partner instanceof Villager other) {
				RefugeeBubble.startLove(other);
				throwFoodToward(level, villager, other);
			}
		});
	}

	public static void onLoveTick(Villager villager) {
		if (!isRefugee(villager)) {
			return;
		}
		if (villager.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET)) {
			RefugeeBubble.startLove(villager);
		}
	}

	public static boolean interceptEntityEvent(Entity entity, byte event) {
		if (!(entity instanceof Villager villager) || !isRefugee(villager)) {
			return false;
		}
		if (event == EntityEvent.LOVE_HEARTS || event == EntityEvent.IN_LOVE_HEARTS) {
			RefugeeBubble.startLove(villager);
			return true;
		}
		if (event == EntityEvent.VILLAGER_SWEAT) {
			RefugeeBubble.startSweat(villager);
			villager.getBrain().getMemory(MemoryModuleType.BREED_TARGET).ifPresent(partner -> {
				if (partner instanceof Villager other) {
					RefugeeBubble.startSweat(other);
				}
			});
			return true;
		}
		return false;
	}

	private static void throwFoodToward(ServerLevel level, Villager from, Villager to) {
		if (level == null || from == null || to == null) {
			return;
		}
		ItemStack food = takeBreedingFood(from);
		if (food.isEmpty()) {
			return;
		}
		ItemEntity thrown = new ItemEntity(level, from.getX(), from.getY() + 0.6, from.getZ(), food);
		Vec3 delta = to.position().subtract(from.position());
		double len = delta.length();
		if (len > 1.0E-4) {
			delta = delta.scale(0.18 / len);
		}
		thrown.setDeltaMovement(delta.x, 0.22, delta.z);
		thrown.setPickUpDelay(20);
		level.addFreshEntity(thrown);
	}

	private static ItemStack takeBreedingFood(Villager villager) {
		var inventory = villager.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (!Villager.FOOD_POINTS.containsKey(stack.getItem())) {
				continue;
			}
			ItemStack thrown = stack.split(1);
			inventory.setChanged();
			return thrown;
		}
		return ItemStack.EMPTY;
	}

	public static void tickLook(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		long gameTime = level.getGameTime();
		if (!data.isLookingAtPlayer(gameTime)) {
			return;
		}
		var player = level.getPlayerByUUID(data.lookAtPlayerId());
		if (player == null || !player.isAlive() || villager.distanceTo(player) > 16.0f) {
			return;
		}
		villager.getLookControl().setLookAt(player, 30.0F, villager.getMaxHeadXRot());
	}
}
