package luowei.refugee.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import luowei.refugee.network.StaffInspectPayload;
import luowei.refugee.network.StaffInspectReplyPayload;
import luowei.refugee.staff.CommandStaffItem;

/**
 * 手持指挥杖看向自己或同组织居民时，右上角棕色检视框。生命就地读实体，其余四项等服务端回包。
 */
public final class StaffInspectHud {
	private static final int BORDER = 0xFF6E4A2A;
	private static final int INNER = 0xFFE2C39A;
	private static final int CORNER = 0xFFF3E2C4;
	private static final int DIVIDER = 0x88E2C39A;
	private static final int FILL = 0xD62C2016;
	private static final int LINE = 0xC8E2C39A;
	private static final int TITLE = 0xFFF6E7CF;
	private static final int VALUE = 0xFFFFF6EA;
	private static final int TRACK = 0xFF1A120C;
	private static final int HEALTH = 0xFFC4473A;
	private static final int SATIETY = 0xFFE08A32;
	private static final int STAMINA = 0xFF7DAA3A;
	private static final int COMFORT = 0xFF4C8DDB;
	private static final int LOYALTY = 0xFFE2C04A;

	private static final int MARGIN = 28;
	private static final int PAD_X = 2;
	private static final int PAD_Y = 1;
	private static final int CORNER_LEN = 3;
	private static final int BAR_H = 4;
	private static final int TEXT_GAP = 3;
	private static final int BAR_GAP = 3;
	private static final int MIN_BAR = 24;
	private static final int REQUEST_INTERVAL = 10;
	private static final double LOOK_REACH_SQ = 16.0 * 16.0;

	private static int shownId = Integer.MIN_VALUE;
	private static int requestedId = Integer.MIN_VALUE;
	private static long lastRequest = Long.MIN_VALUE;
	private static Snapshot snapshot;

	private StaffInspectHud() {
	}

	public static void clear() {
		shownId = Integer.MIN_VALUE;
		requestedId = Integer.MIN_VALUE;
		lastRequest = Long.MIN_VALUE;
		snapshot = null;
	}

	public static void accept(StaffInspectReplyPayload payload) {
		if (payload == null || payload.entityId() != shownId || !payload.allowed()) {
			if (payload != null && payload.entityId() == shownId) {
				snapshot = null;
			}
			return;
		}
		snapshot = new Snapshot(
				payload.entityId(),
				payload.satiety(),
				payload.stamina(),
				payload.comfort(),
				payload.loyalty(),
				Math.max(1.0f, payload.statMax())
		);
	}

	public static void tick(Minecraft client) {
		if (client.player == null || client.level == null || client.screen != null || client.options.hideGui) {
			clear();
			return;
		}
		if (!holdingStaff(client.player)) {
			clear();
			return;
		}
		Villager villager = lookedAt(client);
		if (villager == null) {
			clear();
			return;
		}
		int id = villager.getId();
		if (id != shownId) {
			snapshot = null;
			shownId = id;
		}
		long time = client.level.getGameTime();
		if (id != requestedId || time - lastRequest >= REQUEST_INTERVAL) {
			requestedId = id;
			lastRequest = time;
			ClientPlayNetworking.send(new StaffInspectPayload(id));
		}
	}

