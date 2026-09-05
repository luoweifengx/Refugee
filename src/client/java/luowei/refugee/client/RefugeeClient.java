package luowei.refugee.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;

import luowei.refugee.client.model.RefugeeVillagerModel;
import luowei.refugee.interact.VillagerKitMenus;
import luowei.refugee.network.BlueprintCatalogPayload;
import luowei.refugee.network.BlueprintSelectPayload;
import luowei.refugee.network.BlueprintSelectionPayload;
import luowei.refugee.network.GuideDialoguePayload;
import luowei.refugee.network.OpenImportNamePayload;
import luowei.refugee.network.SpecialSplashAction;
import luowei.refugee.network.SpecialSplashActionPayload;
import luowei.refugee.network.SpecialSplashPayload;
import luowei.refugee.network.SpecialSplashTalkPayload;
import luowei.refugee.network.StaffOpenPiePayload;
import luowei.refugee.network.StaffSyncPayload;
import luowei.refugee.network.TerritoryMapPayload;
import luowei.refugee.network.TerritoryMapRequestPayload;
import luowei.refugee.special.RefugeeSpecialRole;
import luowei.refugee.staff.CommandStaffItem;
import luowei.refugee.staff.StaffPage;

public class RefugeeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityModelLayerRegistry.registerModelLayer(RefugeeVillagerModel.LAYER, RefugeeVillagerModel::createBodyLayer);
		MenuScreens.register(VillagerKitMenus.KIT, VillagerKitScreen::new);
		BlueprintPreviewRenderer.register();
		StaffOverlayRenderer.register();
		HudRenderCallback.EVENT.register(RefugeeClient::renderPreviewHud);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide()) {
				return InteractionResult.PASS;
			}
			if (!(player.getItemInHand(hand).getItem() instanceof CommandStaffItem)) {
				return InteractionResult.PASS;
			}
			if (StaffClientNav.isPreview()) {
				StaffClientNav.handlePreviewUse(hit.getBlockPos());
			}
			return InteractionResult.SUCCESS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!level.isClientSide()) {
				return InteractionResult.PASS;
			}
			if (!(player.getItemInHand(hand).getItem() instanceof CommandStaffItem)) {
				return InteractionResult.PASS;
			}
			if (StaffClientNav.handlePreviewAirUse()) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientBlueprintTemplates.clear();
			ClientBlueprintSelection.clear();
			ClientStaffState.clear();
		});
		ClientPlayNetworking.registerGlobalReceiver(BlueprintCatalogPayload.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			client.execute(() -> {
				ClientBlueprintTemplates.replaceAll(payload.templates());
				if (payload.open()) {
					client.setScreen(new BlueprintSelectScreen(
							payload.entries(),
							payload.hand(),
							payload.selected() == null ? null : payload.selected().orElse(null)
					));
				} else if (client.screen instanceof BlueprintSelectScreen screen) {
					screen.replaceEntries(payload.entries());
				}
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(BlueprintSelectionPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> ClientBlueprintSelection.apply(payload.structureId(), payload.buildOrigin()));
		});
		ClientPlayNetworking.registerGlobalReceiver(StaffOpenPiePayload.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			client.execute(() -> client.setScreen(new StaffPieScreen()));
		});
		ClientPlayNetworking.registerGlobalReceiver(StaffSyncPayload.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			client.execute(() -> {
				ClientStaffState.apply(
						payload.mode(),
						payload.page(),
						payload.chests(),
						payload.zones(),
						payload.builds(),
						payload.pendingCorner(),
						payload.importBox()
				);
				StaffClientNav.applyScreenForPage(client, payload.page());
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(OpenImportNamePayload.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			client.execute(() -> client.setScreen(new ImportNameScreen(
					payload.min(),
					payload.max(),
					payload.maxAxis(),
					payload.maxVolume()
			)));
		});
		ClientPlayNetworking.registerGlobalReceiver(GuideDialoguePayload.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			client.execute(() -> client.setScreen(new GuideDialogueScreen(payload.entries())));
		});
		ClientPlayNetworking.registerGlobalReceiver(SpecialSplashPayload.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			client.execute(() -> {
				RefugeeSpecialRole role = RefugeeSpecialRole.byId(payload.roleId());
				if (role == null) {
					return;
				}
				client.setScreen(new SpecialSplashScreen(
						payload.entityId(),
						role,
						payload.talkLines(),
						payload.initialTalkKey()
				));
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(SpecialSplashTalkPayload.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			client.execute(() -> {
				if (client.screen instanceof SpecialSplashScreen screen && screen.matchesEntity(payload.entityId())) {
					screen.applyTalkKey(payload.talkKey());
				}
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(TerritoryMapPayload.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			client.execute(() -> {
				if (client.screen instanceof TerritoryMapScreen screen) {
					screen.apply(payload.centerX(), payload.centerZ(), payload.radius(), payload.cells(), payload.villagerEntityId());
				} else if (payload.open()) {
					client.setScreen(new TerritoryMapScreen(
							payload.centerX(),
							payload.centerZ(),
							payload.radius(),
							payload.cells(),
							payload.villagerEntityId()
					));
				}
			});
		});
	}

	private static void renderPreviewHud(GuiGraphics graphics, net.minecraft.client.DeltaTracker tickCounter) {
		if (ClientStaffState.page() != StaffPage.BUILD_PREVIEW) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		Font font = client.font;
		int width = client.getWindow().getGuiScaledWidth();
		int height = client.getWindow().getGuiScaledHeight();
		Component channel = ClientBlueprintSelection.channelLabel();
		Component hint = ClientBlueprintSelection.hintLabel();
		graphics.drawCenteredString(font, channel, width / 2, height - 96, 0xFFE8F4FF);
		graphics.drawCenteredString(font, hint, width / 2, height - 84, 0xFFAAAAAA);
	}

	public static void requestTerritoryRadius(int radius, int villagerEntityId) {
		ClientPlayNetworking.send(new TerritoryMapRequestPayload(radius, villagerEntityId));
	}

	public static void sendSplashAction(int entityId, SpecialSplashAction action) {
		if (action == null) {
			return;
		}
		ClientPlayNetworking.send(new SpecialSplashActionPayload(entityId, action));
	}

	public static void selectBlueprint(BlueprintSelectPayload payload) {
		ClientPlayNetworking.send(payload);
	}
}
