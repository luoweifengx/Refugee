package luowei.refugee.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import luowei.refugee.network.BedMarkPayload;

/**
 * 放下床后的职业标记。Esc 和 E 都写成无。
 */
public class BedMarkScreen extends Screen {
	private final BlockPos pos;
	private final List<String> specials;
	private boolean specialPage;
	private boolean sent;

	public BedMarkScreen(BlockPos pos, List<String> specials) {
		super(Component.translatable("screen.refugee.bed.title"));
		this.pos = pos;
		this.specials = specials == null ? List.of() : List.copyOf(specials);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	protected void init() {
		int cx = width / 2;
		int y = height / 2 - buttonCount() * 12;
		if (specialPage) {
			for (String role : specials) {
				addRenderableWidget(choice(cx, y, "role.refugee." + role, role));
				y += 24;
			}
			addRenderableWidget(Button.builder(Component.translatable("screen.refugee.bed.back"), button -> {
				specialPage = false;
				rebuildWidgets();
			}).bounds(cx - 100, y, 200, 20).build());
			return;
		}
		addRenderableWidget(choice(cx, y, "screen.refugee.bed.worker", "worker"));
		y += 24;
		addRenderableWidget(choice(cx, y, "screen.refugee.bed.guard", "guard"));
		y += 24;
		if (!specials.isEmpty()) {
			addRenderableWidget(Button.builder(Component.translatable("screen.refugee.bed.special"), button -> {
				specialPage = true;
				rebuildWidgets();
			}).bounds(cx - 100, y, 200, 20).build());
			y += 24;
		}
		addRenderableWidget(choice(cx, y, "screen.refugee.bed.civilian", "civilian"));
		y += 24;
		addRenderableWidget(choice(cx, y, "screen.refugee.bed.none", ""));
	}

	private int buttonCount() {
		if (specialPage) {
			return specials.size() + 1;
		}
		return specials.isEmpty() ? 4 : 5;
	}

	private Button choice(int cx, int y, String label, String role) {
		return Button.builder(Component.translatable(label), button -> choose(role))
				.bounds(cx - 100, y, 200, 20)
				.build();
	}

	private void choose(String role) {
		if (sent) {
			return;
		}
		sent = true;
		ClientPlayNetworking.send(new BedMarkPayload(pos, role == null ? "" : role));
		if (minecraft != null) {
			minecraft.setScreen(null);
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_E
				|| (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))) {
			choose("");
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - buttonCount() * 12 - 24, 0xFFFFFF);
	}
}
