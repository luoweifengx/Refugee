package luowei.refugee.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import luowei.refugee.staff.CommandStaffItem;
import luowei.refugee.staff.StaffMode;
import luowei.refugee.staff.StaffPage;
import luowei.refugee.zone.AreaBox;

/**
 * 手持指挥杖或处于对应模式时，绘制仓库红框与工作区绿框。
 */
public final class StaffOverlayRenderer {
	private StaffOverlayRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(StaffOverlayRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return;
		}
		boolean holding = isHoldingStaff(player);
		boolean active = ClientStaffState.page() != StaffPage.ROOT;
		if (!holding && !active) {
			return;
		}
		PoseStack poseStack = context.matrixStack();
		MultiBufferSource consumers = context.consumers();
		if (poseStack == null || consumers == null) {
			return;
		}
		Vec3 camera = context.camera().getPosition();
		VertexConsumer lines = consumers.getBuffer(RenderType.lines());
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		if (holding || ClientStaffState.mode() == StaffMode.WAREHOUSE) {
			for (BlockPos pos : ClientStaffState.chests()) {
				AABB box = AABB.encapsulatingFullBlocks(pos, pos).inflate(0.002);
				ShapeRenderer.renderLineBox(
						poseStack,
						lines,
						box.minX,
						box.minY,
						box.minZ,
						box.maxX,
						box.maxY,
						box.maxZ,
						1.0f,
						0.15f,
						0.15f,
						1.0f
				);
			}
		}
		if (holding || ClientStaffState.mode() == StaffMode.ZONE) {
			for (AreaBox zone : ClientStaffState.zones()) {
				drawBox(poseStack, lines, zone.aabb(), 0.2f, 0.9f, 0.25f);
			}
		}
		if (holding || ClientStaffState.page() == StaffPage.BUILD_PREVIEW || ClientStaffState.page() == StaffPage.BUILD_CATALOG) {
			for (AreaBox build : ClientStaffState.builds()) {
				drawBox(poseStack, lines, build.aabb(), 0.35f, 0.75f, 1.0f);
			}
		}
		if (holding || ClientStaffState.mode() == StaffMode.IMPORT) {
			AreaBox locked = ClientStaffState.importBox();
			if (locked != null) {
				drawBox(poseStack, lines, locked.aabb(), 1.0f, 0.72f, 0.12f);
			} else {
				BlockPos first = ClientStaffState.pendingCorner();
				if (first != null) {
					BlockPos second = first;
					if (client.hitResult instanceof BlockHitResult blockHit && client.hitResult.getType() == HitResult.Type.BLOCK) {
						second = blockHit.getBlockPos();
					}
					drawBox(poseStack, lines, AreaBox.of(first, second).aabb(), 1.0f, 0.72f, 0.12f);
				}
			}
		}
		poseStack.popPose();
	}

	private static boolean isHoldingStaff(LocalPlayer player) {
		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		return main.getItem() instanceof CommandStaffItem || off.getItem() instanceof CommandStaffItem;
	}

	private static void drawBox(PoseStack poseStack, VertexConsumer lines, AABB box, float r, float g, float b) {
		AABB inflated = box.inflate(0.002);
		ShapeRenderer.renderLineBox(
				poseStack,
				lines,
				inflated.minX,
				inflated.minY,
				inflated.minZ,
				inflated.maxX,
				inflated.maxY,
				inflated.maxZ,
				r,
				g,
				b,
				1.0f
		);
	}
}
