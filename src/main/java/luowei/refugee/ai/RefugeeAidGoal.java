package luowei.refugee.ai;

import java.util.EnumSet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.special.ClinicService;

/**
 * 被医师或铁匠点到的居民走过去，双方对看，然后接受治疗或修复。
 */
public class RefugeeAidGoal extends Goal {
	private final Villager villager;

	public RefugeeAidGoal(Villager villager) {
		this.villager = villager;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return ready();
	}

	@Override
	public boolean canContinueToUse() {
		return ready();
	}

	@Override
	public void stop() {
		villager.getNavigation().stop();
	}

	@Override
	public void tick() {
		ClinicService.Order order = ClinicService.orderOf(villager);
		if (order == null || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		Entity entity = level.getEntity(order.npcId);
		if (!(entity instanceof Villager npc) || !npc.isAlive()) {
			ClinicService.clear(villager);
			return;
		}
		if (RefugeeRoles.isGuard(villager) && RefugeeCombat.hasHostilesInGuardRadius(villager)) {
			ClinicService.clear(villager);
			return;
		}
		if (!ClinicService.isIdle(villager)) {
			ClinicService.clear(villager);
			return;
		}
		villager.getLookControl().setLookAt(npc, 30.0f, villager.getMaxHeadXRot());
		double dist = villager.distanceTo(npc);
		if (dist > ClinicService.RANGE + 8.0) {
			ClinicService.clear(villager);
			return;
		}
		if (dist > 2.5) {
			villager.getNavigation().moveTo(npc, RefugeeConfig.builderWalkSpeed);
			return;
		}
		villager.getNavigation().stop();
		ClinicService.arrive(villager, npc);
		order.lookTicks++;
		if (order.lookTicks >= 10) {
			ClinicService.finish(villager, npc, order);
		}
	}

	private boolean ready() {
		ClinicService.Order order = ClinicService.orderOf(villager);
		return order != null && !villager.isBaby() && villager.isAlive();
	}
}
