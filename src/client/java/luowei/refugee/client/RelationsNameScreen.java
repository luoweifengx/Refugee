package luowei.refugee.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import luowei.refugee.network.RelationsNamePayload;
import luowei.refugee.staff.RelationsNameKind;

/**
 * 创建组织或修改组织领地名。
 */
public class RelationsNameScreen extends Screen {
	private final RelationsNameKind kind;
	private final String suggested;
	private EditBox nameBox;
	private String error = "";

	public RelationsNameScreen(RelationsNameKind kind, String suggested) {
		super(Component.translatable(titleKey(kind)));
		this.kind = kind == null ? RelationsNameKind.CREATE_ORG : kind;
		this.suggested = suggested == null ? "" : suggested;
	}

	public RelationsNameKind kind() {
		return kind;
	}

	private static String titleKey(RelationsNameKind kind) {
		if (kind == RelationsNameKind.RENAME_TERRITORY) {
			return "screen.refugee.relations.rename.title";
		}
		if (kind == RelationsNameKind.RENAME_PERSONAL) {
			return "screen.refugee.relations.rename_personal.title";
		}
		return "screen.refugee.relations.create.title";
	}

	private static String hintKey(RelationsNameKind kind) {
		if (kind == RelationsNameKind.RENAME_TERRITORY) {
			return "screen.refugee.relations.rename.hint";
		}
		if (kind == RelationsNameKind.RENAME_PERSONAL) {
			return "screen.refugee.relations.rename_personal.hint";
		}
		return "screen.refugee.relations.create.hint";
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int cx = width / 2;
		nameBox = new EditBox(font, cx - 150, height / 2 - 10, 300, 20, Component.translatable("screen.refugee.relations.name.field"));
		nameBox.setMaxLength(32);
		nameBox.setResponder(value -> error = "");
		if (!suggested.isEmpty()) {
			nameBox.setValue(suggested);
		}
		addRenderableWidget(nameBox);
		setInitialFocus(nameBox);
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> cancel())
				.bounds(cx - 155, height / 2 + 24, 150, 20)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("screen.refugee.relations.confirm"), button -> confirm())
				.bounds(cx + 5, height / 2 + 24, 150, 20)
				.build());
	}

	private void confirm() {
		String name = nameBox == null ? "" : nameBox.getValue().trim();
		if (name.isEmpty()) {
			error = Component.translatable("message.refugee.staff.relations.empty_name").getString();
			return;
		}
		ClientPlayNetworking.send(new RelationsNamePayload(kind, true, name));
	}

	private void cancel() {
		ClientPlayNetworking.send(new RelationsNamePayload(kind, false, ""));
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
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 48, 0xFFFFFF);
		graphics.drawCenteredString(
				font,
				Component.translatable(hintKey(kind)),
				width / 2,
				height / 2 - 32,
				0xAAAAAA
		);
		if (error != null && !error.isEmpty()) {
			graphics.drawCenteredString(font, error, width / 2, height / 2 + 52, 0xFF6666);
		}
	}
}
