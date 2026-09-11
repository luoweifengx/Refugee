package luowei.refugee.staff;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import luowei.refugee.Refugee;
import luowei.refugee.ai.RefugeeSmeltGoal;
import luowei.refugee.attachment.PlayerSelectionData;
import luowei.refugee.block.AltarBlockEntity;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.blueprint.BlueprintRegistry;
import luowei.refugee.blueprint.PlayerBlueprints;
import luowei.refugee.build.BuildHealth;
import luowei.refugee.build.BuildJob;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.interact.GearDispatch;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.interact.SelectionService;
import luowei.refugee.logistics.OrgLogisticsData;
import luowei.refugee.logistics.OrgLogisticsData.ContainerRef;
import luowei.refugee.network.RefugeeNetworking;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.special.RefugeeSpecialRole;
import luowei.refugee.warehouse.WarehouseService;
import luowei.refugee.zone.AreaBox;
import luowei.refugee.zone.WorkZone;
import luowei.refugee.zone.WorkerDuty;

/**
 * 指挥杖服务端会话、页面树、仓库选箱、工作区分配与建筑任务。
 */
public final class StaffService {
	private static final Map<UUID, StaffSession> SESSIONS = new ConcurrentHashMap<>();

	private StaffService() {
	}

	public static void register() {
		BuildHealth.register();
	}

	public static StaffSession session(ServerPlayer player) {
		return SESSIONS.computeIfAbsent(player.getUUID(), id -> new StaffSession());
	}

	public static StaffPage page(ServerPlayer player) {
		StaffSession session = SESSIONS.get(player.getUUID());
		return session == null ? StaffPage.ROOT : session.page();
	}

	public static StaffMode mode(ServerPlayer player) {
		return page(player).worldMode();
	}

	public static void setMode(ServerPlayer player, StaffMode mode) {
		StaffSession session = session(player);
		StaffPage previous = session.page();
		session.setMode(mode);
		sync(player);
		if (previous != session.page()) {
			showEnterHint(player, session.page());
		}
	}

	public static void enter(ServerPlayer player, StaffPage... pages) {
		StaffSession session = session(player);
		StaffPage previous = session.page();
		session.setPages(pages);
		sync(player);
		if (previous != session.page()) {
			showEnterHint(player, session.page());
		}
	}

	public static void resetToRoot(ServerPlayer player) {
		StaffSession session = session(player);
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		selection.setBuildOrigin(null);
		RefugeeAttachments.markDirty(player, selection);
		RefugeeNetworking.syncSelection(player);
		session.resetToRoot();
		sync(player);
	}

	public static void handleNav(ServerPlayer player, StaffNavAction action) {
		if (action == StaffNavAction.RESET || action == StaffNavAction.POP) {
			resetToRoot(player);
			return;
		}
		if (page(player) != StaffPage.ZONE_ADVANCE) {
			return;
		}
		StaffSession session = session(player);
		switch (action) {
			case ADVANCE_NEXT_AXIS -> session.cycleAdvanceAxis(true);
			case ADVANCE_PREV_AXIS -> session.cycleAdvanceAxis(false);
			case ADVANCE_POSITIVE -> session.setAdvancePositive(true);
			case ADVANCE_NEGATIVE -> session.setAdvancePositive(false);
			default -> {
			}
		}
	}

