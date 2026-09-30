package luowei.refugee.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import luowei.refugee.Refugee;
import luowei.refugee.network.RelationsInviteReplyPayload;
import luowei.refugee.network.RelationsNamePayload;
import luowei.refugee.network.RelationsPickPayload;
import luowei.refugee.network.RelationsPlayerRow;
import luowei.refugee.network.RelationsTextsPayload;
import luowei.refugee.network.StaffNavPayload;
import luowei.refugee.network.StaffPiePayload;
import luowei.refugee.staff.RelationDeskPage;
import luowei.refugee.staff.RelationsListKind;
import luowei.refugee.staff.RelationsNameKind;
import luowei.refugee.staff.StaffNavAction;
import luowei.refugee.staff.StaffPieAction;

/**
 * 市政工作台。顶部留 20 像素，下面是旗帜和事项，再下面是当前业务页。打开时不画物品栏。
 */
public class RelationDeskScreen extends Screen {
	private static final int FLAG_W = 28;
	private static final int FLAG_H = 72;
	private static final int FLAG_GAP = 8;
	private static final int BAR_W = 46;
	private static final int BAR_GAP = 4;
	private static final int COL_GAP = 4;
	private static final int ROW_H = 14;
	private static final int ROW_GAP = 3;
	private static final int MAX_ROWS = 3;
	private static final int LEFT = 12;
	private static final long SLIDE_MS = 380L;

	private static final Entry[] PEOPLE = {
			new Entry(StaffPieAction.ORG_CREATE, "screen.refugee.staff.pie.org_create", 0xFF3D8B4A),
			new Entry(StaffPieAction.ORG_INVITE, "screen.refugee.staff.pie.org_invite", 0xFF3A6EA5),
			new Entry(StaffPieAction.ORG_INVITE_MANAGE, "screen.refugee.staff.pie.org_invites", 0xFFC4A35A),
			new Entry(StaffPieAction.ORG_MANAGE, "screen.refugee.staff.pie.org_manage", 0xFF8B5A9E),
			new Entry(StaffPieAction.TERRITORY_MINE, "screen.refugee.staff.pie.territory_texts", 0xFF2E8B8B),
			new Entry(StaffPieAction.RESCUE, "screen.refugee.staff.pie.rescue", 0xFF8B3A3A)
	};
	private static final Entry[] ORG = {
			new Entry(StaffPieAction.ORG_INFO, "screen.refugee.staff.pie.org_info", 0xFF3A6EA5),
			new Entry(StaffPieAction.ORG_LEAVE, "screen.refugee.staff.pie.org_leave", 0xFF8B5A3A),
			new Entry(StaffPieAction.ORG_KICK, "screen.refugee.staff.pie.org_kick", 0xFF8B3A3A),
			new Entry(StaffPieAction.ORG_TRANSFER, "screen.refugee.staff.pie.org_transfer", 0xFFC4A35A)
	};
	private static final Entry[] DIPLOMACY = {
			new Entry(StaffPieAction.RELATIONS_LIST, "screen.refugee.staff.pie.relations_list", 0xFF3A6EA5),
			new Entry(StaffPieAction.DECLARE_WAR, "screen.refugee.staff.pie.declare_war", 0xFF8B3A3A),
			new Entry(StaffPieAction.RECONCILE, "screen.refugee.staff.pie.reconcile", 0xFF8FA3B0),
			new Entry(StaffPieAction.ALLY, "screen.refugee.staff.pie.ally", 0xFF3D8B4A),
			new Entry(StaffPieAction.RECONCILE_INBOX, "screen.refugee.staff.pie.reconcile_inbox", 0xFFC4A35A)
	};

	private RelationDeskPage page;
	private boolean sliding;
	private long slideStart;
	private float peopleX = Float.NaN;
	private float diplomacyX = Float.NaN;
	private StaffPieAction selected;
	private final List<Hit> hits = new ArrayList<>();
	private Sheet sheet;
	private EditBox nameBox;
	private EditBox selfBox;
	private EditBox othersBox;

