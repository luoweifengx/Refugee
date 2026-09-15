package luowei.refugee.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import luowei.refugee.network.StaffPiePayload;
import luowei.refugee.staff.StaffPage;
import luowei.refugee.staff.StaffPieAction;

/**
 * 指挥杖扇形菜单：按当前页绘制扇区，物品图标，不暂停世界。
 */
public class StaffPieScreen extends Screen {
	private static final int RADIUS = 52;
	private static final int INNER = 8;
	private static final int FILL_ALPHA = 0xA8;
	private static final int HOVER_ALPHA = 0xD0;
	private static final int SEPARATOR_COLOR = 0xB2141418;
	private static final int RING_COLOR = 0xB2141418;
	private static final float STROKE = 2.0f;
	private static final float AA = 1.0f;
	private static final Slice[] ROOT_SLICES = {
			new Slice(StaffPieAction.WAREHOUSE, new ItemStack(Items.CHEST), 0xCC3A6EA5, "screen.refugee.staff.pie.warehouse"),
			new Slice(StaffPieAction.ZONE, new ItemStack(Items.STONE_PICKAXE), 0xCC3D8B4A, "screen.refugee.staff.pie.zone"),
			new Slice(StaffPieAction.BUILD, new ItemStack(Items.CRAFTING_TABLE), 0xCCB07A2E, "screen.refugee.staff.pie.build"),
			new Slice(StaffPieAction.COMBAT, new ItemStack(Items.IRON_SWORD), 0xCC8B3A3A, "screen.refugee.staff.pie.combat"),
			new Slice(StaffPieAction.RALLY, new ItemStack(Items.GOAT_HORN), 0xCCC4A35A, "screen.refugee.staff.pie.rally"),
			new Slice(StaffPieAction.GUARD, new ItemStack(Items.IRON_HELMET), 0xCC5A6E8B, "screen.refugee.staff.pie.guard")
	};
	private static final Slice[] COMBAT_SLICES = {
			new Slice(StaffPieAction.FOLLOW_ENTITY, new ItemStack(Items.LEAD), 0xCC3A6EA5, "screen.refugee.staff.pie.follow"),
			new Slice(StaffPieAction.PATROL, new ItemStack(Items.COMPASS), 0xCCB07A2E, "screen.refugee.staff.pie.patrol"),
			new Slice(StaffPieAction.FORMATION, new ItemStack(Items.SHIELD), 0xCC6E6E6E, "screen.refugee.staff.pie.formation"),
			new Slice(StaffPieAction.EQUIP_GEAR, new ItemStack(Items.IRON_CHESTPLATE), 0xCC8B5A3A, "screen.refugee.staff.pie.equip")
	};
	private static final Slice[] WAREHOUSE_SLICES = {
			new Slice(StaffPieAction.WAREHOUSE_BLOCKS, new ItemStack(Items.CHEST), 0xCC8B3A3A, "screen.refugee.staff.pie.warehouse_blocks"),
			new Slice(StaffPieAction.WAREHOUSE_FARM, new ItemStack(Items.WHEAT), 0xCC3D8B4A, "screen.refugee.staff.pie.warehouse_farm"),
			new Slice(StaffPieAction.WAREHOUSE_GEAR, new ItemStack(Items.IRON_CHESTPLATE), 0xCC3A6EA5, "screen.refugee.staff.pie.warehouse_gear"),
			new Slice(StaffPieAction.WAREHOUSE_FOOD, new ItemStack(Items.BREAD), 0xCCE08A2A, "screen.refugee.staff.pie.warehouse_food"),
			new Slice(StaffPieAction.WAREHOUSE_SMELT, new ItemStack(Items.FURNACE), 0xCC8B5A9E, "screen.refugee.staff.pie.warehouse_smelt"),
			new Slice(StaffPieAction.WAREHOUSE_SMELT_RESULT, new ItemStack(Items.IRON_INGOT), 0xCCC4A35A, "screen.refugee.staff.pie.warehouse_smelt_result")
	};
	private static final Slice[] BUILD_SLICES = {
			new Slice(StaffPieAction.SELECT, new ItemStack(Items.PAPER), 0xCCB07A2E, "screen.refugee.staff.pie.select"),
			new Slice(StaffPieAction.IMPORT, new ItemStack(Items.WRITABLE_BOOK), 0xCC8B5A9E, "screen.refugee.staff.pie.import")
	};
	private static final Slice[] ZONE_SLICES = {
			new Slice(StaffPieAction.ZONE_BOX, new ItemStack(Items.STONE_PICKAXE), 0xCC3D8B4A, "screen.refugee.staff.pie.zone_box"),
			new Slice(StaffPieAction.ZONE_ADVANCE, new ItemStack(Items.IRON_SHOVEL), 0xCC2E8B8B, "screen.refugee.staff.pie.zone_advance"),
			new Slice(StaffPieAction.ZONE_REPAIR, new ItemStack(Items.BRICKS), 0xCC8B5A3A, "screen.refugee.staff.pie.zone_repair"),
			new Slice(StaffPieAction.ZONE_BUILD, new ItemStack(Items.CRAFTING_TABLE), 0xCCB07A2E, "screen.refugee.staff.pie.zone_build"),
			new Slice(StaffPieAction.ZONE_SMELT, new ItemStack(Items.BLAST_FURNACE), 0xCCB85A2A, "screen.refugee.staff.pie.zone_smelt")
	};
	private static final Slice[] RALLY_SLICES = {
			new Slice(StaffPieAction.RALLY_MELEE, new ItemStack(Items.IRON_SWORD), 0xCC8B3A3A, "screen.refugee.staff.pie.rally_melee"),
			new Slice(StaffPieAction.RALLY_RANGED, new ItemStack(Items.BOW), 0xCC3D8B4A, "screen.refugee.staff.pie.rally_ranged"),
			new Slice(StaffPieAction.RALLY_WORKER, new ItemStack(Items.STONE_PICKAXE), 0xCCB07A2E, "screen.refugee.staff.pie.rally_worker"),
			new Slice(StaffPieAction.RALLY_CIVILIAN, new ItemStack(Items.FEATHER), 0xCC8FA3B0, "screen.refugee.staff.pie.rally_civilian"),
			new Slice(StaffPieAction.RALLY_SPECIAL, new ItemStack(Items.EMERALD), 0xCC8B6EC4, "screen.refugee.staff.pie.rally_special"),
			new Slice(StaffPieAction.RALLY_ALL, new ItemStack(Items.GOAT_HORN), 0xCCC4A35A, "screen.refugee.staff.pie.rally_all")
	};
	private static final Slice[] GUARD_SLICES = {
			new Slice(StaffPieAction.GUARD_ADD, new ItemStack(Items.NAME_TAG), 0xCC3D8B4A, "screen.refugee.staff.pie.guard_add"),
			new Slice(StaffPieAction.GUARD_RALLY_NEAR, new ItemStack(Items.SPYGLASS), 0xCCC4A35A, "screen.refugee.staff.pie.guard_rally_near"),
			new Slice(StaffPieAction.GUARD_RALLY_ALL, new ItemStack(Items.ENDER_EYE), 0xCC8B5A9E, "screen.refugee.staff.pie.guard_rally_all"),
			new Slice(StaffPieAction.GUARD_REMOVE, new ItemStack(Items.SHEARS), 0xCC8B3A3A, "screen.refugee.staff.pie.guard_remove")
	};

