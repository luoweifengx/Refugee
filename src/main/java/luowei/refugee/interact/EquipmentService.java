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
 * Shift+右键给予工具/盔甲；取下工具；停止建造。盔甲不可取下。
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
		if (data.isBuilding()) {
			luowei.refugee.staff.StaffService.unbindWorker(villager);
			player.displayClientMessage(Component.translatable("message.refugee.staff.build.stopped"), true);
			return true;
		}
		ItemStack tool = villager.getMainHandItem();
		if (!tool.isEmpty() && RefugeeRoles.isGiveableTool(tool)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			if (!player.getInventory().add(tool.copy())) {
				player.drop(tool.copy(), false);
			}
			luowei.refugee.staff.StaffService.unbindWorker(villager);
			player.displayClientMessage(Component.translatable("message.refugee.tool.taken"), true);
			return true;
		}
		return false;
	}

	private static boolean give(ServerPlayer player, Villager villager, InteractionHand hand, ItemStack held) {
		if (RefugeeRoles.isGiveableArmor(held)) {
			EquipmentSlot slot = RefugeeRoles.armorSlot(villager, held);
			ItemStack previous = villager.getItemBySlot(slot);
			villager.setItemSlot(slot, held.copyWithCount(1));
			held.shrink(1);
			if (!previous.isEmpty() && !player.getInventory().add(previous)) {
				player.drop(previous, false);
			}
			player.displayClientMessage(Component.translatable("message.refugee.armor.given"), true);
			return true;
		}
		ItemStack previous = villager.getMainHandItem();
		villager.setItemSlot(EquipmentSlot.MAINHAND, held.copyWithCount(1));
		held.shrink(1);
		if (RefugeeRoles.isGuard(villager)) {
			RefugeeVillagerData data = RefugeeAttachments.get(villager);
			if (!data.isFollowing() && data.guardCenter() == null) {
				data.setGuardCenter(villager.blockPosition());
				RefugeeAttachments.markDirty(villager, data);
			}
		}
		if (!previous.isEmpty() && !player.getInventory().add(previous)) {
			player.drop(previous, false);
		}
		player.displayClientMessage(Component.translatable("message.refugee.tool.given"), true);
		BuildReadyDebug.report(player, villager, "give-tool");
		return true;
	}
}
