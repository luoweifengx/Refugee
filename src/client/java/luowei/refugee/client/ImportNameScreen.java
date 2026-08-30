package luowei.refugee.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import luowei.refugee.network.ImportNamePayload;
import luowei.refugee.zone.AreaBox;

/**
 * 导入命名：确认扫描 AABB，取消丢掉本次框选。
 */
public class ImportNameScreen extends Screen {
	private final AreaBox box;
	private final int maxAxis;
	private final int maxVolume;
	private EditBox nameBox;
	private String error = "";

	public ImportNameScreen(BlockPos min, BlockPos max, int maxAxis, int maxVolume) {
		super(Component.translatable("screen.refugee.import.name.title"));
		this.box = AreaBox.of(min, max);
		this.maxAxis = maxAxis;
		this.maxVolume = maxVolume;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int bw = 150;
		int cx = width / 2;
		nameBox = new EditBox(font, cx - 150, height / 2 - 10, 300, 20, Component.translatable("screen.refugee.import.name.field"));
		nameBox.setMaxLength(32);
		nameBox.setResponder(value -> error = "");
		addRenderableWidget(nameBox);
		setInitialFocus(nameBox);
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> cancel())
				.bounds(cx - 155, height / 2 + 24, bw, 20)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("screen.refugee.import.name.confirm"), button -> confirm())
				.bounds(cx + 5, height / 2 + 24, bw, 20)
				.build());
	}

	private void confirm() {
		String name = nameBox == null ? "" : nameBox.getValue().trim();
		if (name.isEmpty()) {
			error = Component.translatable("message.refugee.staff.import.empty_name").getString();
			return;
		}
		ClientPlayNetworking.send(new ImportNamePayload(true, name));
	}

	private void cancel() {
		StaffClientNav.resetToRoot();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 256) {
			StaffClientNav.resetToRoot();
			return true;
		}
		if (keyCode == 257 || keyCode == 335) {
			confirm();
			return true;
		}
		if (super.keyPressed(keyCode, scanCode, modifiers)) {
			return true;
		}
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.renderBackground(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 50, 0xFFFFFF);
		graphics.drawCenteredString(
				font,
				Component.translatable(
						"screen.refugee.import.name.size",
						box.sizeX(),
						box.sizeY(),
						box.sizeZ(),
						box.volume(),
						maxAxis,
						maxVolume
				),
				width / 2,
				height / 2 - 32,
				0xAAAAAA
		);
		if (error != null && !error.isEmpty()) {
			graphics.drawCenteredString(font, error, width / 2, height / 2 + 52, 0xFF6666);
		}
	}
}
