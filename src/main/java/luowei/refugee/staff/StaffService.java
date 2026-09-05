package luowei.refugee.staff;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import luowei.refugee.attachment.PlayerSelectionData;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.blueprint.BlueprintRegistry;
import luowei.refugee.blueprint.PlayerBlueprints;
import luowei.refugee.build.BuildJob;
import luowei.refugee.config.RefugeeConfig;
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

/**
 * 指挥杖服务端会话、页面树、仓库选箱、工作区分配与建筑任务。
 */
public final class StaffService {
	private static final Map<UUID, StaffSession> SESSIONS = new ConcurrentHashMap<>();

	private StaffService() {
	}

	public static void register() {
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
		}
	}

	private static void showEnterHint(ServerPlayer player, StaffPage page) {
		if (page == StaffPage.WAREHOUSE) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.mode.warehouse"), true);
		} else if (page == StaffPage.ZONE) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.mode.zone"), true);
		} else if (page == StaffPage.IMPORT) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.mode.import"), true);
		} else if (page == StaffPage.BUILD_PREVIEW) {
			player.displayClientMessage(Component.translatable("message.refugee.staff.mode.build"), true);
		}
	}

	public static void onAirUse(ServerPlayer player) {
		StaffSession session = session(player);
		if (session.isRoot()) {
			session.setPages(StaffPage.PIE);
			sync(player);
			RefugeeNetworking.openStaffPie(player);
		}
	}

	public static void handlePie(ServerPlayer player, StaffPieAction action) {
		if (action == null) {
			return;
		}
		switch (action) {
			case WAREHOUSE -> enter(player, StaffPage.WAREHOUSE);
			case ZONE -> enter(player, StaffPage.ZONE);
			case IMPORT -> enter(player, StaffPage.IMPORT);
			case SELECT -> {
				session(player).setPages(StaffPage.BUILD_CATALOG);
				sync(player);
				RefugeeNetworking.openSelector(player, InteractionHand.MAIN_HAND);
			}
		}
	}

	public static void enterPreview(ServerPlayer player, ResourceLocation structureId) {
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		selection.setStructureId(structureId);
		selection.setBuildOrigin(null);
		RefugeeAttachments.markDirty(player, selection);
		session(player).setPages(StaffPage.BUILD_CATALOG, StaffPage.BUILD_PREVIEW);
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
				RefugeeNetworking.applyImported(player, result.id());
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
		StaffSession session = session(player);
		return switch (session.page()) {
			case WAREHOUSE -> toggleWarehouse(player, pos);
			case ZONE -> pickZoneCorner(player, pos);
			case BUILD_PREVIEW -> true;
			case IMPORT -> pickImportCorner(player, pos);
			default -> false;
		};
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
		int assigned = assignBuild(
				player,
				selection.structureId(),
				origin,
				offsetX,
				offsetY,
				offsetZ,
				rotation == null ? Rotation.NONE : rotation
		);
		if (assigned <= 0) {
			failAction(player, Component.translatable("message.refugee.staff.build.no_workers"));
			return;
		}
		player.displayClientMessage(Component.translatable("message.refugee.staff.build.assigned", assigned), true);
		playSuccess(player);
		resetToRoot(player);
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
		session.setPages(StaffPage.IMPORT, StaffPage.IMPORT_NAME);
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

	private static int assignZone(ServerPlayer player, ResourceLocation dimension, AreaBox box) {
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		WorkZone zone = new WorkZone(UUID.randomUUID(), dimension, box);
		int assigned = 0;
		List<UUID> selected = selection.snapshotSelected();
		for (UUID villagerId : selected) {
			Entity entity = level.getEntity(villagerId);
			if (!(entity instanceof Villager villager) || !villager.isAlive()) {
				continue;
			}
			if (RefugeeSpecialRole.isSpecial(villager) || !RefugeeRoles.isBuilder(villager)) {
				continue;
			}
			if (!SelectionService.canCommand(player, villager)) {
				continue;
			}
			RefugeeVillagerData data = RefugeeAttachments.get(villager);
			if (data.isBuilding()) {
				continue;
			}
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

	private static int assignBuild(
			ServerPlayer player,
			ResourceLocation structureId,
			BlockPos origin,
			int offsetX,
			int offsetY,
			int offsetZ,
			Rotation rotation
	) {
		if (!(player.level() instanceof ServerLevel level) || structureId == null || origin == null) {
			return 0;
		}
		if (BlueprintRegistry.get(structureId) == null && level.getServer().getStructureManager().get(structureId).isEmpty()) {
			return 0;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		UUID subjectId = PbsAdapter.resolveSubject(player);
		ResourceLocation dimension = level.dimension().location();
		BuildJob job = new BuildJob(
				UUID.randomUUID(),
				dimension,
				structureId,
				origin,
				offsetX,
				offsetY,
				offsetZ,
				rotation
		);
		int assigned = 0;
		for (UUID villagerId : selection.snapshotSelected()) {
			Entity entity = level.getEntity(villagerId);
			if (!(entity instanceof Villager villager) || !villager.isAlive()) {
				continue;
			}
			if (RefugeeSpecialRole.isSpecial(villager) || !RefugeeRoles.isBuilder(villager)) {
				continue;
			}
			if (!SelectionService.canCommand(player, villager)) {
				continue;
			}
			RefugeeVillagerData data = RefugeeAttachments.get(villager);
			if (data.isBuilding()) {
				continue;
			}
			unbindWorker(villager);
			data.stopFollowing();
			data.assignJob(job.id(), structureId, origin);
			selection.removeSelected(villagerId);
			job.addWorker(villager.getUUID());
			RefugeeAttachments.markDirty(villager, data);
			assigned++;
		}
		if (assigned > 0) {
			OrgLogisticsData.get(player.getServer()).addJob(subjectId, job);
			selection.setBuildOrigin(null);
			RefugeeAttachments.markDirty(player, selection);
			SelectionService.collectBannersIfEmpty(player);
			RefugeeNetworking.syncSelection(player);
			syncSubject(player.getServer(), subjectId);
		}
		return assigned;
	}

	public static void unbindWorker(Villager villager) {
		if (villager == null || villager.level().getServer() == null) {
			return;
		}
		unbindWorker(villager.getUUID(), villager.level().getServer(), RefugeeAttachments.get(villager).subjectId());
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.isBuilding()) {
			data.clearBuild();
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
		completeJobIfDone(level, job, true);
	}

	public static void completeJobIfDone(ServerLevel level, BuildJob job, boolean cursorFinished) {
		if (level == null || level.getServer() == null || job == null) {
			return;
		}
		OrgLogisticsData logistics = OrgLogisticsData.get(level.getServer());
		UUID subjectId = logistics.subjectOfJob(job.id());
		if (cursorFinished) {
			for (UUID remaining : job.snapshotWorkers()) {
				Entity entity = level.getEntity(remaining);
				if (entity instanceof Villager other) {
					RefugeeVillagerData data = RefugeeAttachments.get(other);
					data.clearBuild();
					RefugeeAttachments.markDirty(other, data);
				}
				job.removeWorker(remaining);
			}
		}
		if (job.isEmpty() || cursorFinished) {
			logistics.removeJob(job.id());
			if (subjectId != null) {
				syncSubject(level.getServer(), subjectId);
			}
			return;
		}
		logistics.setDirty();
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
		List<BlockPos> chests = new ArrayList<>();
		for (ContainerRef ref : data.warehouses(subjectId)) {
			if (dimension.equals(ref.dimension())) {
				chests.add(ref.pos());
			}
		}
		List<AreaBox> zones = new ArrayList<>();
		for (WorkZone zone : data.zones(subjectId)) {
			if (dimension.equals(zone.dimension())) {
				zones.add(zone.box());
			}
		}
		List<AreaBox> builds = new ArrayList<>();
		for (BuildJob job : data.jobs(subjectId)) {
			if (dimension.equals(job.dimension())) {
				builds.add(job.bounds());
			}
		}
		RefugeeNetworking.syncStaff(
				player,
				mode(player),
				page(player),
				chests,
				zones,
				builds,
				session(player).zoneCorner(),
				session(player).pendingImport()
		);
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
		return BlueprintRegistry.get(structureId) != null
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
		return entity instanceof BaseContainerBlockEntity;
	}
}