	private static void showEnterHint(ServerPlayer player, StaffPage page) {
		if (page == StaffPage.WAREHOUSE) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.mode.warehouse"), true);
		} else if (page == StaffPage.FOOD_WAREHOUSE) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.mode.food_warehouse"), true);
		} else if (page == StaffPage.FARM_WAREHOUSE) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.mode.farm_warehouse"), true);
		} else if (page == StaffPage.GEAR_WAREHOUSE) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.mode.gear_warehouse"), true);
		} else if (page == StaffPage.SMELT_RESULT) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.mode.smelt_result"), true);
		} else if (page == StaffPage.SMELTER) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.mode.smelt"), true);
		} else if (page == StaffPage.ZONE) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.mode.zone"), true);
		} else if (page == StaffPage.ZONE_ADVANCE) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.mode.advance"), true);
		} else if (page == StaffPage.IMPORT) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.mode.import"), true);
		} else if (page == StaffPage.BUILD_PREVIEW) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.mode.build"), true);
		} else if (page == StaffPage.COMBAT_FOLLOW) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.mode.follow_entity"), true);
		} else if (page == StaffPage.COMBAT_PATROL) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.mode.patrol"), true);
		}
	}

	public static void onAirUse(ServerPlayer player) {
		if (tryCancelBuild(player) || tryCancelZone(player)) {
			return;
		}
		StaffSession session = session(player);
		if (session.isRoot()) {
			session.setPages(StaffPage.PIE);
			sync(player);
			RefugeeNetworking.openStaffPie(player, StaffPage.PIE);
			return;
		}
		if (session.page() == StaffPage.COMBAT_PATROL && player.isShiftKeyDown()) {
			completePatrol(player);
		}
	}

	public static void handlePie(ServerPlayer player, StaffPieAction action) {
		if (action == null) {
			return;
		}
		switch (action) {
			case WAREHOUSE -> {
				session(player).setPages(StaffPage.WAREHOUSE_PIE);
				sync(player);
				RefugeeNetworking.openStaffPie(player, StaffPage.WAREHOUSE_PIE);
			}
			case WAREHOUSE_BLOCKS -> enter(player, StaffPage.WAREHOUSE_PIE, StaffPage.WAREHOUSE);
			case WAREHOUSE_FARM -> enter(player, StaffPage.WAREHOUSE_PIE, StaffPage.FARM_WAREHOUSE);
			case WAREHOUSE_GEAR -> enter(player, StaffPage.WAREHOUSE_PIE, StaffPage.GEAR_WAREHOUSE);
			case WAREHOUSE_FOOD -> enter(player, StaffPage.WAREHOUSE_PIE, StaffPage.FOOD_WAREHOUSE);
			case WAREHOUSE_SMELT -> enter(player, StaffPage.WAREHOUSE_PIE, StaffPage.SMELTER);
			case WAREHOUSE_SMELT_RESULT -> enter(player, StaffPage.WAREHOUSE_PIE, StaffPage.SMELT_RESULT);
			case ZONE -> {
				session(player).setPages(StaffPage.ZONE_PIE);
				sync(player);
				RefugeeNetworking.openStaffPie(player, StaffPage.ZONE_PIE);
			}
			case ZONE_BOX -> enter(player, StaffPage.ZONE_PIE, StaffPage.ZONE);
			case ZONE_ADVANCE -> enter(player, StaffPage.ZONE_PIE, StaffPage.ZONE_ADVANCE);
			case ZONE_REPAIR -> assignRepairDuty(player);
			case ZONE_BUILD -> assignBuildDuty(player);
			case ZONE_SMELT -> assignSmeltDuty(player);
			case BUILD -> {
				session(player).setPages(StaffPage.BUILD_PIE);
				sync(player);
				RefugeeNetworking.openStaffPie(player, StaffPage.BUILD_PIE);
			}
			case IMPORT -> enter(player, StaffPage.BUILD_PIE, StaffPage.IMPORT);
			case SELECT -> {
				session(player).setPages(StaffPage.BUILD_PIE, StaffPage.BUILD_CATALOG);
				sync(player);
				RefugeeNetworking.openSelector(player, InteractionHand.MAIN_HAND);
			}
			case COMBAT -> {
				session(player).setPages(StaffPage.COMBAT_PIE);
				sync(player);
				RefugeeNetworking.openStaffPie(player, StaffPage.COMBAT_PIE);
			}
			case FOLLOW_ENTITY -> enterFollowEntity(player);
			case PATROL -> enterPatrol(player);
			case FORMATION -> applyFormation(player);
			case EQUIP_GEAR -> applyEquipGear(player);
			case RALLY -> {
				session(player).setPages(StaffPage.RALLY_PIE);
				sync(player);
				RefugeeNetworking.openStaffPie(player, StaffPage.RALLY_PIE);
			}
			case RALLY_MELEE -> applyRally(player, RefugeeRoles::matchesRallyMelee);
			case RALLY_RANGED -> applyRally(player, RefugeeRoles::matchesRallyRanged);
			case RALLY_WORKER -> applyRally(player, RefugeeRoles::matchesRallyWorker);
			case RALLY_ALL -> applyRally(player, null);
			case RALLY_CIVILIAN -> applyRally(player, RefugeeRoles::matchesRallyCivilian);
			case RALLY_SPECIAL -> applyRally(player, RefugeeRoles::matchesRallySpecial);
			case GUARD -> {
				session(player).setPages(StaffPage.GUARD_PIE);
				sync(player);
				RefugeeNetworking.openStaffPie(player, StaffPage.GUARD_PIE);
			}
			case GUARD_ADD -> applyGuardAdd(player);
			case GUARD_RALLY_NEAR -> applyGuardRallyNear(player);
			case GUARD_RALLY_ALL -> applyGuardRallyAll(player);
			case GUARD_REMOVE -> applyGuardRemove(player);
		}
	}

	public static void enterPreview(ServerPlayer player, ResourceLocation structureId) {
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		selection.setStructureId(structureId);
		selection.setBuildOrigin(null);
		RefugeeAttachments.markDirty(player, selection);
		session(player).setPages(StaffPage.BUILD_PIE, StaffPage.BUILD_CATALOG, StaffPage.BUILD_PREVIEW);
		RefugeeNetworking.syncSelection(player);
		sync(player);
		showEnterHint(player, StaffPage.BUILD_PREVIEW);
	}

	public static void handleImportName(ServerPlayer player, boolean confirm, String name) {
		if (player == null) {
			return;
		}
		StaffSession session = session(player);
		if (session.page() != StaffPage.IMPORT_NAME && session.page() != StaffPage.IMPORT) {
			return;
		}
		if (!confirm) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.import.cancelled"), true);
			resetToRoot(player);
			return;
		}
		AreaBox box = session.pendingImport();
		if (box == null) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.import.no_box"), true);
			return;
		}
		PlayerBlueprints.ImportResult result = PlayerBlueprints.capture(player, box, name);
		switch (result.status()) {
			case OK -> {
				session.clearImportBox();
				session.clearZoneCorner();
				placeImported(player, result.id(), box);
			}
			case EMPTY_NAME -> player.displayClientMessage(
					Component.translatable("message.refugee.staff.import.empty_name"), true);
			case DUPLICATE_NAME -> player.displayClientMessage(
					Component.translatable("message.refugee.staff.import.duplicate"), true);
			case TOO_LARGE_AXIS -> player.displayClientMessage(Component.translatable(
					"message.refugee.staff.import.too_large_axis",
					RefugeeConfig.importMaxAxis
			), true);
			case TOO_LARGE_VOLUME -> player.displayClientMessage(Component.translatable(
					"message.refugee.staff.import.too_large_volume",
					RefugeeConfig.importMaxVolume
			), true);
			case UNLOADED -> player.displayClientMessage(
					Component.translatable("message.refugee.staff.import.unloaded"), true);
			case EMPTY -> player.displayClientMessage(
					Component.translatable("message.refugee.staff.import.empty"), true);
			case FAILED -> player.displayClientMessage(
					Component.translatable("message.refugee.staff.import.failed"), true);
		}
	}

	public static boolean handleBlock(ServerPlayer player, BlockPos pos) {
		ItemStack held = player.getMainHandItem();
		if (!(held.getItem() instanceof CommandStaffItem)) {
			held = player.getOffhandItem();
		}
		if (!(held.getItem() instanceof CommandStaffItem)) {
			return false;
		}
		if (tryCancelBuild(player) || tryCancelZone(player)) {
			return true;
		}
		StaffSession session = session(player);
		return switch (session.page()) {
			case WAREHOUSE -> toggleWarehouse(player, pos);
			case FOOD_WAREHOUSE -> toggleFoodWarehouse(player, pos);
			case FARM_WAREHOUSE -> toggleFarmWarehouse(player, pos);
			case GEAR_WAREHOUSE -> toggleGearWarehouse(player, pos);
			case SMELT_RESULT -> toggleSmeltResult(player, pos);
			case SMELTER -> toggleSmelter(player, pos);
			case ZONE -> pickZoneCorner(player, pos);
			case ZONE_ADVANCE -> pickAdvanceCorner(player, pos);
			case BUILD_PREVIEW -> true;
			case IMPORT -> pickImportCorner(player, pos);
			case COMBAT_FOLLOW -> true;
			case COMBAT_PATROL -> addPatrolPoint(player, pos);
			default -> false;
		};
	}

	public static boolean handleEntity(ServerPlayer player, Entity entity) {
		if (player == null || entity == null || page(player) != StaffPage.COMBAT_FOLLOW) {
			return false;
		}
		ItemStack held = player.getMainHandItem();
		if (!(held.getItem() instanceof CommandStaffItem)) {
			held = player.getOffhandItem();
		}
		if (!(held.getItem() instanceof CommandStaffItem)) {
			return false;
		}
		if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.follow.invalid"), true);
			Refugee.LOGGER.debug("[refugee staff follow] invalid target type={}", entity.getType().toShortString());
			return true;
		}
		FollowAssign result = assignFollowEntity(player, living);
		if (result.followed() > 0) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.follow.assigned", result.followed()), true);
			playSuccess(player);
			resetToRoot(player);
			return true;
		}
		if (result.cancelled() > 0) {
			player.displayClientMessage(Component.translatable("message.refugee.follow.stop"), true);
			playSuccess(player);
			resetToRoot(player);
			return true;
		}
		failAction(player, Component.translatable("message.refugee.staff.combat.none"));
		return true;
	}

	public static void handleBuildPlace(
			ServerPlayer player,
			BlockPos origin,
			int offsetX,
			int offsetY,
			int offsetZ,
			Rotation rotation
	) {
		if (player == null || origin == null) {
			return;
		}
		if (page(player) != StaffPage.BUILD_PREVIEW) {
			return;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		if (selection.structureId() == null) {
			failAction(player, Component.translatable("message.refugee.staff.build.need_select"));
			return;
		}
		if (!isValidStructure(player, selection.structureId())) {
			failAction(player, Component.translatable("message.refugee.staff.build.invalid"));
			return;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		UUID subjectId = PbsAdapter.resolveSubject(player);
		ResourceLocation dimension = level.dimension().location();
		BuildJob job = new BuildJob(
				UUID.randomUUID(),
				dimension,
				selection.structureId(),
				origin,
				offsetX,
				offsetY,
				offsetZ,
				rotation == null ? Rotation.NONE : rotation
		);
		OrgLogisticsData.get(player.getServer()).addJob(subjectId, job);
		selection.setBuildOrigin(null);
		RefugeeAttachments.markDirty(player, selection);
		RefugeeNetworking.syncSelection(player);
		syncSubject(player.getServer(), subjectId);
		player.displayClientMessage(Component.translatable("message.refugee.staff.build.placed"), true);
		playSuccess(player);
	}

	public static void placeImported(ServerPlayer player, ResourceLocation structureId, AreaBox box) {
		if (player == null || structureId == null || box == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (!BlueprintRegistry.visibleTo(player, structureId)) {
			failAction(player, Component.translatable("message.refugee.staff.build.invalid"));
			return;
		}
		UUID subjectId = PbsAdapter.resolveSubject(player);
		BuildJob job = new BuildJob(
				UUID.randomUUID(),
				level.dimension().location(),
				structureId,
				box.min(),
				0,
				0,
				0,
				Rotation.NONE
		);
		job.markVerified();
		OrgLogisticsData.get(player.getServer()).addJob(subjectId, job);
		RefugeeNetworking.syncCatalogToAll(player.getServer());
		resetToRoot(player);
		player.displayClientMessage(Component.translatable("message.refugee.staff.import.placed"), true);
		playSuccess(player);
	}

	public static void deleteBlueprint(ServerPlayer player, ResourceLocation structureId) {
		if (player == null || structureId == null) {
			return;
		}
		MinecraftServer server = player.getServer();
		if (!PlayerBlueprints.delete(server, player.getUUID(), structureId)) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.blueprint.delete.denied"), true);
			playFail(player);
			return;
		}
		cancelJobsWithStructure(server, structureId);
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			PlayerSelectionData selection = RefugeeAttachments.get(other);
			if (structureId.equals(selection.structureId())) {
				selection.setStructureId(null);
				selection.setBuildOrigin(null);
				RefugeeAttachments.markDirty(other, selection);
				RefugeeNetworking.syncSelection(other);
				if (page(other) == StaffPage.BUILD_PREVIEW) {
					resetToRoot(other);
				}
			}
		}
		RefugeeNetworking.syncCatalogToAll(server);
		player.displayClientMessage(Component.translatable("message.refugee.staff.blueprint.deleted"), true);
		playSuccess(player);
	}

	public static void cancelJobsWithStructure(MinecraftServer server, ResourceLocation structureId) {
		if (server == null || structureId == null) {
			return;
		}
		List<BuildJob> jobs = new ArrayList<>(OrgLogisticsData.get(server).jobsWithStructure(structureId));
		for (BuildJob job : jobs) {
			ServerLevel level = null;
			for (ServerLevel candidate : server.getAllLevels()) {
				if (candidate.dimension().location().equals(job.dimension())) {
					level = candidate;
					break;
				}
			}
			if (level != null) {
				cancelJob(level, job);
			} else {
				OrgLogisticsData.get(server).removeJob(job.id());
			}
		}
	}

	private static boolean pickImportCorner(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		StaffSession session = session(player);
		if (session.pendingImport() != null) {
			return true;
		}
		ResourceLocation dimension = level.dimension().location();
		if (session.zoneCorner() == null || !dimension.equals(session.zoneDimension())) {
			session.setZoneCorner(dimension, pos);
			sync(player);
			player.displayClientMessage(Component.translatable("message.refugee.staff.import.corner1"), true);
			return true;
		}
		AreaBox box = AreaBox.of(session.zoneCorner(), pos);
		PlayerBlueprints.ImportStatus size = PlayerBlueprints.checkSize(box);
		if (size != PlayerBlueprints.ImportStatus.OK) {
			if (size == PlayerBlueprints.ImportStatus.TOO_LARGE_AXIS) {
				player.displayClientMessage(Component.translatable(
						"message.refugee.staff.import.too_large_axis",
						RefugeeConfig.importMaxAxis
				), true);
			} else {
				player.displayClientMessage(Component.translatable(
						"message.refugee.staff.import.too_large_volume",
						RefugeeConfig.importMaxVolume
				), true);
			}
			return true;
		}
		if (!PlayerBlueprints.areaLoaded(level, box)) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.import.unloaded"), true);
			return true;
		}
		session.clearZoneCorner();
		session.setPendingImport(box);
		session.setPages(StaffPage.BUILD_PIE, StaffPage.IMPORT, StaffPage.IMPORT_NAME);
		sync(player);
		RefugeeNetworking.openImportName(player, box);
		return true;
	}

	private static boolean toggleWarehouse(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!isContainer(level.getBlockEntity(pos))) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.warehouse.not_container"), true);
			return true;
		}
		UUID subjectId = PbsAdapter.resolveSubject(player);
		ResourceLocation dimension = level.dimension().location();
		OrgLogisticsData data = OrgLogisticsData.get(player.getServer());
		if (data.hasWarehouse(subjectId, dimension, pos)) {
			WarehouseService.remove(level, subjectId, pos);
			player.displayClientMessage(Component.translatable(
					"message.refugee.staff.warehouse.removed",
					pos.getX(),
					pos.getY(),
					pos.getZ()
			), true);
		} else {
			WarehouseService.add(level, subjectId, pos);
			player.displayClientMessage(Component.translatable(
					"message.refugee.staff.warehouse.added",
					pos.getX(),
					pos.getY(),
					pos.getZ()
			), true);
		}
		syncSubject(player.getServer(), subjectId);
		return true;
	}

	private static boolean toggleFoodWarehouse(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!isContainer(level.getBlockEntity(pos))) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.warehouse.not_container"), true);
			return true;
		}
		UUID subjectId = PbsAdapter.resolveSubject(player);
		ResourceLocation dimension = level.dimension().location();
		OrgLogisticsData data = OrgLogisticsData.get(player.getServer());
		if (data.hasFoodWarehouse(subjectId, dimension, pos)) {
			WarehouseService.removeFood(level, subjectId, pos);
			player.displayClientMessage(Component.translatable(
					"message.refugee.staff.food_warehouse.removed",
					pos.getX(),
					pos.getY(),
					pos.getZ()
			), true);
		} else {
			WarehouseService.addFood(level, subjectId, pos);
			player.displayClientMessage(Component.translatable(
					"message.refugee.staff.food_warehouse.added",
					pos.getX(),
					pos.getY(),
					pos.getZ()
			), true);
		}
		syncSubject(player.getServer(), subjectId);
		return true;
	}

	private static boolean toggleFarmWarehouse(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!isContainer(level.getBlockEntity(pos))) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.warehouse.not_container"), true);
			return true;
		}
		UUID subjectId = PbsAdapter.resolveSubject(player);
		ResourceLocation dimension = level.dimension().location();
		OrgLogisticsData data = OrgLogisticsData.get(player.getServer());
		if (data.hasFarmWarehouse(subjectId, dimension, pos)) {
			WarehouseService.removeFarm(level, subjectId, pos);
			player.displayClientMessage(Component.translatable(
					"message.refugee.staff.farm_warehouse.removed",
					pos.getX(),
					pos.getY(),
					pos.getZ()
			), true);
		} else {
			WarehouseService.addFarm(level, subjectId, pos);
			player.displayClientMessage(Component.translatable(
					"message.refugee.staff.farm_warehouse.added",
					pos.getX(),
					pos.getY(),
					pos.getZ()
			), true);
		}
		syncSubject(player.getServer(), subjectId);
		return true;
	}

	private static boolean toggleGearWarehouse(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!isContainer(level.getBlockEntity(pos))) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.warehouse.not_container"), true);
			return true;
		}
		UUID subjectId = PbsAdapter.resolveSubject(player);
		ResourceLocation dimension = level.dimension().location();
		OrgLogisticsData data = OrgLogisticsData.get(player.getServer());
		if (data.hasGearWarehouse(subjectId, dimension, pos)) {
			WarehouseService.removeGear(level, subjectId, pos);
			player.displayClientMessage(Component.translatable(
					"message.refugee.staff.gear_warehouse.removed",
					pos.getX(),
					pos.getY(),
					pos.getZ()
			), true);
		} else {
			WarehouseService.addGear(level, subjectId, pos);
			player.displayClientMessage(Component.translatable(
					"message.refugee.staff.gear_warehouse.added",
					pos.getX(),
					pos.getY(),
					pos.getZ()
			), true);
		}
		syncSubject(player.getServer(), subjectId);
		return true;
	}

	private static boolean toggleSmeltResult(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!isContainer(level.getBlockEntity(pos))) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.warehouse.not_container"), true);
			return true;
		}
		UUID subjectId = PbsAdapter.resolveSubject(player);
		ResourceLocation dimension = level.dimension().location();
		OrgLogisticsData data = OrgLogisticsData.get(player.getServer());
		if (data.hasSmeltResult(subjectId, dimension, pos)) {
			WarehouseService.removeSmeltResult(level, subjectId, pos);
			player.displayClientMessage(Component.translatable(
					"message.refugee.staff.smelt_result.removed",
					pos.getX(),
					pos.getY(),
					pos.getZ()
			), true);
		} else {
			WarehouseService.addSmeltResult(level, subjectId, pos);
			player.displayClientMessage(Component.translatable(
					"message.refugee.staff.smelt_result.added",
					pos.getX(),
					pos.getY(),
					pos.getZ()
			), true);
		}
		syncSubject(player.getServer(), subjectId);
		return true;
	}

	private static boolean toggleSmelter(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!RefugeeSmeltGoal.isMarkableFurnace(level.getBlockEntity(pos))) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.smelt.not_furnace"), true);
			return true;
		}
		UUID subjectId = PbsAdapter.resolveSubject(player);
		ResourceLocation dimension = level.dimension().location();
		OrgLogisticsData data = OrgLogisticsData.get(player.getServer());
		if (data.hasSmelter(subjectId, dimension, pos)) {
			WarehouseService.removeSmelter(level, subjectId, pos);
			player.displayClientMessage(Component.translatable(
					"message.refugee.staff.smelt.removed",
					pos.getX(),
					pos.getY(),
					pos.getZ()
			), true);
		} else {
			WarehouseService.addSmelter(level, subjectId, pos);
			player.displayClientMessage(Component.translatable(
					"message.refugee.staff.smelt.added",
					pos.getX(),
					pos.getY(),
					pos.getZ()
			), true);
		}
		syncSubject(player.getServer(), subjectId);
		return true;
	}

	private static boolean pickZoneCorner(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		StaffSession session = session(player);
		ResourceLocation dimension = level.dimension().location();
		if (session.zoneCorner() == null || !dimension.equals(session.zoneDimension())) {
			session.setZoneCorner(dimension, pos);
			sync(player);
			player.displayClientMessage(Component.translatable("message.refugee.staff.zone.corner1"), true);
			return true;
		}
		AreaBox box = AreaBox.of(session.zoneCorner(), pos);
		session.clearZoneCorner();
		int assigned = assignZone(player, dimension, box);
		if (assigned <= 0) {
			failAction(player, Component.translatable("message.refugee.staff.zone.no_workers"));
			return true;
		}
		player.displayClientMessage(Component.translatable("message.refugee.staff.zone.assigned", assigned), true);
		playSuccess(player);
		resetToRoot(player);
		return true;
	}

	private static boolean pickAdvanceCorner(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		StaffSession session = session(player);
		ResourceLocation dimension = level.dimension().location();
		if (session.zoneCorner() == null || !dimension.equals(session.zoneDimension())) {
			session.setZoneCorner(dimension, pos);
			sync(player);
			player.displayClientMessage(Component.translatable("message.refugee.staff.zone.corner1"), true);
			return true;
		}
		AreaBox box = AreaBox.of(session.zoneCorner(), pos);
		session.clearZoneCorner();
		int assigned = assignAdvance(player, dimension, box, session.advanceAxis(), session.advancePositive());
		if (assigned <= 0) {
			failAction(player, Component.translatable("message.refugee.staff.advance.no_workers"));
			return true;
		}
		player.displayClientMessage(Component.translatable("message.refugee.staff.advance.assigned", assigned), true);
		playSuccess(player);
		resetToRoot(player);
		return true;
	}

	private static int assignZone(ServerPlayer player, ResourceLocation dimension, AreaBox box) {
		return assignZoneInternal(player, dimension, box, false, Direction.Axis.Y, true);
	}

	private static int assignAdvance(
			ServerPlayer player,
			ResourceLocation dimension,
			AreaBox box,
			Direction.Axis axis,
			boolean positive
	) {
		return assignZoneInternal(player, dimension, box, true, axis, positive);
	}

	private static int assignZoneInternal(
			ServerPlayer player,
			ResourceLocation dimension,
			AreaBox box,
			boolean advance,
			Direction.Axis axis,
			boolean positive
	) {
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		WorkZone zone = advance
				? WorkZone.advance(UUID.randomUUID(), dimension, box, axis, positive)
				: new WorkZone(UUID.randomUUID(), dimension, box);
		int assigned = 0;
		List<UUID> selected = selection.snapshotSelected();
		for (UUID villagerId : selected) {
			Villager villager = commandableBuilder(level, player, villagerId);
			if (villager == null) {
				continue;
			}
			if (advance && !RefugeeRoles.isAdvanceMiner(villager)) {
				continue;
			}
			RefugeeVillagerData data = RefugeeAttachments.get(villager);
			unbindWorker(villager);
			data.stopFollowing();
			selection.removeSelected(villagerId);
			zone.addWorker(villager.getUUID());
			RefugeeAttachments.markDirty(villager, data);
			assigned++;
		}
		if (assigned > 0) {
			OrgLogisticsData.get(player.getServer()).addZone(PbsAdapter.resolveSubject(player), zone);
			RefugeeAttachments.markDirty(player, selection);
			SelectionService.collectBannersIfEmpty(player);
			syncSubject(player.getServer(), PbsAdapter.resolveSubject(player));
		}
		return assigned;
	}

	private static void assignRepairDuty(ServerPlayer player) {
		int assigned = assignDuty(player, WorkerDuty.REPAIRER);
		if (assigned <= 0) {
			failAction(player, Component.translatable("message.refugee.staff.zone.no_workers"));
			return;
		}
		player.displayClientMessage(Component.translatable("message.refugee.staff.repair.assigned", assigned), true);
		playSuccess(player);
		resetToRoot(player);
	}

	private static void assignBuildDuty(ServerPlayer player) {
		int assigned = assignDuty(player, WorkerDuty.BUILDER);
		if (assigned <= 0) {
			failAction(player, Component.translatable("message.refugee.staff.zone.no_workers"));
			return;
		}
		player.displayClientMessage(Component.translatable("message.refugee.staff.build.duty.assigned", assigned), true);
		playSuccess(player);
		resetToRoot(player);
	}

	private static void assignSmeltDuty(ServerPlayer player) {
		int assigned = assignDuty(player, WorkerDuty.SMELTER);
		if (assigned <= 0) {
			failAction(player, Component.translatable("message.refugee.staff.zone.no_workers"));
			return;
		}
		player.displayClientMessage(Component.translatable("message.refugee.staff.smelt.assigned", assigned), true);
		playSuccess(player);
		resetToRoot(player);
	}

	private static int assignDuty(ServerPlayer player, WorkerDuty duty) {
		if (!(player.level() instanceof ServerLevel level) || duty == null || duty == WorkerDuty.NONE) {
			return 0;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		int assigned = 0;
		List<UUID> selected = selection.snapshotSelected();
		for (UUID villagerId : selected) {
			Villager villager = commandableBuilder(level, player, villagerId);
			if (villager == null) {
				continue;
			}
			RefugeeVillagerData data = RefugeeAttachments.get(villager);
			unbindWorker(villager);
			data.stopFollowing();
			data.setWorkerDuty(duty);
			selection.removeSelected(villagerId);
			RefugeeAttachments.markDirty(villager, data);
			assigned++;
		}
		if (assigned > 0) {
			RefugeeAttachments.markDirty(player, selection);
			SelectionService.collectBannersIfEmpty(player);
		}
		return assigned;
	}

	private static Villager commandableBuilder(ServerLevel level, ServerPlayer player, UUID villagerId) {
		Entity entity = level.getEntity(villagerId);
		if (!(entity instanceof Villager villager) || !villager.isAlive()) {
			return null;
		}
		if (RefugeeSpecialRole.isSpecial(villager) || !RefugeeRoles.isBuilder(villager)) {
			return null;
		}
		if (!SelectionService.canCommand(player, villager)) {
			return null;
		}
		return villager;
	}

	public static void unbindWorker(Villager villager) {
		if (villager == null || villager.level().getServer() == null) {
			return;
		}
		unbindWorker(villager.getUUID(), villager.level().getServer(), RefugeeAttachments.get(villager).subjectId());
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		boolean changed = false;
		if (data.isBuilding()) {
			data.clearBuild();
			changed = true;
		}
		if (data.workerDuty().isAssigned()) {
			data.clearWorkerDuty();
			changed = true;
		}
		if (changed) {
			RefugeeAttachments.markDirty(villager, data);
		}
	}

	public static void unbindWorker(UUID villagerId, MinecraftServer server, UUID subjectHint) {
		if (villagerId == null || server == null) {
			return;
		}
		OrgLogisticsData logistics = OrgLogisticsData.get(server);
		UUID subjectId = subjectHint != null ? subjectHint : logistics.subjectOfWorker(villagerId);
		if (logistics.removeWorker(villagerId) && subjectId != null) {
			syncSubject(server, subjectId);
		}
	}

	public static void finishWorker(ServerLevel level, Villager villager, BuildJob job) {
		if (villager == null) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.clearBuild();
		RefugeeAttachments.markDirty(villager, data);
		if (job == null || level == null || level.getServer() == null) {
			return;
		}
		job.removeWorker(villager.getUUID());
		beginVerify(level, job);
	}

	public static void releaseBuilder(ServerLevel level, Villager villager, BuildJob job) {
		if (villager == null) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.clearBuild();
		RefugeeAttachments.markDirty(villager, data);
		if (job != null) {
			job.removeWorker(villager.getUUID());
		}
		if (level != null && level.getServer() != null && data.subjectId() != null) {
			OrgLogisticsData.get(level.getServer()).setDirty();
			syncSubject(level.getServer(), data.subjectId());
		}
	}

	public static void markJobVerified(ServerLevel level, BuildJob job) {
		if (level == null || level.getServer() == null || job == null) {
			return;
		}
		OrgLogisticsData logistics = OrgLogisticsData.get(level.getServer());
		UUID subjectId = logistics.subjectOfJob(job.id());
		for (UUID remaining : job.snapshotWorkers()) {
			Entity entity = level.getEntity(remaining);
			if (entity instanceof Villager other) {
				RefugeeVillagerData data = RefugeeAttachments.get(other);
				data.clearBuild();
				RefugeeAttachments.markDirty(other, data);
			}
			job.removeWorker(remaining);
		}
		job.markVerified();
		logistics.setDirty();
		if (subjectId != null) {
			syncSubject(level.getServer(), subjectId);
		}
	}

	public static void beginVerify(ServerLevel level, BuildJob job) {
		if (level == null || level.getServer() == null || job == null) {
			return;
		}
		OrgLogisticsData logistics = OrgLogisticsData.get(level.getServer());
		UUID subjectId = logistics.subjectOfJob(job.id());
		for (UUID remaining : job.snapshotWorkers()) {
			Entity entity = level.getEntity(remaining);
			if (entity instanceof Villager other) {
				RefugeeVillagerData data = RefugeeAttachments.get(other);
				data.clearBuild();
				RefugeeAttachments.markDirty(other, data);
			}
			job.removeWorker(remaining);
		}
		job.beginVerify();
		logistics.setDirty();
		if (subjectId != null) {
			syncSubject(level.getServer(), subjectId);
		}
	}

	public static void cancelJob(ServerLevel level, BuildJob job) {
		if (level == null || level.getServer() == null || job == null) {
			return;
		}
		OrgLogisticsData logistics = OrgLogisticsData.get(level.getServer());
		UUID subjectId = logistics.subjectOfJob(job.id());
		for (UUID remaining : job.snapshotWorkers()) {
			Entity entity = level.getEntity(remaining);
			if (entity instanceof Villager other) {
				RefugeeVillagerData data = RefugeeAttachments.get(other);
				data.clearBuild();
				RefugeeAttachments.markDirty(other, data);
			}
			job.removeWorker(remaining);
		}
		logistics.removeJob(job.id());
		if (subjectId != null) {
			syncSubject(level.getServer(), subjectId);
		}
	}

	public static void finishAdvance(ServerLevel level, WorkZone zone) {
		if (level == null || zone == null || level.getServer() == null) {
			return;
		}
		OrgLogisticsData logistics = OrgLogisticsData.get(level.getServer());
		UUID subjectId = null;
		BlockPos pos = null;
		for (UUID workerId : zone.snapshotWorkers()) {
			if (subjectId == null) {
				subjectId = logistics.subjectOfWorker(workerId);
			}
			Entity entity = level.getEntity(workerId);
			if (pos == null && entity instanceof Villager villager && villager.isAlive()) {
				pos = villager.blockPosition();
			}
		}
		if (pos == null) {
			pos = zone.currentSlice().center();
		}
		if (subjectId != null) {
			Component posText = Component.translatable(
					"message.refugee.roster.pos",
					pos.getX(),
					pos.getY(),
					pos.getZ()
			);
			Component message = Component.translatable("message.refugee.staff.advance.stopped", posText);
			for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
				if (subjectId.equals(player.getUUID()) || subjectId.equals(PbsAdapter.resolveSubject(player))) {
					player.sendSystemMessage(message);
				}
			}
		}
		for (UUID workerId : zone.snapshotWorkers()) {
			Entity entity = level.getEntity(workerId);
			if (entity instanceof Villager villager) {
				unbindWorker(villager);
			} else {
				unbindWorker(workerId, level.getServer(), null);
			}
		}
	}

	public static BuildJob claimBuildJob(ServerLevel level, Villager villager) {
		if (level == null || villager == null) {
			return null;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.jobId() != null) {
			BuildJob existing = OrgLogisticsData.get(level.getServer()).job(data.jobId());
			if (existing != null && existing.needsWork()) {
				return existing;
			}
			data.clearBuild();
			RefugeeAttachments.markDirty(villager, data);
			if (existing != null) {
				existing.removeWorker(villager.getUUID());
			}
		}
		UUID subjectId = data.subjectId();
		if (subjectId == null) {
			return null;
		}
		ResourceLocation dimension = level.dimension().location();
		List<BuildJob> jobs = new ArrayList<>();
		for (BuildJob job : OrgLogisticsData.get(level.getServer()).jobs(subjectId)) {
			if (dimension.equals(job.dimension()) && isUsableJob(level, job) && job.needsWork()) {
				jobs.add(job);
			}
		}
		BuildJob job;
		if (jobs.isEmpty()) {
			job = BuildHealth.pollStandingRepair(level, subjectId);
			if (job == null || !isUsableJob(level, job)) {
				return null;
			}
		} else {
			job = jobs.get(villager.getRandom().nextInt(jobs.size()));
		}
		job.addWorker(villager.getUUID());
		data.assignJob(job.id(), job.structureId(), job.origin());
		RefugeeAttachments.markDirty(villager, data);
		OrgLogisticsData.get(level.getServer()).setDirty();
		syncSubject(level.getServer(), subjectId);
		return job;
	}

	public static void leaveBuilderDuty(Villager villager) {
		if (villager == null) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (!data.isBuilderDuty()) {
			return;
		}
		if (villager.level().getServer() != null) {
			unbindWorker(villager.getUUID(), villager.level().getServer(), data.subjectId());
		}
		data.clearBuild();
		data.clearWorkerDuty();
		RefugeeAttachments.markDirty(villager, data);
	}

	public static void leaveRepairerDuty(Villager villager) {
		if (villager == null) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (!data.isRepairerDuty()) {
			return;
		}
		data.clearWorkerDuty();
		RefugeeAttachments.markDirty(villager, data);
	}

	public static boolean tryCancelBuild(ServerPlayer player) {
		if (player == null || !player.isShiftKeyDown() || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		ItemStack held = player.getMainHandItem();
		if (!(held.getItem() instanceof CommandStaffItem)) {
			held = player.getOffhandItem();
		}
		if (!(held.getItem() instanceof CommandStaffItem)) {
			return false;
		}
		if (page(player) == StaffPage.COMBAT_PATROL) {
			return false;
		}
		BuildJob hit = raycastBuild(player, level);
		if (hit == null) {
			return false;
		}
		UUID subjectId = OrgLogisticsData.get(level.getServer()).subjectOfJob(hit.id());
		cancelJob(level, hit);
		player.displayClientMessage(Component.translatable("message.refugee.staff.build.cancelled"), true);
		playSuccess(player);
		if (subjectId != null) {
			syncSubject(level.getServer(), subjectId);
		}
		return true;
	}

	public static boolean tryCancelZone(ServerPlayer player) {
		if (player == null || !player.isShiftKeyDown() || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		ItemStack held = player.getMainHandItem();
		if (!(held.getItem() instanceof CommandStaffItem)) {
			held = player.getOffhandItem();
		}
		if (!(held.getItem() instanceof CommandStaffItem)) {
			return false;
		}
		if (page(player) == StaffPage.COMBAT_PATROL) {
			return false;
		}
		WorkZone hit = raycastZone(player, level);
		if (hit == null) {
			return false;
		}
		UUID subjectId = OrgLogisticsData.get(level.getServer()).subjectOfZone(hit.id());
		cancelZone(level, hit);
		player.displayClientMessage(Component.translatable("message.refugee.staff.zone.cancelled"), true);
		playSuccess(player);
		if (subjectId != null) {
			syncSubject(level.getServer(), subjectId);
		}
		return true;
	}

	public static void cancelZone(ServerLevel level, WorkZone zone) {
		if (level == null || zone == null || level.getServer() == null) {
			return;
		}
		OrgLogisticsData logistics = OrgLogisticsData.get(level.getServer());
		UUID subjectId = logistics.subjectOfZone(zone.id());
		for (UUID workerId : zone.snapshotWorkers()) {
			Entity entity = level.getEntity(workerId);
			if (entity instanceof Villager villager) {
				unbindWorker(villager);
			} else {
				unbindWorker(workerId, level.getServer(), subjectId);
			}
		}
		logistics.removeZone(zone.id());
		if (subjectId != null) {
			syncSubject(level.getServer(), subjectId);
		}
	}

	private static WorkZone raycastZone(ServerPlayer player, ServerLevel level) {
		UUID subjectId = PbsAdapter.resolveSubject(player);
		Vec3 start = player.getEyePosition();
		double range = Math.max(32.0, player.blockInteractionRange() * 4.0);
		Vec3 end = start.add(player.getLookAngle().scale(range));
		WorkZone best = null;
		double bestDist = Double.MAX_VALUE;
		for (WorkZone zone : OrgLogisticsData.get(level.getServer()).zones(subjectId)) {
			if (!level.dimension().location().equals(zone.dimension())) {
				continue;
			}
			AABB box = (zone.isAdvance() ? zone.currentSlice() : zone.box()).aabb();
			double dist;
			if (box.contains(start)) {
				dist = 0.0;
			} else {
				var clip = box.clip(start, end);
				if (clip.isEmpty()) {
					continue;
				}
				dist = clip.get().distanceToSqr(start);
			}
			if (dist < bestDist) {
				bestDist = dist;
				best = zone;
			}
		}
		return best;
	}

	private static BuildJob raycastBuild(ServerPlayer player, ServerLevel level) {
		UUID subjectId = PbsAdapter.resolveSubject(player);
		Vec3 start = player.getEyePosition();
		double range = Math.max(32.0, player.blockInteractionRange() * 4.0);
		Vec3 end = start.add(player.getLookAngle().scale(range));
		BuildJob best = null;
		double bestDist = Double.MAX_VALUE;
		for (BuildJob job : OrgLogisticsData.get(level.getServer()).jobs(subjectId)) {
			if (!level.dimension().location().equals(job.dimension())) {
				continue;
			}
			AABB box = job.bounds().aabb();
			var clip = box.clip(start, end);
			if (clip.isEmpty()) {
				continue;
			}
			double dist = clip.get().distanceToSqr(start);
			if (dist < bestDist) {
				bestDist = dist;
				best = job;
			}
		}
		return best;
	}

	private static boolean isUsableJob(ServerLevel level, BuildJob job) {
		if (job == null || job.structureId() == null) {
			return false;
		}
		return BlueprintRegistry.get(job.structureId()) != null
				|| level.getServer().getStructureManager().get(job.structureId()).isPresent();
	}

	public static void onVillagerGone(UUID villagerId, MinecraftServer server) {
		if (villagerId == null || server == null) {
			return;
		}
		unbindWorker(villagerId, server, null);
	}

	public static BuildJob migrateLegacy(ServerLevel level, Villager villager, RefugeeVillagerData data) {
		if (level == null || villager == null || data == null) {
			return null;
		}
		if (data.jobId() != null) {
			return OrgLogisticsData.get(level.getServer()).job(data.jobId());
		}
		if (data.structureId() == null || data.buildOrigin() == null) {
			return null;
		}
		UUID subjectId = data.subjectId();
		if (subjectId == null) {
			return null;
		}
		BuildJob job = new BuildJob(
				UUID.randomUUID(),
				level.dimension().location(),
				data.structureId(),
				data.buildOrigin(),
				0,
				0,
				0,
				Rotation.NONE
		);
		job.setNextIndex(data.buildIndex());
		job.addWorker(villager.getUUID());
		data.assignJob(job.id(), data.structureId(), data.buildOrigin());
		data.setBuildIndex(job.nextIndex());
		RefugeeAttachments.markDirty(villager, data);
		OrgLogisticsData.get(level.getServer()).addJob(subjectId, job);
		syncSubject(level.getServer(), subjectId);
		return job;
	}

	public static void sync(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		UUID subjectId = PbsAdapter.resolveSubject(player);
		ResourceLocation dimension = level.dimension().location();
		OrgLogisticsData data = OrgLogisticsData.get(player.getServer());
		List<BlockPos> chests = posInDimension(data.warehouses(subjectId), dimension);
		List<BlockPos> foodChests = posInDimension(data.foodWarehouses(subjectId), dimension);
		List<BlockPos> farmChests = posInDimension(data.farmWarehouses(subjectId), dimension);
		List<BlockPos> gearChests = posInDimension(data.gearWarehouses(subjectId), dimension);
		List<BlockPos> resultChests = posInDimension(data.smeltResults(subjectId), dimension);
		List<BlockPos> furnaces = posInDimension(data.smelters(subjectId), dimension);
		List<AreaBox> zones = new ArrayList<>();
		for (WorkZone zone : data.zones(subjectId)) {
			if (dimension.equals(zone.dimension())) {
				zones.add(zone.isAdvance() ? zone.currentSlice() : zone.box());
			}
		}
		List<AreaBox> builds = new ArrayList<>();
		for (BuildJob job : data.jobs(subjectId)) {
			if (dimension.equals(job.dimension()) && job.showsOverlay()) {
				builds.add(job.bounds());
			}
		}
		RefugeeNetworking.syncStaff(
				player,
				mode(player),
				page(player),
				chests,
				foodChests,
				furnaces,
				zones,
				builds,
				session(player).zoneCorner(),
				session(player).pendingImport(),
				session(player).patrolPoints(),
				farmChests,
				gearChests,
				resultChests
		);
	}

	private static List<BlockPos> posInDimension(List<ContainerRef> refs, ResourceLocation dimension) {
		List<BlockPos> result = new ArrayList<>();
		for (ContainerRef ref : refs) {
			if (dimension.equals(ref.dimension())) {
				result.add(ref.pos());
			}
		}
		return result;
	}

	public static void syncSubject(MinecraftServer server, UUID subjectId) {
		if (server == null || subjectId == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (subjectId.equals(PbsAdapter.resolveSubject(player))) {
				sync(player);
			}
		}
	}

	public static void onLogout(UUID playerId) {
		SESSIONS.remove(playerId);
	}

	private static boolean isValidStructure(ServerPlayer player, ResourceLocation structureId) {
		if (!(player.level() instanceof ServerLevel level) || structureId == null) {
			return false;
		}
		return BlueprintRegistry.visibleTo(player, structureId)
				|| level.getServer().getStructureManager().get(structureId).isPresent();
	}

	private static void failAction(ServerPlayer player, Component message) {
		player.displayClientMessage(message, true);
		playFail(player);
		resetToRoot(player);
	}

	private static void playSuccess(ServerPlayer player) {
		player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	private static void playFail(ServerPlayer player) {
		player.playNotifySound(SoundEvents.GENERIC_BURN, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	private static boolean isContainer(BlockEntity entity) {
		return entity instanceof BaseContainerBlockEntity && !(entity instanceof AltarBlockEntity);
	}

	private static void enterFollowEntity(ServerPlayer player) {
		if (selectedCount(player) <= 0) {
			failAction(player, Component.translatable("message.refugee.staff.combat.none"));
			return;
		}
		enter(player, StaffPage.COMBAT_PIE, StaffPage.COMBAT_FOLLOW);
	}

	private static void enterPatrol(ServerPlayer player) {
		if (selectedCount(player) <= 0) {
			failAction(player, Component.translatable("message.refugee.staff.combat.none"));
			return;
		}
		enter(player, StaffPage.COMBAT_PIE, StaffPage.COMBAT_PATROL);
	}

	private static void applyEquipGear(ServerPlayer player) {
		if (selectedCount(player) <= 0) {
			failAction(player, Component.translatable("message.refugee.staff.combat.none"));
			return;
		}
		int count = GearDispatch.dispatch(player);
		if (count <= 0) {
			failAction(player, Component.translatable("message.refugee.staff.equip.empty"));
			return;
		}
		player.displayClientMessage(Component.translatable("message.refugee.staff.equip.done", count), true);
		playSuccess(player);
		resetToRoot(player);
	}

	private static void applyFormation(ServerPlayer player) {
		int count = SelectionService.deselectAll(player);
		if (count <= 0) {
			failAction(player, Component.translatable("message.refugee.staff.combat.none"));
			return;
		}
		player.displayClientMessage(Component.translatable("message.refugee.staff.formation.done", count), true);
		playSuccess(player);
		resetToRoot(player);
	}

	private static void applyRally(ServerPlayer player, Predicate<Villager> filter) {
		int count = SelectionService.selectAround(player, RefugeeConfig.hornBellRadius, filter);
		if (count <= 0) {
			failAction(player, Component.translatable("message.refugee.staff.rally.none"));
			return;
		}
		playRallyHorn(player);
		resetToRoot(player);
	}

	private static void applyGuardAdd(ServerPlayer player) {
		int count = GuardService.addFromFollowing(player);
		if (count < 0) {
			failAction(player, Component.translatable("message.refugee.staff.guard.none_follow"));
			return;
		}
		if (count == 0) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.guard.already"), true);
		} else {
			player.displayClientMessage(Component.translatable("message.refugee.staff.guard.added", count), true);
		}
		playSuccess(player);
		resetToRoot(player);
	}

	private static void applyGuardRemove(ServerPlayer player) {
		int count = GuardService.removeFromFollowing(player);
		if (count < 0) {
			failAction(player, Component.translatable("message.refugee.staff.guard.none_follow"));
			return;
		}
		if (count == 0) {
			failAction(player, Component.translatable("message.refugee.staff.guard.remove.none"));
			return;
		}
		player.displayClientMessage(Component.translatable("message.refugee.staff.guard.removed", count), true);
		playSuccess(player);
		resetToRoot(player);
	}

	private static void applyGuardRallyNear(ServerPlayer player) {
		int count = GuardService.rallyNear(player);
		if (count <= 0) {
			failAction(player, Component.translatable("message.refugee.staff.guard.near.none"));
			return;
		}
		player.displayClientMessage(Component.translatable("message.refugee.staff.guard.near.done", count), true);
		playRallyHorn(player);
		resetToRoot(player);
	}

	private static void applyGuardRallyAll(ServerPlayer player) {
		GuardService.RallyAllStart start = GuardService.rallyAll(player);
		if (!start.hasWork()) {
			failAction(player, Component.translatable("message.refugee.staff.guard.all.empty"));
			return;
		}
		if (start.pending() > 0) {
			player.displayClientMessage(Component.translatable(
					"message.refugee.staff.guard.all.loading",
					start.immediate(),
					start.pending()
			), true);
		} else {
			player.displayClientMessage(Component.translatable("message.refugee.staff.guard.all.done", start.immediate()), true);
		}
		playRallyHorn(player);
		resetToRoot(player);
	}

	/** 原版 Call 号角：volume = 256/16。except 为 null，持杖玩家也能听到。 */
	private static void playRallyHorn(ServerPlayer player) {
		player.level().playSound(
				null,
				player.getX(),
				player.getY(),
				player.getZ(),
				SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(5).value(),
				SoundSource.RECORDS,
				16.0F,
				1.0F
		);
		player.level().gameEvent(GameEvent.INSTRUMENT_PLAY, player.position(), GameEvent.Context.of(player));
	}

	private static boolean addPatrolPoint(ServerPlayer player, BlockPos pos) {
		if (player.isShiftKeyDown()) {
			completePatrol(player);
			return true;
		}
		StaffSession session = session(player);
		if (!session.addPatrolPoint(pos)) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.patrol.duplicate"), true);
			return true;
		}
		sync(player);
		player.displayClientMessage(Component.translatable(
				"message.refugee.staff.patrol.added",
				session.patrolPoints().size(),
				pos.getX(),
				pos.getY(),
				pos.getZ()
		), true);
		return true;
	}

	private static void completePatrol(ServerPlayer player) {
		StaffSession session = session(player);
		List<BlockPos> points = session.patrolPoints();
		if (points.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.patrol.empty"), true);
			return;
		}
		int assigned = assignPatrol(player, points);
		if (assigned <= 0) {
			failAction(player, Component.translatable("message.refugee.staff.combat.none"));
			return;
		}
		player.displayClientMessage(Component.translatable(
				"message.refugee.staff.patrol.assigned",
				assigned,
				points.size()
		), true);
		playSuccess(player);
		resetToRoot(player);
	}

	private static int selectedCount(ServerPlayer player) {
		return RefugeeAttachments.get(player).snapshotSelected().size();
	}

	private static FollowAssign assignFollowEntity(ServerPlayer player, LivingEntity target) {
		if (!(player.level() instanceof ServerLevel level) || target == null || !target.isAlive()) {
			return FollowAssign.EMPTY;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		List<UUID> selected = selection.snapshotSelected();
		int followed = 0;
		int cancelled = 0;
		for (UUID villagerId : selected) {
			Entity entity = level.getEntity(villagerId);
			if (!(entity instanceof Villager villager) || !villager.isAlive()) {
				continue;
			}
			if (!SelectionService.canCommand(player, villager)) {
				continue;
			}
			RefugeeVillagerData data = RefugeeAttachments.get(villager);
			unbindWorker(villager);
			if (villager == target) {
				if (RefugeeRoles.isGuard(villager)) {
					data.stopFollowing(villager.blockPosition());
				} else {
					data.stopFollowing();
				}
				cancelled++;
			} else {
				data.startFollowingEntity(target.getUUID());
				followed++;
			}
			RefugeeAttachments.markDirty(villager, data);
		}
		if (followed + cancelled > 0) {
			SelectionService.dropFromSelection(player, selected);
		}
		Refugee.LOGGER.debug(
				"[refugee staff follow] target={} id={} selected={} followed={} cancelled={}",
				target.getType().toShortString(),
				target.getUUID().toString().substring(0, 8),
				selected.size(),
				followed,
				cancelled
		);
		return new FollowAssign(followed, cancelled);
	}

	private static int assignPatrol(ServerPlayer player, List<BlockPos> points) {
		if (!(player.level() instanceof ServerLevel level) || points == null || points.isEmpty()) {
			return 0;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		List<UUID> selected = selection.snapshotSelected();
		int assigned = 0;
		for (UUID villagerId : selected) {
			Entity entity = level.getEntity(villagerId);
			if (!(entity instanceof Villager villager) || !villager.isAlive()) {
				continue;
			}
			if (!SelectionService.canCommand(player, villager)) {
				continue;
			}
			RefugeeVillagerData data = RefugeeAttachments.get(villager);
			unbindWorker(villager);
			data.startPatrol(points);
			RefugeeAttachments.markDirty(villager, data);
			assigned++;
		}
		if (assigned > 0) {
			SelectionService.dropFromSelection(player, selected);
		}
		return assigned;
	}

	private record FollowAssign(int followed, int cancelled) {
		private static final FollowAssign EMPTY = new FollowAssign(0, 0);
	}
}
