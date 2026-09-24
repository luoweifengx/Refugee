package luowei.refugee.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import luowei.refugee.network.RelationsInviteReplyPayload;

/**
 * 查看并处理自己未处理的组织邀请。
 */
public class RelationsInviteScreen extends Screen {
	private final boolean pending;
	private final String orgName;
	private final String territoryName;

	public RelationsInviteScreen(boolean pending, String orgName, String territoryName) {
		super(Component.translatable("screen.refugee.relations.invites.title"));
		this.pending = pending;
		this.orgName = orgName == null ? "" : orgName;
		this.territoryName = territoryName == null ? "" : territoryName;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int y = height / 2 + 24;
		if (!pending) {
			addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> StaffClientNav.resetToRoot())
					.bounds(width / 2 - 75, y, 150, 20)
					.build());
			return;
		}
		addRenderableWidget(Button.builder(Component.translatable("screen.refugee.relations.invites.deny"), button -> reply(false))
				.bounds(width / 2 - 155, y, 150, 20)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("screen.refugee.relations.invites.accept"), button -> reply(true))
				.bounds(width / 2 + 5, y, 150, 20)
				.build());
	}

	private void reply(boolean accept) {
		ClientPlayNetworking.send(new RelationsInviteReplyPayload(accept));
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

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.renderBackground(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 48, 0xFFFFFF);
		if (!pending) {
			graphics.drawCenteredString(
					font,
					Component.translatable("screen.refugee.relations.invites.empty"),
					width / 2,
					height / 2 - 16,
					0xAAAAAA
			);
			return;
		}
		graphics.drawCenteredString(
				font,
				Component.translatable("screen.refugee.relations.invites.body", orgName),
				width / 2,
				height / 2 - 20,
				0xFFFFFF
		);
		graphics.drawCenteredString(
				font,
				Component.translatable("screen.refugee.relations.invites.territory", territoryName),
				width / 2,
				height / 2 - 4,
				0xAAAAAA
		);
	}
}
