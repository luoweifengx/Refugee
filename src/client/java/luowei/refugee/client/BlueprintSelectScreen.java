package luowei.refugee.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import luowei.refugee.blueprint.BlueprintShareTarget;
import luowei.refugee.network.BlueprintSelectPayload;
import luowei.refugee.network.BlueprintShareOpenPayload;
import luowei.refugee.network.BlueprintSharePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 蓝图目录与分享对象列表共用同一套界面。
 */
public class BlueprintSelectScreen extends Screen {
	private final Mode mode;
	private final InteractionHand hand;
	private final ResourceLocation preselect;
	private final List<ResourceLocation> sharingIds;
	private List<Row> rows;
	private RowList list;

	private Button uploadButton;
	private Button shareButton;
	private Button deleteButton;
	private Button confirmButton;

	public BlueprintSelectScreen(List<BlueprintCatalogEntry> entries, InteractionHand hand) {
		this(entries, hand, null);
	}

	public BlueprintSelectScreen(List<BlueprintCatalogEntry> entries, InteractionHand hand, ResourceLocation selected) {
		super(Component.translatable("screen.refugee.blueprint.title"));
		this.mode = Mode.CATALOG;
		this.hand = hand;
		this.preselect = selected;
		this.sharingIds = List.of();
		this.rows = catalogRows(entries);
	}

	public BlueprintSelectScreen(List<ResourceLocation> sharingIds, List<BlueprintShareTarget> targets) {
		super(Component.translatable("screen.refugee.blueprint.share.title"));
		this.mode = Mode.SHARE;
		this.hand = InteractionHand.MAIN_HAND;
		this.preselect = null;
		this.sharingIds = sharingIds == null ? List.of() : List.copyOf(sharingIds);
		this.rows = shareRows(targets);
	}

	public boolean isCatalog() {
		return mode == Mode.CATALOG;
	}

	public void replaceEntries(List<BlueprintCatalogEntry> entries) {
		if (mode != Mode.CATALOG) {
			return;
		}
		this.rows = catalogRows(entries);
		if (list != null) {
			list.refresh();
		}
		refreshButtons();
	}

