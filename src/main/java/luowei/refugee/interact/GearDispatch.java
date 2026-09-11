package luowei.refugee.interact;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.item.ArmorKitItem;
import luowei.refugee.logistics.OrgLogisticsData.ContainerRef;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.special.SpecialRefugeeService;
import luowei.refugee.warehouse.WarehouseService;

/**
 * 战斗「派发装备」：装备仓单趟扫槽，空槽才装；
 * 已有装备者不吃套装；套装失败只剪 kit，散甲失败才连带同档套装。
 */
public final class GearDispatch {
	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD,
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET
	};

	private GearDispatch() {
	}

	/**
	 * @return 实际换上至少一件的村民数
	 */
	public static int dispatch(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return 0;
		}
		UUID subjectId = PbsAdapter.resolveSubject(player);
		List<Villager> candidates = candidates(player, level);
		if (candidates.isEmpty()) {
			return 0;
		}
		Set<Object> pruned = new HashSet<>();
		Set<UUID> armed = new HashSet<>();
		List<ItemStack> returned = new ArrayList<>();
		WarehouseService.forEachGearSlot(level, subjectId, (ContainerRef ref, int slot, ItemStack stack) -> {
			if (candidates.isEmpty()) {
				return false;
			}
			if (stack == null || stack.isEmpty()) {
				return true;
			}
			Object key = pruneKey(stack);
			if (key == null || pruned.contains(key)) {
				return true;
			}
			while (!stack.isEmpty() && !candidates.isEmpty()) {
				Villager target = firstSuitable(candidates, stack);
				if (target == null) {
					prune(pruned, stack);
					break;
				}
				ItemStack taken = WarehouseService.takeAt(level, subjectId, ref, slot);
				if (taken.isEmpty()) {
					break;
				}
				returned.addAll(equip(target, taken));
				armed.add(target.getUUID());
				if (ArmorKitItem.isKit(taken) || RefugeeRoles.isWeapon(taken)) {
					EquipmentService.settleIdleGuard(target);
				}
				if (isComplete(target)) {
					candidates.remove(target);
				}
			}
			return !candidates.isEmpty();
		});
		for (ItemStack old : returned) {
			depositOld(player, level, subjectId, old);
		}
		return armed.size();
	}

	private static List<Villager> candidates(ServerPlayer player, ServerLevel level) {
		List<Villager> list = new ArrayList<>();
		for (UUID villagerId : RefugeeAttachments.get(player).snapshotSelected()) {
			Entity entity = level.getEntity(villagerId);
			if (!(entity instanceof Villager villager) || !villager.isAlive() || villager.isBaby()) {
				continue;
			}
			if (!SelectionService.canCommand(player, villager)) {
				continue;
			}
			if (isComplete(villager)) {
				continue;
			}
			list.add(villager);
		}
		return list;
	}

	private static Villager firstSuitable(List<Villager> candidates, ItemStack stack) {
		for (Villager villager : candidates) {
			if (canWear(villager, stack)) {
				return villager;
			}
		}
		return null;
	}

	private static boolean canWear(Villager villager, ItemStack stack) {
		if (ArmorKitItem.isKit(stack)) {
			return canTakeKit(villager, stack);
		}
		if (RefugeeRoles.isGiveableArmor(stack)) {
			EquipmentSlot slot = RefugeeRoles.armorSlot(villager, stack);
			if (slot != EquipmentSlot.HEAD
					&& slot != EquipmentSlot.CHEST
					&& slot != EquipmentSlot.LEGS
					&& slot != EquipmentSlot.FEET) {
				return false;
			}
			return villager.getItemBySlot(slot).isEmpty();
		}
		if (RefugeeRoles.isShield(stack)) {
			ItemStack off = villager.getOffhandItem();
			return off == null || off.isEmpty();
		}
		if (RefugeeRoles.isWeapon(stack)) {
			if (SpecialRefugeeService.blocksEquipment(villager, stack) || RefugeeRoles.isBuilder(villager)) {
				return false;
			}
			return RefugeeRoles.logicalMainHand(villager).isEmpty();
		}
		return false;
	}

	private static boolean canTakeKit(Villager villager, ItemStack kit) {
		if (SpecialRefugeeService.blocksEquipment(villager, kit) || RefugeeRoles.isBuilder(villager)) {
			return false;
		}
		if (!RefugeeRoles.logicalMainHand(villager).isEmpty()) {
			return false;
		}
		ItemStack off = villager.getOffhandItem();
		if (off != null && !off.isEmpty()) {
			return false;
		}
		return !hasPartialArmor(villager);
	}

	private static List<ItemStack> equip(Villager villager, ItemStack taken) {
		if (ArmorKitItem.isKit(taken)) {
			return ArmorKitItem.equip(villager, taken);
		}
		if (RefugeeRoles.isGiveableArmor(taken)) {
			EquipmentSlot slot = RefugeeRoles.armorSlot(villager, taken);
			ItemStack previous = villager.getItemBySlot(slot);
			villager.setItemSlot(slot, taken);
			return copyIfPresent(previous);
		}
		if (RefugeeRoles.isShield(taken)) {
			ItemStack previous = villager.getOffhandItem();
			villager.setItemSlot(EquipmentSlot.OFFHAND, taken);
			return copyIfPresent(previous);
		}
		ItemStack previous = RefugeeRoles.logicalMainHand(villager);
		RefugeeRoles.setLogicalMainHand(villager, taken);
		return copyIfPresent(previous);
	}

	private static List<ItemStack> copyIfPresent(ItemStack previous) {
		if (previous == null || previous.isEmpty()) {
			return List.of();
		}
		return List.of(previous.copy());
	}

	private static boolean isComplete(Villager villager) {
		return !RefugeeRoles.logicalMainHand(villager).isEmpty() && !hasEmptyArmorSlot(villager);
	}

	private static boolean hasEmptyArmorSlot(Villager villager) {
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack worn = villager.getItemBySlot(slot);
			if (worn == null || worn.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasPartialArmor(Villager villager) {
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			ItemStack worn = villager.getItemBySlot(slot);
			if (worn != null && !worn.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static Object pruneKey(ItemStack stack) {
		if (stack.getItem() instanceof ArmorKitItem kit) {
			return new KitClass(kit.kind());
		}
		if (RefugeeRoles.isGiveableArmor(stack) || RefugeeRoles.isWeapon(stack) || RefugeeRoles.isShield(stack)) {
			return stack.getItem();
		}
		return null;
	}

	private static void prune(Set<Object> pruned, ItemStack stack) {
		Object key = pruneKey(stack);
		if (key == null) {
			return;
		}
		pruned.add(key);
		if (stack.getItem() instanceof ArmorKitItem) {
			return;
		}
		if (!RefugeeRoles.isGiveableArmor(stack)) {
			return;
		}
		ArmorKitItem.Kind kind = kitKindOfArmor(stack.getItem());
		if (kind != null) {
			pruned.add(new KitClass(kind));
		}
	}

	private static ArmorKitItem.Kind kitKindOfArmor(Item item) {
		if (item == Items.LEATHER_HELMET
				|| item == Items.LEATHER_CHESTPLATE
				|| item == Items.LEATHER_LEGGINGS
				|| item == Items.LEATHER_BOOTS) {
			return ArmorKitItem.Kind.LEATHER;
		}
		if (item == Items.CHAINMAIL_HELMET
				|| item == Items.CHAINMAIL_CHESTPLATE
				|| item == Items.CHAINMAIL_LEGGINGS
				|| item == Items.CHAINMAIL_BOOTS) {
			return ArmorKitItem.Kind.CHAIN;
		}
		if (item == Items.IRON_HELMET
				|| item == Items.IRON_CHESTPLATE
				|| item == Items.IRON_LEGGINGS
				|| item == Items.IRON_BOOTS) {
			return ArmorKitItem.Kind.IRON;
		}
		if (item == Items.DIAMOND_HELMET
				|| item == Items.DIAMOND_CHESTPLATE
				|| item == Items.DIAMOND_LEGGINGS
				|| item == Items.DIAMOND_BOOTS) {
			return ArmorKitItem.Kind.DIAMOND;
		}
		return null;
	}

	private static void depositOld(ServerPlayer player, ServerLevel level, UUID subjectId, ItemStack old) {
		if (old == null || old.isEmpty()) {
			return;
		}
		ItemStack left = WarehouseService.depositGear(level, subjectId, old);
		if (left.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(left.copy())) {
			player.drop(left.copy(), false);
		}
	}

	private record KitClass(ArmorKitItem.Kind kind) {
	}
}
