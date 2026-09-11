package luowei.refugee.client;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import luowei.refugee.network.StaffBuildPlacePayload;
import luowei.refugee.network.StaffNavPayload;
import luowei.refugee.staff.CommandStaffItem;
import luowei.refugee.staff.StaffNavAction;
import luowei.refugee.staff.StaffPage;

/**
 * 指挥杖页面树客户端导航：E / Esc 回根，预览时 Tab 切通道、滚轮改值，右键先钉原点再开工；开工后解开钉住，可继续摆下一处。
 */
public final class StaffClientNav {
	private StaffClientNav() {
	}

	public static boolean holdingStaff() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return false;
		}
		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		return main.getItem() instanceof CommandStaffItem || off.getItem() instanceof CommandStaffItem;
	}

	public static boolean pageActive() {
		return ClientStaffState.page() != StaffPage.ROOT;
	}

	public static boolean isPreview() {
		return ClientStaffState.page() == StaffPage.BUILD_PREVIEW;
	}

	public static boolean isAdvance() {
		return ClientStaffState.page() == StaffPage.ZONE_ADVANCE;
	}

	public static boolean isStaffScreen(Screen screen) {
		return screen instanceof StaffPieScreen
				|| screen instanceof BlueprintSelectScreen
				|| screen instanceof ImportNameScreen;
	}

	public static boolean shouldInterceptWorldInput() {
		return pageActive() && (holdingStaff() || isPreview() || isAdvance());
	}

	public static boolean isInventoryKey(Minecraft client, int keyCode, int scanCode) {
		if (keyCode == GLFW.GLFW_KEY_E) {
			return true;
		}
		return client != null && client.options.keyInventory.matches(keyCode, scanCode);
	}

	public static void resetToRoot() {
		ClientPlayNetworking.send(new StaffNavPayload(StaffNavAction.RESET));
		ClientStaffState.setPage(StaffPage.ROOT);
		ClientBlueprintSelection.clearPreview();
		ClientAdvanceSelection.reset();
		closeStaffScreens();
	}

	public static boolean handleWorldKey(Minecraft client, int keyCode, int scanCode) {
		if (!shouldInterceptWorldInput()) {
			return false;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE || isInventoryKey(client, keyCode, scanCode)) {
			resetToRoot();
			return true;
		}
		if (isPreview() && keyCode == GLFW.GLFW_KEY_TAB) {
			if (Screen.hasShiftDown()) {
				ClientBlueprintSelection.previousChannel();
			} else {
				ClientBlueprintSelection.nextChannel();
			}
			return true;
		}
		if (isAdvance() && keyCode == GLFW.GLFW_KEY_TAB) {
			if (Screen.hasShiftDown()) {
				ClientAdvanceSelection.previousAxis();
				ClientPlayNetworking.send(new StaffNavPayload(StaffNavAction.ADVANCE_PREV_AXIS));
			} else {
				ClientAdvanceSelection.nextAxis();
				ClientPlayNetworking.send(new StaffNavPayload(StaffNavAction.ADVANCE_NEXT_AXIS));
			}
			return true;
		}
		return false;
	}

	public static boolean handleWorldScroll(double vertical) {
		if (vertical == 0.0) {
			return false;
		}
		if (isAdvance() && holdingStaff()) {
			boolean positive = vertical > 0.0;
			ClientAdvanceSelection.setPositive(positive);
			ClientPlayNetworking.send(new StaffNavPayload(
					positive ? StaffNavAction.ADVANCE_POSITIVE : StaffNavAction.ADVANCE_NEGATIVE
			));
			return true;
		}
		if (!isPreview() || !holdingStaff()) {
			return false;
		}
		ClientBlueprintSelection.adjust(vertical > 0.0 ? 1 : -1);
		return true;
	}

	public static boolean handleScreenKey(Screen screen, int keyCode, int scanCode) {
		if (!isStaffScreen(screen)) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			resetToRoot();
			return true;
		}
		if (screen instanceof ImportNameScreen) {
			return false;
		}
		if (isInventoryKey(client, keyCode, scanCode)) {
			resetToRoot();
			return true;
		}
		return false;
	}

	/**
	 * 预览右键：未钉住则把准星方块定为原点，已钉住则发包开工。
	 */
	public static boolean handlePreviewUse(BlockPos clicked) {
		if (!isPreview()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.player.isShiftKeyDown()) {
			return true;
		}
		if (ClientBlueprintSelection.originLocked()) {
			return confirmPreviewPlace();
		}
		BlockPos origin = clicked;
		if (origin == null) {
			origin = lookBlock(Minecraft.getInstance());
		}
		if (origin == null) {
			return false;
		}
		ClientBlueprintSelection.lockOrigin(origin);
		if (client.player != null) {
			client.player.displayClientMessage(
					Component.translatable("message.refugee.staff.preview.pinned"),
					true
			);
		}
		return true;
	}

	public static boolean handlePreviewAirUse() {
		if (!isPreview() || !ClientBlueprintSelection.originLocked()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.player.isShiftKeyDown()) {
			return true;
		}
		return confirmPreviewPlace();
	}

	public static boolean confirmPreviewPlace() {
		if (!isPreview()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		BlockPos origin = resolveLookOrigin(client);
		if (origin == null) {
			return false;
		}
		ClientPlayNetworking.send(new StaffBuildPlacePayload(
				origin,
				ClientBlueprintSelection.offsetX(),
				ClientBlueprintSelection.offsetY(),
				ClientBlueprintSelection.offsetZ(),
				ClientBlueprintSelection.rotation()
		));
		ClientBlueprintSelection.unlockOrigin();
		return true;
	}

	public static BlockPos resolveLookOrigin(Minecraft client) {
		if (client == null) {
			return null;
		}
		if (ClientBlueprintSelection.buildOrigin() != null) {
			return ClientBlueprintSelection.buildOrigin();
		}
		return lookBlock(client);
	}

	private static BlockPos lookBlock(Minecraft client) {
		if (client == null) {
			return null;
		}
		HitResult hit = client.hitResult;
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			return blockHit.getBlockPos();
		}
		return null;
	}

	public static void closeStaffScreens() {
		Minecraft client = Minecraft.getInstance();
		if (isStaffScreen(client.screen)) {
			client.setScreen(null);
		}
	}

	public static void applyScreenForPage(Minecraft client, StaffPage page) {
		if (client == null) {
			return;
		}
		Screen screen = client.screen;
		if (!page.isPie() && screen instanceof StaffPieScreen) {
			client.setScreen(null);
		}
		if (page != StaffPage.BUILD_CATALOG && screen instanceof BlueprintSelectScreen) {
			client.setScreen(null);
		}
		if (page != StaffPage.IMPORT_NAME && screen instanceof ImportNameScreen) {
			client.setScreen(null);
		}
	}
}
