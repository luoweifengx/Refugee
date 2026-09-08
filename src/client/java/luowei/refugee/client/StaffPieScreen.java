package luowei.refugee.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
	private static final Slice[] ROOT_SLICES = {
			new Slice(StaffPieAction.WAREHOUSE, new ItemStack(Items.CHEST), 0xCC3A6EA5, "screen.refugee.staff.pie.warehouse"),
			new Slice(StaffPieAction.ZONE, new ItemStack(Items.STONE_PICKAXE), 0xCC3D8B4A, "screen.refugee.staff.pie.zone"),
			new Slice(StaffPieAction.SELECT, new ItemStack(Items.PAPER), 0xCCB07A2E, "screen.refugee.staff.pie.select"),
			new Slice(StaffPieAction.IMPORT, new ItemStack(Items.WRITABLE_BOOK), 0xCC8B5A9E, "screen.refugee.staff.pie.import"),
			new Slice(StaffPieAction.COMBAT, new ItemStack(Items.IRON_SWORD), 0xCC8B3A3A, "screen.refugee.staff.pie.combat"),
			new Slice(StaffPieAction.RALLY, new ItemStack(Items.GOAT_HORN), 0xCCC4A35A, "screen.refugee.staff.pie.rally")
	};
	private static final Slice[] COMBAT_SLICES = {
			new Slice(StaffPieAction.FOLLOW_ENTITY, new ItemStack(Items.LEAD), 0xCC3A6EA5, "screen.refugee.staff.pie.follow"),
			new Slice(StaffPieAction.PATROL, new ItemStack(Items.COMPASS), 0xCCB07A2E, "screen.refugee.staff.pie.patrol"),
			new Slice(StaffPieAction.FORMATION, new ItemStack(Items.SHIELD), 0xCC6E6E6E, "screen.refugee.staff.pie.formation")
	};
	private static final Slice[] WAREHOUSE_SLICES = {
			new Slice(StaffPieAction.WAREHOUSE_BLOCKS, new ItemStack(Items.CHEST), 0xCC8B3A3A, "screen.refugee.staff.pie.warehouse_blocks"),
			new Slice(StaffPieAction.WAREHOUSE_FOOD, new ItemStack(Items.BREAD), 0xCCE08A2A, "screen.refugee.staff.pie.warehouse_food")
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
		if (page == StaffPage.WAREHOUSE_PIE) {
			return Component.translatable("screen.refugee.staff.pie.warehouse");
		}
		return Component.translatable("screen.refugee.staff.pie.title");
	}

	private Slice[] slices() {
		if (page == StaffPage.COMBAT_PIE) {
			return COMBAT_SLICES;
		}
		if (page == StaffPage.WAREHOUSE_PIE) {
			return WAREHOUSE_SLICES;
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
		for (int i = 0; i < slices.length; i++) {
			drawSector(graphics, cx, cy, i, slices.length, hover == slices[i], slices[i].color);
		}
		drawSeparators(graphics, cx, cy, slices.length);
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

	private static void drawSector(GuiGraphics graphics, int cx, int cy, int index, int count, boolean hover, int color) {
		double[] range = sectorRange(index, count);
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

	private static void drawSeparators(GuiGraphics graphics, int cx, int cy, int count) {
		for (int i = 0; i < count; i++) {
			double t = -Math.PI / 2.0 + (Math.PI * 2.0) * i / count;
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

	private record Slice(StaffPieAction action, ItemStack icon, int color, String labelKey) {
	}
}
