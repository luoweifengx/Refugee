package luowei.refugee.ai;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.config.RefugeeConfig;

/**
 * 闭环巡逻：到达当前点水平 5×5 后前进下一点。优先级最低，战斗 Goal 会抢 MOVE。
 */
public class RefugeePatrolGoal extends Goal {
	private final Villager villager;

	public RefugeePatrolGoal(Villager villager) {
		this.villager = villager;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (villager.isBaby() || RefugeeCombat.isBusy(villager) || RefugeeCombat.isEating(villager)) {
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		return data.isPatrolling() && !data.isFollowing() && !data.isFollowingEntity();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void stop() {
		villager.getNavigation().stop();
	}

	@Override
	public void tick() {
		RefugeeSwim.tick(villager);
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		BlockPos point = data.currentPatrolPoint();
		if (point == null) {
			villager.getNavigation().stop();
			return;
		}
		if (RefugeeVillagerData.reachedPatrolPoint(villager.blockPosition(), point)) {
			data.advancePatrolPoint();
			RefugeeAttachments.markDirty(villager, data);
			point = data.currentPatrolPoint();
			if (point == null || RefugeeVillagerData.reachedPatrolPoint(villager.blockPosition(), point)) {
				villager.getNavigation().stop();
				return;
			}
		}
		double cx = point.getX() + 0.5;
		double cy = point.getY();
		double cz = point.getZ() + 0.5;
		villager.getLookControl().setLookAt(cx, cy + 1.0, cz, 10.0f, villager.getMaxHeadXRot());
		if (villager.getNavigation().isDone() || villager.tickCount % 20 == 0) {
			villager.getNavigation().moveTo(cx, cy, cz, RefugeeConfig.guardWalkSpeed);
		}
	}
}
