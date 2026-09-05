package luowei.refugee.interact;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

import luowei.refugee.ai.RefugeeCombat;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;

/**
 * 村民死亡时掉落装备槽与村民背包（原版掉落率通常为 0）。
 */
public final class DeathDropService {
	private DeathDropService() {
	}

	public static void dropOnDeath(Villager villager, ServerLevel level) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.isEating()) {
			RefugeeCombat.cancelEat(villager);
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = villager.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				villager.spawnAtLocation(level, stack.copy());
				villager.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
		SimpleContainer inventory = villager.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				villager.spawnAtLocation(level, stack.copy());
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
		ItemStack resource = data.resourceItem();
		if (!resource.isEmpty()) {
			villager.spawnAtLocation(level, resource.copy());
			data.setResourceItem(ItemStack.EMPTY);
			RefugeeAttachments.markDirty(villager, data);
		}
		luowei.refugee.staff.StaffService.unbindWorker(villager);
		if (data.isBuilding()) {
			data.clearBuild();
			RefugeeAttachments.markDirty(villager, data);
		}
	}
}
