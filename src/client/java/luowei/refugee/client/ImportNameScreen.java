package luowei.refugee.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.network.ImportNamePayload;
import luowei.refugee.zone.AreaBox;

/**
 * 划为蓝图或上传蓝图时的命名：确认后扫描框选范围或发送文件。
 */
public class ImportNameScreen extends Screen {
	private final Screen parent;
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final long volume;
	private final int maxAxis;
	private final int maxVolume;
	private final byte[] uploadBytes;
	private final String suggested;
	private EditBox nameBox;
	private String error = "";

	public ImportNameScreen(BlockPos min, BlockPos max, int maxAxis, int maxVolume) {
		super(Component.translatable("screen.refugee.import.name.title"));
		AreaBox box = AreaBox.of(min, max);
		this.parent = null;
		this.suggested = "";
		this.sizeX = box.sizeX();
		this.sizeY = box.sizeY();
		this.sizeZ = box.sizeZ();
		this.volume = box.volume();
		this.maxAxis = maxAxis;
		this.maxVolume = maxVolume;
		this.uploadBytes = null;
	}

	public ImportNameScreen(Screen parent, String suggested, int sizeX, int sizeY, int sizeZ, byte[] uploadBytes) {
		super(Component.translatable("screen.refugee.import.name.title"));
		this.parent = parent;
		this.suggested = suggested == null ? "" : suggested;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.volume = (long) Math.max(0, sizeX) * Math.max(0, sizeY) * Math.max(0, sizeZ);
		this.maxAxis = RefugeeConfig.importMaxAxis;
		this.maxVolume = RefugeeConfig.importMaxVolume;
		this.uploadBytes = uploadBytes;
	}

	boolean isUpload() {
		return uploadBytes != null;
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
		if (!suggested.isEmpty()) {
			nameBox.setValue(suggested);
		}
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
		if (isUpload()) {
			RefugeeClient.uploadBlueprint(name, uploadBytes);
			return;
		}
		ClientPlayNetworking.send(new ImportNamePayload(true, name));
	}

	private void cancel() {
		if (isUpload()) {
			minecraft.setScreen(parent);
			return;
		}
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
						sizeX,
						sizeY,
						sizeZ,
						volume,
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
