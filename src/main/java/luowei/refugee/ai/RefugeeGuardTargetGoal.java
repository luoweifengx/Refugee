package luowei.refugee.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.interact.RefugeeRoles;

/**
 * 守卫索敌：以村民自身为圆心，{@link RefugeeConfig#guardRadius} 内的敌对生物。
 */
public class RefugeeGuardTargetGoal extends NearestAttackableTargetGoal<Monster> {
	private final Villager villager;

	public RefugeeGuardTargetGoal(Villager villager) {
		super(villager, Monster.class, true);
		this.villager = villager;
		this.targetConditions.range(-1.0);
	}

	@Override
	protected double getFollowDistance() {
		return RefugeeConfig.guardRadius;
	}

	@Override
	protected void findTarget() {
		this.target = null;
		LivingEntity enemy = RefugeeCombat.nearestCombatTarget(villager, villager.position(), getFollowDistance());
		if (enemy instanceof Monster monster) {
			this.target = monster;
		}
		villager.setTarget(enemy);
	}

	@Override
	protected boolean canAttack(LivingEntity target, TargetingConditions conditions) {
		if (target == null || !target.isAlive()) {
			return false;
		}
		Vec3 center = villager.position();
		if (!RefugeeGuardGoal.isWithinGuardRadius(target, center)) {
			return false;
		}
		if (!(villager.level() instanceof ServerLevel level)) {
			return false;
		}
		return conditions.range(-1.0).test(level, villager, target);
	}

	@Override
	public boolean canUse() {
		if (villager.isBaby() || !RefugeeCombat.mood(villager).canAcquireTarget()) {
			return false;
		}
		if (luowei.refugee.livability.LivabilityService.isSpent(villager)) {
			return false;
		}
		if (!RefugeeRoles.isGuard(villager)) {
			return false;
		}
		findTarget();
		return this.target != null || isHostileFactionTarget(villager.getTarget());
	}

	private static boolean isHostileFactionTarget(LivingEntity target) {
		return target instanceof Villager other
				&& other.isAlive()
				&& luowei.refugee.attachment.RefugeeAttachments.get(other).isHostileFaction();
	}

	@Override
	public boolean canContinueToUse() {
		if (!RefugeeCombat.mood(villager).canAcquireTarget() || !RefugeeRoles.isGuard(villager)) {
			return false;
		}
		Vec3 center = villager.position();
		LivingEntity current = villager.getTarget();
		if (current == null) {
			current = this.targetMob;
		}
		if (current == null || !current.isAlive()) {
			return false;
		}
		if (!isHostileFactionTarget(current) && !villager.canAttack(current)) {
			return false;
		}
		if (!RefugeeGuardGoal.isWithinGuardRadius(current, center)) {
			return false;
		}
		if (this.mustSee && !villager.getSensing().hasLineOfSight(current)) {
			return false;
		}
		villager.setTarget(current);
		return true;
	}
}
