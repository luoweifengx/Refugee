package luowei.refugee.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.interact.RefugeeRoles;

/**
 * 守卫索敌：以跟随玩家或驻守标定点为圆心，标定范围 {@link RefugeeConfig#guardRadius} 内的敌对生物。
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
		Vec3 center = RefugeeGuardGoal.resolveGuardCenter(villager);
		if (center == null || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		double radius = getFollowDistance();
		AABB box = new AABB(center, center).inflate(radius);
		this.target = level.getNearestEntity(
				level.getEntitiesOfClass(Monster.class, box, monster ->
						monster.isAlive() && RefugeeGuardGoal.isWithinGuardRadius(monster, center)),
				this.targetConditions.range(-1.0),
				villager,
				villager.getX(),
				villager.getEyeY(),
				villager.getZ()
		);
	}

	@Override
	protected boolean canAttack(LivingEntity target, TargetingConditions conditions) {
		if (target == null || !target.isAlive()) {
			return false;
		}
		Vec3 center = RefugeeGuardGoal.resolveGuardCenter(villager);
		if (center == null || !RefugeeGuardGoal.isWithinGuardRadius(target, center)) {
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
		if (!RefugeeRoles.isGuard(villager) || RefugeeGuardGoal.resolveGuardCenter(villager) == null) {
			return false;
		}
		this.targetConditions.range(-1.0);
		return super.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		if (!RefugeeCombat.mood(villager).canAcquireTarget() || !RefugeeRoles.isGuard(villager)) {
			return false;
		}
		Vec3 center = RefugeeGuardGoal.resolveGuardCenter(villager);
		if (center == null) {
			return false;
		}
		LivingEntity current = villager.getTarget();
		if (current == null) {
			current = this.targetMob;
		}
		if (current == null || !current.isAlive() || !villager.canAttack(current)) {
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
