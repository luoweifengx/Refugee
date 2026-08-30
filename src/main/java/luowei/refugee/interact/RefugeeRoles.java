package luowei.refugee.interact;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.logistics.OrgLogisticsData;
import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 手持物决定角色：弓/弩=远程守卫，剑=近战守卫；镐斧锄铲=工人（仓库存取与建筑）。
 */
public final class RefugeeRoles {
	private RefugeeRoles() {
	}

	public static boolean isGiveableTool(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return isWeapon(stack) || isBuilderTool(stack);
	}

	public static boolean isGiveableArmor(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return stack.is(ItemTags.HEAD_ARMOR)
				|| stack.is(ItemTags.CHEST_ARMOR)
				|| stack.is(ItemTags.LEG_ARMOR)
				|| stack.is(ItemTags.FOOT_ARMOR);
	}

	public static boolean isGiveable(ItemStack stack) {
		return isGiveableTool(stack) || isGiveableArmor(stack);
	}

	public static boolean isWeapon(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return stack.getItem() instanceof BowItem
				|| stack.getItem() instanceof CrossbowItem
				|| stack.is(ItemTags.SWORDS)
				|| stack.is(Items.BOW)
				|| stack.is(Items.CROSSBOW);
	}

	public static boolean isRangedWeapon(ItemStack stack) {
		return stack.getItem() instanceof BowItem
				|| stack.getItem() instanceof CrossbowItem
				|| stack.is(Items.BOW)
				|| stack.is(Items.CROSSBOW);
	}

	public static boolean isHoe(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.is(ItemTags.HOES);
	}

	public static boolean isAxe(ItemStack stack) {
		return stack != null && !stack.isEmpty()
				&& (stack.getItem() instanceof AxeItem || stack.is(ItemTags.AXES));
	}

	public static boolean isPickaxe(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.is(ItemTags.PICKAXES);
	}

	public static boolean isShovel(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.is(ItemTags.SHOVELS);
	}

	public static boolean isBuilderTool(ItemStack stack) {
		if (stack == null || stack.isEmpty() || isWeapon(stack)) {
			return false;
		}
		return isPickaxe(stack) || isAxe(stack) || isHoe(stack) || isShovel(stack);
	}

	public static boolean isGuard(Villager villager) {
		return isWeapon(villager.getMainHandItem());
	}

	public static boolean isBuilder(Villager villager) {
		return isBuilderTool(villager.getMainHandItem());
	}

	public static boolean overridesBrain(Villager villager) {
		if (RefugeeSpecialRole.isSpecial(villager)) {
			return true;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.isFollowing() || data.isBuilding()) {
			return true;
		}
		if (villager.level().getServer() != null) {
			OrgLogisticsData logistics = OrgLogisticsData.get(villager.level().getServer());
			if (logistics.zoneOfWorker(villager.getUUID()) != null || logistics.jobOfWorker(villager.getUUID()) != null) {
				return true;
			}
		}
		return isGuard(villager) || isBuilder(villager);
	}

	public static EquipmentSlot armorSlot(Villager villager, ItemStack stack) {
		return villager.getEquipmentSlotForItem(stack);
	}
}
