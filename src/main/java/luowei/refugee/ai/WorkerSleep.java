package luowei.refugee.ai;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.logistics.OrgLogisticsData;

/**
 * 工人空闲时交给原版 Brain。晚上有空床就让 Brain 去睡；没有床，或一段时间仍没睡着，就继续干活。
 */
public final class WorkerSleep {
	public static final int REST_START = 12000;
	public static final int BED_SEARCH = 48;
	public static final int ATTEMPT_TICKS = 600;

	private WorkerSleep() {
	}

	public static boolean yields(Villager villager) {
		if (villager == null || villager.isBaby() || !RefugeeRoles.isBuilder(villager)) {
			return false;
		}
		if (!(villager.level() instanceof ServerLevel level)) {
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (!isNight(level)) {
			data.clearSleepAttempt();
			return false;
		}
		if (villager.isSleeping()) {
			return true;
		}
		if (!hasBed(level, villager)) {
			data.clearSleepAttempt();
			return false;
		}
		return data.continueSleepAttempt(level.getGameTime(), ATTEMPT_TICKS);
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

	private static boolean isNight(ServerLevel level) {
		return Math.floorMod(level.getDayTime(), 24000L) >= REST_START;
	}

	private static boolean hasBed(ServerLevel level, Villager villager) {
		if (villager.getBrain().hasMemoryValue(MemoryModuleType.HOME)) {
			return true;
		}
		Optional<BlockPos> bed = level.getPoiManager().findClosest(
				poi -> poi.is(PoiTypes.HOME),
				pos -> freeBed(level, pos),
				villager.blockPosition(),
				BED_SEARCH,
				PoiManager.Occupancy.ANY
		);
		return bed.isPresent();
	}

	private static boolean freeBed(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof BedBlock && !state.getValue(BedBlock.OCCUPIED);
	}
}
