package luowei.refugee.network;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import luowei.refugee.attachment.PlayerSelectionData;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.blueprint.BlueprintRegistry;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.special.GuideDialogueConfig;
import luowei.refugee.special.GuideTutorialService;
import luowei.refugee.special.RefugeeSpecialRole;
import luowei.refugee.special.SpecialRefugeeService;
import luowei.refugee.special.TerritoryMapService;
import luowei.refugee.staff.CommandStaffItem;
import luowei.refugee.staff.StaffMode;
import luowei.refugee.staff.StaffPage;
import luowei.refugee.staff.StaffService;
import luowei.refugee.zone.AreaBox;

/**
 * 结构目录同步、选定状态、指挥杖描边与选定写入。
 */
public final class RefugeeNetworking {
	private RefugeeNetworking() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(BlueprintCatalogPayload.TYPE, BlueprintCatalogPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(BlueprintSelectionPayload.TYPE, BlueprintSelectionPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(GuideDialoguePayload.TYPE, GuideDialoguePayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(TerritoryMapPayload.TYPE, TerritoryMapPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(SpecialSplashPayload.TYPE, SpecialSplashPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(SpecialSplashTalkPayload.TYPE, SpecialSplashTalkPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(StaffOpenPiePayload.TYPE, StaffOpenPiePayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(StaffSyncPayload.TYPE, StaffSyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(OpenImportNamePayload.TYPE, OpenImportNamePayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(BlueprintSelectPayload.TYPE, BlueprintSelectPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(TerritoryMapRequestPayload.TYPE, TerritoryMapRequestPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(SpecialSplashActionPayload.TYPE, SpecialSplashActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(StaffPiePayload.TYPE, StaffPiePayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(ImportNamePayload.TYPE, ImportNamePayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(StaffNavPayload.TYPE, StaffNavPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(StaffBuildPlacePayload.TYPE, StaffBuildPlacePayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BlueprintSelectPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> applySelection(player, payload.id()));
		});
		ServerPlayNetworking.registerGlobalReceiver(TerritoryMapRequestPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> sendTerritoryMap(player, payload.villagerEntityId(), payload.radius(), false));
		});
		ServerPlayNetworking.registerGlobalReceiver(SpecialSplashActionPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> SpecialRefugeeService.handleSplashAction(player, payload.entityId(), payload.action()));
		});
		ServerPlayNetworking.registerGlobalReceiver(StaffPiePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> StaffService.handlePie(player, payload.action()));
		});
		ServerPlayNetworking.registerGlobalReceiver(ImportNamePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> StaffService.handleImportName(player, payload.confirm(), payload.name()));
		});
		ServerPlayNetworking.registerGlobalReceiver(StaffNavPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> StaffService.handleNav(player, payload.action()));
		});
		ServerPlayNetworking.registerGlobalReceiver(StaffBuildPlacePayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> StaffService.handleBuildPlace(
					player,
					payload.origin(),
					payload.offsetX(),
					payload.offsetY(),
					payload.offsetZ(),
					payload.rotation()
			));
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			syncCatalog(player, false, InteractionHand.MAIN_HAND);
			syncSelection(player);
			StaffService.sync(player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> StaffService.onLogout(handler.getPlayer().getUUID()));
	}

	public static void openGuide(ServerPlayer player) {
		if (player == null) {
			return;
		}
		ServerPlayNetworking.send(player, new GuideDialoguePayload(GuideDialogueConfig.localized(player)));
	}

	public static void openSpecialSplash(ServerPlayer player, Villager villager) {
		openSpecialSplash(player, villager, null);
	}

	public static void openSpecialSplash(ServerPlayer player, Villager villager, String initialTalkKey) {
		RefugeeSpecialRole role = RefugeeSpecialRole.of(villager);
		if (player == null || villager == null || role == null) {
			return;
		}
		List<String> talkLines = role == RefugeeSpecialRole.GUIDE
				? GuideDialogueConfig.talkLines(player)
				: List.of();
		boolean foodSecret = role == RefugeeSpecialRole.GUIDE && GuideTutorialService.foodSecretVisible();
		ServerPlayNetworking.send(player, new SpecialSplashPayload(
				villager.getId(),
				role.id(),
				talkLines,
				initialTalkKey,
				SpecialSplashPayload.MODE_NORMAL,
				0,
				"",
				foodSecret,
				false
		));
	}

	public static void openGuideIntro(
			ServerPlayer player,
			Villager villager,
			int introIndex,
			String interruptKey,
			boolean abandon,
			boolean seek
	) {
		if (player == null || villager == null) {
			return;
		}
		List<String> talkLines = GuideDialogueConfig.talkLines(player);
		ServerPlayNetworking.send(player, new SpecialSplashPayload(
				villager.getId(),
				RefugeeSpecialRole.GUIDE.id(),
				talkLines,
				abandon ? GuideTutorialService.ABANDON_KEY : null,
				abandon ? SpecialSplashPayload.MODE_ABANDON : SpecialSplashPayload.MODE_INTRO,
				introIndex,
				interruptKey == null ? "" : interruptKey,
				GuideTutorialService.foodSecretVisible(),
				seek && !abandon
		));
	}

	public static void updateSpecialSplashTalk(ServerPlayer player, int entityId, String talkKey) {
		if (player == null || talkKey == null || talkKey.isBlank()) {
			return;
		}
		ServerPlayNetworking.send(player, new SpecialSplashTalkPayload(entityId, talkKey));
	}

	public static void openTerritoryMap(ServerPlayer player, int radius) {
		sendTerritoryMap(player, 0, radius, true);
	}

	public static void openTerritoryMap(ServerPlayer player, Villager villager, int radius) {
		if (villager == null) {
			sendTerritoryMap(player, 0, radius, true);
			return;
		}
		sendTerritoryMap(player, villager.getId(), radius, true);
	}

	public static void sendTerritoryMap(ServerPlayer player, int villagerEntityId, int radius, boolean open) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		Villager villager = findCartographer(player, villagerEntityId);
		if (villager == null) {
			return;
		}
		int clamped = TerritoryMapService.clampRadius(radius);
		ChunkPos center = player.chunkPosition();
		byte[] cells = TerritoryMapService.buildGrid(
				level,
				PbsAdapter.resolveSubject(player),
				center,
				clamped
		);
		ServerPlayNetworking.send(player, new TerritoryMapPayload(
				center.x,
				center.z,
				clamped,
				cells,
				open,
				villager.getId()
		));
	}

	private static Villager findCartographer(ServerPlayer player, int entityId) {
		if (!(player.level() instanceof ServerLevel level)) {
			return null;
		}
		if (entityId > 0) {
			Entity entity = level.getEntity(entityId);
			if (isNearbyCartographer(player, entity)) {
				return (Villager) entity;
			}
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		if (selection.cartographerId() != null) {
			Entity entity = level.getEntity(selection.cartographerId());
			if (isNearbyCartographer(player, entity)) {
				return (Villager) entity;
			}
		}
		Villager closest = null;
		float best = 16.0f;
		AABB box = player.getBoundingBox().inflate(16.0);
		for (Villager villager : level.getEntitiesOfClass(Villager.class, box)) {
			if (!RefugeeSpecialRole.is(villager, RefugeeSpecialRole.CARTOGRAPHER)) {
				continue;
			}
			float distance = villager.distanceTo(player);
			if (distance <= best) {
				best = distance;
				closest = villager;
			}
		}
		return closest;
	}

	private static boolean isNearbyCartographer(ServerPlayer player, Entity entity) {
		return entity instanceof Villager villager
				&& RefugeeSpecialRole.is(villager, RefugeeSpecialRole.CARTOGRAPHER)
				&& villager.distanceTo(player) <= 16.0f;
	}

	public static void openSelector(ServerPlayer player, InteractionHand hand) {
		syncCatalog(player, true, hand, null);
	}

	public static void openSelector(ServerPlayer player, InteractionHand hand, ResourceLocation selected) {
		syncCatalog(player, true, hand, selected);
	}

	public static void openImportName(ServerPlayer player, AreaBox box) {
		if (player == null || box == null) {
			return;
		}
		ServerPlayNetworking.send(player, new OpenImportNamePayload(
				box.min(),
				box.max(),
				RefugeeConfig.importMaxAxis,
				RefugeeConfig.importMaxVolume
		));
	}

	public static void applyImported(ServerPlayer player, ResourceLocation id) {
		if (id == null) {
			return;
		}
		if (!holdingStaff(player) || !BlueprintRegistry.visibleTo(player.getUUID(), id)) {
			return;
		}
		StaffService.enterPreview(player, id);
	}

	public static void openStaffPie(ServerPlayer player) {
		openStaffPie(player, StaffPage.PIE);
	}

	public static void openStaffPie(ServerPlayer player, StaffPage page) {
		if (player == null) {
			return;
		}
		ServerPlayNetworking.send(player, new StaffOpenPiePayload(page == null ? StaffPage.PIE : page));
	}

	public static void syncStaff(
			ServerPlayer player,
			StaffMode mode,
			StaffPage page,
			List<BlockPos> chests,
			List<BlockPos> foodChests,
			List<AreaBox> zones,
			List<AreaBox> builds,
			BlockPos pendingCorner,
			AreaBox importBox,
			List<BlockPos> patrolPoints
	) {
		if (player == null) {
			return;
		}
		ServerPlayNetworking.send(player, new StaffSyncPayload(
				mode == null ? StaffMode.NONE : mode,
				page == null ? StaffPage.ROOT : page,
				chests == null ? List.of() : chests,
				foodChests == null ? List.of() : foodChests,
				zones == null ? List.of() : zones,
				builds == null ? List.of() : builds,
				Optional.ofNullable(pendingCorner),
				Optional.ofNullable(importBox),
				patrolPoints == null ? List.of() : patrolPoints
		));
	}

	public static void syncCatalogToAll(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			syncCatalog(player, false, InteractionHand.MAIN_HAND);
		}
	}

	public static void syncCatalog(ServerPlayer player, boolean open, InteractionHand hand) {
		syncCatalog(player, open, hand, null);
	}

	public static void syncCatalog(ServerPlayer player, boolean open, InteractionHand hand, ResourceLocation selected) {
		if (player == null) {
			return;
		}
		UUID playerId = player.getUUID();
		ServerPlayNetworking.send(player, new BlueprintCatalogPayload(
				BlueprintRegistry.catalog(playerId),
				BlueprintRegistry.templateNbts(playerId),
				open,
				hand,
				Optional.ofNullable(selected)
		));
	}

	public static void syncSelection(ServerPlayer player) {
		if (player == null) {
			return;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		ServerPlayNetworking.send(player, new BlueprintSelectionPayload(
				Optional.ofNullable(selection.structureId()),
				Optional.ofNullable(selection.buildOrigin())
		));
	}

	private static void applySelection(ServerPlayer player, ResourceLocation id) {
		if (player == null || id == null) {
			return;
		}
		if (!holdingStaff(player)) {
			return;
		}
		if (!BlueprintRegistry.visibleTo(player.getUUID(), id)) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.build.invalid"), true);
			return;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		selection.setStructureId(id);
		selection.setBuildOrigin(null);
		RefugeeAttachments.markDirty(player, selection);
		StaffService.enterPreview(player, id);
	}

	private static boolean holdingStaff(ServerPlayer player) {
		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		return main.getItem() instanceof CommandStaffItem || off.getItem() instanceof CommandStaffItem;
	}
}
