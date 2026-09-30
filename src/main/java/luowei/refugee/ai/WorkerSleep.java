package luowei.refugee.ai;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.logistics.OrgLogisticsData;
import luowei.refugee.special.ClinicService;
import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 晚上整段时间里，有能认的床就去睡。
 * 守卫和还挂着活的工人让出大脑；散人和完全没活的人本来就整晚跑睡觉日程。
 */
public final class WorkerSleep {
	public static final int REST_START = 12000;
	public static final int BED_SEARCH = 48;
	private static final int BED_SCAN_INTERVAL = 5;

	private WorkerSleep() {
	}

	public static boolean yields(Villager villager) {
		if (villager == null || villager.isBaby() || !RefugeeRoles.isBuilder(villager)) {
			return false;
		}
		if (!(villager.level() instanceof ServerLevel level) || blocked(villager)) {
			return false;
		}
		return tryRest(villager, level);
	}

	/**
	 * 士兵等没有空闲大脑的人。跟随和打架时不找床。
	 * 散人和特殊居民本来就整晚跑睡觉日程，这里不再让一次。
	 */
	public static boolean seeksBed(Villager villager) {
		if (villager == null || villager.isBaby() || RefugeeRoles.isBuilder(villager)) {
			return false;
		}
		if (RefugeeRoles.matchesRallyCivilian(villager) || RefugeeSpecialRole.isSpecial(villager)) {
			return false;
		}
		if (!(villager.level() instanceof ServerLevel level) || blocked(villager)) {
			return false;
		}
		return tryRest(villager, level);
	}

	public static boolean isWorking(Villager villager) {
		if (villager == null || villager.level().getServer() == null) {
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.isFollowing() || data.isFollowingEntity() || data.isPatrolling()
				|| data.isBuilding() || data.workerDuty().isAssigned()
				|| data.combatMood().isBusy()) {
			return true;
		}
		return OrgLogisticsData.get(villager.level().getServer()).zoneOfWorker(villager.getUUID()) != null;
	}

	private static boolean blocked(Villager villager) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.isHostileFaction() || data.isFollowing() || data.isFollowingEntity()
				|| data.combatMood().isBusy()
				|| ClinicService.hasOrder(villager)) {
			return true;
		}
		return RefugeeRoles.isGuard(villager) && RefugeeCombat.hasHostilesInGuardRadius(villager);
	}

	/** 从 12000 刻到第二天早上第 10 刻。已经躺下不再搜床。 */
	private static boolean tryRest(Villager villager, ServerLevel level) {
		long time = Math.floorMod(level.getDayTime(), 24000L);
		if (time >= 10L && time < REST_START) {
			return false;
		}
		if (villager.isSleeping()) {
			return true;
		}
		return hasBedCached(level, villager);
	}

	private static boolean hasBedCached(ServerLevel level, Villager villager) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		long now = level.getGameTime();
		if (!data.bedScanDue(now, BED_SCAN_INTERVAL)) {
			return data.bedScanFound();
		}
		boolean found = hasBed(level, villager);
		data.noteBedScan(now, found);
		return found;
	}

	private static boolean hasBed(ServerLevel level, Villager villager) {
		Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
		if (home.isPresent()
				&& home.get().dimension().equals(level.dimension())
				&& BedClaim.keeps(level, villager, home.get().pos())) {
			return true;
		}
		Optional<BlockPos> bed = level.getPoiManager().findClosest(
				poi -> poi.is(PoiTypes.HOME),
				pos -> BedClaim.canTake(level, villager, pos),
				villager.blockPosition(),
				BED_SEARCH,
				PoiManager.Occupancy.HAS_SPACE
		);
		return bed.isPresent();
	}
}
