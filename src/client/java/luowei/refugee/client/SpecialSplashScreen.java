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
import luowei.refugee.network.SpecialSplashPayload;
import luowei.refugee.special.GuideTutorialService;
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
	private static final float LOOK_MIN_DEG_S = 1f;
	private static final float LOOK_QUAD = 1f;
	private static final float LOOK_MAX_DEG_S = 220f;
	private static final float LOOK_ARRIVE_DEG = 0.15f;
	private static final long FAREWELL_HOLD_MS = 900L;

	private final int villagerEntityId;
	private final RefugeeSpecialRole role;
	private List<MenuOption> options;
	private final List<Component> talkPool;
	private final byte screenMode;
	private final boolean foodSecret;
	private final List<String> rawTalkLines;

	private int selectedIndex;
	private int hoveredIndex = -1;
	private int lastTalkIndex = -1;
	private Component talkText = Component.empty();
	private long talkSwapStartMs = -1L;
	private boolean farewellPending;
	private long farewellCloseAtMs = -1L;
	private boolean suppressInterrupt;
	private int introIndex;
	private boolean seekPending;
	private boolean interruptPending;
	private String interruptTalkKey = "";
	private AskLevel askLevel = AskLevel.NONE;
	private String askTopic = "";

	private float[] markerProgress;
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

	private long lookLastMs = -1L;
	private boolean lookActive;

	public SpecialSplashScreen(int villagerEntityId, RefugeeSpecialRole role, List<String> talkLines) {
		this(villagerEntityId, role, talkLines, null, SpecialSplashPayload.MODE_NORMAL, 0, "", false, false);
	}

	public SpecialSplashScreen(int villagerEntityId, RefugeeSpecialRole role, List<String> talkLines, String initialTalkKey) {
		this(villagerEntityId, role, talkLines, initialTalkKey, SpecialSplashPayload.MODE_NORMAL, 0, "", false, false);
	}

	public SpecialSplashScreen(
			int villagerEntityId,
			RefugeeSpecialRole role,
			List<String> talkLines,
			String initialTalkKey,
			byte screenMode,
			int introIndex,
			String interruptKey,
			boolean foodSecret
	) {
		this(villagerEntityId, role, talkLines, initialTalkKey, screenMode, introIndex, interruptKey, foodSecret, false);
	}

	public SpecialSplashScreen(
			int villagerEntityId,
			RefugeeSpecialRole role,
			List<String> talkLines,
			String initialTalkKey,
			byte screenMode,
			int introIndex,
			String interruptKey,
			boolean foodSecret,
			boolean seek
	) {
		super(Component.translatable("role.refugee." + role.id()));
		this.villagerEntityId = villagerEntityId;
		this.role = role;
		this.rawTalkLines = talkLines == null ? List.of() : List.copyOf(talkLines);
		this.talkPool = buildTalkPool(role, this.rawTalkLines);
		this.screenMode = screenMode;
		this.foodSecret = foodSecret;
		this.introIndex = Mth.clamp(introIndex, 0, GuideTutorialService.INTRO_LINES - 1);
		this.options = new ArrayList<>(optionsFor(role, screenMode));
		this.markerProgress = newMarkers(this.options.size());
		if (screenMode == SpecialSplashPayload.MODE_ABANDON) {
			this.suppressInterrupt = true;
			this.talkText = Component.translatable(GuideTutorialService.ABANDON_KEY);
			this.farewellPending = true;
			this.farewellCloseAtMs = System.currentTimeMillis() + FAREWELL_HOLD_MS;
		} else if (screenMode == SpecialSplashPayload.MODE_INTRO) {
			if (interruptKey != null && !interruptKey.isBlank()) {
				this.interruptPending = true;
				this.interruptTalkKey = interruptKey;
			}
			if (seek) {
				this.seekPending = true;
				this.talkText = Component.translatable(GuideTutorialService.SEEK_KEY);
			} else if (this.interruptPending) {
				this.talkText = Component.translatable(this.interruptTalkKey);
			} else {
				this.talkText = Component.translatable(GuideTutorialService.INTRO_KEY_PREFIX + this.introIndex);
			}
		} else if (initialTalkKey != null && !initialTalkKey.isBlank()) {
			this.talkText = Component.translatable(initialTalkKey);
		} else {
			this.talkText = Component.translatable(lineKey(role, "greeting"));
		}
	}

	public static SpecialSplashScreen ask(int entityId, RefugeeSpecialRole role, List<String> talkLines, boolean foodSecret) {
		SpecialSplashScreen screen = new SpecialSplashScreen(
				entityId,
				role,
				talkLines,
				"screen.refugee.splash.guide.what",
				SpecialSplashPayload.MODE_NORMAL,
				0,
				"",
				foodSecret
		);
		screen.enterAskCategories();
		return screen;
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
		if (this.lookLastMs < 0L) {
			beginLookAtNpc();
		}
	}

	@Override
	public void tick() {
		applyLookAtNpc();
		tickAnimations();
	}

	@Override
	public void onClose() {
		if (this.askLevel != AskLevel.NONE && this.minecraft != null) {
			this.minecraft.setScreen(new SpecialSplashScreen(
					this.villagerEntityId,
					this.role,
					this.rawTalkLines,
					lineKey(this.role, "greeting"),
					SpecialSplashPayload.MODE_NORMAL,
					0,
					"",
					this.foodSecret
			));
			return;
		}
		super.onClose();
	}

	@Override
	public void removed() {
		this.lookActive = false;
		if (this.screenMode == SpecialSplashPayload.MODE_INTRO && !this.suppressInterrupt && !this.farewellPending) {
			RefugeeClient.sendSplashAction(this.villagerEntityId, SpecialSplashAction.INTRO_INTERRUPT);
		}
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return this.askLevel != AskLevel.ITEMS;
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
		if (this.screenMode == SpecialSplashPayload.MODE_INTRO) {
			advanceIntro();
			return true;
		}
		if (this.screenMode == SpecialSplashPayload.MODE_ABANDON) {
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
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.askLevel == AskLevel.ITEMS) {
			enterAskCategories();
			return true;
		}
		if (this.screenMode == SpecialSplashPayload.MODE_INTRO
				&& (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
			advanceIntro();
			return true;
		}
		if (isInventoryKey(keyCode, scanCode)) {
			this.onClose();
			return true;
		}
		if (this.screenMode == SpecialSplashPayload.MODE_INTRO || this.screenMode == SpecialSplashPayload.MODE_ABANDON) {
			return super.keyPressed(keyCode, scanCode, modifiers);
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
		this.lookLastMs = System.currentTimeMillis();
		this.lookActive = true;
	}

	private void applyLookAtNpc() {
		if (!this.lookActive || this.lookLastMs < 0L) {
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
		float targetPitch = Mth.clamp((float) -(Mth.atan2(dy, distXZ) * (180.0 / Math.PI)), -90.0F, 90.0F);
		float yawErr = Mth.wrapDegrees(targetYaw - player.getYRot());
		float pitchErr = targetPitch - player.getXRot();
		float remaining = (float) Math.hypot(yawErr, pitchErr);
		if (remaining < LOOK_ARRIVE_DEG) {
			applyPlayerLook(player, targetYaw, targetPitch);
			this.lookLastMs = System.currentTimeMillis();
			return;
		}
		long now = System.currentTimeMillis();
		float dt = Math.min(0.05f, Math.max(0f, now - this.lookLastMs) / 1000f);
		this.lookLastMs = now;
		if (dt <= 0f) {
			return;
		}
		float speed = Mth.clamp(LOOK_MIN_DEG_S + LOOK_QUAD * remaining * remaining, LOOK_MIN_DEG_S, LOOK_MAX_DEG_S);
		float step = Math.min(1f, (speed * dt) / remaining);
		applyPlayerLook(player, player.getYRot() + yawErr * step, player.getXRot() + pitchErr * step);
	}

	private static void applyPlayerLook(Player player, float yaw, float pitch) {
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
		return hideMenu() ? 0 : Math.max(0, this.options.size());
	}

	private boolean hideMenu() {
		return this.screenMode == SpecialSplashPayload.MODE_INTRO || this.screenMode == SpecialSplashPayload.MODE_ABANDON;
	}

	/** 超过 2 项时右侧再开一列，与旅商开屏一致。 */
	private int splashMenuColumns() {
		int count = menuCount();
		if (count <= 0) {
			return 1;
		}
		return count > 2 ? 2 : 1;
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
			case ASK -> openAskWindow();
			case ASK_CATEGORY -> enterAskItems(option.id());
			case ASK_ITEM -> showAskBody(option);
			case HEAL -> RefugeeClient.sendSplashAction(this.villagerEntityId, SpecialSplashAction.HEAL);
			case MAP -> RefugeeClient.sendSplashAction(this.villagerEntityId, SpecialSplashAction.MAP);
			case TRADE -> RefugeeClient.sendSplashAction(this.villagerEntityId, SpecialSplashAction.TRADE);
			case FAREWELL -> beginFarewell();
		}
	}

	private void advanceIntro() {
		if (this.farewellPending) {
			return;
		}
		if (this.seekPending) {
			this.seekPending = false;
			if (this.interruptPending) {
				startTalkSwap(Component.translatable(this.interruptTalkKey));
				return;
			}
			startTalkSwap(Component.translatable(GuideTutorialService.INTRO_KEY_PREFIX + this.introIndex));
			return;
		}
		if (this.interruptPending) {
			this.interruptPending = false;
			startTalkSwap(Component.translatable(GuideTutorialService.INTRO_KEY_PREFIX + this.introIndex));
			return;
		}
		if (this.introIndex >= GuideTutorialService.INTRO_LINES - 1) {
			this.suppressInterrupt = true;
			RefugeeClient.sendSplashAction(this.villagerEntityId, SpecialSplashAction.INTRO_FINISH);
			return;
		}
		this.introIndex++;
		RefugeeClient.sendSplashAction(this.villagerEntityId, SpecialSplashAction.INTRO_ADVANCE);
		startTalkSwap(Component.translatable(GuideTutorialService.INTRO_KEY_PREFIX + this.introIndex));
	}

	private void openAskWindow() {
		if (this.minecraft == null) {
			return;
		}
		this.minecraft.setScreen(ask(this.villagerEntityId, this.role, this.rawTalkLines, this.foodSecret));
	}

	private void enterAskCategories() {
		this.askLevel = AskLevel.CATEGORIES;
		this.askTopic = "";
		replaceOptions(askCategoryOptions(this.foodSecret));
		startTalkSwap(Component.translatable("screen.refugee.splash.guide.what"));
	}

	private void enterAskItems(String topic) {
		this.askLevel = AskLevel.ITEMS;
		this.askTopic = topic == null ? "" : topic;
		replaceOptions(askItemOptions(this.askTopic, this.foodSecret));
		startTalkSwap(Component.translatable("screen.refugee.splash.guide.what"));
	}

	private void showAskBody(MenuOption option) {
		if (option.bodyKey() == null || option.bodyKey().isBlank()) {
			return;
		}
		startTalkSwap(Component.translatable(option.bodyKey()));
		if (option.portalFx()) {
			RefugeeClient.playGuidePortalFx();
		}
	}

	private void replaceOptions(List<MenuOption> next) {
		this.options = new ArrayList<>(next);
		this.markerProgress = newMarkers(this.options.size());
		this.selectedIndex = 0;
		this.hoveredIndex = -1;
	}

	private static float[] newMarkers(int size) {
		float[] markers = new float[Math.max(0, size)];
		if (markers.length > 0) {
			markers[0] = 1f;
		}
		return markers;
	}

	/**
	 * 舞台自下而上：菜单 → 白线（选项上方）→ 对话。
	 * 面板高度随当前文案换行数与菜单行数变化；超长文案才裁切。
	 */
	private void layoutSplashMenu() {
		int lineWidth = Math.min(this.width * 2 / 3, this.width - 24);
		int lineX = (this.width - lineWidth) / 2;
		this.talkMaxWidth = Math.max(16, lineWidth - 8);

		int padX = 14;
		this.dialogueLeft = Math.max(8, lineX - padX);
		int dialogueRight = Math.min(this.width - 8, lineX + lineWidth + padX);
		this.dialogueWidth = Math.max(48, dialogueRight - this.dialogueLeft);

		this.menuCols = splashMenuColumns();
		this.menuRows = splashMenuRows();
		this.talkScale = 1f;
		this.talkLineHeight = this.font.lineHeight + 2;
		int talkLines = Math.max(1, this.font.split(this.talkText, this.talkMaxWidth).size());
		int talkH = talkLines * this.talkLineHeight;
		int menuRowH = hideMenu() ? 0 : Math.max(this.font.lineHeight + 5, 18);
		int menuH = hideMenu() ? 0 : Math.max(1, this.menuRows) * menuRowH;
		int padTop = 8;
		int padBottom = 6;
		int gapTalkLine = 6;
		int gapLineMenu = 6;
		int chrome = padTop + gapTalkLine + 1 + gapLineMenu + menuH + padBottom;
		int cap = Math.max(chrome + this.talkLineHeight, this.height - 36);
		if (chrome + talkH > cap) {
			talkH = Math.max(this.talkLineHeight, cap - chrome);
		}
		this.dialogueHeight = chrome + talkH;
		this.dialogueTop = this.height - 8 - this.dialogueHeight;

		int innerTop = this.dialogueTop + padTop;
		int innerBottom = this.dialogueTop + this.dialogueHeight - padBottom;
		this.menuRowHeight = menuRowH;
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

		if (hideMenu()) {
			return;
		}

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

	private static List<MenuOption> optionsFor(RefugeeSpecialRole role, byte screenMode) {
		if (screenMode == SpecialSplashPayload.MODE_INTRO || screenMode == SpecialSplashPayload.MODE_ABANDON) {
			return List.of();
		}
		List<MenuOption> list = new ArrayList<>();
		if (role == RefugeeSpecialRole.GUIDE) {
			list.add(new MenuOption(Kind.TALK, "screen.refugee.splash.guide.chat", "", "", false));
			list.add(new MenuOption(Kind.ASK, "screen.refugee.splash.guide.ask", "", "", false));
			list.add(new MenuOption(Kind.FAREWELL, "screen.refugee.splash.farewell", "", "", false));
			return List.copyOf(list);
		}
		list.add(new MenuOption(Kind.TALK, "screen.refugee.splash.talk", "", "", false));
		switch (role) {
			case NURSE -> list.add(new MenuOption(Kind.HEAL, "screen.refugee.splash.heal", "", "", false));
			case CARTOGRAPHER -> list.add(new MenuOption(Kind.MAP, "screen.refugee.splash.map", "", "", false));
			case ENCHANTER -> list.add(new MenuOption(Kind.TRADE, "screen.refugee.splash.trade", "", "", false));
			default -> {
			}
		}
		list.add(new MenuOption(Kind.FAREWELL, "screen.refugee.splash.farewell", "", "", false));
		return List.copyOf(list);
	}

	private static List<MenuOption> askCategoryOptions(boolean foodSecret) {
		List<MenuOption> list = new ArrayList<>();
		list.add(category("combat"));
		list.add(category("build"));
		list.add(category("town"));
		list.add(category("secret"));
		return List.copyOf(list);
	}

	private static MenuOption category(String id) {
		return new MenuOption(Kind.ASK_CATEGORY, "screen.refugee.splash.guide.ask." + id, id, "", false);
	}

	private static List<MenuOption> askItemOptions(String topic, boolean foodSecret) {
		List<MenuOption> list = new ArrayList<>();
		switch (topic == null ? "" : topic) {
			case "combat" -> {
				list.add(item("combat", "garrison"));
				list.add(item("combat", "follow"));
				list.add(item("combat", "patrol"));
				list.add(item("combat", "formation"));
			}
			case "build" -> {
				list.add(item("build", "repair"));
				list.add(item("build", "build"));
				list.add(item("build", "mine"));
				list.add(item("build", "advance"));
			}
			case "town" -> {
				list.add(item("town", "kit"));
				list.add(item("town", "armor"));
				list.add(item("town", "why"));
			}
			case "secret" -> {
				if (foodSecret) {
					list.add(item("secret", "ritual"));
				}
				list.add(new MenuOption(
						Kind.ASK_ITEM,
						"screen.refugee.splash.guide.ask.secret.portal",
						"portal",
						"screen.refugee.splash.guide.ask.secret.portal.body",
						true
				));
			}
			default -> {
			}
		}
		return List.copyOf(list);
	}

	private static MenuOption item(String topic, String id) {
		return new MenuOption(
				Kind.ASK_ITEM,
				"screen.refugee.splash.guide.ask." + topic + "." + id,
				id,
				"screen.refugee.splash.guide.ask." + topic + "." + id + ".body",
				false
		);
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
		ASK,
		ASK_CATEGORY,
		ASK_ITEM,
		HEAL,
		MAP,
		TRADE,
		FAREWELL
	}

	private enum AskLevel {
		NONE,
		CATEGORIES,
		ITEMS
	}

	private record MenuOption(Kind kind, String labelKey, String id, String bodyKey, boolean portalFx) {
	}
}
