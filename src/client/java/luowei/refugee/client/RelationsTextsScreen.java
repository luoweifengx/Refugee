package luowei.refugee.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import luowei.refugee.network.RelationsTextsPayload;

/**
 * 领地文字：自己走进领地时看到的，以及别人看到的，各一条。
 */
public class RelationsTextsScreen extends Screen {
	private final String selfSuggested;
	private final String othersSuggested;
	private final boolean othersEditable;
	private EditBox selfBox;
	private EditBox othersBox;
	private String error = "";

	public RelationsTextsScreen(String selfSuggested, String othersSuggested, boolean othersEditable) {
		super(Component.translatable("screen.refugee.relations.texts.title"));
		this.selfSuggested = selfSuggested == null ? "" : selfSuggested;
		this.othersSuggested = othersSuggested == null ? "" : othersSuggested;
		this.othersEditable = othersEditable;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int cx = width / 2;
		int y = height / 2 - 46;
		selfBox = new EditBox(font, cx - 150, y + 14, 300, 20, Component.translatable("screen.refugee.relations.texts.self"));
		selfBox.setMaxLength(32);
		selfBox.setValue(selfSuggested);
		selfBox.setResponder(value -> error = "");
		addRenderableWidget(selfBox);
		othersBox = new EditBox(font, cx - 150, y + 52, 300, 20, Component.translatable("screen.refugee.relations.texts.others"));
		othersBox.setMaxLength(32);
		othersBox.setValue(othersSuggested);
		othersBox.setEditable(othersEditable);
		othersBox.setResponder(value -> error = "");
		addRenderableWidget(othersBox);
		setInitialFocus(selfBox);
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> cancel())
				.bounds(cx - 155, y + 84, 150, 20)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("screen.refugee.relations.confirm"), button -> confirm())
				.bounds(cx + 5, y + 84, 150, 20)
				.build());
	}

	private void confirm() {
		String self = selfBox == null ? "" : selfBox.getValue().trim();
		String others = othersBox == null ? "" : othersBox.getValue().trim();
		if (self.isEmpty() || (othersEditable && others.isEmpty())) {
			error = Component.translatable("message.refugee.staff.relations.empty_name").getString();
			return;
		}
		ClientPlayNetworking.send(new RelationsTextsPayload(true, self, others));
	}

	private void cancel() {
		ClientPlayNetworking.send(new RelationsTextsPayload(false, "", ""));
		StaffClientNav.resetToRoot();
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
		if (keyCode == 256) {
			cancel();
			return true;
		}
		if (keyCode == 257 || keyCode == 335) {
			confirm();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.renderBackground(graphics, mouseX, mouseY, delta);
		int cx = width / 2;
		int y = height / 2 - 46;
		graphics.drawCenteredString(font, title, cx, y - 28, 0xFFFFFF);
		graphics.drawString(font, Component.translatable("screen.refugee.relations.texts.self"), cx - 150, y + 2, 0xAAAAAA);
		graphics.drawString(font, Component.translatable("screen.refugee.relations.texts.others"), cx - 150, y + 40, 0xAAAAAA);
		if (!othersEditable) {
			graphics.drawCenteredString(
					font,
					Component.translatable("screen.refugee.relations.texts.others.locked"),
					cx,
					y + 108,
					0xAAAAAA
			);
		}
		if (error != null && !error.isEmpty()) {
			graphics.drawCenteredString(font, error, cx, y + 122, 0xFF6666);
		}
	}
}
