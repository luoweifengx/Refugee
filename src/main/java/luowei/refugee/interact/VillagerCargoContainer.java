package luowei.refugee.interact;

import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import luowei.refugee.ai.WorkerCargo;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;

/**
 * 工人 9 格工作背包，给装具界面让玩家拿走。
 */
public final class VillagerCargoContainer implements Container {
	public static final int SIZE = RefugeeVillagerData.CARGO_SLOTS;

	private final Villager villager;

	public VillagerCargoContainer(Villager villager) {
		this.villager = villager;
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : slots()) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		List<ItemStack> slots = slots();
		if (slot < 0 || slot >= slots.size()) {
			return ItemStack.EMPTY;
		}
		return slots.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack current = getItem(slot);
		if (current.isEmpty() || amount <= 0) {
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
		List<ItemStack> slots = slots();
		if (slot < 0 || slot >= slots.size()) {
			return;
		}
		ItemStack previous = slots.get(slot).copy();
		ItemStack stored = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack;
		slots.set(slot, stored);
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		RefugeeAttachments.markDirty(villager, data);
		if (villager.level().isClientSide()) {
			return;
		}
		int before = previous.isEmpty() ? 0 : previous.getCount();
		int after = stored.isEmpty() ? 0 : stored.getCount();
		boolean notFull = stored.isEmpty() || stored.getCount() < stored.getMaxStackSize();
		if (after < before && notFull) {
			WorkerCargo.wakeVillager(villager);
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
		return stack != null && !stack.isEmpty();
	}

	private List<ItemStack> slots() {
		return RefugeeAttachments.get(villager).cargoSlots();
	}
}
