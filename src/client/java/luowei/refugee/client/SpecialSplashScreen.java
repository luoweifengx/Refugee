package luowei.refugee.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import luowei.refugee.network.SpecialSplashAction;
import luowei.refugee.special.NurseService;
import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 特殊居民开屏：对齐旅商 SPLASH——无全屏遮罩、底部对话框、超过两项分两列、对准 NPC。
 */
public class SpecialSplashScreen extends Screen {
	private static final int COL_SCRIM = 0xB8070C10;
	private static final int COL_INK = 0xFFE8EEF2;
	private static final int COL_INK_DIM = 0xFF8FA3B0;
	private static final int COL_INK_MUTE = 0xFF5C7382;
	private static final int COL_LINE = 0xEBECEFF2;
	private static final int COL_LINE_SOFT = 0x47ECF2F6;
	private static final int COL_LANTERN = 0xFFD4A15A;
	private static final int COL_LANTERN_HOT = 0xFFF0C078;
	private static final int COL_HOVER = 0x1FF0C078;
	private static final int COL_SELECTED = 0x33F0C078;
	private static final int COL_PANEL_EDGE = 0x59D4A15A;
	private static final int COL_PANEL_EDGE_INNER = 0x28ECF2F6;

	private static final float MARKER_DUR_MS = 280f;
	private static final float TALK_SWAP_MS = 180f;
	private static final float LOOK_TURN_MS = 600f;
	private static final long FAREWELL_HOLD_MS = 900L;

	private final int villagerEntityId;
	private final RefugeeSpecialRole role;
	private final List<MenuOption> options;
	private final List<Component> talkPool;

	private int selectedIndex;
	private int hoveredIndex = -1;
	private int lastTalkIndex = -1;
	private Component talkText = Component.empty();
	private long talkSwapStartMs = -1L;
	private boolean farewellPending;
	private long farewellCloseAtMs = -1L;

	private final float[] markerProgress;
	private long lastAnimMs = -1L;

	private int menuLeft;
	private int menuTop;
	private int menuRowHeight = 18;
	private int menuWidth = 96;
	private int menuColGap = 24;
	private int menuCols = 1;
	private int menuRows = 1;
	private float menuScale = 1f;
	private float talkScale = 1f;
	private int talkLineHeight = 9;
	private int ruleLineY;
	private int talkY;
	private int talkMaxWidth;
	private int dialogueLeft;
	private int dialogueTop;
	private int dialogueWidth;
	private int dialogueHeight;

	private float lookStartYaw;
	private float lookStartPitch;
	private long lookStartMs = -1L;
	private boolean lookActive;

	public SpecialSplashScreen(int villagerEntityId, RefugeeSpecialRole role, List<String> talkLines) {
		this(villagerEntityId, role, talkLines, null);
	}

	public SpecialSplashScreen(int villagerEntityId, RefugeeSpecialRole role, List<String> talkLines, String initialTalkKey) {
		super(Component.translatable("role.refugee." + role.id()));
		this.villagerEntityId = villagerEntityId;
		this.role = role;
		this.options = optionsFor(role);
		this.talkPool = buildTalkPool(role, talkLines);
		this.markerProgress = new float[this.options.size()];
		if (this.markerProgress.length > 0) {
			this.markerProgress[0] = 1f;
		}
		if (initialTalkKey != null && !initialTalkKey.isBlank()) {
			this.talkText = Component.translatable(initialTalkKey);
		} else {
			this.talkText = Component.translatable(lineKey(role, "greeting"));
		}
	}

	public boolean matchesEntity(int entityId) {
		return this.villagerEntityId == entityId;
	}

	public void applyTalkKey(String talkKey) {
		if (talkKey == null || talkKey.isBlank()) {
			return;
		}
		startTalkSwap(Component.translatable(talkKey));
	}

	@Override
	protected void init() {
		super.init();
		if (this.lookStartMs < 0L) {
			beginLookAtNpc();
		}
	}

	@Override
	public void tick() {
		applyLookAtNpc();
		tickAnimations();
	}

	@Override
	public void removed() {
		this.lookActive = false;
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// 不开全屏遮罩，好让村民留在画面里。
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		applyLookAtNpc();
		this.renderBackground(graphics, mouseX, mouseY, partialTick);
		this.hoveredIndex = menuHitIndex(mouseX, mouseY);
		renderSplash(graphics);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return true;
		}
		int hit = menuHitIndex(mouseX, mouseY);
		if (hit < 0) {
			return true;
		}
		setSelected(hit);
		confirmSelection();
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (isInventoryKey(keyCode, scanCode)) {
			this.onClose();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_DOWN) {
			moveSelection(0, 1);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_UP) {
			moveSelection(0, -1);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_RIGHT) {
			moveSelection(1, 0);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_LEFT) {
			moveSelection(-1, 0);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			confirmSelection();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private boolean isInventoryKey(int keyCode, int scanCode) {
		if (keyCode == GLFW.GLFW_KEY_E) {
			return true;
		}
		return this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode);
	}

