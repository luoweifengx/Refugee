package luowei.refugee.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;

/**
 * 难民村民主副手物品跟独立手臂走，而不是原版抱臂位置。
 */
public class RefugeeVillagerHeldItemLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
	public RefugeeVillagerHeldItemLayer(RenderLayerParent<VillagerRenderState, VillagerModel> parent) {
		super(parent);
	}

	@Override
	public void render(
			PoseStack pose,
			MultiBufferSource buffer,
			int packedLight,
			VillagerRenderState state,
			float yRot,
			float xRot
	) {
		if (state.isBaby || !(state instanceof RefugeeVillagerArmState arms) || !arms.refugee$independentArms()) {
			return;
		}
		if (!(getParentModel() instanceof RefugeeVillagerModel model)) {
			return;
		}
		renderHand(pose, buffer, packedLight, arms.refugee$mainHandItem(), HumanoidArm.RIGHT, model);
		renderHand(pose, buffer, packedLight, arms.refugee$offHandItem(), HumanoidArm.LEFT, model);
	}

	private static void renderHand(
			PoseStack pose,
			MultiBufferSource buffer,
			int packedLight,
			ItemStackRenderState item,
			HumanoidArm arm,
			RefugeeVillagerModel model
	) {
		if (item == null || item.isEmpty()) {
			return;
		}
		pose.pushPose();
		model.translateToHand(arm, pose);
		pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
		pose.mulPose(Axis.YP.rotationDegrees(180.0F));
		boolean left = arm == HumanoidArm.LEFT;
		pose.translate((left ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
		item.render(pose, buffer, packedLight, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}
}
