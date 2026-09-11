package luowei.refugee.interact;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.special.SpecialRefugeeService;

/**
 * Shift+右键给予盔甲/主副手/食物；空手打开装具界面。盔甲仍不可用空手直接扯下。
 */
public final class EquipmentService {
	private EquipmentService() {
	}

	public static boolean handleShiftUse(ServerPlayer player, Villager villager, InteractionHand hand) {
		if (!SelectionService.canCommand(player, villager)) {
			player.displayClientMessage(Component.translatable("message.refugee.command.denied"), true);
			return true;
		}
		ItemStack held = player.getItemInHand(hand);
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		boolean wasBuilder = RefugeeRoles.isBuilder(villager);

		if (RefugeeRoles.isGiveable(held)) {
			if (SpecialRefugeeService.blocksEquipment(villager, held)) {
				player.displayClientMessage(Component.translatable("message.refugee.special.no_equipment"), true);
				return true;
			}
			boolean given = give(player, villager, hand, held);
			if (given && wasBuilder && !RefugeeRoles.isBuilder(villager)) {
				luowei.refugee.staff.StaffService.unbindWorker(villager);
			}
			return given;
		}
		if (data.isBuilding() || data.workerDuty().isAssigned()) {
			luowei.refugee.staff.StaffService.unbindWorker(villager);
			player.displayClientMessage(Component.translatable("message.refugee.staff.build.stopped"), true);
			return true;
		}
		if (held.isEmpty()) {
			return VillagerKitMenus.open(player, villager);
		}
		return false;
	}

	private static boolean give(ServerPlayer player, Villager villager, InteractionHand hand, ItemStack held) {
		if (luowei.refugee.item.ArmorKitItem.isKit(held)) {
			boolean given = luowei.refugee.item.ArmorKitItem.give(player, villager, held);
			if (given) {
				settleIdleGuard(villager);
			}
			return given;
		}
		if (RefugeeRoles.isGiveableArmor(held)) {
			EquipmentSlot slot = RefugeeRoles.armorSlot(villager, held);
			return swapSlot(player, villager, slot, held);
		}
		if (RefugeeRoles.isFood(held)) {
			return giveFood(player, villager, held);
		}
		if (RefugeeRoles.isShield(held)) {
			return swapSlot(player, villager, EquipmentSlot.OFFHAND, held);
		}
		ItemStack logicalMain = RefugeeRoles.logicalMainHand(villager);
		boolean given;
		if (logicalMain.isEmpty() || !villager.getOffhandItem().isEmpty()) {
			given = swapLogicalMain(player, villager, held);
		} else {
			given = swapSlot(player, villager, EquipmentSlot.OFFHAND, held);
		}
		if (given) {
			settleIdleGuard(villager);
		}
		return given;
	}

	public static void settleIdleGuard(Villager villager) {
		if (!RefugeeRoles.isGuard(villager)) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (!data.isFollowing() && !data.isFollowingEntity() && !data.isPatrolling() && data.guardCenter() == null) {
			data.setGuardCenter(villager.blockPosition());
			RefugeeAttachments.markDirty(villager, data);
		}
	}

	private static boolean giveFood(ServerPlayer player, Villager villager, ItemStack held) {
		ItemStack current = RefugeeRoles.logicalFood(villager);
		if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, held)) {
			int space = current.getMaxStackSize() - current.getCount();
			int moved = Math.min(space, held.getCount());
			if (moved > 0) {
				current.grow(moved);
				held.shrink(moved);
				if (RefugeeRoles.isEating(villager)) {
					RefugeeVillagerData data = RefugeeAttachments.get(villager);
					data.syncEatWatch(villager.getMainHandItem());
					RefugeeAttachments.markDirty(villager, data);
				} else {
					RefugeeVillagerData data = RefugeeAttachments.get(villager);
					data.setResourceItem(current);
					RefugeeAttachments.markDirty(villager, data);
				}
			}
			return true;
		}
		ItemStack previous = current.copy();
		ItemStack given = held.split(held.getCount());
		RefugeeRoles.setLogicalFood(villager, given);
		if (RefugeeRoles.isEating(villager)) {
			villager.stopUsingItem();
			if (RefugeeRoles.isFood(villager.getMainHandItem())) {
				villager.startUsingItem(InteractionHand.MAIN_HAND);
			}
		}
		giveBack(player, previous);
		return true;
	}

	private static boolean swapLogicalMain(ServerPlayer player, Villager villager, ItemStack held) {
		ItemStack previous = RefugeeRoles.logicalMainHand(villager);
		RefugeeRoles.setLogicalMainHand(villager, held.copyWithCount(1));
		held.shrink(1);
		giveBack(player, previous);
		return true;
	}

	private static boolean swapSlot(
			ServerPlayer player,
			Villager villager,
			EquipmentSlot slot,
			ItemStack held
	) {
		ItemStack previous = villager.getItemBySlot(slot);
		villager.setItemSlot(slot, held.copyWithCount(1));
		held.shrink(1);
		giveBack(player, previous);
		return true;
	}

	private static void giveBack(ServerPlayer player, ItemStack previous) {
		if (previous == null || previous.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(previous.copy())) {
			player.drop(previous.copy(), false);
		}
	}
}
