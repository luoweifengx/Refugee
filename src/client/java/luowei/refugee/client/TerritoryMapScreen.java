package luowei.refugee.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import luowei.refugee.Refugee;
import luowei.refugee.special.TerritoryMapService;

/**
 * 绘图师领土查询：原版填充地图边框 + 区块占领网格，不含地形。
 */
public class TerritoryMapScreen extends Screen {
	private static final ResourceLocation MAP_BACKGROUND =
			ResourceLocation.withDefaultNamespace("textures/map/map_background.png");
	private static final ResourceLocation COMPASS = Refugee.id("textures/talk/compass.png");
	private static final int INNER_SIZE = 147;
	private static final int FRAME = 7;
	private static final int COMPASS_SIZE = 48;
	private static final int[] RADIUS_OPTIONS = {20, 15, 10, 5};

	private int centerX;
	private int centerZ;
	private int radius;
	private byte[] cells;
	private int villagerEntityId;

	public TerritoryMapScreen(int centerX, int centerZ, int radius, byte[] cells, int villagerEntityId) {
		super(Component.translatable("screen.refugee.territory.title"));
		apply(centerX, centerZ, radius, cells, villagerEntityId);
	}

	public void apply(int centerX, int centerZ, int radius, byte[] cells, int villagerEntityId) {
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.radius = TerritoryMapService.clampRadius(radius);
		this.cells = cells == null ? new byte[0] : cells;
		if (villagerEntityId > 0) {
			this.villagerEntityId = villagerEntityId;
		}
	}

	@Override
	protected void init() {
		int buttonY = height - 28;
		int totalWidth = 4 * 40 + 3 * 4 + 110;
		int x = width / 2 - totalWidth / 2;
		for (int option : RADIUS_OPTIONS) {
			int value = option;
			addRenderableWidget(Button.builder(Component.literal(Integer.toString(value)), button -> requestRadius(value))
					.bounds(x, buttonY, 40, 20)
					.build());
			x += 44;
		}
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(x + 6, buttonY, 100, 20)
				.build());
	}

	private void requestRadius(int value) {
		if (value == radius) {
			return;
		}
		RefugeeClient.requestTerritoryRadius(value, villagerEntityId);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.renderBackground(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
		graphics.drawCenteredString(font, Component.literal(centerX + ", " + centerZ), width / 2, 20, 0xAAAAAA);
		int side = Math.max(1, radius * 2 + 1);
		float cell = INNER_SIZE / (float) side;
		int mapW = INNER_SIZE + FRAME * 2;
		int mapH = INNER_SIZE + FRAME * 2;
		int mapX = width / 2 - mapW / 2;
		int mapY = 32;
		graphics.blit(RenderType::guiTextured, MAP_BACKGROUND, mapX, mapY, 0.0f, 0.0f, mapW, mapH, mapW, mapH);
		int gridX = mapX + FRAME;
		int gridY = mapY + FRAME;
		graphics.enableScissor(gridX, gridY, gridX + INNER_SIZE, gridY + INNER_SIZE);
		var pose = graphics.pose();
		pose.pushPose();
		pose.translate(gridX, gridY, 0.0f);
		pose.scale(cell, cell, 1.0f);
		if (cells.length >= side * side) {
			int i = 0;
			for (int row = 0; row < side; row++) {
				for (int col = 0; col < side; col++) {
					graphics.fill(col, row, col + 1, row + 1, colorOf(cells[i++]));
				}
			}
		}
		graphics.fill(radius, radius, radius + 1, radius + 1, 0xFFFFFFFF);
		pose.popPose();
		graphics.disableScissor();
		int compassX = width - COMPASS_SIZE - 8;
		int compassY = 8;
		graphics.blit(
				RenderType::guiTextured,
				COMPASS,
				compassX,
				compassY,
				0.0f,
				0.0f,
				COMPASS_SIZE,
				COMPASS_SIZE,
				COMPASS_SIZE,
				COMPASS_SIZE
		);
		int legendY = mapY + mapH + 6;
		drawLegend(graphics, legendY);
	}

	private void drawLegend(GuiGraphics graphics, int y) {
		int x = width / 2 - 150;
		x = legendSwatch(graphics, x, y, 0xFF2E7D32, "screen.refugee.territory.occupied");
		x = legendSwatch(graphics, x, y, 0xFFF9A825, "screen.refugee.territory.border");
		x = legendSwatch(graphics, x, y, 0xFFC62828, "screen.refugee.territory.other");
		legendSwatch(graphics, x, y, 0xFF9E9E9E, "screen.refugee.territory.empty");
	}

	private int legendSwatch(GuiGraphics graphics, int x, int y, int color, String key) {
		graphics.fill(x, y, x + 8, y + 8, color);
		Component label = Component.translatable(key);
		graphics.drawString(font, label, x + 10, y, 0xFFFFFF);
		return x + 18 + font.width(label);
	}

	private static int colorOf(byte kind) {
		return switch (kind) {
			case TerritoryMapService.OCCUPIED -> 0xFF2E7D32;
			case TerritoryMapService.BORDER -> 0xFFF9A825;
			case TerritoryMapService.OTHER -> 0xFFC62828;
			case TerritoryMapService.EMPTY -> 0xFFBDBDBD;
			default -> 0xFF616161;
		};
	}
}
