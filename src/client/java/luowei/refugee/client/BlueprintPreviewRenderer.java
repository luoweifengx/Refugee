package luowei.refugee.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

import luowei.refugee.blueprint.BlueprintBlocks;
import luowei.refugee.blueprint.BlueprintTransforms;
import luowei.refugee.staff.CommandStaffItem;
import luowei.refugee.staff.StaffPage;

/**
 * 手持指挥杖且已选定结构时，在准星方块或已定原点处绘制半透明结构投影。
 */
public final class BlueprintPreviewRenderer {
	private static final double MAX_DISTANCE = 20.0;
	private static final float ALPHA = 0.45f;

	private BlueprintPreviewRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(BlueprintPreviewRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return;
		}
		ItemStack held = findHeldStaff(player);
		if (held == null) {
			return;
		}
		ResourceLocation structureId = ClientBlueprintSelection.structureId();
		StructureTemplate template = ClientBlueprintTemplates.get(structureId);
		if (template == null) {
			return;
		}
		if (ClientStaffState.page() != StaffPage.BUILD_PREVIEW) {
			return;
		}
		BlockPos origin = StaffClientNav.resolveLookOrigin(client);
		if (origin == null) {
			return;
		}
		Vec3 originCenter = Vec3.atCenterOf(origin);
		if (player.position().distanceTo(originCenter) > MAX_DISTANCE) {
			return;
		}
		List<StructureTemplate.StructureBlockInfo> blocks = BlueprintTransforms.placedBlocks(
				template,
				origin,
				ClientBlueprintSelection.offsetX(),
				ClientBlueprintSelection.offsetY(),
				ClientBlueprintSelection.offsetZ(),
				ClientBlueprintSelection.rotation()
		);
		if (blocks.isEmpty()) {
			return;
		}

		PoseStack poseStack = context.matrixStack();
		MultiBufferSource consumers = context.consumers();
		if (poseStack == null || consumers == null) {
			return;
		}
		Vec3 camera = context.camera().getPosition();
		BlockRenderDispatcher dispatcher = client.getBlockRenderer();
		MultiBufferSource ghostBuffers = type -> new AlphaVertexConsumer(consumers.getBuffer(RenderType.translucent()), ALPHA);

		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		for (StructureTemplate.StructureBlockInfo info : blocks) {
			if (shouldSkip(info)) {
				continue;
			}
			BlockPos worldPos = info.pos();
			poseStack.pushPose();
			poseStack.translate(worldPos.getX(), worldPos.getY(), worldPos.getZ());
			dispatcher.renderSingleBlock(
					info.state(),
					poseStack,
					ghostBuffers,
					0xF000F0,
					OverlayTexture.NO_OVERLAY
			);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private static ItemStack findHeldStaff(LocalPlayer player) {
		ItemStack main = player.getMainHandItem();
		if (main.getItem() instanceof CommandStaffItem) {
			return main;
		}
		ItemStack off = player.getOffhandItem();
		if (off.getItem() instanceof CommandStaffItem) {
			return off;
		}
		return null;
	}

	private static boolean shouldSkip(StructureTemplate.StructureBlockInfo info) {
		return BlueprintBlocks.shouldSkip(info);
	}

	/**
	 * 把顶点颜色 alpha 乘以幽灵透明度。
	 */
	private static final class AlphaVertexConsumer implements VertexConsumer {
		private final VertexConsumer delegate;
		private final float alpha;

		private AlphaVertexConsumer(VertexConsumer delegate, float alpha) {
			this.delegate = delegate;
			this.alpha = alpha;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			delegate.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			delegate.setColor(r, g, b, Math.round(a * alpha));
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			delegate.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			delegate.setUv1(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			delegate.setUv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			delegate.setNormal(x, y, z);
			return this;
		}
	}
}
