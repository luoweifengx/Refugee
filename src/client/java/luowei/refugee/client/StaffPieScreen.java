package luowei.refugee.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import luowei.refugee.network.StaffPiePayload;
import luowei.refugee.staff.StaffPieAction;

/**
 * 指挥杖扇形菜单：四象限，物品图标，不暂停世界。
 */
public class StaffPieScreen extends Screen {
	private static final int RADIUS = 45;
	private static final int INNER = 8;
	private static final ItemStack ICON_WAREHOUSE = new ItemStack(Items.CHEST);
	private static final ItemStack ICON_ZONE = new ItemStack(Items.STONE_PICKAXE);
	private static final ItemStack ICON_SELECT = new ItemStack(Items.PAPER);
	private static final ItemStack ICON_IMPORT = new ItemStack(Items.WRITABLE_BOOK);

	public StaffPieScreen() {
		super(Component.translatable("screen.refugee.staff.pie.title"));
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
		StaffPieAction hover = hit(mouseX, mouseY, cx, cy);
		drawSector(graphics, cx, cy, StaffPieAction.WAREHOUSE, hover == StaffPieAction.WAREHOUSE, 0xCC3A6EA5);
		drawSector(graphics, cx, cy, StaffPieAction.ZONE, hover == StaffPieAction.ZONE, 0xCC3D8B4A);
		drawSector(graphics, cx, cy, StaffPieAction.SELECT, hover == StaffPieAction.SELECT, 0xCCB07A2E);
		drawSector(graphics, cx, cy, StaffPieAction.IMPORT, hover == StaffPieAction.IMPORT, 0xCC8B5A9E);
		drawSeparators(graphics, cx, cy);
		drawIcon(graphics, cx, cy, StaffPieAction.WAREHOUSE, ICON_WAREHOUSE);
		drawIcon(graphics, cx, cy, StaffPieAction.ZONE, ICON_ZONE);
		drawIcon(graphics, cx, cy, StaffPieAction.SELECT, ICON_SELECT);
		drawIcon(graphics, cx, cy, StaffPieAction.IMPORT, ICON_IMPORT);
		graphics.drawCenteredString(font, title, cx, cy - RADIUS - 16, 0xFFECECEC);
		graphics.drawCenteredString(
				font,
				Component.translatable("screen.refugee.staff.pie.hint"),
				cx,
				cy + RADIUS + 12,
				0xFFAAAAAA
		);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			StaffPieAction action = hit(mouseX, mouseY, width / 2.0, height / 2.0);
			if (action != null) {
				ClientPlayNetworking.send(new StaffPiePayload(action));
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

	private void drawIcon(GuiGraphics graphics, int cx, int cy, StaffPieAction action, ItemStack stack) {
		double[] range = sectorRange(action);
		double mid = (range[0] + range[1]) / 2.0;
		int x = cx + (int) Math.round(Math.cos(mid) * RADIUS * 0.58);
		int y = cy + (int) Math.round(Math.sin(mid) * RADIUS * 0.58);
		graphics.renderFakeItem(stack, x - 8, y - 8);
	}

	private static StaffPieAction hit(double mouseX, double mouseY, double cx, double cy) {
		double dx = mouseX - cx;
		double dy = mouseY - cy;
		double dist = Math.hypot(dx, dy);
		if (dist < INNER || dist > RADIUS + 8) {
			return null;
		}
		double angle = Math.toDegrees(Math.atan2(dy, dx));
		if (angle >= -90.0 && angle < 0.0) {
			return StaffPieAction.WAREHOUSE;
		}
		if (angle >= 0.0 && angle < 90.0) {
			return StaffPieAction.ZONE;
		}
		if (angle >= 90.0 && angle < 180.0) {
			return StaffPieAction.SELECT;
		}
		return StaffPieAction.IMPORT;
	}

	private static double[] sectorRange(StaffPieAction action) {
		return switch (action) {
			case WAREHOUSE -> new double[] { Math.toRadians(-90), Math.toRadians(0) };
			case ZONE -> new double[] { Math.toRadians(0), Math.toRadians(90) };
			case SELECT -> new double[] { Math.toRadians(90), Math.toRadians(180) };
			case IMPORT -> new double[] { Math.toRadians(180), Math.toRadians(270) };
		};
	}

	private static void drawSector(GuiGraphics graphics, int cx, int cy, StaffPieAction action, boolean hover, int color) {
		double[] range = sectorRange(action);
		int argb = hover ? (0xF0000000 | (color & 0x00FFFFFF)) : color;
		for (int i = 0; i <= 72; i++) {
			double t = range[0] + (range[1] - range[0]) * i / 72.0;
			int x1 = cx + (int) Math.round(Math.cos(t) * INNER);
			int y1 = cy + (int) Math.round(Math.sin(t) * INNER);
			int x2 = cx + (int) Math.round(Math.cos(t) * RADIUS);
			int y2 = cy + (int) Math.round(Math.sin(t) * RADIUS);
			drawThickLine(graphics, x1, y1, x2, y2, argb);
		}
		drawArc(graphics, cx, cy, RADIUS, range[0], range[1], argb);
		drawArc(graphics, cx, cy, INNER, range[0], range[1], argb);
	}

	private static void drawSeparators(GuiGraphics graphics, int cx, int cy) {
		int[] degrees = { 0, 90, 180, 270 };
		for (int deg : degrees) {
			double t = Math.toRadians(deg);
			int x1 = cx + (int) Math.round(Math.cos(t) * INNER);
			int y1 = cy + (int) Math.round(Math.sin(t) * INNER);
			int x2 = cx + (int) Math.round(Math.cos(t) * RADIUS);
			int y2 = cy + (int) Math.round(Math.sin(t) * RADIUS);
			drawSeparatorLine(graphics, x1, y1, x2, y2);
		}
	}

	private static void drawArc(GuiGraphics graphics, int cx, int cy, int radius, double start, double end, int color) {
		int prevX = cx + (int) Math.round(Math.cos(start) * radius);
		int prevY = cy + (int) Math.round(Math.sin(start) * radius);
		for (int i = 1; i <= 36; i++) {
			double t = start + (end - start) * i / 36.0;
			int x = cx + (int) Math.round(Math.cos(t) * radius);
			int y = cy + (int) Math.round(Math.sin(t) * radius);
			drawThickLine(graphics, prevX, prevY, x, y, color);
			prevX = x;
			prevY = y;
		}
	}

	private static void drawThickLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
		strokeLine(graphics, x0, y0, x1, y1, color, 1);
	}

	private static void drawSeparatorLine(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
		strokeLine(graphics, x0, y0, x1, y1, 0xFF000000, 2);
	}

	private static void strokeLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color, int half) {
		int dx = Math.abs(x1 - x0);
		int dy = Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int err = dx - dy;
		int x = x0;
		int y = y0;
		while (true) {
			graphics.fill(x - half, y - half, x + half + 1, y + half + 1, color);
			if (x == x1 && y == y1) {
				break;
			}
			int e2 = 2 * err;
			if (e2 > -dy) {
				err -= dy;
				x += sx;
			}
			if (e2 < dx) {
				err += dx;
				y += sy;
			}
		}
	}
}
