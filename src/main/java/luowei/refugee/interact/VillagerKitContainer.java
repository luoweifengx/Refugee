package luowei.refugee.interact;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;

/**
 * 7 格装具：4 甲、主手、副手、资源（食物）。
 */
public final class VillagerKitContainer implements Container {
	public static final int SIZE = 7;
	public static final int HEAD = 0;
	public static final int CHEST = 1;
	public static final int LEGS = 2;
	public static final int FEET = 3;
	public static final int MAINHAND = 4;
	public static final int OFFHAND = 5;
	public static final int RESOURCE = 6;

	private static final EquipmentSlot[] EQUIPMENT = {
			EquipmentSlot.HEAD,
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET,
			EquipmentSlot.MAINHAND,
			EquipmentSlot.OFFHAND
	};

	private final Villager villager;

	public VillagerKitContainer(Villager villager) {
		this.villager = villager;
	}

	public Villager villager() {
		return villager;
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (int i = 0; i < SIZE; i++) {
			if (!getItem(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return getPhysical(physicalSlot(slot));
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack current = getItem(slot);
		if (current.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack taken = current.split(amount);
		setItem(slot, current);
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack current = getItem(slot).copy();
		setItem(slot, ItemStack.EMPTY);
		return current;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		ItemStack stored = stack == null ? ItemStack.EMPTY : stack;
		boolean eating = eating();
		setPhysical(physicalSlot(slot), stored);
		if (eating && slot == RESOURCE) {
			RefugeeAttachments.get(villager).syncEatWatch(stored);
		}
	}

	private boolean eating() {
		return villager != null && RefugeeAttachments.get(villager).isEating();
	}

	private int physicalSlot(int slot) {
		if (!eating()) {
			return slot;
		}
		if (slot == MAINHAND) {
			return RESOURCE;
		}
		if (slot == RESOURCE) {
			return MAINHAND;
		}
		return slot;
	}

	private ItemStack getPhysical(int slot) {
		if (slot == RESOURCE) {
			return RefugeeAttachments.get(villager).resourceItem();
		}
		if (slot >= 0 && slot < EQUIPMENT.length) {
			return villager.getItemBySlot(EQUIPMENT[slot]);
		}
		return ItemStack.EMPTY;
	}

	private void setPhysical(int slot, ItemStack stored) {
		if (slot == RESOURCE) {
			RefugeeVillagerData data = RefugeeAttachments.get(villager);
			data.setResourceItem(stored);
			RefugeeAttachments.markDirty(villager, data);
			return;
		}
		if (slot >= 0 && slot < EQUIPMENT.length) {
			villager.setItemSlot(EQUIPMENT[slot], stored);
		}
	}

	@Override
	public void setChanged() {
		if (villager != null) {
			RefugeeAttachments.markDirty(villager, RefugeeAttachments.get(villager));
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return villager != null && villager.isAlive() && player.distanceTo(villager) < 8.0f;
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < SIZE; i++) {
			setItem(i, ItemStack.EMPTY);
		}
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot == RESOURCE) {
			return RefugeeRoles.isFood(stack);
		}
		if (slot >= HEAD && slot <= FEET) {
			return villager.getEquipmentSlotForItem(stack) == EQUIPMENT[slot];
		}
		if (slot == MAINHAND || slot == OFFHAND) {
			return RefugeeRoles.isGiveableTool(stack) || RefugeeRoles.isShield(stack);
		}
		return false;
	}
}