	private final StaffPage page;

	public StaffPieScreen() {
		this(ClientStaffState.page());
	}

	public StaffPieScreen(StaffPage page) {
		super(titleFor(page));
		this.page = page == null ? StaffPage.PIE : page;
	}

	private static Component titleFor(StaffPage page) {
		if (page == StaffPage.COMBAT_PIE) {
			return Component.translatable("screen.refugee.staff.pie.combat");
		}
		if (page == StaffPage.BUILD_PIE) {
			return Component.translatable("screen.refugee.staff.pie.build");
		}
		if (page == StaffPage.WAREHOUSE_PIE) {
			return Component.translatable("screen.refugee.staff.pie.warehouse");
		}
		if (page == StaffPage.ZONE_PIE) {
			return Component.translatable("screen.refugee.staff.pie.zone");
		}
		if (page == StaffPage.RALLY_PIE) {
			return Component.translatable("screen.refugee.staff.pie.rally");
		}
		if (page == StaffPage.GUARD_PIE) {
			return Component.translatable("screen.refugee.staff.pie.guard");
		}
		return Component.translatable("screen.refugee.staff.pie.title");
	}

	private Slice[] slices() {
		if (page == StaffPage.COMBAT_PIE) {
			return COMBAT_SLICES;
		}
		if (page == StaffPage.BUILD_PIE) {
			return BUILD_SLICES;
		}
		if (page == StaffPage.WAREHOUSE_PIE) {
			return WAREHOUSE_SLICES;
		}
		if (page == StaffPage.ZONE_PIE) {
			return ZONE_SLICES;
		}
		if (page == StaffPage.RALLY_PIE) {
			return RALLY_SLICES;
		}
		if (page == StaffPage.GUARD_PIE) {
			return GUARD_SLICES;
		}
		return ROOT_SLICES;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
		int cx = width / 2;
		int cy = height / 2;
		Slice[] slices = slices();
		Slice hover = hit(mouseX, mouseY, cx, cy, slices);
		drawPie(graphics, cx, cy, slices, hover);
		for (int i = 0; i < slices.length; i++) {
			drawIcon(graphics, cx, cy, i, slices.length, slices[i].icon);
		}
		graphics.drawCenteredString(font, title, cx, cy - RADIUS - 16, 0xFFECECEC);
		Component hoverName = hover == null
				? Component.translatable("screen.refugee.staff.pie.hint")
				: Component.translatable(hover.labelKey);
		graphics.drawCenteredString(font, hoverName, cx, cy + RADIUS + 12, hover == null ? 0xFFAAAAAA : 0xFFECECEC);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			Slice action = hit(mouseX, mouseY, width / 2.0, height / 2.0, slices());
			if (action != null) {
				ClientPlayNetworking.send(new StaffPiePayload(action.action));
				onClose();
				return true;
			}
		}
		if (button == 1) {
			StaffClientNav.resetToRoot();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (StaffClientNav.handleScreenKey(this, keyCode, scanCode)) {
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void drawIcon(GuiGraphics graphics, int cx, int cy, int index, int count, ItemStack stack) {
		double[] range = sectorRange(index, count);
		double mid = (range[0] + range[1]) / 2.0;
		int x = cx + (int) Math.round(Math.cos(mid) * RADIUS * 0.58);
		int y = cy + (int) Math.round(Math.sin(mid) * RADIUS * 0.58);
		graphics.renderFakeItem(stack, x - 8, y - 8);
	}

	private static Slice hit(double mouseX, double mouseY, double cx, double cy, Slice[] slices) {
		double dx = mouseX - cx;
		double dy = mouseY - cy;
		double dist = Math.hypot(dx, dy);
		if (dist < INNER || dist > RADIUS + 8) {
			return null;
		}
		double angle = Math.atan2(dy, dx);
		if (angle < -Math.PI / 2.0) {
			angle += Math.PI * 2.0;
		}
		double start = -Math.PI / 2.0;
		double span = (Math.PI * 2.0) / slices.length;
		int index = (int) Math.floor((angle - start) / span);
		if (index < 0 || index >= slices.length) {
			return null;
		}
		return slices[index];
	}

	private static double[] sectorRange(int index, int count) {
		double start = -Math.PI / 2.0 + (Math.PI * 2.0) * index / count;
		double end = -Math.PI / 2.0 + (Math.PI * 2.0) * (index + 1) / count;
		return new double[] { start, end };
	}

	private static void drawPie(GuiGraphics graphics, int cx, int cy, Slice[] slices, Slice hover) {
		graphics.drawSpecial(buffers -> paintPie(graphics, buffers, cx, cy, slices, hover));
	}

	private static void paintPie(
			GuiGraphics graphics,
			MultiBufferSource buffers,
			int cx,
			int cy,
			Slice[] slices,
			Slice hover
	) {
		VertexConsumer consumer = buffers.getBuffer(RenderType.gui());
		PoseStack.Pose pose = graphics.pose().last();
		float half = STROKE * 0.5f;
		for (int i = 0; i < slices.length; i++) {
			double[] range = sectorRange(i, slices.length);
			int argb = withAlpha(slices[i].color, hover == slices[i] ? HOVER_ALPHA : FILL_ALPHA);
			fillAnnulus(consumer, pose, cx, cy, INNER, RADIUS, range[0], range[1], argb);
		}
		for (int i = 0; i < slices.length; i++) {
			double t = -Math.PI / 2.0 + (Math.PI * 2.0) * i / slices.length;
			strokeRadial(consumer, pose, cx, cy, INNER, RADIUS, t, half, AA, SEPARATOR_COLOR);
		}
		strokeCircle(consumer, pose, cx, cy, RADIUS, half, AA, RING_COLOR);
		strokeCircle(consumer, pose, cx, cy, INNER, half, AA, RING_COLOR);
	}

	private static void strokeCircle(
			VertexConsumer consumer,
			PoseStack.Pose pose,
			float cx,
			float cy,
			float radius,
			float half,
			float aa,
			int color
	) {
		int clear = fade(color, 0.0f);
		fillAnnulus(consumer, pose, cx, cy, radius - half, radius + half, 0.0, Math.PI * 2.0, color);
		fillAnnulus(consumer, pose, cx, cy, radius + half, radius + half + aa, 0.0, Math.PI * 2.0, color, clear);
		fillAnnulus(consumer, pose, cx, cy, radius - half - aa, radius - half, 0.0, Math.PI * 2.0, clear, color);
	}

	private static void strokeRadial(
			VertexConsumer consumer,
			PoseStack.Pose pose,
			float cx,
			float cy,
			float inner,
			float outer,
			double angle,
			float half,
			float aa,
			int color
	) {
		float cos = (float) Math.cos(angle);
		float sin = (float) Math.sin(angle);
		float px = -sin;
		float py = cos;
		float ix = cx + cos * inner;
		float iy = cy + sin * inner;
		float ox = cx + cos * outer;
		float oy = cy + sin * outer;
		int clear = fade(color, 0.0f);
		radialStrip(consumer, pose, ix, iy, ox, oy, px, py, -half, half, color, color);
		radialStrip(consumer, pose, ix, iy, ox, oy, px, py, half, half + aa, color, clear);
		radialStrip(consumer, pose, ix, iy, ox, oy, px, py, -half - aa, -half, clear, color);
	}

	private static void radialStrip(
			VertexConsumer consumer,
			PoseStack.Pose pose,
			float ix,
			float iy,
			float ox,
			float oy,
			float px,
			float py,
			float a,
			float b,
			int ca,
			int cb
	) {
		float ax = px * a;
		float ay = py * a;
		float bx = px * b;
		float by = py * b;
		quad(
				consumer,
				pose,
				ix + ax,
				iy + ay,
				ox + ax,
				oy + ay,
				ox + bx,
				oy + by,
				ix + bx,
				iy + by,
				ca,
				ca,
				cb,
				cb
		);
	}

	private static void fillAnnulus(
			VertexConsumer consumer,
			PoseStack.Pose pose,
			float cx,
			float cy,
			float inner,
			float outer,
			double start,
			double end,
			int color
	) {
		fillAnnulus(consumer, pose, cx, cy, inner, outer, start, end, color, color);
	}

	private static void fillAnnulus(
			VertexConsumer consumer,
			PoseStack.Pose pose,
			float cx,
			float cy,
			float inner,
			float outer,
			double start,
			double end,
			int innerColor,
			int outerColor
	) {
		int steps = Math.max(12, (int) Math.ceil(Math.abs(end - start) * 18.0));
		float prevCos = (float) Math.cos(start);
		float prevSin = (float) Math.sin(start);
		for (int i = 1; i <= steps; i++) {
			double t = start + (end - start) * i / steps;
			float cos = (float) Math.cos(t);
			float sin = (float) Math.sin(t);
			quad(
					consumer,
					pose,
					cx + prevCos * outer,
					cy + prevSin * outer,
					cx + prevCos * inner,
					cy + prevSin * inner,
					cx + cos * inner,
					cy + sin * inner,
					cx + cos * outer,
					cy + sin * outer,
					outerColor,
					innerColor,
					innerColor,
					outerColor
			);
			prevCos = cos;
			prevSin = sin;
		}
	}

	private static void quad(
			VertexConsumer consumer,
			PoseStack.Pose pose,
			float x0,
			float y0,
			float x1,
			float y1,
			float x2,
			float y2,
			float x3,
			float y3,
			int c0,
			int c1,
			int c2,
			int c3
	) {
		consumer.addVertex(pose, x0, y0, 0).setColor(c0);
		consumer.addVertex(pose, x1, y1, 0).setColor(c1);
		consumer.addVertex(pose, x2, y2, 0).setColor(c2);
		consumer.addVertex(pose, x3, y3, 0).setColor(c3);
	}

	private static int withAlpha(int color, int alpha) {
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	private static int fade(int color, float t) {
		if (t <= 0.0f) {
			return color & 0x00FFFFFF;
		}
		if (t >= 1.0f) {
			return color;
		}
		int alpha = Math.round(((color >>> 24) & 0xFF) * t);
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	private record Slice(StaffPieAction action, ItemStack icon, int color, String labelKey) {
	}
}
