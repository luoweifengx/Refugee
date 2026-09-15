package luowei.refugee.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import luowei.refugee.item.ItemData;
import luowei.refugee.network.BannerStyleSavePayload;
import luowei.refugee.settle.BannerStyle;

/**
 * 安顿旗样式：输入底色与图案层，保存后手持与投掷共用。
 */
public class BannerStyleScreen extends Screen {
	private final InteractionHand hand;
	private final String initial;
	private EditBox input;
	private ItemStack preview = ItemStack.EMPTY;
	private String error = "";

	public BannerStyleScreen(InteractionHand hand, String initial) {
		super(Component.translatable("screen.refugee.banner.style.title"));
		this.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
		this.initial = initial == null ? "" : initial;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int cx = width / 2;
		input = new EditBox(font, cx - 160, height / 2 - 6, 320, 20, Component.translatable("screen.refugee.banner.style.field"));
		input.setMaxLength(BannerStyle.MAX_INPUT);
		input.setValue(initial);
		input.setResponder(value -> refreshPreview());
		addRenderableWidget(input);
		setInitialFocus(input);
		addRenderableWidget(Button.builder(Component.translatable("screen.refugee.banner.style.copy_offhand"), button -> copyOffhand())
				.bounds(cx - 155, height / 2 + 24, 150, 20)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("screen.refugee.banner.style.save"), button -> save())
				.bounds(cx + 5, height / 2 + 24, 150, 20)
				.build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
				.bounds(cx - 75, height / 2 + 48, 150, 20)
				.build());
		refreshPreview();
	}

	private void refreshPreview() {
		if (minecraft == null || minecraft.player == null) {
			return;
		}
		ItemStack held = minecraft.player.getItemInHand(hand);
		DyeColor fallback = held.getItem() instanceof BannerItem banner ? banner.getColor() : DyeColor.WHITE;
		BannerStyle style = BannerStyle.parse(minecraft.player, input == null ? "" : input.getValue(), fallback);
		if (style == null) {
			preview = held.copy();
			error = Component.translatable("message.refugee.banner.style.invalid").getString();
			return;
		}
		error = "";
		preview = ItemData.createSettlementBanner(style.base(), style.patterns());
	}

	private void save() {
		if (error != null && !error.isEmpty()) {
			return;
		}
		String text = input == null ? "" : input.getValue();
		ClientPlayNetworking.send(new BannerStyleSavePayload(hand, text, false));
		onClose();
	}

	private void copyOffhand() {
		if (minecraft == null || minecraft.player == null
				|| !(minecraft.player.getOffhandItem().getItem() instanceof BannerItem)) {
			error = Component.translatable("message.refugee.banner.style.offhand").getString();
			return;
		}
		ClientPlayNetworking.send(new BannerStyleSavePayload(hand, "", true));
		onClose();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 257 || keyCode == 335) {
			save();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.renderBackground(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 78, 0xFFFFFF);
		graphics.drawCenteredString(
				font,
				Component.translatable("screen.refugee.banner.style.hint"),
				width / 2,
				height / 2 - 62,
				0xAAAAAA
		);
		graphics.drawCenteredString(
				font,
				Component.translatable("screen.refugee.banner.style.example"),
				width / 2,
				height / 2 - 50,
				0x888888
		);
		if (!preview.isEmpty()) {
			graphics.renderItem(preview, width / 2 - 8, height / 2 - 36);
		}
		if (error != null && !error.isEmpty()) {
			graphics.drawCenteredString(font, error, width / 2, height / 2 + 74, 0xFF6666);
		}
	}
}
