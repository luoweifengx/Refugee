package luowei.refugee.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.Refugee;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.interact.RefugeeRoles;

public class RefugeeFollowGoal extends Goal {
	public static final double FOLLOW_STAY_DISTANCE = 2.5;
	private static final int LOG_INTERVAL = 20;

	private final Villager villager;
	private int moveToCount;
	private int stopCount;
	private int lastLogTick;

	public RefugeeFollowGoal(Villager villager) {
		this.villager = villager;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return RefugeeAttachments.get(villager).isFollowing()
				&& !villager.isBaby()
				&& !RefugeeRoles.isGuard(villager)
				&& !RefugeeCombat.isBusy(villager);
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void tick() {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (!(villager.level().getPlayerByUUID(data.followPlayerId()) instanceof ServerPlayer player) || !player.isAlive()) {
			return;
		}
		villager.getLookControl().setLookAt(player, 10.0f, villager.getMaxHeadXRot());
		double dist = villager.distanceTo(player);
		boolean walking = dist > FOLLOW_STAY_DISTANCE;
		if (walking) {
			moveToCount++;
			villager.getNavigation().moveTo(player, RefugeeConfig.followSpeed);
		} else {
			stopCount++;
			villager.getNavigation().stop();
		}
		logFollow(player, dist, walking);
	}

	private void logFollow(ServerPlayer player, double dist, boolean walking) {
		if (!Refugee.LOGGER.isDebugEnabled()) {
			return;
		}
		if (villager.tickCount - lastLogTick < LOG_INTERVAL) {
			return;
		}
		lastLogTick = villager.tickCount;
		Refugee.LOGGER.debug(
				"[refugee follow] id={} pos={} player={} dist={} walking={} navDone={} moveTo={} stop={} interval={}",
				villager.getUUID().toString().substring(0, 8),
				villager.blockPosition().toShortString(),
				player.getGameProfile().getName(),
				String.format("%.2f", dist),
				walking,
				villager.getNavigation().isDone(),
				moveToCount,
				stopCount,
				LOG_INTERVAL
		);
		moveToCount = 0;
		stopCount = 0;
	}
}