	public RelationDeskScreen(RelationDeskPage page) {
		super(Component.translatable("block.refugee.relation_desk"));
		this.page = page == null ? RelationDeskPage.HOME : page;
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
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	protected void init() {
		RelationDeskNav.remember(page);
		rebuildFields();
		Refugee.LOGGER.info(
				"[关系管理台] 打开界面 page={} size={}x{} hits={}",
				page,
				width,
				height,
				hits.size()
		);
	}

	public void showList(RelationsListKind kind, List<RelationsPlayerRow> rows) {
		sheet = new Sheet(Mode.LIST);
		sheet.listKind = kind == null ? RelationsListKind.INVITE : kind;
		sheet.rows = rows == null ? List.of() : List.copyOf(rows);
		rebuildFields();
		Refugee.LOGGER.info("[关系管理台] 打开名单 kind={} rows={}", sheet.listKind, sheet.rows.size());
	}

	public void showName(RelationsNameKind kind, String suggested) {
		sheet = new Sheet(Mode.NAME);
		sheet.nameKind = kind == null ? RelationsNameKind.CREATE_ORG : kind;
		sheet.suggested = suggested == null ? "" : suggested;
		rebuildFields();
		Refugee.LOGGER.info("[关系管理台] 打开填写 kind={}", sheet.nameKind);
	}

	public void showTexts(String self, String others, boolean othersEditable) {
		sheet = new Sheet(Mode.TEXTS);
		sheet.self = self == null ? "" : self;
		sheet.others = others == null ? "" : others;
		sheet.othersEditable = othersEditable;
		rebuildFields();
		Refugee.LOGGER.info("[关系管理台] 打开领地文字 editable={}", othersEditable);
	}

	public void showInvites(boolean pending, String orgName, String territory) {
		sheet = new Sheet(Mode.INVITE);
		sheet.invitePending = pending;
		sheet.orgName = orgName == null ? "" : orgName;
		sheet.territory = territory == null ? "" : territory;
		rebuildFields();
		Refugee.LOGGER.info("[关系管理台] 打开邀请管理 pending={}", pending);
	}

	public void dismissSheet() {
		if (sheet == null && nameBox == null && selfBox == null && othersBox == null) {
			return;
		}
		sheet = null;
		nameBox = null;
		selfBox = null;
		othersBox = null;
		selected = null;
		clearWidgets();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (sheet != null) {
				cancelSheet();
				return true;
			}
			if (page == RelationDeskPage.ORG) {
				show(RelationDeskPage.PEOPLE, false);
			} else if (page == RelationDeskPage.HOME) {
				closeDesk();
			} else {
				show(RelationDeskPage.HOME, false);
			}
			return true;
		}
		if (sheet != null && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
			confirmSheet();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		Refugee.LOGGER.info(
				"[关系管理台] 收到点击 ({}, {}) button={} page={} sliding={} 区域={}",
				Math.round(mouseX),
				Math.round(mouseY),
				button,
				page,
				sliding,
				hits.size()
		);
		for (Hit hit : hits) {
			boolean inside = hit.contains(mouseX, mouseY);
			if (button == 0 && inside) {
				Refugee.LOGGER.info("[关系管理台] 按下 {}", hit.name);
				hit.press().run();
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		Frame frame = frame();
		if (sheet != null && sheet.mode == Mode.LIST && mouseY >= frame.contentTop && mouseY <= frame.contentBottom) {
			int max = Math.max(0, sheet.rows.size() - visibleRows(frame));
			sheet.scroll = Mth.clamp(sheet.scroll - (int) Math.signum(scrollY), 0, max);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		hits.clear();
		if (sliding && System.currentTimeMillis() - slideStart >= SLIDE_MS) {
			sliding = false;
			Refugee.LOGGER.info("[关系管理台] 让位结束，展开横条 page={}", page);
		}
		Frame frame = frame();
		int openIndex = openIndex();
		boolean barsVisible = openIndex >= 0 && !sliding;
		Entry[] entries = barsVisible ? entries() : new Entry[0];
		int cols = entries.length == 0 ? 0 : (entries.length + MAX_ROWS - 1) / MAX_ROWS;
		int usedRows = Math.min(MAX_ROWS, entries.length);
		int body = frame.clothH * 86 / 100;
		int stack = usedRows == 0 ? 0 : usedRows * ROW_H + (usedRows - 1) * ROW_GAP;
		int rowTop = frame.clothTop + Math.max(0, (body - stack) / 2);
		int gridW = cols == 0 ? 0 : cols * BAR_W + (cols - 1) * COL_GAP;
		float peopleRest = LEFT;
		float diplomacyRest = LEFT + FLAG_W + FLAG_GAP;
		float peopleTarget = peopleRest;
		float diplomacyTarget = diplomacyRest;
		if (openIndex == 0) {
			peopleTarget = Math.max(8, peopleRest - 8);
			diplomacyTarget = barsVisible
					? peopleTarget + FLAG_W + BAR_GAP + gridW + FLAG_GAP
					: peopleTarget + FLAG_W + FLAG_GAP;
		} else if (openIndex == 1) {
			peopleTarget = Math.max(8, peopleRest - 8);
			diplomacyTarget = peopleTarget + FLAG_W + FLAG_GAP;
		}
		if (Float.isNaN(peopleX)) {
			peopleX = peopleTarget;
			diplomacyX = diplomacyTarget;
		}
		peopleX = Mth.lerp(0.35F, peopleX, peopleTarget);
		diplomacyX = Mth.lerp(0.35F, diplomacyX, diplomacyTarget);
		int poleY = frame.clothTop - 10;
		drawPole(graphics, 4, poleY, width - 4);
		drawFlag(
				graphics,
				peopleX,
				poleY,
				frame.clothTop,
				frame.clothH,
				0xFF2F6B4F,
				"screen.refugee.staff.pie.relations",
				mouseX,
				mouseY,
				() -> clickFlag(0)
		);
		drawFlag(
				graphics,
				diplomacyX,
				poleY,
				frame.clothTop,
				frame.clothH,
				0xFF3D5F8A,
				"screen.refugee.staff.pie.diplomacy",
				mouseX,
				mouseY,
				() -> clickFlag(1)
		);
		if (barsVisible) {
			float origin = openIndex == 0 ? peopleX : diplomacyX;
			for (int index = 0; index < entries.length; index++) {
				int col = index / MAX_ROWS;
				int row = index % MAX_ROWS;
				int x = Math.round(origin) + FLAG_W + BAR_GAP + col * (BAR_W + COL_GAP);
				int y = rowTop + row * (ROW_H + ROW_GAP);
				drawBar(graphics, x, y, entries[index], entries[index].action == selected, mouseX, mouseY);
			}
		}
		drawSheet(graphics, frame, mouseX, mouseY);
		placeFields(frame);
		graphics.drawCenteredString(
				font,
				Component.translatable("screen.refugee.relation_desk.hint.home"),
				width / 2,
				frame.hintY,
				0xFFE7E7E7
		);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	private void drawSheet(GuiGraphics graphics, Frame frame, int mouseX, int mouseY) {
		if (frame.contentTop >= frame.contentBottom - 8) {
			return;
		}
		int panel = sheet == null ? 0x44101010 : 0xAA101010;
		graphics.fill(8, frame.contentTop, width - 8, frame.contentBottom, panel);
		if (sheet == null) {
			return;
		}
		int cx = width / 2;
		if (sheet.error != null) {
			graphics.drawCenteredString(font, sheet.error, cx, frame.contentTop + 2, 0xFFFF7777);
		}
		switch (sheet.mode) {
			case NAME -> drawName(graphics, frame, cx, mouseX, mouseY);
			case TEXTS -> drawTexts(graphics, frame, cx, mouseX, mouseY);
			case INVITE -> drawInvite(graphics, frame, cx, mouseX, mouseY);
			case LIST -> drawList(graphics, frame, cx, mouseX, mouseY);
		}
	}

	private void drawName(GuiGraphics graphics, Frame frame, int cx, int mouseX, int mouseY) {
		int titleY = frame.contentTop + (sheet.error == null ? 4 : 14);
		graphics.drawCenteredString(font, Component.translatable(nameTitle(sheet.nameKind)), cx, titleY, 0xFFFFFFFF);
		graphics.drawCenteredString(
				font,
				Component.translatable(nameHint(sheet.nameKind)),
				cx,
				titleY + 12,
				0xFFB0B0B0
		);
		buttons(graphics, frame, cx, mouseX, mouseY, "screen.refugee.relations.confirm");
	}

	private void drawTexts(GuiGraphics graphics, Frame frame, int cx, int mouseX, int mouseY) {
		graphics.drawCenteredString(
				font,
				Component.translatable("screen.refugee.relations.texts.title"),
				cx,
				frame.contentTop + 2,
				0xFFFFFFFF
		);
		int labelX = cx - 110;
		graphics.drawString(font, Component.translatable("screen.refugee.relations.texts.self"), labelX, frame.contentTop + 14, 0xFFB0B0B0, false);
		graphics.drawString(font, Component.translatable("screen.refugee.relations.texts.others"), labelX, frame.contentTop + 40, 0xFFB0B0B0, false);
		if (!sheet.othersEditable) {
			graphics.drawString(
					font,
					Component.translatable("screen.refugee.relations.texts.others.locked"),
					labelX,
					frame.contentTop + 68,
					0xFFB0B0B0,
					false
			);
		}
		buttons(graphics, frame, cx, mouseX, mouseY, "screen.refugee.relations.confirm");
	}

	private void drawInvite(GuiGraphics graphics, Frame frame, int cx, int mouseX, int mouseY) {
		graphics.drawCenteredString(
				font,
				Component.translatable("screen.refugee.relations.invites.title"),
				cx,
				frame.contentTop + 6,
				0xFFFFFFFF
		);
		if (!sheet.invitePending) {
			graphics.drawCenteredString(
					font,
					Component.translatable("screen.refugee.relations.invites.empty"),
					cx,
					frame.contentTop + 24,
					0xFFB0B0B0
			);
			button(
					graphics,
					"done",
					Component.translatable("gui.done"),
					cx - 40,
					frame.contentBottom - 18,
					80,
					14,
					this::cancelSheet,
					mouseX,
					mouseY
			);
			return;
		}
		graphics.drawCenteredString(
				font,
				Component.translatable("screen.refugee.relations.invites.body", sheet.orgName),
				cx,
				frame.contentTop + 22,
				0xFFFFFFFF
		);
		graphics.drawCenteredString(
				font,
				Component.translatable("screen.refugee.relations.invites.territory", sheet.territory),
				cx,
				frame.contentTop + 34,
				0xFFB0B0B0
		);
		button(
				graphics,
				"deny",
				Component.translatable("screen.refugee.relations.invites.deny"),
				cx - 78,
				frame.contentBottom - 18,
				74,
				14,
				() -> replyInvite(false),
				mouseX,
				mouseY
		);
		button(
				graphics,
				"accept",
				Component.translatable("screen.refugee.relations.invites.accept"),
				cx + 4,
				frame.contentBottom - 18,
				74,
				14,
				() -> replyInvite(true),
				mouseX,
				mouseY
		);
	}

	private void drawList(GuiGraphics graphics, Frame frame, int cx, int mouseX, int mouseY) {
		graphics.drawCenteredString(font, Component.translatable(listTitle(sheet.listKind)), cx, frame.contentTop + 2, 0xFFFFFFFF);
		int listTop = frame.contentTop + (sheet.error == null ? 14 : 24);
		int listBottom = frame.contentBottom - 20;
		if (sheet.rows.isEmpty()) {
			graphics.drawWordWrap(
					font,
					Component.translatable(emptyKey(sheet.listKind)),
					cx - 120,
					listTop + 4,
					240,
					0xFFB0B0B0
			);
			button(
					graphics,
					"done",
					Component.translatable("gui.done"),
					cx - 40,
					frame.contentBottom - 18,
					80,
					14,
					this::cancelSheet,
					mouseX,
					mouseY
			);
			return;
		}
		int visible = Math.max(1, (listBottom - listTop) / ROW_H);
		int max = Math.max(0, sheet.rows.size() - visible);
		sheet.scroll = Mth.clamp(sheet.scroll, 0, max);
		graphics.enableScissor(10, listTop, width - 10, listBottom);
		for (int index = 0; index < visible && sheet.scroll + index < sheet.rows.size(); index++) {
			RelationsPlayerRow row = sheet.rows.get(sheet.scroll + index);
			int y = listTop + index * ROW_H;
			boolean chosen = sheet.picked.contains(row.id());
			boolean hover = mouseX >= 16 && mouseX < width - 16 && mouseY >= y && mouseY < y + ROW_H;
			graphics.fill(16, y, width - 16, y + ROW_H - 1, chosen ? 0xAA4A4A4A : hover ? 0x88383838 : 0x66181818);
			String detail = row.detail() == null ? "" : row.detail();
			String mark = chosen ? "✓ " : (sheet.listKind.multi() ? "· " : "");
			graphics.drawString(font, mark + row.name(), 20, y + 3, chosen ? 0xFFFFE2B3 : 0xFFFFFFFF, false);
			if (!detail.isEmpty()) {
				int nameW = font.width(mark + row.name());
				graphics.drawString(font, detail, 28 + nameW, y + 3, 0xFF9A9A9A, false);
			}
			UUID id = row.id();
			hits.add(new Hit(row.name(), 16, y, width - 32, ROW_H - 1, () -> toggle(id)));
		}
		graphics.disableScissor();
		if (sheet.listKind == RelationsListKind.PEACE_INBOX) {
			button(
					graphics,
					"reject",
					Component.translatable("screen.refugee.relations.inbox.reject"),
					cx - 78,
					frame.contentBottom - 18,
					74,
					14,
					this::rejectInbox,
					mouseX,
					mouseY
			);
			button(
					graphics,
					"accept",
					Component.translatable("screen.refugee.relations.inbox.accept"),
					cx + 4,
					frame.contentBottom - 18,
					74,
					14,
					this::confirmSheet,
					mouseX,
					mouseY
			);
			return;
		}
		String ok = sheet.listKind == RelationsListKind.RESCUE
				? "screen.refugee.relations.rescue.confirm"
				: "screen.refugee.relations.confirm";
		buttons(graphics, frame, cx, mouseX, mouseY, ok);
	}

	private void buttons(GuiGraphics graphics, Frame frame, int cx, int mouseX, int mouseY, String okKey) {
		button(
				graphics,
				"cancel",
				Component.translatable("gui.cancel"),
				cx - 78,
				frame.contentBottom - 18,
				74,
				14,
				this::cancelSheet,
				mouseX,
				mouseY
		);
		button(
				graphics,
				"ok",
				Component.translatable(okKey),
				cx + 4,
				frame.contentBottom - 18,
				74,
				14,
				this::confirmSheet,
				mouseX,
				mouseY
		);
	}

	private void button(
			GuiGraphics graphics,
			String name,
			Component label,
			int x,
			int y,
			int w,
			int h,
			Runnable press,
			int mouseX,
			int mouseY
	) {
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		graphics.fill(x, y, x + w, y + h, hover ? 0xFF8A8A8A : 0xFF6A6A6A);
		graphics.fill(x, y, x + w, y + 1, 0xFFB0B0B0);
		graphics.fill(x, y + h - 1, x + w, y + h, 0xFF2A2A2A);
		graphics.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
		hits.add(new Hit(name, x, y, w, h, press));
	}

	private void placeFields(Frame frame) {
		int x = width / 2 - 110;
		if (nameBox != null) {
			nameBox.setPosition(x, frame.contentTop + 36);
		}
		if (selfBox != null) {
			selfBox.setPosition(x, frame.contentTop + 24);
		}
		if (othersBox != null) {
			othersBox.setPosition(x, frame.contentTop + 50);
		}
	}

	private void rebuildFields() {
		clearWidgets();
		nameBox = null;
		selfBox = null;
		othersBox = null;
		if (sheet == null) {
			return;
		}
		if (sheet.mode == Mode.NAME) {
			nameBox = addRenderableWidget(new EditBox(
					font,
					width / 2 - 110,
					0,
					220,
					14,
					Component.translatable("screen.refugee.relations.name.field")
			));
			nameBox.setMaxLength(32);
			nameBox.setValue(sheet.suggested);
			nameBox.setResponder(value -> {
				if (sheet != null) {
					sheet.suggested = value;
					sheet.error = null;
				}
			});
			setInitialFocus(nameBox);
		} else if (sheet.mode == Mode.TEXTS) {
			selfBox = addRenderableWidget(new EditBox(
					font,
					width / 2 - 110,
					0,
					220,
					14,
					Component.translatable("screen.refugee.relations.texts.self")
			));
			selfBox.setMaxLength(32);
			selfBox.setValue(sheet.self);
			selfBox.setResponder(value -> {
				if (sheet != null) {
					sheet.self = value;
					sheet.error = null;
				}
			});
			othersBox = addRenderableWidget(new EditBox(
					font,
					width / 2 - 110,
					0,
					220,
					14,
					Component.translatable("screen.refugee.relations.texts.others")
			));
			othersBox.setMaxLength(32);
			othersBox.setValue(sheet.others);
			othersBox.setEditable(sheet.othersEditable);
			othersBox.setResponder(value -> {
				if (sheet != null) {
					sheet.others = value;
					sheet.error = null;
				}
			});
			setInitialFocus(selfBox);
		}
	}

	private void confirmSheet() {
		if (sheet == null) {
			return;
		}
		switch (sheet.mode) {
			case NAME -> confirmName();
			case TEXTS -> confirmTexts();
			case LIST -> confirmList(true);
			case INVITE -> replyInvite(true);
		}
	}

	private void confirmName() {
		String name = nameBox == null ? "" : nameBox.getValue().trim();
		if (name.isEmpty()) {
			sheet.error = Component.translatable("message.refugee.staff.relations.empty_name");
			return;
		}
		ClientPlayNetworking.send(new RelationsNamePayload(sheet.nameKind, true, name));
	}

	private void confirmTexts() {
		String self = selfBox == null ? "" : selfBox.getValue().trim();
		String others = othersBox == null ? "" : othersBox.getValue().trim();
		boolean needOthers = sheet.othersEditable;
		if (self.isEmpty() || (needOthers && others.isEmpty())) {
			sheet.error = Component.translatable("message.refugee.staff.relations.empty_name");
			return;
		}
		ClientPlayNetworking.send(new RelationsTextsPayload(true, self, others));
	}

	private void confirmList(boolean accept) {
		if (sheet.picked.isEmpty()) {
			sheet.error = Component.translatable("message.refugee.staff.relations.none_selected");
			return;
		}
		ClientPlayNetworking.send(new RelationsPickPayload(sheet.listKind, accept, new ArrayList<>(sheet.picked)));
	}

	private void rejectInbox() {
		confirmList(false);
	}

	private void replyInvite(boolean accept) {
		ClientPlayNetworking.send(new RelationsInviteReplyPayload(accept));
	}

	private void cancelSheet() {
		if (sheet == null) {
			return;
		}
		switch (sheet.mode) {
			case LIST -> ClientPlayNetworking.send(new RelationsPickPayload(sheet.listKind, false, List.of()));
			case NAME -> ClientPlayNetworking.send(new RelationsNamePayload(sheet.nameKind, false, ""));
			case TEXTS -> ClientPlayNetworking.send(new RelationsTextsPayload(false, "", ""));
			case INVITE -> ClientPlayNetworking.send(new StaffNavPayload(StaffNavAction.RESET));
		}
		dismissSheet();
	}

	private void toggle(UUID id) {
		if (sheet == null || id == null) {
			return;
		}
		sheet.error = null;
		if (sheet.listKind.multi()) {
			if (!sheet.picked.add(id)) {
				sheet.picked.remove(id);
			}
		} else {
			sheet.picked.clear();
			sheet.picked.add(id);
		}
	}

	private Frame frame() {
		int poleY = 22;
		int clothTop = poleY + 10;
		int hintY = height - 12;
		int contentTop = clothTop + FLAG_H + 6;
		int contentBottom = hintY - 4;
		return new Frame(clothTop, FLAG_H, contentTop, contentBottom, hintY);
	}

	private int visibleRows(Frame frame) {
		int listTop = frame.contentTop + (sheet != null && sheet.error != null ? 24 : 14);
		int listBottom = frame.contentBottom - 20;
		return Math.max(1, (listBottom - listTop) / ROW_H);
	}

	private void drawPole(GuiGraphics graphics, int x, int y, int right) {
		graphics.fill(x, y, right, y + 6, 0xFF7A4A28);
		graphics.fill(x, y, right, y + 2, 0xFFC4894E);
		graphics.fill(x, y + 4, right, y + 6, 0xFF3A2414);
		graphics.fill(x - 2, y - 2, x + 3, y + 8, 0xFFE0B56A);
		graphics.fill(right - 3, y - 2, right + 2, y + 8, 0xFFE0B56A);
	}

	private void drawFlag(
			GuiGraphics graphics,
			float fx,
			int poleY,
			int clothTop,
			int clothH,
			int color,
			String labelKey,
			int mouseX,
			int mouseY,
			Runnable press
	) {
		int x = Math.round(fx);
		int sleeveY = poleY - 1;
		boolean hover = mouseX >= x && mouseX < x + FLAG_W && mouseY >= sleeveY && mouseY < clothTop + clothH;
		int fill = hover ? lighten(color) : color;
		graphics.fill(x, sleeveY, x + FLAG_W, sleeveY + 8, fill);
		int body = clothH * 86 / 100;
		graphics.fill(x, clothTop, x + FLAG_W, clothTop + body, fill);
		int tip = clothH - body;
		for (int row = 0; row < tip; row++) {
			int inset = FLAG_W * row / (2 * Math.max(1, tip));
			graphics.fill(x + inset, clothTop + body + row, x + FLAG_W - inset, clothTop + body + row + 1, fill);
		}
		String text = Component.translatable(labelKey).getString();
		int textH = text.length() * font.lineHeight;
		int textY = clothTop + Math.max(2, (body - textH) / 2);
		for (int index = 0; index < text.length(); index++) {
			graphics.drawCenteredString(font, text.substring(index, index + 1), x + FLAG_W / 2, textY, 0xFFF6F1E4);
			textY += font.lineHeight;
		}
		hits.add(new Hit(labelKey, x, sleeveY, FLAG_W, clothTop + clothH - sleeveY, press));
	}

	private void drawBar(GuiGraphics graphics, int x, int y, Entry entry, boolean chosen, int mouseX, int mouseY) {
		boolean hover = mouseX >= x && mouseX < x + BAR_W && mouseY >= y && mouseY < y + ROW_H;
		int color = chosen ? darken(entry.color) : hover ? lighten(entry.color) : entry.color;
		graphics.fill(x, y, x + BAR_W, y + ROW_H, color);
		graphics.fill(x, y + ROW_H - 2, x + BAR_W, y + ROW_H, darken(color));
		graphics.drawString(
				font,
				Component.translatable(entry.labelKey),
				x + 6,
				y + (ROW_H - 8) / 2,
				0xFFF6F1E4,
				false
		);
		hits.add(new Hit(entry.labelKey, x, y, BAR_W, ROW_H, () -> choose(entry.action)));
	}

	private void clickFlag(int index) {
		if (sheet != null) {
			cancelSheet();
		}
		RelationDeskPage target = index == 0 ? RelationDeskPage.PEOPLE : RelationDeskPage.DIPLOMACY;
		Refugee.LOGGER.info("[关系管理台] 点旗帜 index={} 当前={} 目标={}", index, page, target);
		if (!sliding && page == RelationDeskPage.ORG && index == 0) {
			show(RelationDeskPage.PEOPLE, false);
			return;
		}
		if (!sliding && page == target) {
			selected = null;
			show(RelationDeskPage.HOME, false);
			return;
		}
		selected = null;
		show(target, true);
	}

	private void choose(StaffPieAction action) {
		if (sheet != null) {
			cancelSheet();
		}
		selected = action;
		RelationDeskNav.remember(page);
		Refugee.LOGGER.info("[关系管理台] 点事项 action={} page={}", action, page);
		ClientPlayNetworking.send(new StaffPiePayload(action));
	}

	private void show(RelationDeskPage next, boolean slide) {
		page = next;
		if (next != RelationDeskPage.ORG) {
			selected = null;
		}
		RelationDeskNav.remember(page);
		sliding = slide && next != RelationDeskPage.HOME;
		slideStart = System.currentTimeMillis();
		Refugee.LOGGER.info("[关系管理台] 切到 page={} sliding={}", page, sliding);
	}

	private void closeDesk() {
		if (sheet != null) {
			cancelSheet();
		}
		RelationDeskNav.closed();
		if (minecraft != null) {
			minecraft.setScreen(null);
		}
	}

	private int openIndex() {
		return switch (page) {
			case PEOPLE, ORG -> 0;
			case DIPLOMACY -> 1;
			case HOME -> -1;
		};
	}

	private Entry[] entries() {
		if (page == RelationDeskPage.ORG) {
			return ORG;
		}
		if (page == RelationDeskPage.DIPLOMACY) {
			return DIPLOMACY;
		}
		return PEOPLE;
	}

	private static String nameTitle(RelationsNameKind kind) {
		if (kind == RelationsNameKind.RENAME_TERRITORY) {
			return "screen.refugee.relations.rename.title";
		}
		if (kind == RelationsNameKind.RENAME_PERSONAL) {
			return "screen.refugee.relations.rename_personal.title";
		}
		return "screen.refugee.relations.create.title";
	}

	private static String nameHint(RelationsNameKind kind) {
		if (kind == RelationsNameKind.RENAME_TERRITORY) {
			return "screen.refugee.relations.rename.hint";
		}
		if (kind == RelationsNameKind.RENAME_PERSONAL) {
			return "screen.refugee.relations.rename_personal.hint";
		}
		return "screen.refugee.relations.create.hint";
	}

	private static String listTitle(RelationsListKind kind) {
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

	private static String emptyKey(RelationsListKind kind) {
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

	private static int lighten(int color) {
		int red = Math.min(255, ((color >> 16) & 0xFF) + 28);
		int green = Math.min(255, ((color >> 8) & 0xFF) + 28);
		int blue = Math.min(255, (color & 0xFF) + 28);
		return 0xFF000000 | (red << 16) | (green << 8) | blue;
	}

	private static int darken(int color) {
		int red = (int) (((color >> 16) & 0xFF) * 0.55F);
		int green = (int) (((color >> 8) & 0xFF) * 0.55F);
		int blue = (int) ((color & 0xFF) * 0.55F);
		return 0xFF000000 | (red << 16) | (green << 8) | blue;
	}

	private record Frame(int clothTop, int clothH, int contentTop, int contentBottom, int hintY) {
	}

	private record Hit(String name, int x, int y, int w, int h, Runnable press) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}

	private record Entry(StaffPieAction action, String labelKey, int color) {
	}

	private enum Mode {
		LIST,
		NAME,
		TEXTS,
		INVITE
	}

	private static final class Sheet {
		private final Mode mode;
		private RelationsListKind listKind = RelationsListKind.INVITE;
		private List<RelationsPlayerRow> rows = List.of();
		private final Set<UUID> picked = new LinkedHashSet<>();
		private int scroll;
		private RelationsNameKind nameKind = RelationsNameKind.CREATE_ORG;
		private String suggested = "";
		private String self = "";
		private String others = "";
		private boolean othersEditable = true;
		private boolean invitePending;
		private String orgName = "";
		private String territory = "";
		private Component error;

		private Sheet(Mode mode) {
			this.mode = mode;
		}
	}
}