	@Override
	protected void init() {
		int listTop = 32;
		int listHeight = Math.max(20, height - 72);
		list = new RowList(minecraft, width, listHeight, listTop, 24);
		addRenderableWidget(list);
		int y = height - 28;
		int gap = 4;
		if (mode == Mode.CATALOG) {
			int count = 5;
			int bw = Math.max(60, Math.min(80, (width - 20 - gap * (count - 1)) / count));
			int total = count * bw + (count - 1) * gap;
			int x = width / 2 - total / 2;
			addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> StaffClientNav.resetToRoot())
					.bounds(x, y, bw, 20).build());
			uploadButton = addRenderableWidget(Button.builder(
					Component.translatable("screen.refugee.blueprint.upload"),
					button -> BlueprintFilePicker.pickAndUpload()
			).bounds(x + bw + gap, y, bw, 20).build());
			shareButton = addRenderableWidget(Button.builder(
					Component.translatable("screen.refugee.blueprint.share"),
					button -> shareSelected()
			).bounds(x + 2 * (bw + gap), y, bw, 20).build());
			deleteButton = addRenderableWidget(Button.builder(
					Component.translatable("screen.refugee.blueprint.delete"),
					button -> deleteSelected()
			).bounds(x + 3 * (bw + gap), y, bw, 20).build());
			confirmButton = addRenderableWidget(Button.builder(
					Component.translatable("screen.refugee.blueprint.confirm"),
					button -> confirm()
			).bounds(x + 4 * (bw + gap), y, bw, 20).build());
		} else {
			addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> cancelShare())
					.bounds(width / 2 - 155, y, 150, 20).build());
			confirmButton = addRenderableWidget(Button.builder(
					Component.translatable("screen.refugee.blueprint.share.confirm"),
					button -> confirmShare()
			).bounds(width / 2 + 5, y, 150, 20).build());
		}
		refreshButtons();
	}

	@Override
	public void tick() {
		super.tick();
		refreshButtons();
	}

	private void refreshButtons() {
		Row selected = selectedRow();
		boolean hasOwned = selected != null && selected.owned;
		if (confirmButton != null) {
			confirmButton.active = selected != null;
		}
		if (deleteButton != null) {
			deleteButton.active = hasOwned;
		}
		if (shareButton != null) {
			shareButton.active = hasOwned;
		}
	}

	private Row selectedRow() {
		if (list == null) {
			return null;
		}
		RowEntry selected = list.getSelected();
		return selected == null ? null : selected.row;
	}

	private void confirm() {
		Row row = selectedRow();
		if (row == null || row.structureId == null) {
			return;
		}
		RefugeeClient.selectBlueprint(new BlueprintSelectPayload(row.structureId, hand));
		onClose();
	}

	private void shareSelected() {
		Row row = selectedRow();
		if (row == null || !row.owned || row.structureId == null) {
			return;
		}
		ClientPlayNetworking.send(new BlueprintShareOpenPayload(List.of(row.structureId)));
	}

	private void deleteSelected() {
		Row row = selectedRow();
		if (row == null || !row.owned || row.structureId == null) {
			return;
		}
		RefugeeClient.deleteBlueprint(row.structureId);
	}

	private void confirmShare() {
		Row row = selectedRow();
		if (row == null || row.targetId == null) {
			return;
		}
		ClientPlayNetworking.send(new BlueprintSharePayload(true, sharingIds, List.of(row.targetId)));
	}

	private void cancelShare() {
		ClientPlayNetworking.send(new BlueprintSharePayload(false, sharingIds, List.of()));
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
		if (rows.isEmpty()) {
			graphics.drawWordWrap(
					font,
					Component.translatable(mode == Mode.SHARE
							? "screen.refugee.blueprint.share.empty"
							: "screen.refugee.blueprint.empty"),
					width / 2 - 140,
					height / 2 - 20,
					280,
					0xAAAAAA
			);
		}
	}

	private static List<Row> catalogRows(List<BlueprintCatalogEntry> entries) {
		List<Row> rows = new ArrayList<>();
		if (entries == null) {
			return rows;
		}
		for (BlueprintCatalogEntry entry : entries) {
			rows.add(new Row(
					entry.displayName(),
					entry.id().toString(),
					entry.id(),
					null,
					entry.imported() && entry.owned()
			));
		}
		return rows;
	}

	private static List<Row> shareRows(List<BlueprintShareTarget> targets) {
		List<Row> rows = new ArrayList<>();
		if (targets == null) {
			return rows;
		}
		for (BlueprintShareTarget target : targets) {
			Component kind = Component.translatable(target.organization()
					? "screen.refugee.blueprint.kind.org"
					: "screen.refugee.blueprint.kind.player");
			rows.add(new Row(
					Component.translatable("screen.refugee.blueprint.share.entry", target.name(), kind).getString(),
					target.territoryName(),
					null,
					target.id(),
					false
			));
		}
		return rows;
	}

	private enum Mode {
		CATALOG,
		SHARE
	}

	private record Row(
			String title,
			String subtitle,
			ResourceLocation structureId,
			UUID targetId,
			boolean owned
	) {
	}

	private final class RowList extends ObjectSelectionList<RowEntry> {
		private RowList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
			super(minecraft, width, height, y, itemHeight);
			refresh();
		}

		private void refresh() {
			clearEntries();
			RowEntry preselected = null;
			for (Row row : rows) {
				RowEntry entry = new RowEntry(row);
				addEntry(entry);
				if (preselect != null && preselect.equals(row.structureId)) {
					preselected = entry;
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
			List<RowEntry> children = children();
			if (children.isEmpty()) {
				return;
			}
			RowEntry selected = getSelected();
			int index = selected == null ? 0 : children.indexOf(selected);
			if (reverse) {
				index = (index - 1 + children.size()) % children.size();
			} else {
				index = selected == null ? 0 : (index + 1) % children.size();
			}
			RowEntry next = children.get(index);
			setSelected(next);
			ensureVisible(next);
		}
	}

	private final class RowEntry extends ObjectSelectionList.Entry<RowEntry> {
		private final Row row;

		private RowEntry(Row row) {
			this.row = row;
		}

		@Override
		public Component getNarration() {
			return Component.literal(row.title);
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
			graphics.drawString(font, row.title, left + 2, top + 1, 0xFFFFFF);
			graphics.drawString(font, row.subtitle == null ? "" : row.subtitle, left + 2, top + 12, 0x808080);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			list.setSelected(this);
			return true;
		}
	}
}
