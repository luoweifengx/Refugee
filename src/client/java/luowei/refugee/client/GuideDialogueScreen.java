package luowei.refugee.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import luowei.refugee.special.GuideDialogueConfig.LocalizedEntry;

/**
 * 向导对话：左侧选题，右侧逐条说明。
 */
public class GuideDialogueScreen extends Screen {
	private List<LocalizedEntry> entries;
	private TopicList list;
	private LocalizedEntry selected;

	public GuideDialogueScreen(List<LocalizedEntry> entries) {
		super(Component.translatable("screen.refugee.guide.title"));
		this.entries = List.copyOf(entries);
		this.selected = this.entries.isEmpty() ? null : this.entries.getFirst();
	}

	@Override
	protected void init() {
		int listTop = 32;
		int listHeight = Math.max(20, height - 72);
		list = new TopicList(minecraft, Math.min(180, width / 3), listHeight, listTop, 22);
		list.setX(12);
		addRenderableWidget(list);
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(width / 2 - 75, height - 28, 150, 20)
				.build());
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.renderBackground(graphics, mouseX, mouseY, delta);
		graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
		if (selected == null) {
			return;
		}
		int textLeft = Math.min(180, width / 3) + 24;
		int textWidth = Math.max(80, width - textLeft - 16);
		int y = 36;
		graphics.drawString(font, selected.title(), textLeft, y, 0xFFFFFF);
		y += 14;
		for (String line : selected.lines()) {
			y = drawWrapped(graphics, line, textLeft, y, textWidth, 0xDDDDDD) + 4;
		}
	}

	private int drawWrapped(GuiGraphics graphics, String line, int x, int y, int width, int color) {
		var wrapped = font.split(Component.literal(line), width);
		for (var piece : wrapped) {
			graphics.drawString(font, piece, x, y, color);
			y += 10;
		}
		return y;
	}

	private final class TopicList extends ObjectSelectionList<TopicEntry> {
		private TopicList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
			super(minecraft, width, height, y, itemHeight);
			for (LocalizedEntry entry : entries) {
				TopicEntry row = new TopicEntry(entry);
				addEntry(row);
				if (entry == selected) {
					setSelected(row);
				}
			}
		}

		@Override
		public int getRowWidth() {
			return width - 8;
		}
	}

	private final class TopicEntry extends ObjectSelectionList.Entry<TopicEntry> {
		private final LocalizedEntry entry;

		private TopicEntry(LocalizedEntry entry) {
			this.entry = entry;
		}

		@Override
		public Component getNarration() {
			return Component.literal(entry.title());
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
			graphics.drawString(font, entry.title(), left + 2, top + 6, hovering || this == list.getSelected() ? 0xFFFFFF : 0xBBBBBB);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			list.setSelected(this);
			selected = entry;
			return true;
		}
	}
}
