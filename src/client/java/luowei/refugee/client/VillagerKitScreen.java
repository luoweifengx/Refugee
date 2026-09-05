package luowei.refugee.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;

import luowei.refugee.interact.VillagerKitLayout;
import luowei.refugee.interact.VillagerKitMenu;

/**
 * 自绘装具窗：左甲、中主副手、右食物，下为玩家背包。不用原版箱子底图。
 */
public class VillagerKitScreen extends AbstractContainerScreen<VillagerKitMenu> {
	private static final ResourceLocation SLOT_SPRITE =
			ResourceLocation.withDefaultNamespace("container/slot");
	private static final int PANEL = 0xFFC6C6C6;
	private static final int PANEL_DARK = 0xFF8B8B8B;
	private static final int PANEL_LIGHT = 0xFFFFFFFF;
	private static final int PANEL_EDGE = 0xFF000000;
	private static final int LABEL = 0xFF404040;

	public VillagerKitScreen(VillagerKitMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = VillagerKitLayout.WIDTH;
		this.imageHeight = VillagerKitLayout.HEIGHT;
		this.titleLabelX = VillagerKitLayout.TITLE_X;
		this.titleLabelY = VillagerKitLayout.TITLE_Y;
		this.inventoryLabelX = VillagerKitLayout.INV_LABEL_X;
		this.inventoryLabelY = VillagerKitLayout.INV_LABEL_Y;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
		int x = leftPos;
		int y = topPos;
		blitPanel(graphics, x, y, imageWidth, imageHeight);
		blitSlot(graphics, x + VillagerKitLayout.HEAD_X, y + VillagerKitLayout.HEAD_Y);
		blitSlot(graphics, x + VillagerKitLayout.CHEST_X, y + VillagerKitLayout.CHEST_Y);
		blitSlot(graphics, x + VillagerKitLayout.LEGS_X, y + VillagerKitLayout.LEGS_Y);
		blitSlot(graphics, x + VillagerKitLayout.FEET_X, y + VillagerKitLayout.FEET_Y);
		blitSlot(graphics, x + VillagerKitLayout.MAIN_X, y + VillagerKitLayout.MAIN_Y);
		blitSlot(graphics, x + VillagerKitLayout.OFF_X, y + VillagerKitLayout.OFF_Y);
		blitSlot(graphics, x + VillagerKitLayout.FOOD_X, y + VillagerKitLayout.FOOD_Y);
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				blitSlot(
						graphics,
						x + VillagerKitLayout.INV_X + col * VillagerKitLayout.SLOT,
						y + VillagerKitLayout.INV_Y + row * VillagerKitLayout.SLOT
				);
			}
		}
		for (int col = 0; col < 9; col++) {
			blitSlot(
					graphics,
					x + VillagerKitLayout.HOTBAR_X + col * VillagerKitLayout.SLOT,
					y + VillagerKitLayout.HOTBAR_Y
			);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, titleLabelX, titleLabelY, LABEL, false);
		graphics.drawString(
				font,
				Component.translatable("screen.refugee.kit.armor"),
				VillagerKitLayout.ARMOR_LABEL_X,
				VillagerKitLayout.ARMOR_LABEL_Y,
				LABEL,
				false
		);
		graphics.drawString(
				font,
				Component.translatable("screen.refugee.kit.hands"),
				VillagerKitLayout.HANDS_LABEL_X,
				VillagerKitLayout.HANDS_LABEL_Y,
				LABEL,
				false
		);
		graphics.drawString(
				font,
				Component.translatable("screen.refugee.kit.food"),
				VillagerKitLayout.FOOD_LABEL_X,
				VillagerKitLayout.FOOD_LABEL_Y,
				LABEL,
				false
		);
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
		Villager villager = menu.villager();
		if (villager != null) {
			Component health = Component.translatable(
					"screen.refugee.kit.health",
					(int) Math.ceil(villager.getHealth()),
					(int) Math.ceil(villager.getMaxHealth())
			);
			graphics.drawString(font, health, VillagerKitLayout.HEALTH_X, VillagerKitLayout.HEALTH_Y, LABEL, false);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
		renderTooltip(graphics, mouseX, mouseY);
	}

	private static void blitPanel(GuiGraphics graphics, int x, int y, int width, int height) {
		graphics.fill(x, y, x + width, y + height, PANEL_EDGE);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL_LIGHT);
		graphics.fill(x + 2, y + 2, x + width - 1, y + height - 1, PANEL_DARK);
		graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, PANEL);
	}

	private static void blitSlot(GuiGraphics graphics, int x, int y) {
		graphics.blitSprite(
				RenderType::guiTextured,
				SLOT_SPRITE,
				x,
				y,
				VillagerKitLayout.SLOT,
				VillagerKitLayout.SLOT
		);
	}
}
