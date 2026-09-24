package luowei.refugee.client;

import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Crackiness;

import luowei.refugee.Refugee;

/**
 * 铜色裂纹贴图，对应铁傀儡裂纹层。
 */
public class CopperGolemCrackinessLayer extends RenderLayer<IronGolemRenderState, IronGolemModel> {
	private static final Map<Crackiness.Level, ResourceLocation> TEXTURES = Map.of(
			Crackiness.Level.LOW, Refugee.id("textures/entity/copper_golem/copper_golem_crackiness_low.png"),
			Crackiness.Level.MEDIUM, Refugee.id("textures/entity/copper_golem/copper_golem_crackiness_medium.png"),
			Crackiness.Level.HIGH, Refugee.id("textures/entity/copper_golem/copper_golem_crackiness_high.png")
	);

	public CopperGolemCrackinessLayer(RenderLayerParent<IronGolemRenderState, IronGolemModel> parent) {
		super(parent);
	}

	@Override
	public void render(
			PoseStack pose,
			MultiBufferSource buffer,
			int packedLight,
			IronGolemRenderState state,
			float yRot,
			float xRot
	) {
		if (state.isInvisible) {
			return;
		}
		ResourceLocation texture = TEXTURES.get(state.crackiness);
		if (texture == null) {
			return;
		}
		VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(texture));
		this.getParentModel().renderToBuffer(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY);
	}
}
