package luowei.refugee.client.model;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * 难民村民独立手臂所需的渲染状态：持物、挥击、拉弓/弩、持盾。
 */
public interface RefugeeVillagerArmState {
	boolean refugee$independentArms();

	void refugee$setIndependentArms(boolean value);

	float refugee$attackTime();

	void refugee$setAttackTime(float value);

	boolean refugee$usingItem();

	void refugee$setUsingItem(boolean value);

	int refugee$useTicks();

	void refugee$setUseTicks(int value);

	float refugee$useDuration();

	void refugee$setUseDuration(float value);

	ItemStack refugee$mainHand();

	void refugee$setMainHand(ItemStack stack);

	ItemStack refugee$offHand();

	void refugee$setOffHand(ItemStack stack);

	ItemStack refugee$useItem();

	void refugee$setUseItem(ItemStack stack);

	HumanoidArm refugee$swingingArm();

	void refugee$setSwingingArm(HumanoidArm arm);

	HumanoidArm refugee$useArm();

	void refugee$setUseArm(HumanoidArm arm);

	ItemStack refugee$headArmor();

	void refugee$setHeadArmor(ItemStack stack);

	ItemStack refugee$chestArmor();

	void refugee$setChestArmor(ItemStack stack);

	ItemStack refugee$legsArmor();

	void refugee$setLegsArmor(ItemStack stack);

	ItemStack refugee$feetArmor();

	void refugee$setFeetArmor(ItemStack stack);

	ItemStackRenderState refugee$mainHandItem();

	ItemStackRenderState refugee$offHandItem();
}