	private void tickAnimations() {
		long now = System.currentTimeMillis();
		if (this.lastAnimMs < 0L) {
			this.lastAnimMs = now;
		}
		float dt = Math.min(50f, now - this.lastAnimMs);
		this.lastAnimMs = now;
		float step = dt / MARKER_DUR_MS;
		for (int i = 0; i < this.markerProgress.length; i++) {
			float target = i == this.selectedIndex ? 1f : 0f;
			float p = this.markerProgress[i];
			if (p < target) {
				this.markerProgress[i] = Math.min(target, p + step);
			} else if (p > target) {
				this.markerProgress[i] = Math.max(target, p - step);
			}
		}
		if (this.talkSwapStartMs >= 0L && (now - this.talkSwapStartMs) / TALK_SWAP_MS >= 1f) {
			this.talkSwapStartMs = -1L;
		}
		if (this.farewellPending && this.farewellCloseAtMs >= 0L && now >= this.farewellCloseAtMs) {
			this.onClose();
		}
	}

	private static float easeOutCubic(float t) {
		float u = 1f - t;
		return 1f - u * u * u;
	}

	private void beginLookAtNpc() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		this.lookStartYaw = this.minecraft.player.getYRot();
		this.lookStartPitch = this.minecraft.player.getXRot();
		this.lookStartMs = System.currentTimeMillis();
		this.lookActive = true;
	}

	private void applyLookAtNpc() {
		if (!this.lookActive || this.lookStartMs < 0L) {
			return;
		}
		if (this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null) {
			return;
		}
		Entity npc = this.minecraft.level.getEntity(this.villagerEntityId);
		if (npc == null) {
			return;
		}
		var player = this.minecraft.player;
		Vec3 from = new Vec3(player.getX(), player.getEyeY(), player.getZ());
		Vec3 to = new Vec3(npc.getX(), npc.getEyeY(), npc.getZ());
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		double distXZ = Math.sqrt(dx * dx + dz * dz);
		if (distXZ < 1.0E-4 && Math.abs(dy) < 1.0E-4) {
			return;
		}
		float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
		float targetPitch = (float) -(Mth.atan2(dy, distXZ) * (180.0 / Math.PI));
		targetPitch = Mth.clamp(targetPitch, -90.0F, 90.0F);
		float t = Mth.clamp((System.currentTimeMillis() - this.lookStartMs) / LOOK_TURN_MS, 0f, 1f);
		float e = easeOutCubic(t);
		float yaw = Mth.rotLerp(e, this.lookStartYaw, targetYaw);
		float pitch = Mth.lerp(e, this.lookStartPitch, targetPitch);
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.yRotO = yaw;
		player.xRotO = pitch;
		player.setYHeadRot(yaw);
		player.yHeadRotO = yaw;
	}

	private void startTalkSwap(Component text) {
		this.talkText = text;
		this.talkSwapStartMs = System.currentTimeMillis();
	}

	private void cycleTalk() {
		if (this.talkPool.isEmpty()) {
			return;
		}
		this.lastTalkIndex = (this.lastTalkIndex + 1) % this.talkPool.size();
		startTalkSwap(this.talkPool.get(this.lastTalkIndex));
		RefugeeClient.sendSplashAction(this.villagerEntityId, SpecialSplashAction.TALK);
	}

	private void beginFarewell() {
		if (this.farewellPending) {
			this.onClose();
			return;
		}
		this.farewellPending = true;
		startTalkSwap(Component.translatable(lineKey(this.role, "farewell")));
		this.farewellCloseAtMs = System.currentTimeMillis() + FAREWELL_HOLD_MS;
	}

	private void setSelected(int index) {
		this.selectedIndex = Mth.clamp(index, 0, Math.max(0, this.options.size() - 1));
	}

	private void moveSelection(int dCol, int dRow) {
		layoutSplashMenu();
		int count = this.options.size();
		if (count <= 0) {
			return;
		}
		int col = this.selectedIndex / this.menuRows;
		int row = this.selectedIndex % this.menuRows;
		int next = (col + dCol) * this.menuRows + (row + dRow);
		if (next >= 0 && next < count) {
			setSelected(next);
		}
	}

	private int menuCount() {
		return Math.max(1, this.options.size());
	}

	/** 超过 2 项时右侧再开一列，与旅商开屏一致。 */
	private int splashMenuColumns() {
		return menuCount() > 2 ? 2 : 1;
	}

	private int splashMenuRows() {
		int cols = splashMenuColumns();
		return Math.max(1, (menuCount() + cols - 1) / cols);
	}

	private int menuCellX(int index) {
		int col = index / this.menuRows;
		return this.menuLeft + col * (this.menuWidth + this.menuColGap);
	}

	private int menuCellY(int index) {
		int row = index % this.menuRows;
		return this.menuTop + row * this.menuRowHeight;
	}

	private void confirmSelection() {
		if (this.farewellPending || this.options.isEmpty()) {
			return;
		}
		MenuOption option = this.options.get(this.selectedIndex);
		switch (option.kind()) {
			case TALK -> cycleTalk();
			case HEAL -> RefugeeClient.sendSplashAction(this.villagerEntityId, SpecialSplashAction.HEAL);
			case MAP -> RefugeeClient.sendSplashAction(this.villagerEntityId, SpecialSplashAction.MAP);
			case TRADE -> RefugeeClient.sendSplashAction(this.villagerEntityId, SpecialSplashAction.TRADE);
			case FAREWELL -> beginFarewell();
		}
	}

	/**
	 * 舞台自下而上：菜单 → 白线（选项上方）→ 对话。
	 * 面板按文案行数加高，避免白线切字；高度有上限以免挡住热键栏太多。
	 */
	private void layoutSplashMenu() {
		int count = menuCount();
		int lineWidth = Math.min(this.width * 2 / 3, this.width - 24);
		int lineX = (this.width - lineWidth) / 2;
		this.talkMaxWidth = Math.min(440, this.width * 2 / 3);

		int oldPadBottom = 8;
		int oldPadTop = 12;
		int oldRowH = 18;
		int oldStageBottom = this.height - Math.max(28, this.height / 8);
		int oldMenuTop = oldStageBottom - count * oldRowH;
		int oldRuleLineY = oldMenuTop - 12;
		int oldTalkLineH = this.font.lineHeight + 2;
		int talkLines = Math.max(1, this.font.split(this.talkText, this.talkMaxWidth).size());
		int oldTalkH = talkLines * oldTalkLineH;
		int brandY = Math.max(16, this.height / 12);
		int titleBottom = brandY + 18 + Math.round(this.font.lineHeight * 2.4f) + 12;
		int oldTalkY = Math.max(titleBottom, oldRuleLineY - 10 - oldTalkH);
		int oldBottom = this.height - oldPadBottom;
		int oldHeight = Math.max(48, oldBottom - (oldTalkY - oldPadTop));

		int padX = 14;
		this.dialogueLeft = Math.max(8, lineX - padX);
		int dialogueRight = Math.min(this.width - 8, lineX + lineWidth + padX);
		this.dialogueWidth = Math.max(48, dialogueRight - this.dialogueLeft);

		this.menuCols = splashMenuColumns();
		this.menuRows = splashMenuRows();
		this.talkScale = 1f;
		this.talkLineHeight = this.font.lineHeight + 2;
		int talkH = talkLines * this.talkLineHeight;
		int minRowH = this.font.lineHeight + 5;
		int minMenuH = this.menuRows * minRowH;
		int padTop = 8;
		int padBottom = 6;
		int gapTalkLine = 6;
		int gapLineMenu = 6;
		int needed = padTop + talkH + gapTalkLine + 1 + gapLineMenu + minMenuH + padBottom;
		int baseline = Math.max(64, Math.round(oldHeight / 3f * 1.2f * 1.1f * 1.35f));
		int cap = Math.max(baseline, Math.min(this.height * 2 / 5, this.height - 36));
		this.dialogueHeight = Mth.clamp(Math.max(baseline, needed), 64, cap);
		this.dialogueTop = oldBottom - this.dialogueHeight;

		int panelBottom = this.dialogueTop + this.dialogueHeight;
		int innerTop = this.dialogueTop + padTop;
		int innerBottom = panelBottom - padBottom;
		int innerH = Math.max(1, innerBottom - innerTop);

		int maxTalkH = Math.max(this.talkLineHeight, innerH - minMenuH - gapTalkLine - 1 - gapLineMenu);
		if (talkH > maxTalkH) {
			talkH = maxTalkH;
		}

		int menuBudget = Math.max(minMenuH, innerH - talkH - gapTalkLine - 1 - gapLineMenu);
		this.menuRowHeight = Math.max(minRowH, Math.min(18, menuBudget / this.menuRows));
		this.menuScale = 1f;
		this.menuColGap = 24;
		int innerW = Math.max(48, lineWidth - 8);
		if (this.menuCols <= 1) {
			this.menuWidth = Math.min(120, innerW);
		} else {
			this.menuWidth = Math.max(72, (innerW - (this.menuCols - 1) * this.menuColGap) / this.menuCols);
		}
		this.menuLeft = lineX + 4;
		this.menuTop = innerBottom - this.menuRows * this.menuRowHeight;
		this.ruleLineY = this.menuTop - gapLineMenu;
		this.talkY = innerTop;
	}

	private int menuHitIndex(double mouseX, double mouseY) {
		layoutSplashMenu();
		int count = this.options.size();
		if (count <= 0) {
			return -1;
		}
		if (mouseY < this.menuTop || mouseY >= this.menuTop + this.menuRows * this.menuRowHeight) {
			return -1;
		}
		int row = Mth.clamp((int) ((mouseY - this.menuTop) / this.menuRowHeight), 0, this.menuRows - 1);
		for (int col = 0; col < this.menuCols; col++) {
			int x0 = this.menuLeft + col * (this.menuWidth + this.menuColGap);
			if (mouseX >= x0 && mouseX < x0 + this.menuWidth) {
				int index = col * this.menuRows + row;
				return index < count ? index : -1;
			}
		}
		return -1;
	}

	private void renderDialoguePanel(GuiGraphics graphics) {
		int x0 = this.dialogueLeft;
		int y0 = this.dialogueTop;
		int x1 = this.dialogueLeft + this.dialogueWidth;
		int y1 = this.dialogueTop + this.dialogueHeight;
		graphics.fill(x0, y0, x1, y1, COL_SCRIM);
		fillRectOutline(graphics, x0, y0, x1, y1, COL_PANEL_EDGE);
		fillRectOutline(graphics, x0 + 1, y0 + 1, x1 - 1, y1 - 1, COL_PANEL_EDGE_INNER);
	}

	private static void fillRectOutline(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
		graphics.fill(x0, y0, x1, y0 + 1, color);
		graphics.fill(x0, y1 - 1, x1, y1, color);
		graphics.fill(x0, y0, x0 + 1, y1, color);
		graphics.fill(x1 - 1, y0, x1, y1, color);
	}

	private void renderSplash(GuiGraphics graphics) {
		layoutSplashMenu();
		renderDialoguePanel(graphics);

		var pose = graphics.pose();
		int lineWidth = Math.min(this.width * 2 / 3, this.width - 24);
		int lineX = (this.width - lineWidth) / 2;

		float talkAlpha = 1f;
		float talkDy = 0f;
		long now = System.currentTimeMillis();
		if (this.talkSwapStartMs >= 0L) {
			float t = easeOutCubic(Mth.clamp((now - this.talkSwapStartMs) / TALK_SWAP_MS, 0f, 1f));
			talkAlpha = t;
			talkDy = (1f - t) * 3f;
		}

		int talkColor = withAlpha(COL_INK_DIM, talkAlpha);
		graphics.enableScissor(this.dialogueLeft, this.dialogueTop, this.dialogueLeft + this.dialogueWidth, this.ruleLineY);
		pose.pushPose();
		pose.translate(0f, talkDy, 0f);
		drawWrapped(graphics, this.talkText, this.menuLeft, this.talkY, this.talkMaxWidth, talkColor, this.talkScale);
		pose.popPose();
		graphics.disableScissor();

		drawRuleLine(graphics, lineX, this.ruleLineY, lineWidth);

		float scale = this.menuScale;
		int markerGlyphW = this.font.width("<");
		for (int i = 0; i < this.options.size(); i++) {
			int cellX = menuCellX(i);
			int rowY = menuCellY(i);
			boolean selected = i == this.selectedIndex;
			boolean hovered = i == this.hoveredIndex;
			if (selected) {
				graphics.fill(cellX, rowY, cellX + this.menuWidth, rowY + this.menuRowHeight - 1, COL_SELECTED);
			} else if (hovered) {
				graphics.fill(cellX, rowY, cellX + this.menuWidth, rowY + this.menuRowHeight - 1, COL_HOVER);
			}
			int ink = selected || hovered ? COL_INK : COL_INK_DIM;
			int dot = selected ? COL_LANTERN_HOT : (hovered ? COL_LANTERN : COL_INK_MUTE);
			float textY = rowY + (this.menuRowHeight - this.font.lineHeight * scale) * 0.5f;

			pose.pushPose();
			pose.translate(cellX, textY, 0f);
			pose.scale(scale, scale, 1f);
			graphics.drawString(this.font, "·", 3, 0, dot, false);
			float p = easeOutCubic(Mth.clamp(this.markerProgress[i], 0f, 1f));
			if (p > 0.02f) {
				int markerColor = withAlpha(COL_LANTERN_HOT, p);
				float markerX = (this.menuWidth - 8) / scale - markerGlyphW;
				pose.pushPose();
				pose.translate((1f - p) * 8f, 0f, 0f);
				graphics.drawString(this.font, "<", Math.round(markerX), 0, markerColor, false);
				pose.popPose();
			}
			pose.popPose();
			drawMenuLabel(
					graphics,
					this.options.get(i),
					cellX + Math.round(12 * scale),
					Math.round(textY),
					ink
			);
		}
	}

	private void drawMenuLabel(GuiGraphics graphics, MenuOption option, int x, int y, int color) {
		if (option.kind() == Kind.HEAL && this.role == RefugeeSpecialRole.NURSE && this.minecraft != null && this.minecraft.player != null) {
			Player player = this.minecraft.player;
			int fee = NurseService.computeFee(player);
			if (fee <= 0) {
				graphics.drawString(this.font, Component.translatable(option.labelKey()).getString(), x, y, color, false);
				return;
			}
			String prefix = Component.translatable("screen.refugee.splash.heal.fee", fee).getString();
			int textW = this.font.width(prefix);
			graphics.drawString(this.font, prefix, x, y, color, false);
			int iconX = x + textW;
			int iconY = y + (this.font.lineHeight - 16) / 2;
			graphics.renderItem(new ItemStack(Items.EMERALD), iconX, iconY);
			graphics.drawString(this.font, ")", iconX + 16, y, color, false);
			return;
		}
		graphics.drawString(this.font, Component.translatable(option.labelKey()).getString(), x, y, color, false);
	}

	private void drawWrapped(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color, float scale) {
		var lines = this.font.split(text, maxWidth);
		var pose = graphics.pose();
		int yy = y;
		for (var line : lines) {
			pose.pushPose();
			pose.translate(x, yy, 0f);
			pose.scale(scale, scale, 1f);
			graphics.drawString(this.font, line, 0, 0, color, false);
			pose.popPose();
			yy += this.talkLineHeight;
		}
	}

	private void drawRuleLine(GuiGraphics graphics, int x, int y, int width) {
		int soft = Math.max(8, width / 12);
		graphics.fill(x, y, x + soft, y + 1, COL_LINE_SOFT);
		graphics.fill(x + soft, y, x + width - soft, y + 1, COL_LINE);
		graphics.fill(x + width - soft, y, x + width, y + 1, COL_LINE_SOFT);
		graphics.fill(x + soft, y - 1, x + width - soft, y, 0x28ECF2F6);
	}

	private static int withAlpha(int argb, float alpha) {
		int a = Mth.clamp(Math.round(((argb >>> 24) & 0xFF) * alpha), 0, 255);
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	private static List<MenuOption> optionsFor(RefugeeSpecialRole role) {
		List<MenuOption> list = new ArrayList<>();
		list.add(new MenuOption(Kind.TALK, "screen.refugee.splash.talk"));
		switch (role) {
			case NURSE -> list.add(new MenuOption(Kind.HEAL, "screen.refugee.splash.heal"));
			case CARTOGRAPHER -> list.add(new MenuOption(Kind.MAP, "screen.refugee.splash.map"));
			case ENCHANTER -> list.add(new MenuOption(Kind.TRADE, "screen.refugee.splash.trade"));
			case GUIDE -> {
			}
		}
		list.add(new MenuOption(Kind.FAREWELL, "screen.refugee.splash.farewell"));
		return List.copyOf(list);
	}

	private static List<Component> buildTalkPool(RefugeeSpecialRole role, List<String> talkLines) {
		List<Component> pool = new ArrayList<>();
		if (talkLines != null) {
			for (String line : talkLines) {
				if (line != null && !line.isBlank()) {
					pool.add(Component.literal(line));
				}
			}
		}
		if (!pool.isEmpty()) {
			return List.copyOf(pool);
		}
		for (int i = 0; i < 3; i++) {
			pool.add(Component.translatable("screen.refugee.splash." + role.id() + ".talk." + i));
		}
		return List.copyOf(pool);
	}

	private static String lineKey(RefugeeSpecialRole role, String suffix) {
		return "screen.refugee.splash." + role.id() + "." + suffix;
	}

	private enum Kind {
		TALK,
		HEAL,
		MAP,
		TRADE,
		FAREWELL
	}

	private record MenuOption(Kind kind, String labelKey) {
	}
}
