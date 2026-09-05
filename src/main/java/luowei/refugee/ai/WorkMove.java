package luowei.refugee.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.Refugee;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.settle.StandableFinder;

/**
 * 干活够着与走近：到达只看工作距离，不用导航 {@code isDone}。
 */
public final class WorkMove {
	/** 4 格欧氏，distanceToSqr。 */
	public static final double REACH_SQ = 16.0;
	private static final int LOG_INTERVAL = 20;

	private WorkMove() {
	}

	public static boolean inReach(Entity entity, BlockPos pos) {
		return entity.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH_SQ;
	}

	/** 朝工作格 {@code moveTo}，让村民看起来在靠近。 */
	public static void moveToward(Villager villager, BlockPos workAt) {
		if (villager == null || workAt == null) {
			return;
		}
		villager.getNavigation().moveTo(
				workAt.getX() + 0.5,
				workAt.getY() + 0.5,
				workAt.getZ() + 0.5,
				RefugeeConfig.builderWalkSpeed
		);
	}

	/** 走到能摸到 {@code workAt} 的可站格。 */
	public static void goToWork(Villager villager, ServerLevel level, BlockPos workAt) {
		long started = debugNanos();
		BlockPos dest = StandableFinder.findStandNear(level, workAt, villager.blockPosition(), REACH_SQ);
		long findNs = elapsedNanos(started);
		if (dest == null) {
			var nav = villager.getNavigation();
			if (!nav.isDone()) {
				logThrottled(villager, "goToWork", workAt, "hold dest=none navDone=false findNs=" + findNs);
				return;
			}
			boolean moved = nav.moveTo(
					workAt.getX() + 0.5,
					workAt.getY() + 0.5,
					workAt.getZ() + 0.5,
					RefugeeConfig.builderWalkSpeed
			);
			logMove(villager, "goToWork-fallback", workAt, moved, findNs);
			return;
		}
		logThrottled(villager, "goToWork", workAt, "dest=" + dest.toShortString() + " findNs=" + findNs);
		goTo(villager, level, dest);
	}

	/** 走到指定可站格。 */
	public static void goTo(Villager villager, ServerLevel level, BlockPos dest) {
		if (dest == null) {
			return;
		}
		double y = StandableFinder.standY(level, dest);
		double x = dest.getX() + 0.5;
		double z = dest.getZ() + 0.5;
		double distSq = villager.distanceToSqr(x, y, z);
		var nav = villager.getNavigation();
		if (!nav.isDone()) {
			logThrottled(villager, "goTo", dest, "hold navDone=false distSq=" + String.format("%.2f", distSq));
			return;
		}
		boolean moved = nav.moveTo(x, y, z, RefugeeConfig.builderWalkSpeed);
		logMove(villager, "goTo", dest, moved, -1L);
	}

	private static long debugNanos() {
		return Refugee.LOGGER.isDebugEnabled() ? System.nanoTime() : 0L;
	}

	private static long elapsedNanos(long started) {
		return started == 0L ? 0L : System.nanoTime() - started;
	}

	private static void logMove(Villager villager, String action, BlockPos dest, boolean moved, long findNs) {
		if (!Refugee.LOGGER.isDebugEnabled()) {
			return;
		}
		Refugee.LOGGER.debug(
				"[refugee work-move] {} id={} pos={} dest={} moved={} navDone={} findNs={}",
				action,
				id(villager),
				villager.blockPosition().toShortString(),
				dest == null ? "none" : dest.toShortString(),
				moved,
				villager.getNavigation().isDone(),
				findNs
		);
	}

	private static void logThrottled(Villager villager, String action, BlockPos dest, String extra) {
		if (!Refugee.LOGGER.isDebugEnabled() || villager.tickCount % LOG_INTERVAL != 0) {
			return;
		}
		Refugee.LOGGER.debug(
				"[refugee work-move] {} id={} pos={} dest={} {}",
				action,
				id(villager),
				villager.blockPosition().toShortString(),
				dest == null ? "none" : dest.toShortString(),
				extra
		);
	}

	private static String id(Villager villager) {
		return villager.getUUID().toString().substring(0, 8);
	}
}
