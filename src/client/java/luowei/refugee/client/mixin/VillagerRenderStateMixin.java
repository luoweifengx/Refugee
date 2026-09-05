package luowei.refugee.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import luowei.refugee.client.model.RefugeeVillagerArmState;
import luowei.refugee.client.talk.RefugeeBubbleRenderState;
import luowei.refugee.talk.RefugeeBubbleIcon;

@Mixin(VillagerRenderState.class)
public class VillagerRenderStateMixin implements RefugeeBubbleRenderState, RefugeeVillagerArmState {
	@Unique
	private RefugeeBubbleIcon refugee$bubbleIcon = RefugeeBubbleIcon.NONE;
	@Unique
	private boolean refugee$independentArms;
	@Unique
	private float refugee$attackTime;
	@Unique
	private boolean refugee$usingItem;
	@Unique
	private int refugee$useTicks;
	@Unique
	private float refugee$useDuration;
	@Unique
	private ItemStack refugee$mainHand = ItemStack.EMPTY;
	@Unique
	private ItemStack refugee$offHand = ItemStack.EMPTY;
	@Unique
	private ItemStack refugee$useItem = ItemStack.EMPTY;
	@Unique
	private HumanoidArm refugee$swingingArm = HumanoidArm.RIGHT;
	@Unique
	private HumanoidArm refugee$useArm = HumanoidArm.RIGHT;
	@Unique
	private ItemStack refugee$headArmor = ItemStack.EMPTY;
	@Unique
	private ItemStack refugee$chestArmor = ItemStack.EMPTY;
	@Unique
	private ItemStack refugee$legsArmor = ItemStack.EMPTY;
	@Unique
	private ItemStack refugee$feetArmor = ItemStack.EMPTY;
	@Unique
	private final ItemStackRenderState refugee$mainHandItem = new ItemStackRenderState();
	@Unique
	private final ItemStackRenderState refugee$offHandItem = new ItemStackRenderState();

	@Override
	public RefugeeBubbleIcon refugee$bubbleIcon() {
		return refugee$bubbleIcon == null ? RefugeeBubbleIcon.NONE : refugee$bubbleIcon;
	}

	@Override
	public void refugee$setBubbleIcon(RefugeeBubbleIcon icon) {
		this.refugee$bubbleIcon = icon == null ? RefugeeBubbleIcon.NONE : icon;
	}

	@Override
	public boolean refugee$independentArms() {
		return refugee$independentArms;
	}

	@Override
	public void refugee$setIndependentArms(boolean value) {
		this.refugee$independentArms = value;
	}

	@Override
	public float refugee$attackTime() {
		return refugee$attackTime;
	}

	@Override
	public void refugee$setAttackTime(float value) {
		this.refugee$attackTime = value;
	}

	@Override
	public boolean refugee$usingItem() {
		return refugee$usingItem;
	}

	@Override
	public void refugee$setUsingItem(boolean value) {
		this.refugee$usingItem = value;
	}

	@Override
	public int refugee$useTicks() {
		return refugee$useTicks;
	}

	@Override
	public void refugee$setUseTicks(int value) {
		this.refugee$useTicks = value;
	}

	@Override
	public float refugee$useDuration() {
		return refugee$useDuration;
	}

	@Override
	public void refugee$setUseDuration(float value) {
		this.refugee$useDuration = value;
	}

	@Override
	public ItemStack refugee$mainHand() {
		return refugee$mainHand == null ? ItemStack.EMPTY : refugee$mainHand;
	}

	@Override
	public void refugee$setMainHand(ItemStack stack) {
		this.refugee$mainHand = stack == null ? ItemStack.EMPTY : stack;
	}

	@Override
	public ItemStack refugee$offHand() {
		return refugee$offHand == null ? ItemStack.EMPTY : refugee$offHand;
	}

	@Override
	public void refugee$setOffHand(ItemStack stack) {
		this.refugee$offHand = stack == null ? ItemStack.EMPTY : stack;
	}

	@Override
	public ItemStack refugee$useItem() {
		return refugee$useItem == null ? ItemStack.EMPTY : refugee$useItem;
	}

	@Override
	public void refugee$setUseItem(ItemStack stack) {
		this.refugee$useItem = stack == null ? ItemStack.EMPTY : stack;
	}

	@Override
	public HumanoidArm refugee$swingingArm() {
		return refugee$swingingArm == null ? HumanoidArm.RIGHT : refugee$swingingArm;
	}

	@Override
	public void refugee$setSwingingArm(HumanoidArm arm) {
		this.refugee$swingingArm = arm == null ? HumanoidArm.RIGHT : arm;
	}

	@Override
	public HumanoidArm refugee$useArm() {
		return refugee$useArm == null ? HumanoidArm.RIGHT : refugee$useArm;
	}

	@Override
	public void refugee$setUseArm(HumanoidArm arm) {
		this.refugee$useArm = arm == null ? HumanoidArm.RIGHT : arm;
	}

	@Override
	public ItemStack refugee$headArmor() {
		return refugee$headArmor == null ? ItemStack.EMPTY : refugee$headArmor;
	}

	@Override
	public void refugee$setHeadArmor(ItemStack stack) {
		this.refugee$headArmor = stack == null ? ItemStack.EMPTY : stack;
	}

	@Override
	public ItemStack refugee$chestArmor() {
		return refugee$chestArmor == null ? ItemStack.EMPTY : refugee$chestArmor;
	}

	@Override
	public void refugee$setChestArmor(ItemStack stack) {
		this.refugee$chestArmor = stack == null ? ItemStack.EMPTY : stack;
	}

	@Override
	public ItemStack refugee$legsArmor() {
		return refugee$legsArmor == null ? ItemStack.EMPTY : refugee$legsArmor;
	}

	@Override
	public void refugee$setLegsArmor(ItemStack stack) {
		this.refugee$legsArmor = stack == null ? ItemStack.EMPTY : stack;
	}

	@Override
	public ItemStack refugee$feetArmor() {
		return refugee$feetArmor == null ? ItemStack.EMPTY : refugee$feetArmor;
	}

	@Override
	public void refugee$setFeetArmor(ItemStack stack) {
		this.refugee$feetArmor = stack == null ? ItemStack.EMPTY : stack;
	}

	@Override
	public ItemStackRenderState refugee$mainHandItem() {
		return refugee$mainHandItem;
	}

	@Override
	public ItemStackRenderState refugee$offHandItem() {
		return refugee$offHandItem;
	}
}
