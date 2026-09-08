package luowei.refugee.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;

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
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide()) {
				return InteractionResult.PASS;
			}
			if (!(player.getItemInHand(hand).getItem() instanceof CommandStaffItem)) {
				return InteractionResult.PASS;
			}
			if (ClientStaffState.page() == StaffPage.COMBAT_FOLLOW) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
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
			client.execute(() -> {
				StaffPage piePage = payload.page();
				if (piePage != null && piePage.isPie()) {
					ClientStaffState.setPage(piePage);
				}
				client.setScreen(new StaffPieScreen(piePage));
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(StaffSyncPayload.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			client.execute(() -> {
				ClientStaffState.apply(
						payload.mode(),
						payload.page(),
						payload.chests(),
						payload.foodChests(),
						payload.zones(),
						payload.builds(),
						payload.pendingCorner(),
						payload.importBox(),
						payload.patrolPoints()
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
						payload.initialTalkKey(),
						payload.screenMode(),
						payload.introIndex(),
						payload.interruptKey() == null ? "" : payload.interruptKey(),
						payload.foodSecret()
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
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		renderGuidePortalFx(graphics, client);
		if (client.options.hideGui) {
			return;
		}
		StaffPage page = ClientStaffState.page();
		Font font = client.font;
		int width = client.getWindow().getGuiScaledWidth();
		int height = client.getWindow().getGuiScaledHeight();
		if (page == StaffPage.BUILD_PREVIEW) {
			Component channel = ClientBlueprintSelection.channelLabel();
			Component hint = ClientBlueprintSelection.hintLabel();
			graphics.drawCenteredString(font, channel, width / 2, height - 96, 0xFFE8F4FF);
			graphics.drawCenteredString(font, hint, width / 2, height - 84, 0xFFAAAAAA);
			return;
		}
		if (page == StaffPage.COMBAT_FOLLOW) {
			graphics.drawCenteredString(
					font,
					Component.translatable("message.refugee.staff.follow.hint"),
					width / 2,
					height - 84,
					0xFFAAAAAA
			);
			return;
		}
		if (page == StaffPage.COMBAT_PATROL) {
			graphics.drawCenteredString(
					font,
					Component.translatable("message.refugee.staff.patrol.hint", ClientStaffState.patrolPoints().size()),
					width / 2,
					height - 84,
					0xFFAAAAAA
			);
		}
	}

	public static void requestTerritoryRadius(int radius, int villagerEntityId) {
		ClientPlayNetworking.send(new TerritoryMapRequestPayload(radius, villagerEntityId));
	}

	private static long guidePortalFxUntilMs;

	private static void renderGuidePortalFx(GuiGraphics graphics, Minecraft client) {
		long now = System.currentTimeMillis();
		if (now >= guidePortalFxUntilMs) {
			return;
		}
		float t = Mth.clamp((guidePortalFxUntilMs - now) / 4000f, 0f, 1f);
		float alpha = t * t;
		alpha *= alpha;
		alpha = alpha * 0.8f + 0.2f * t;
		TextureAtlasSprite sprite = client.getBlockRenderer()
				.getBlockModelShaper()
				.getParticleIcon(Blocks.NETHER_PORTAL.defaultBlockState());
		int color = ARGB.colorFromFloat(alpha, 1f, 1f, 1f);
		graphics.blitSprite(RenderType::guiTextured, sprite, 0, 0, graphics.guiWidth(), graphics.guiHeight(), color);
	}

	public static void playGuidePortalFx() {
		Minecraft client = Minecraft.getInstance();
		guidePortalFxUntilMs = System.currentTimeMillis() + 4000L;
		if (client.player != null) {
			client.player.playSound(SoundEvents.PORTAL_TRIGGER, 0.85f, 0.75f);
			client.player.playSound(SoundEvents.PORTAL_AMBIENT, 0.55f, 1f);
		}
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