	public static void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.options.hideGui || client.screen != null || client.player == null || client.level == null) {
			return;
		}
		if (!holdingStaff(client.player) || snapshot == null) {
			return;
		}
		Villager villager = lookedAt(client);
		if (villager == null || villager.getId() != snapshot.entityId) {
			return;
		}
		float partialTick = delta.getGameTimeDeltaPartialTick(true);
		Font font = client.font;
		Component name = villager.getName();
		float health = villager.getHealth();
		float maxHealth = Math.max(1.0f, villager.getMaxHealth());
		Component healthLabel = Component.translatable("hud.refugee.inspect.health");
		Component satietyLabel = Component.translatable("hud.refugee.inspect.satiety");
		Component staminaLabel = Component.translatable("hud.refugee.inspect.stamina");
		Component comfortLabel = Component.translatable("hud.refugee.inspect.comfort");
		Component loyaltyLabel = Component.translatable("hud.refugee.inspect.loyalty");
		String healthValue = (int) Math.ceil(health) + "/" + (int) Math.ceil(maxHealth);
		String satietyValue = formatStat(snapshot.satiety);
		String staminaValue = formatStat(snapshot.stamina);
		String comfortValue = formatStat(snapshot.comfort);
		String loyaltyValue = formatStat(snapshot.loyalty);
		int labelW = Math.max(
				font.width(healthLabel),
				Math.max(
						font.width(satietyLabel),
						Math.max(
								font.width(staminaLabel),
								Math.max(font.width(comfortLabel), font.width(loyaltyLabel))
						)
				)
		);
		int valueW = Math.max(
				font.width(healthValue),
				Math.max(
						font.width(satietyValue),
						Math.max(
								font.width(staminaValue),
								Math.max(font.width(comfortValue), font.width(loyaltyValue))
						)
				)
		);
		int legacyPad = 6;
		int legacyW = Math.max(164, legacyPad * 2 + font.width(name) + 8);
		int legacyRow = font.lineHeight + 1 + 5 + 4;
		int legacyH = legacyPad + font.lineHeight + 6 + legacyRow * 5 - 4 + legacyPad;
		int textBlock = labelW + TEXT_GAP + valueW;
		int panelW = Math.max(
				legacyW / 2,
				Math.max(PAD_X * 2 + font.width(name), PAD_X * 2 + textBlock + BAR_GAP + MIN_BAR)
		);
		int panelH = legacyH / 2;
		int panelX = graphics.guiWidth() - MARGIN - panelW;
		int panelY = MARGIN;

		drawPanel(graphics, panelX, panelY, panelW, panelH);
		float[] from = project(villager.getPosition(partialTick).add(0.0, villager.getBbHeight() * 0.85, 0.0));
		if (from != null) {
			float x1 = from[0];
			float y1 = from[1];
			float x2 = panelX;
			float y2 = panelY + panelH * 0.35f;
			float mx = x1 + (x2 - x1) * 0.55f;
			drawLine(graphics, x1, y1, mx, y1, LINE);
			drawLine(graphics, mx, y1, x2, y2, LINE);
			int dx = Math.round(x1);
			int dy = Math.round(y1);
			graphics.fill(RenderType.guiOverlay(), dx - 1, dy - 1, dx + 2, dy + 2, CORNER);
		}

		int textLeft = panelX + PAD_X;
		int textRight = panelX + panelW - PAD_X;
		int lineY = panelY + PAD_Y;
		graphics.drawString(font, name, textLeft, lineY, TITLE, true);
		int divY = lineY + font.lineHeight + 1;
		graphics.fill(RenderType.guiOverlay(), textLeft, divY, textRight, divY + 1, DIVIDER);
		int rowsY = divY + 2;
		int rowH = Math.max(font.lineHeight, (panelY + panelH - PAD_Y - rowsY) / 5);
		int valueX = textLeft + labelW + TEXT_GAP;
		int barLeft = valueX + valueW + BAR_GAP;

		drawBar(graphics, font, healthLabel, healthValue, health / maxHealth, HEALTH, textLeft, valueX, barLeft, textRight, rowsY, rowH);
		drawBar(
				graphics,
				font,
				satietyLabel,
				satietyValue,
				snapshot.satiety / snapshot.statMax,
				SATIETY,
				textLeft,
				valueX,
				barLeft,
				textRight,
				rowsY + rowH,
				rowH
		);
		drawBar(
				graphics,
				font,
				staminaLabel,
				staminaValue,
				snapshot.stamina / snapshot.statMax,
				STAMINA,
				textLeft,
				valueX,
				barLeft,
				textRight,
				rowsY + rowH * 2,
				rowH
		);
		drawBar(
				graphics,
				font,
				comfortLabel,
				comfortValue,
				snapshot.comfort / snapshot.statMax,
				COMFORT,
				textLeft,
				valueX,
				barLeft,
				textRight,
				rowsY + rowH * 3,
				rowH
		);
		drawBar(
				graphics,
				font,
				loyaltyLabel,
				loyaltyValue,
				snapshot.loyalty / snapshot.statMax,
				LOYALTY,
				textLeft,
				valueX,
				barLeft,
				textRight,
				rowsY + rowH * 4,
				rowH
		);
	}

	private static void drawBar(
			GuiGraphics graphics,
			Font font,
			Component label,
			String value,
			float ratio,
			int color,
			int labelX,
			int valueX,
			int barLeft,
			int barRight,
			int y,
			int rowH
	) {
		int textY = y + Math.max(0, (rowH - font.lineHeight) / 2);
		graphics.drawString(font, label, labelX, textY, color, true);
		graphics.drawString(font, value, valueX, textY, VALUE, true);
		int barY = y + Math.max(0, (rowH - BAR_H) / 2);
		int width = Math.max(1, barRight - barLeft);
		int fillW = Math.round(width * Mth.clamp(ratio, 0.0f, 1.0f));
		graphics.fill(RenderType.guiOverlay(), barLeft, barY, barRight, barY + BAR_H, TRACK);
		if (fillW > 0) {
			graphics.fill(RenderType.guiOverlay(), barLeft, barY, barLeft + fillW, barY + BAR_H, 0xFF000000 | color);
		}
	}

	private static String formatStat(float value) {
		return String.format(java.util.Locale.ROOT, "%.1f", value);
	}

	private static boolean holdingStaff(Player player) {
		return player.getMainHandItem().getItem() instanceof CommandStaffItem
				|| player.getOffhandItem().getItem() instanceof CommandStaffItem;
	}

	private static Villager lookedAt(Minecraft client) {
		HitResult hit = client.hitResult;
		if (!(hit instanceof EntityHitResult entityHit) || entityHit.getType() != HitResult.Type.ENTITY) {
			return null;
		}
		if (!(entityHit.getEntity() instanceof Villager villager) || !villager.isAlive()) {
			return null;
		}
		if (client.player != null && client.player.distanceToSqr(villager) > LOOK_REACH_SQ) {
			return null;
		}
		return villager;
	}

	private static void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
		int x2 = x + w;
		int y2 = y + h;
		fill(graphics, x, y, x2, y + 1, BORDER);
		fill(graphics, x, y2 - 1, x2, y2, BORDER);
		fill(graphics, x, y, x + 1, y2, BORDER);
		fill(graphics, x2 - 1, y, x2, y2, BORDER);
		fill(graphics, x + 1, y + 1, x2 - 1, y + 2, INNER);
		fill(graphics, x + 1, y2 - 2, x2 - 1, y2 - 1, INNER);
		fill(graphics, x + 1, y + 1, x + 2, y2 - 1, INNER);
		fill(graphics, x2 - 2, y + 1, x2 - 1, y2 - 1, INNER);
		fill(graphics, x + 2, y + 2, x2 - 2, y2 - 2, FILL);
		drawCorner(graphics, x, y, 1, 1);
		drawCorner(graphics, x2 - 1, y, -1, 1);
		drawCorner(graphics, x, y2 - 1, 1, -1);
		drawCorner(graphics, x2 - 1, y2 - 1, -1, -1);
	}

	private static void drawCorner(GuiGraphics graphics, int x, int y, int dx, int dy) {
		fill(graphics, x, y, x + dx * CORNER_LEN, y + dy, CORNER);
		fill(graphics, x, y, x + dx, y + dy * CORNER_LEN, CORNER);
	}

	private static void fill(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
		if (x2 < x1) {
			int swap = x1;
			x1 = x2;
			x2 = swap;
		}
		if (y2 < y1) {
			int swap = y1;
			y1 = y2;
			y2 = swap;
		}
		if (x2 <= x1) {
			x2 = x1 + 1;
		}
		if (y2 <= y1) {
			y2 = y1 + 1;
		}
		graphics.fill(RenderType.guiOverlay(), x1, y1, x2, y2, color);
	}

	private static void drawLine(GuiGraphics graphics, float x1, float y1, float x2, float y2, int color) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float length = Mth.sqrt(dx * dx + dy * dy);
		if (length < 0.5f) {
			return;
		}
		PoseStack pose = graphics.pose();
		pose.pushPose();
		pose.translate(x1, y1, 0.0f);
		pose.mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
		graphics.fill(RenderType.guiOverlay(), 0, 0, Math.max(1, Math.round(length)), 1, color);
		pose.popPose();
	}

	private static float[] project(Vec3 world) {
		Minecraft minecraft = Minecraft.getInstance();
		Camera camera = minecraft.gameRenderer.getMainCamera();
		Vec3 rel = world.subtract(camera.getPosition());
		Vector3f local = new Vector3f((float) rel.x, (float) rel.y, (float) rel.z);
		local.rotate(new Quaternionf(camera.rotation()).conjugate());
		if (local.z >= -0.05f) {
			return null;
		}
		float fov = minecraft.options.fov().get() * ((float) Math.PI / 180.0f);
		int width = minecraft.getWindow().getGuiScaledWidth();
		int height = minecraft.getWindow().getGuiScaledHeight();
		float scale = (height * 0.5f) / (float) Math.tan(fov * 0.5f);
		float x = width * 0.5f - local.x * scale / local.z;
		float y = height * 0.5f + local.y * scale / local.z;
		return new float[] { x, y };
	}

	private record Snapshot(
			int entityId,
			float satiety,
			float stamina,
			float comfort,
			float loyalty,
			float statMax
	) {
	}
}
