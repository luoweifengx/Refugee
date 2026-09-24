package luowei.refugee.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import luowei.refugee.block.AltarBlockEntity;

/**
 * 终局祭坛光柱：与 Glory 巨弩 special 相同的末地门圆柱。
 */
public class AltarBeamRenderer implements BlockEntityRenderer<AltarBlockEntity> {
	private static final float RADIUS = 0.1F;
	private static final float HEIGHT = 384.0F;
	private static final int SLICES = 24;

	public AltarBeamRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(
			AltarBlockEntity altar,
			float partialTick,
			PoseStack pose,
			MultiBufferSource buffer,
			int packedLight,
			int packedOverlay,
			Vec3 cameraPos
	) {
		if (!altar.hasFinaleBeam()) {
			return;
		}
		pose.pushPose();
		pose.translate(0.5F, 0.5F, 0.5F);
		VertexConsumer consumer = buffer.getBuffer(RenderType.endPortal());
		Matrix4f matrix = pose.last().pose();
		for (int slice = 0; slice < SLICES; slice++) {
			float u0 = slice / (float) SLICES;
			float u1 = (slice + 1) / (float) SLICES;
			float theta0 = u0 * Mth.TWO_PI;
			float theta1 = u1 * Mth.TWO_PI;
			float x0 = Mth.cos(theta0) * RADIUS;
			float z0 = Mth.sin(theta0) * RADIUS;
			float x1 = Mth.cos(theta1) * RADIUS;
			float z1 = Mth.sin(theta1) * RADIUS;
			quad(consumer, matrix, x0, 0.0F, z0, x1, 0.0F, z1, x1, HEIGHT, z1, x0, HEIGHT, z0);
			quad(consumer, matrix, x0, 0.0F, z0, x0, HEIGHT, z0, x1, HEIGHT, z1, x1, 0.0F, z1);
		}
		pose.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen(AltarBlockEntity altar) {
		return altar.hasFinaleBeam();
	}

	@Override
	public boolean shouldRender(AltarBlockEntity altar, Vec3 cameraPos) {
		if (!altar.hasFinaleBeam()) {
			return BlockEntityRenderer.super.shouldRender(altar, cameraPos);
		}
		net.minecraft.core.BlockPos pos = altar.getBlockPos();
		double dx = cameraPos.x - (pos.getX() + 0.5);
		double dz = cameraPos.z - (pos.getZ() + 0.5);
		int view = getViewDistance();
		return dx * dx + dz * dz < (double) view * view;
	}

	@Override
	public int getViewDistance() {
		return 256;
	}

	private static void quad(
			VertexConsumer consumer,
			Matrix4f matrix,
			float x0, float y0, float z0,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3
	) {
		consumer.addVertex(matrix, x0, y0, z0);
		consumer.addVertex(matrix, x1, y1, z1);
		consumer.addVertex(matrix, x2, y2, z2);
		consumer.addVertex(matrix, x3, y3, z3);
	}
}
