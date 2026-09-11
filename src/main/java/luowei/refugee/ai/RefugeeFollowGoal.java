package luowei.refugee.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		return (data.isFollowing() || data.isFollowingEntity())
				&& !villager.isBaby()
				&& !RefugeeRoles.isGuard(villager)
				&& !RefugeeCombat.isBusy(villager)
				&& !RefugeeCombat.isEating(villager);
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void tick() {
		RefugeeSwim.tick(villager);
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		LivingEntity target = resolveTarget(data);
		if (target == null) {
			return;
		}
		villager.getLookControl().setLookAt(target, 10.0f, villager.getMaxHeadXRot());
		double dist = villager.distanceTo(target);
		boolean walking = dist > FOLLOW_STAY_DISTANCE;
		if (walking) {
			moveToCount++;
			villager.getNavigation().moveTo(target, RefugeeConfig.followSpeed);
		} else {
			stopCount++;
			villager.getNavigation().stop();
		}
		logFollow(target, dist, walking);
	}

	private LivingEntity resolveTarget(RefugeeVillagerData data) {
		if (data.isFollowing()) {
			if (villager.level().getPlayerByUUID(data.followPlayerId()) instanceof ServerPlayer player && player.isAlive()) {
				return player;
			}
			return null;
		}
		if (data.isFollowingEntity() && villager.level() instanceof net.minecraft.server.level.ServerLevel level) {
			Entity entity = level.getEntity(data.followEntityId());
			if (entity instanceof LivingEntity living && living.isAlive() && living != villager) {
				return living;
			}
		}
		return null;
	}

	private void logFollow(LivingEntity target, double dist, boolean walking) {
		if (!Refugee.LOGGER.isDebugEnabled()) {
			return;
		}
		if (villager.tickCount - lastLogTick < LOG_INTERVAL) {
			return;
		}
		lastLogTick = villager.tickCount;
		String targetName = target instanceof ServerPlayer player
				? player.getGameProfile().getName()
				: target.getType().toShortString() + "/" + target.getUUID().toString().substring(0, 8);
		Refugee.LOGGER.debug(
				"[refugee follow] id={} pos={} target={} dist={} walking={} navDone={} moveTo={} stop={} interval={}",
				villager.getUUID().toString().substring(0, 8),
				villager.blockPosition().toShortString(),
				targetName,
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
