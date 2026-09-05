package luowei.refugee.client.model;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * 把玩家内外甲网格对齐到村民身体零件上，职业衣服仍走原版村民模型。
 * 头盔先跟村民头转，再沿头的局部轴上抬；胸甲对齐身体。抱臂时袖随前置手臂；护腿与靴不绘制。
 */
public class RefugeeVillagerArmorLayer extends RenderLayer<VillagerRenderState, VillagerModel> {
	private static final float HELMET_LIFT = 2.5F / 16.0F;
	private static final float CHEST_X = 1.0F;
	private static final float CHEST_Z = 1.2F;

	private final HumanoidArmorModel outer;
	private final EquipmentLayerRenderer equipmentRenderer;

	public RefugeeVillagerArmorLayer(
			RenderLayerParent<VillagerRenderState, VillagerModel> parent,
			EntityRendererProvider.Context context
	) {
		super(parent);
		this.outer = new HumanoidArmorModel(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
		this.equipmentRenderer = context.getEquipmentRenderer();
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
		if (!(state instanceof RefugeeVillagerArmState arms)) {
			return;
		}
		if (state.isBaby || !(getParentModel() instanceof RefugeeVillagerModel model)) {
			return;
		}
		renderPiece(pose, buffer, packedLight, arms.refugee$headArmor(), EquipmentSlot.HEAD, model, arms);
		renderPiece(pose, buffer, packedLight, arms.refugee$chestArmor(), EquipmentSlot.CHEST, model, arms);
	}

	private void renderPiece(
			PoseStack pose,
			MultiBufferSource buffer,
			int packedLight,
			ItemStack stack,
			EquipmentSlot slot,
			RefugeeVillagerModel villager,
			RefugeeVillagerArmState arms
	) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		if (slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) {
			return;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.slot() != slot || equippable.assetId().isEmpty()) {
			return;
		}
		HumanoidArmorModel armor = outer;
		copyPoses(villager, armor, arms.refugee$independentArms());
		setPartVisibility(armor, slot);
		stretchParts(armor, slot);
		EquipmentClientInfo.LayerType type = slot == EquipmentSlot.LEGS
				? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
				: EquipmentClientInfo.LayerType.HUMANOID;
		pose.pushPose();
		if (slot == EquipmentSlot.HEAD) {
			villager.getHead().translateAndRotate(pose);
			pose.translate(0.0F, -HELMET_LIFT, 0.0F);
			resetLocal(armor.head);
			resetLocal(armor.hat);
		}
		equipmentRenderer.renderLayers(type, equippable.assetId().get(), armor, stack, pose, buffer, packedLight);
		pose.popPose();
	}

	private static void copyPoses(RefugeeVillagerModel villager, HumanoidArmorModel armor, boolean independent) {
		copy(villager.getHead(), armor.head);
		copy(villager.getHead(), armor.hat);
		copy(villager.body(), armor.body);
		copy(villager.rightLeg(), armor.rightLeg);
		copy(villager.leftLeg(), armor.leftLeg);
		poseChestArms(villager, armor, independent);
	}

	private static void poseChestArms(RefugeeVillagerModel villager, HumanoidArmorModel armor, boolean independent) {
		copy(villager.arm(HumanoidArm.RIGHT), armor.rightArm);
		copy(villager.arm(HumanoidArm.LEFT), armor.leftArm);
		if (independent) {
			return;
		}
		ModelPart folded = villager.foldedArms();
		ModelPart body = villager.body();
		float pitch = folded.xRot;
		float sin = Mth.sin(pitch);
		float cos = Mth.cos(pitch);
		float cornerA = -2.0F * cos - (-2.0F) * sin;
		float cornerB = -2.0F * cos - 2.0F * sin;
		float minLocalY = Math.min(cornerA, cornerB);
		float shoulderY = body.y - minLocalY;
		poseFoldedSleeve(armor.rightArm, body, folded, pitch, shoulderY);
		poseFoldedSleeve(armor.leftArm, body, folded, pitch, shoulderY);
	}

	private static void poseFoldedSleeve(
			ModelPart arm,
			ModelPart body,
			ModelPart folded,
			float pitch,
			float shoulderY
	) {
		arm.xRot = pitch;
		arm.yRot = 0.0F;
		arm.zRot = 0.0F;
		arm.y = shoulderY;
		arm.z = body.z + folded.z;
	}

	private static void copy(ModelPart from, ModelPart to) {
		to.copyFrom(from);
	}

	private static void resetLocal(ModelPart part) {
		part.x = 0.0F;
		part.y = 0.0F;
		part.z = 0.0F;
		part.xRot = 0.0F;
		part.yRot = 0.0F;
		part.zRot = 0.0F;
	}

	private static void stretchParts(HumanoidArmorModel armor, EquipmentSlot slot) {
		if (slot == EquipmentSlot.CHEST) {
			stretch(armor.body, CHEST_X, 1.0F, CHEST_Z);
		}
	}

	private static void stretch(ModelPart part, float x, float y, float z) {
		part.xScale = x;
		part.yScale = y;
		part.zScale = z;
	}

	private static void setPartVisibility(HumanoidArmorModel armor, EquipmentSlot slot) {
		armor.setAllVisible(false);
		switch (slot) {
			case HEAD -> {
				armor.head.visible = true;
				armor.hat.visible = true;
			}
			case CHEST -> {
				armor.body.visible = true;
				armor.rightArm.visible = true;
				armor.leftArm.visible = true;
			}
			default -> {
			}
		}
	}
}
