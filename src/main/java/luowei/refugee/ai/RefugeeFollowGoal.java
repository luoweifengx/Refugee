package luowei.refugee.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.interact.RefugeeRoles;

public class RefugeeFollowGoal extends Goal {
	public static final double FOLLOW_STAY_DISTANCE = 2.5;

	private final Villager villager;

	public RefugeeFollowGoal(Villager villager) {
		this.villager = villager;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return RefugeeAttachments.get(villager).isFollowing() && !RefugeeRoles.isGuard(villager);
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
		if (villager.distanceTo(player) > FOLLOW_STAY_DISTANCE) {
			villager.getNavigation().moveTo(player, RefugeeConfig.followSpeed);
		} else {
			villager.getNavigation().stop();
		}
	}
}
