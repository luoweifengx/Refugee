package luowei.refugee.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import luowei.refugee.blueprint.BlueprintCatalogEntry;
import luowei.refugee.network.BlueprintSelectPayload;

/**
 * 蓝图目录选择：仅列出本模组 {@code config/refugee/blueprints} 中的条目。
 */
public class BlueprintSelectScreen extends Screen {
	private final InteractionHand hand;
	private final ResourceLocation preselect;
	private List<BlueprintCatalogEntry> entries;
	private BlueprintList list;

	public BlueprintSelectScreen(List<BlueprintCatalogEntry> entries, InteractionHand hand) {
		this(entries, hand, null);
	}

	public BlueprintSelectScreen(List<BlueprintCatalogEntry> entries, InteractionHand hand, ResourceLocation selected) {
		super(Component.translatable("screen.refugee.blueprint.title"));
		this.entries = List.copyOf(entries);
		this.hand = hand;
		this.preselect = selected;
	}

	public void replaceEntries(List<BlueprintCatalogEntry> entries) {
		this.entries = List.copyOf(entries);
		if (list != null) {
			list.refresh();
		}
	}

	@Override
	protected void init() {
		int listTop = 32;
		int listHeight = Math.max(20, height - 72);
		list = new BlueprintList(minecraft, width, listHeight, listTop, 24);
		addRenderableWidget(list);
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> StaffClientNav.resetToRoot())
				.bounds(width / 2 - 155, height - 28, 150, 20)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("screen.refugee.blueprint.confirm"), button -> confirm())
				.bounds(width / 2 + 5, height - 28, 150, 20)
				.build());
	}

	private void confirm() {
		if (list == null) {
			return;
		}
		BlueprintEntry selected = list.getSelected();
		if (selected == null) {
			return;
		}
		RefugeeClient.selectBlueprint(new BlueprintSelectPayload(selected.entry.id(), hand));
		onClose();
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
		if (keyCode == GLFW.GLFW_KEY_TAB) {
			cycleSelection(Screen.hasShiftDown());
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void cycleSelection(boolean reverse) {
		if (list != null) {
			list.cycle(reverse);
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.renderBackground(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
		if (entries.isEmpty()) {
			graphics.drawWordWrap(
					font,
					Component.translatable("screen.refugee.blueprint.empty"),
					width / 2 - 140,
					height / 2 - 20,
					280,
					0xAAAAAA
			);
		}
	}

	private final class BlueprintList extends ObjectSelectionList<BlueprintEntry> {
		private BlueprintList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
			super(minecraft, width, height, y, itemHeight);
			refresh();
		}

		private void refresh() {
			clearEntries();
			BlueprintEntry preselected = null;
			for (BlueprintCatalogEntry entry : entries) {
				BlueprintEntry row = new BlueprintEntry(entry);
				addEntry(row);
				if (preselect != null && preselect.equals(entry.id())) {
					preselected = row;
				}
			}
			if (children().isEmpty()) {
				setSelected(null);
			} else if (preselected != null) {
				setSelected(preselected);
			} else if (getSelected() == null) {
				setSelected(getFirstElement());
			}
		}

		@Override
		public int getRowWidth() {
			return Math.min(340, width - 20);
		}

		private void cycle(boolean reverse) {
			List<BlueprintEntry> children = children();
			if (children.isEmpty()) {
				return;
			}
			BlueprintEntry selected = getSelected();
			int index = selected == null ? 0 : children.indexOf(selected);
			if (reverse) {
				index = (index - 1 + children.size()) % children.size();
			} else {
				index = selected == null ? 0 : (index + 1) % children.size();
			}
			BlueprintEntry next = children.get(index);
			setSelected(next);
			ensureVisible(next);
		}
	}

	private final class BlueprintEntry extends ObjectSelectionList.Entry<BlueprintEntry> {
		private final BlueprintCatalogEntry entry;

		private BlueprintEntry(BlueprintCatalogEntry entry) {
			this.entry = entry;
		}

		@Override
		public Component getNarration() {
			return Component.literal(entry.displayName());
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
			graphics.drawString(font, entry.displayName(), left + 2, top + 1, 0xFFFFFF);
			graphics.drawString(font, entry.id().toString(), left + 2, top + 12, 0x808080);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			list.setSelected(this);
			return true;
		}
	}
}
