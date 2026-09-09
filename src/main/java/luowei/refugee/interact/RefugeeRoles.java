package luowei.refugee.interact;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.logistics.OrgLogisticsData;
import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 主副手决定角色：剑/弓/弩=守卫，镐斧锄铲=工人。战斗时忽略工具、优先非工具武器。
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
		return isGiveableTool(stack)
				|| isGiveableArmor(stack)
				|| isShield(stack)
				|| isFood(stack)
				|| luowei.refugee.item.ArmorKitItem.isKit(stack);
	}

	public static boolean isShield(ItemStack stack) {
		return stack != null && !stack.isEmpty()
				&& (stack.getItem() instanceof ShieldItem || stack.is(Items.SHIELD));
	}

	public static boolean isFood(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.has(DataComponents.FOOD);
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
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return stack.getItem() instanceof BowItem
				|| stack.getItem() instanceof CrossbowItem
				|| stack.is(Items.BOW)
				|| stack.is(Items.CROSSBOW);
	}

	public static boolean isMeleeWeapon(ItemStack stack) {
		return isWeapon(stack) && !isRangedWeapon(stack);
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

	/** 范围推进只用镐/斧/铲，不含锄。 */
	public static boolean isAdvanceTool(ItemStack stack) {
		return isPickaxe(stack) || isAxe(stack) || isShovel(stack);
	}

	public static boolean isAdvanceMiner(Villager villager) {
		return villager != null && isAdvanceTool(workTool(villager));
	}

	public static boolean isBuilderTool(ItemStack stack) {
		if (stack == null || stack.isEmpty() || isWeapon(stack)) {
			return false;
		}
		return isPickaxe(stack) || isAxe(stack) || isHoe(stack) || isShovel(stack);
	}

	public static boolean isEating(Villager villager) {
		return villager != null && RefugeeAttachments.get(villager).isEating();
	}

	/** 进食时原主手在食物槽。 */
	public static ItemStack logicalMainHand(Villager villager) {
		if (isEating(villager)) {
			return RefugeeAttachments.get(villager).resourceItem();
		}
		return villager.getMainHandItem();
	}

	/** 进食时食物在主手。 */
	public static ItemStack logicalFood(Villager villager) {
		if (isEating(villager)) {
			return villager.getMainHandItem();
		}
		return RefugeeAttachments.get(villager).resourceItem();
	}

	public static void setLogicalMainHand(Villager villager, ItemStack stack) {
		ItemStack stored = stack == null ? ItemStack.EMPTY : stack;
		if (isEating(villager)) {
			RefugeeVillagerData data = RefugeeAttachments.get(villager);
			data.setResourceItem(stored);
			RefugeeAttachments.markDirty(villager, data);
			return;
		}
		villager.setItemSlot(EquipmentSlot.MAINHAND, stored);
	}

	public static void setLogicalFood(Villager villager, ItemStack stack) {
		ItemStack stored = stack == null ? ItemStack.EMPTY : stack;
		if (isEating(villager)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, stored);
			RefugeeVillagerData data = RefugeeAttachments.get(villager);
			data.syncEatWatch(stored);
			RefugeeAttachments.markDirty(villager, data);
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.setResourceItem(stored);
		RefugeeAttachments.markDirty(villager, data);
	}

	public static boolean hasFood(Villager villager) {
		return isFood(logicalFood(villager));
	}

	public static boolean isGuard(Villager villager) {
		return isWeapon(logicalMainHand(villager)) || isWeapon(villager.getOffhandItem());
	}

	public static boolean isBuilder(Villager villager) {
		return isBuilderTool(logicalMainHand(villager)) || isBuilderTool(villager.getOffhandItem());
	}

	public static boolean holdsMeleeWeapon(Villager villager) {
		return isMeleeWeapon(logicalMainHand(villager)) || isMeleeWeapon(villager.getOffhandItem());
	}

	public static boolean holdsRangedWeapon(Villager villager) {
		return isRangedWeapon(logicalMainHand(villager)) || isRangedWeapon(villager.getOffhandItem());
	}

	/** 持有工具即工人；即使同时持有武器也不进近战/远程集结。 */
	public static boolean matchesRallyWorker(Villager villager) {
		return isBuilder(villager);
	}

	public static boolean matchesRallyMelee(Villager villager) {
		return !isBuilder(villager) && holdsMeleeWeapon(villager);
	}

	public static boolean matchesRallyRanged(Villager villager) {
		return !isBuilder(villager) && holdsRangedWeapon(villager);
	}

	/** 散人：主副手皆空。盔甲与食物槽不算手持。 */
	public static boolean matchesRallyCivilian(Villager villager) {
		ItemStack main = logicalMainHand(villager);
		ItemStack off = villager.getOffhandItem();
		return (main == null || main.isEmpty()) && (off == null || off.isEmpty());
	}

	public static ItemStack workTool(Villager villager) {
		ItemStack main = logicalMainHand(villager);
		if (isBuilderTool(main)) {
			return main;
		}
		ItemStack off = villager.getOffhandItem();
		if (isBuilderTool(off)) {
			return off;
		}
		return main;
	}

	public static InteractionHand workHand(Villager villager) {
		if (isBuilderTool(logicalMainHand(villager))) {
			return InteractionHand.MAIN_HAND;
		}
		if (isBuilderTool(villager.getOffhandItem())) {
			return InteractionHand.OFF_HAND;
		}
		return InteractionHand.MAIN_HAND;
	}

	public static boolean hasShield(Villager villager) {
		return isShield(logicalMainHand(villager)) || isShield(villager.getOffhandItem());
	}

	public static boolean overridesBrain(Villager villager) {
		if (villager.isBaby()) {
			return false;
		}
		if (RefugeeSpecialRole.isSpecial(villager)) {
			return true;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.isFollowing() || data.isFollowingEntity() || data.isPatrolling()
				|| data.isBuilding() || data.isBuilderDuty() || data.isRepairerDuty()
				|| data.combatMood().isBusy()) {
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
