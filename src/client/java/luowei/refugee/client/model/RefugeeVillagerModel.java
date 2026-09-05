package luowei.refugee.client.model;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import luowei.refugee.Refugee;
import luowei.refugee.interact.RefugeeRoles;

/**
 * 原版村民体型 + 玩家/掠夺者式独立双臂。
 */
public class RefugeeVillagerModel extends VillagerModel {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Refugee.id("villager"), "main");

	private final ModelPart foldedArms;
	private final ModelPart body;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final ModelPart rightArm;
	private final ModelPart leftArm;

	public RefugeeVillagerModel(ModelPart root) {
		super(root);
		this.foldedArms = root.getChild("arms");
		this.body = root.getChild("body");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
	}

	public ModelPart body() {
		return body;
	}

	public ModelPart rightLeg() {
		return rightLeg;
	}

	public ModelPart leftLeg() {
		return leftLeg;
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = VillagerModel.createBodyModel();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild(
				"right_arm",
				CubeListBuilder.create().texOffs(44, 22).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
				PartPose.offset(-5.0F, 2.0F, 0.0F)
		);
		root.addOrReplaceChild(
				"left_arm",
				CubeListBuilder.create().texOffs(44, 22).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
				PartPose.offset(5.0F, 2.0F, 0.0F)
		);
		return LayerDefinition.create(mesh, 64, 64);
	}

	public ModelPart foldedArms() {
		return foldedArms;
	}

	public ModelPart arm(HumanoidArm side) {
		return side == HumanoidArm.LEFT ? leftArm : rightArm;
	}

	public void translateToHand(HumanoidArm side, com.mojang.blaze3d.vertex.PoseStack pose) {
		arm(side).translateAndRotate(pose);
	}

	@Override
	public void setupAnim(VillagerRenderState state) {
		super.setupAnim(state);
		boolean independent = state instanceof RefugeeVillagerArmState arms
				&& arms.refugee$independentArms();
		foldedArms.visible = !independent;
		rightArm.visible = independent;
		leftArm.visible = independent;
		if (!independent) {
			return;
		}
		resetArm(rightArm);
		resetArm(leftArm);
		float walkPos = state.walkAnimationPos;
		float walkSpeed = state.walkAnimationSpeed;
		rightArm.xRot += Mth.cos(walkPos * 0.6662F + (float) Math.PI) * 2.0F * walkSpeed * 0.5F;
		leftArm.xRot += Mth.cos(walkPos * 0.6662F) * 2.0F * walkSpeed * 0.5F;
		if (!(state instanceof RefugeeVillagerArmState pose)) {
			return;
		}
		applyHeldPoses(pose);
		applyAttackSwing(pose);
	}

	private void applyHeldPoses(RefugeeVillagerArmState pose) {
		ItemStack use = pose.refugee$useItem();
		if (pose.refugee$usingItem() && RefugeeRoles.isFood(use)) {
			applyEat(arm(pose.refugee$useArm()), pose.refugee$useArm() == HumanoidArm.LEFT, pose);
			return;
		}
		if (pose.refugee$usingItem() && RefugeeRoles.isShield(use)) {
			applyBlock(use == pose.refugee$offHand() ? leftArm : rightArm, use == pose.refugee$offHand());
			return;
		}
		if (pose.refugee$usingItem() && isBow(use)) {
			applyBow(getHead(), pose.refugee$useArm() == HumanoidArm.LEFT);
			return;
		}
		if (isCrossbow(pose.refugee$mainHand()) || isCrossbow(pose.refugee$offHand())) {
			boolean rightHanded = isCrossbow(pose.refugee$mainHand());
			ItemStack crossbow = rightHanded ? pose.refugee$mainHand() : pose.refugee$offHand();
			if (pose.refugee$usingItem() && isCrossbow(use) && !CrossbowItem.isCharged(crossbow)) {
				AnimationUtils.animateCrossbowCharge(
						rightArm,
						leftArm,
						pose.refugee$useDuration(),
						pose.refugee$useTicks(),
						rightHanded
				);
			} else {
				AnimationUtils.animateCrossbowHold(rightArm, leftArm, getHead(), rightHanded);
			}
			return;
		}
	}

	private void applyAttackSwing(RefugeeVillagerArmState pose) {
		float attackTime = pose.refugee$attackTime();
		if (attackTime <= 0.0F) {
			return;
		}
		if (pose.refugee$usingItem() && (isBow(pose.refugee$useItem()) || isCrossbow(pose.refugee$useItem()) || RefugeeRoles.isShield(pose.refugee$useItem()) || RefugeeRoles.isFood(pose.refugee$useItem()))) {
			return;
		}
		ModelPart attacking = arm(pose.refugee$swingingArm());
		float remain = 1.0F - attackTime;
		remain *= remain;
		remain *= remain;
		remain = 1.0F - remain;
		float swing = Mth.sin(remain * (float) Math.PI);
		float extra = Mth.sin(attackTime * (float) Math.PI) * -(getHead().xRot - 0.7F) * 0.75F;
		attacking.xRot -= swing * 1.2F + extra;
		attacking.zRot += Mth.sin(attackTime * (float) Math.PI) * -0.4F;
	}

	private void applyBow(ModelPart head, boolean leftHanded) {
		if (leftHanded) {
			rightArm.yRot = -0.1F + head.yRot - 0.4F;
			leftArm.yRot = 0.1F + head.yRot;
		} else {
			rightArm.yRot = -0.1F + head.yRot;
			leftArm.yRot = 0.1F + head.yRot + 0.4F;
		}
		rightArm.xRot = (float) (-Math.PI / 2.0) + head.xRot;
		leftArm.xRot = (float) (-Math.PI / 2.0) + head.xRot;
	}

	private void applyEat(ModelPart arm, boolean left, RefugeeVillagerArmState pose) {
		ModelPart head = getHead();
		float duration = Math.max(1.0F, pose.refugee$useDuration());
		float remaining = Math.max(0.0F, duration - pose.refugee$useTicks());
		float f = remaining + 1.0F;
		float g = f / duration;
		float raise = 1.0F - (float) Math.pow(g, 27.0);
		arm.xRot = arm.xRot * 0.5F - ((float) Math.PI / 5.0F) + head.xRot * 0.5F;
		if (g < 0.8F) {
			arm.xRot += Mth.abs(Mth.cos(f / 4.0F * (float) Math.PI) * 0.1F);
		}
		arm.yRot = head.yRot + (left ? 0.3F : -0.3F) * Math.max(0.35F, raise);
		arm.zRot = 0.0F;
	}

	private static void applyBlock(ModelPart arm, boolean left) {
		arm.xRot = arm.xRot * 0.5F - 0.9424779F;
		arm.yRot = left ? 0.5235988F : -0.5235988F;
	}

	private static void resetArm(ModelPart arm) {
		arm.xRot = 0.0F;
		arm.yRot = 0.0F;
		arm.zRot = 0.0F;
	}

	private static boolean isBow(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof BowItem || stack.is(Items.BOW));
	}

	private static boolean isCrossbow(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof CrossbowItem || stack.is(Items.CROSSBOW));
	}
}
