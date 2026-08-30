package luowei.refugee.client.talk;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import luowei.refugee.talk.RefugeeBubbleIcon;

/**
 * 村民头顶气泡：在实体世界坐标（脚底原点、Y 朝上）绘制，始终朝向镜头。
 * 不能挂在 {@code RenderLayer} 里画：生物渲染会先 {@code scale(-1,-1,1)}，+Y 指向脚底。
 */
public final class RefugeeBubbleLayer {
	private static final float BUBBLE_WIDTH = 0.72F;
	private static final float BUBBLE_HEIGHT = 0.36F;
	private static final float ICON_SIZE = 0.20F;
	private static final float ICON_Y = 0.035F;
	private static final float HEAD_GAP = 0.42F;
	private static final double MAX_DISTANCE_SQ = 48.0 * 48.0;

	private RefugeeBubbleLayer() {
	}

	public static void render(PoseStack poseStack, MultiBufferSource buffer, VillagerRenderState state) {
		if (!(state instanceof RefugeeBubbleRenderState bubbleState)) {
			return;
		}
		RefugeeBubbleIcon icon = bubbleState.refugee$bubbleIcon();
		if (!icon.visible() || state.isInvisible || state.distanceToCameraSq > MAX_DISTANCE_SQ) {
			return;
		}
		int light = LightTexture.FULL_BRIGHT;
		poseStack.pushPose();
		poseStack.translate(0.0F, state.boundingBoxHeight + HEAD_GAP, 0.0F);
		poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
		blit(poseStack, buffer, RefugeeBubbleIcon.BUBBLE_TEXTURE, BUBBLE_WIDTH, BUBBLE_HEIGHT, 0.0F, 0.0F, light);
		blit(poseStack, buffer, icon.iconTexture(), ICON_SIZE, ICON_SIZE, ICON_Y, 0.01F, light);
		poseStack.popPose();
	}

	private static void blit(
			PoseStack poseStack,
			MultiBufferSource buffer,
			ResourceLocation texture,
			float width,
			float height,
			float yOffset,
			float z,
			int light
	) {
		PoseStack.Pose pose = poseStack.last();
		VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(texture));
		float x = width / 2.0F;
		float y0 = yOffset - height / 2.0F;
		float y1 = yOffset + height / 2.0F;
		vertex(consumer, pose, -x, y1, z, 0.0F, 0.0F, light);
		vertex(consumer, pose, x, y1, z, 1.0F, 0.0F, light);
		vertex(consumer, pose, x, y0, z, 1.0F, 1.0F, light);
		vertex(consumer, pose, -x, y0, z, 0.0F, 1.0F, light);
		vertex(consumer, pose, -x, y1, z, 0.0F, 0.0F, light);
		vertex(consumer, pose, -x, y0, z, 0.0F, 1.0F, light);
		vertex(consumer, pose, x, y0, z, 1.0F, 1.0F, light);
		vertex(consumer, pose, x, y1, z, 1.0F, 0.0F, light);
	}

	private static void vertex(
			VertexConsumer consumer,
			PoseStack.Pose pose,
			float x,
			float y,
			float z,
			float u,
			float v,
			int light
	) {
		consumer.addVertex(pose, x, y, z)
				.setColor(255, 255, 255, 255)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, 0.0F, 0.0F, 1.0F);
	}
}
