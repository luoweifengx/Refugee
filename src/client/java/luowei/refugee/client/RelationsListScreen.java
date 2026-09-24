package luowei.refugee.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import luowei.refugee.network.RelationsPickPayload;
import luowei.refugee.network.RelationsPlayerRow;
import luowei.refugee.staff.RelationsListKind;

/**
 * 人员关系玩家列表。邀请可多选，踢人、移交和救助选一人。
 */
public class RelationsListScreen extends Screen {
	private final RelationsListKind kind;
	private final List<RelationsPlayerRow> rows;
	private final Set<UUID> picked = new LinkedHashSet<>();
	private RowList list;
	private Button confirmButton;

	public RelationsListScreen(RelationsListKind kind, List<RelationsPlayerRow> rows) {
		super(Component.translatable(titleKey(kind)));
		this.kind = kind == null ? RelationsListKind.INVITE : kind;
		this.rows = rows == null ? List.of() : List.copyOf(rows);
	}

	public RelationsListKind kind() {
		return kind;
	}

	private static String titleKey(RelationsListKind kind) {
		if (kind == null) {
			return "screen.refugee.relations.invite.title";
		}
		return switch (kind) {
			case INVITE -> "screen.refugee.relations.invite.title";
			case KICK -> "screen.refugee.relations.kick.title";
			case TRANSFER -> "screen.refugee.relations.transfer.title";
			case RESCUE -> "screen.refugee.relations.rescue.title";
			case RELATIONS -> "screen.refugee.relations.list.title";
			case WAR -> "screen.refugee.relations.war.title";
			case PEACE -> "screen.refugee.relations.peace.title";
			case ALLY -> "screen.refugee.relations.ally.title";
			case PEACE_INBOX -> "screen.refugee.relations.inbox.title";
		};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int listTop = 32;
		int listHeight = Math.max(20, height - 72);
		list = new RowList(minecraft, width, listHeight, listTop, 24);
		addRenderableWidget(list);
		int y = height - 28;
		addRenderableWidget(Button.builder(
				kind == RelationsListKind.PEACE_INBOX
						? Component.translatable("screen.refugee.relations.inbox.reject")
						: CommonComponents.GUI_CANCEL,
				button -> cancel()
		).bounds(width / 2 - 155, y, 150, 20).build());
		confirmButton = addRenderableWidget(Button.builder(
				Component.translatable(kind == RelationsListKind.RESCUE
						? "screen.refugee.relations.rescue.confirm"
						: kind == RelationsListKind.PEACE_INBOX
						? "screen.refugee.relations.inbox.accept"
						: "screen.refugee.relations.confirm"),
				button -> confirm()
		).bounds(width / 2 + 5, y, 150, 20).build());
		refreshButtons();
	}

	private void refreshButtons() {
		if (confirmButton != null) {
			confirmButton.active = !picked.isEmpty();
		}
	}

	private void confirm() {
		if (picked.isEmpty()) {
			return;
		}
		ClientPlayNetworking.send(new RelationsPickPayload(kind, true, new ArrayList<>(picked)));
	}

	private void cancel() {
		if (kind == RelationsListKind.PEACE_INBOX && !picked.isEmpty()) {
			ClientPlayNetworking.send(new RelationsPickPayload(kind, false, new ArrayList<>(picked)));
			return;
		}
		ClientPlayNetworking.send(new RelationsPickPayload(kind, false, List.of()));
		StaffClientNav.resetToRoot();
	}

	private void toggle(UUID id) {
		if (id == null) {
			return;
		}
		if (kind.multi()) {
			if (!picked.add(id)) {
				picked.remove(id);
			}
		} else {
			picked.clear();
			picked.add(id);
		}
		refreshButtons();
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
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			confirm();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.renderBackground(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
		if (rows.isEmpty()) {
			graphics.drawWordWrap(
					font,
					Component.translatable(emptyKey()),
					width / 2 - 140,
					height / 2 - 20,
					280,
					0xAAAAAA
			);
		}
	}

	private String emptyKey() {
		return switch (kind) {
			case INVITE -> "screen.refugee.relations.invite.empty";
			case KICK -> "screen.refugee.relations.kick.empty";
			case TRANSFER -> "screen.refugee.relations.transfer.empty";
			case RESCUE -> "screen.refugee.relations.rescue.empty";
			case RELATIONS -> "screen.refugee.relations.list.empty";
			case WAR -> "screen.refugee.relations.war.empty";
			case PEACE -> "screen.refugee.relations.peace.empty";
			case ALLY -> "screen.refugee.relations.ally.empty";
			case PEACE_INBOX -> "screen.refugee.relations.inbox.empty";
		};
	}

	private final class RowList extends ObjectSelectionList<RowEntry> {
		private RowList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
			super(minecraft, width, height, y, itemHeight);
			for (RelationsPlayerRow row : rows) {
				addEntry(new RowEntry(row));
			}
		}

		@Override
		public int getRowWidth() {
			return Math.min(340, width - 20);
		}
	}

	private final class RowEntry extends ObjectSelectionList.Entry<RowEntry> {
		private final RelationsPlayerRow row;

		private RowEntry(RelationsPlayerRow row) {
			this.row = row;
		}

		@Override
		public Component getNarration() {
			return Component.literal(row.name());
		}

		@Override
		public void render(
				GuiGraphics graphics,
				int index,
				int top,
				int left,
				int width,
				int height,
				int mouseX,
				int mouseY,
				boolean hovering,
				float partialTick
		) {
			boolean selected = picked.contains(row.id());
			String mark = selected ? "✓ " : (kind.multi() ? "· " : "");
			graphics.drawString(font, mark + row.name(), left + 2, top + 1, selected ? 0xFFE2B340 : 0xFFFFFF);
			if (row.detail() != null && !row.detail().isEmpty()) {
				graphics.drawString(font, row.detail(), left + 2, top + 12, 0x808080);
			}
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			toggle(row.id());
			return true;
		}
	}
}
