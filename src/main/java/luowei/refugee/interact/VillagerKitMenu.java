package luowei.refugee.interact;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class VillagerKitMenu extends AbstractContainerMenu {
	private static final int KIT_SLOTS = VillagerKitContainer.SIZE;
	private final Villager villager;
	private final Container kit;

	public VillagerKitMenu(int syncId, Inventory playerInv, VillagerKitMenus.OpenData data) {
		this(syncId, playerInv, VillagerKitMenus.findVillager(playerInv.player, data.entityId()));
	}

	public VillagerKitMenu(int syncId, Inventory playerInv, Villager villager) {
		super(VillagerKitMenus.KIT, syncId);
		this.villager = villager;
		this.kit = villager == null ? new SimpleContainer(KIT_SLOTS) : new VillagerKitContainer(villager);
		addSlot(kitSlot(
				VillagerKitContainer.HEAD,
				VillagerKitLayout.slotX(VillagerKitLayout.HEAD_X),
				VillagerKitLayout.slotY(VillagerKitLayout.HEAD_Y)
		));
		addSlot(kitSlot(
				VillagerKitContainer.CHEST,
				VillagerKitLayout.slotX(VillagerKitLayout.CHEST_X),
				VillagerKitLayout.slotY(VillagerKitLayout.CHEST_Y)
		));
		addSlot(kitSlot(
				VillagerKitContainer.LEGS,
				VillagerKitLayout.slotX(VillagerKitLayout.LEGS_X),
				VillagerKitLayout.slotY(VillagerKitLayout.LEGS_Y)
		));
		addSlot(kitSlot(
				VillagerKitContainer.FEET,
				VillagerKitLayout.slotX(VillagerKitLayout.FEET_X),
				VillagerKitLayout.slotY(VillagerKitLayout.FEET_Y)
		));
		addSlot(kitSlot(
				VillagerKitContainer.MAINHAND,
				VillagerKitLayout.slotX(VillagerKitLayout.MAIN_X),
				VillagerKitLayout.slotY(VillagerKitLayout.MAIN_Y)
		));
		addSlot(kitSlot(
				VillagerKitContainer.OFFHAND,
				VillagerKitLayout.slotX(VillagerKitLayout.OFF_X),
				VillagerKitLayout.slotY(VillagerKitLayout.OFF_Y)
		));
		addSlot(kitSlot(
				VillagerKitContainer.RESOURCE,
				VillagerKitLayout.slotX(VillagerKitLayout.FOOD_X),
				VillagerKitLayout.slotY(VillagerKitLayout.FOOD_Y)
		));
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(
						playerInv,
						col + row * 9 + 9,
						VillagerKitLayout.slotX(VillagerKitLayout.INV_X + col * VillagerKitLayout.SLOT),
						VillagerKitLayout.slotY(VillagerKitLayout.INV_Y + row * VillagerKitLayout.SLOT)
				));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(
					playerInv,
					col,
					VillagerKitLayout.slotX(VillagerKitLayout.HOTBAR_X + col * VillagerKitLayout.SLOT),
					VillagerKitLayout.slotY(VillagerKitLayout.HOTBAR_Y)
			));
		}
	}

	public Villager villager() {
		return villager;
	}

	private Slot kitSlot(int index, int x, int y) {
		return new Slot(kit, index, x, y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return kit.canPlaceItem(index, stack);
			}

			@Override
			public int getMaxStackSize() {
				return index == VillagerKitContainer.RESOURCE ? 64 : 1;
			}
		};
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return result;
		}
		ItemStack stack = slot.getItem();
		result = stack.copy();
		if (index < KIT_SLOTS) {
			if (!moveItemStackTo(stack, KIT_SLOTS, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveItemStackTo(stack, 0, KIT_SLOTS, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return result;
	}

	@Override
	public boolean stillValid(Player player) {
		return kit.stillValid(player);
	}
}
