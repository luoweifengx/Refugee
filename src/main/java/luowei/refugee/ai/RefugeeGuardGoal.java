package luowei.refugee.ai;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.logistics.OrgLogisticsData;

/**
 * 守卫与战斗总控：战斗圈看村民自身；IDLE 才按距离回岗；慌乱不回岗。
 */
public class RefugeeGuardGoal extends Goal {
	private static final double ARRIVED_AT_CENTER_DISTANCE = 1.5;

	private final Villager villager;
	private boolean returningToCenter;

	public RefugeeGuardGoal(Villager villager) {
		this.villager = villager;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (villager.isBaby()) {
			return false;
		}
		if (!RefugeeRoles.isGuard(villager)) {
			return RefugeeCombat.mood(villager) == RefugeeCombat.Mood.FLEE;
		}
		if (RefugeeCombat.isBusy(villager)) {
			return true;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (!data.isFollowing() && !data.isFollowingEntity() && RefugeeRoles.isBuilder(villager) && hasAssignedWork(data)) {
			return false;
		}
		if (data.isPatrolling() && !data.isFollowing() && !data.isFollowingEntity()) {
			return RefugeeCombat.hasHostilesInGuardRadius(villager);
		}
		return true;
	}

	private boolean hasAssignedWork(RefugeeVillagerData data) {
		if (data.isBuilding() || data.workerDuty().isAssigned()) {
			return true;
		}
		if (villager.level().getServer() == null) {
			return false;
		}
		OrgLogisticsData logistics = OrgLogisticsData.get(villager.level().getServer());
		return logistics.zoneOfWorker(villager.getUUID()) != null;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		returningToCenter = false;
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (RefugeeRoles.isGuard(villager)
				&& !data.isFollowing()
				&& !data.isFollowingEntity()
				&& !data.isPatrolling()
				&& data.guardCenter() == null) {
			data.setGuardCenter(villager.blockPosition());
			RefugeeAttachments.markDirty(villager, data);
		}
	}

	@Override
	public void stop() {
		returningToCenter = false;
		villager.getNavigation().stop();
		RefugeeCombat.tickShield(villager, false);
	}

	@Override
	public void tick() {
		RefugeeSwim.tick(villager);
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.tickCombatCooldowns();
		RefugeeCombat.Mood mood = data.combatMood();
		if (mood.isPanic()) {
			if (!RefugeeRoles.isGuard(villager)) {
				if (mood == RefugeeCombat.Mood.FLEE) {
					RefugeeCombat.tickHitAndFlee(villager);
				} else {
					RefugeeCombat.setMood(villager, RefugeeCombat.Mood.IDLE);
				}
				return;
			}
			tickPanic(mood);
			return;
		}
		boolean hostiles = RefugeeCombat.hasHostilesInGuardRadius(villager);
		if (mood == RefugeeCombat.Mood.COMBAT) {
			if (!hostiles) {
				RefugeeCombat.setMood(villager, RefugeeCombat.Mood.IDLE);
				villager.setTarget(null);
				RefugeeCombat.tickShield(villager, false);
				return;
			}
			tickCombat(false);
			return;
		}
		if (hostiles) {
			RefugeeCombat.setMood(villager, RefugeeCombat.Mood.COMBAT);
			tickCombat(false);
			return;
		}
		tickIdle(data);
	}

	private void tickPanic(RefugeeCombat.Mood mood) {
		returningToCenter = false;
		if (RefugeeCombat.leavePanicIfHealthy(villager)) {
			return;
		}
		Monster nearby = RefugeeCombat.nearestHostile(villager, villager.position(), RefugeeConfig.panicClearRadius);
		RefugeeCombat.Mood next = RefugeeCombat.panicMoodWhenHurt(villager, nearby);
		if (next != mood) {
			RefugeeCombat.logPanic(villager, mood + " tick -> " + next
					+ " nearby=" + (nearby == null ? "none" : nearby.getType().toShortString())
					+ " food=" + RefugeeRoles.hasFood(villager));
			RefugeeCombat.setMood(villager, next);
		}
		if (next == RefugeeCombat.Mood.FLEE) {
			RefugeeCombat.tickShield(villager, false);
			RefugeeCombat.flee(villager, nearby);
			return;
		}
		if (next == RefugeeCombat.Mood.RECOVER) {
			villager.setTarget(null);
			villager.getNavigation().stop();
			RefugeeCombat.tickShield(villager, false);
			RefugeeCombat.tryEat(villager, (float) RefugeeConfig.recoverHealthRatio);
			return;
		}
		tickCombat(true);
	}

	private void tickCombat(boolean lastStand) {
		LivingEntity target = villager.getTarget();
		Vec3 center = RefugeeCombat.combatCenter(villager);
		if (target == null || !target.isAlive() || !RefugeeGuardGoal.isWithinGuardRadius(target, center)) {
			target = RefugeeCombat.nearestHostile(villager, center, RefugeeConfig.guardRadius);
			villager.setTarget(target);
		}
		if (target == null || !target.isAlive()) {
			villager.getNavigation().stop();
			RefugeeCombat.stopRangedDraw(villager);
			RefugeeCombat.tickShield(villager, RefugeeRoles.hasShield(villager));
			return;
		}
		villager.getLookControl().setLookAt(target, 30.0f, 30.0f);
		InteractionHand[] hands = lastStand
				? weaponsOnly(villager)
				: RefugeeCombat.combatAttackHands(villager, target);
		if (hands.length == 0) {
			if (lastStand) {
				villager.getNavigation().stop();
				RefugeeCombat.stopRangedDraw(villager);
				RefugeeCombat.tickShield(villager, RefugeeRoles.hasShield(villager));
				RefugeeCombat.tauntUntargeted(villager);
				return;
			}
			RefugeeCombat.flee(villager, target);
			return;
		}
		RefugeeCombat.preferShieldOffhand(villager);
		boolean shooting = false;
		for (InteractionHand hand : hands) {
			if (RefugeeRoles.isRangedWeapon(RefugeeCombat.stackIn(villager, hand))) {
				shooting = true;
				break;
			}
		}
		if (shooting) {
			villager.getNavigation().stop();
			RefugeeCombat.tickShield(villager, false);
		} else {
			RefugeeCombat.stopRangedDraw(villager);
			villager.getNavigation().moveTo(target, RefugeeConfig.guardWalkSpeed);
			RefugeeCombat.tickShield(villager, RefugeeRoles.hasShield(villager));
		}
		RefugeeCombat.tauntUntargeted(villager);
		for (InteractionHand hand : hands) {
			RefugeeCombat.attackWith(villager, target, hand);
		}
	}

	private static InteractionHand[] weaponsOnly(Villager villager) {
		RefugeeCombat.preferWeaponMainHand(villager);
		if (RefugeeRoles.isWeapon(villager.getMainHandItem())) {
			return new InteractionHand[] {InteractionHand.MAIN_HAND};
		}
		return new InteractionHand[0];
	}

	private void tickIdle(RefugeeVillagerData data) {
		RefugeeCombat.tickShield(villager, false);
		if (villager.getTarget() != null) {
			villager.setTarget(null);
		}
		RefugeeCombat.tryEat(villager, 1.0f);
		if (!RefugeeRoles.isGuard(villager)) {
			villager.getNavigation().stop();
			return;
		}
		ServerPlayer followPlayer = null;
		if (data.isFollowing()) {
			followPlayer = resolveFollowPlayer(villager, data);
			if (followPlayer == null) {
				villager.getNavigation().stop();
				return;
			}
			tickFollowPlayer(followPlayer);
			return;
		}
		if (data.isFollowingEntity()) {
			LivingEntity followTarget = resolveFollowEntity(villager, data);
			if (followTarget == null) {
				data.clearFollowEntity();
				if (data.guardCenter() == null) {
					data.setGuardCenter(villager.blockPosition());
				}
				RefugeeAttachments.markDirty(villager, data);
				villager.getNavigation().stop();
				return;
			}
			tickFollowEntity(followTarget);
			return;
		}
		if (data.isPatrolling()) {
			villager.getNavigation().stop();
			return;
		}
		BlockPos stationCenter = data.guardCenter();
		if (stationCenter == null) {
			data.setGuardCenter(villager.blockPosition());
			RefugeeAttachments.markDirty(villager, data);
			stationCenter = data.guardCenter();
		}
		tickReturnToCenter(stationCenter);
	}

	private void tickFollowPlayer(ServerPlayer player) {
		tickFollowLiving(player);
	}

	private void tickFollowEntity(LivingEntity target) {
		tickFollowLiving(target);
	}

	private void tickFollowLiving(LivingEntity target) {
		villager.getLookControl().setLookAt(target, 10.0f, villager.getMaxHeadXRot());
		double distSq = villager.distanceToSqr(target);
		double teleport = RefugeeConfig.guardReturnTeleportDistance;
		if (distSq > teleport * teleport) {
			villager.teleportTo(target.getX(), target.getY(), target.getZ());
			villager.getNavigation().stop();
			return;
		}
		if (villager.distanceTo(target) > RefugeeFollowGoal.FOLLOW_STAY_DISTANCE) {
			villager.getNavigation().moveTo(target, RefugeeConfig.followSpeed);
		} else {
			villager.getNavigation().stop();
		}
	}

	private void tickReturnToCenter(BlockPos center) {
		double cx = center.getX() + 0.5;
		double cy = center.getY();
		double cz = center.getZ() + 0.5;
		double distSq = villager.distanceToSqr(cx, cy, cz);
		double teleport = RefugeeConfig.guardReturnTeleportDistance;
		double walk = RefugeeConfig.guardReturnWalkDistance;
		if (distSq > teleport * teleport) {
			villager.teleportTo(cx, cy, cz);
			returningToCenter = false;
			villager.getNavigation().stop();
			return;
		}
		if (distSq > walk * walk) {
			returningToCenter = true;
		}
		if (returningToCenter) {
			if (distSq <= ARRIVED_AT_CENTER_DISTANCE * ARRIVED_AT_CENTER_DISTANCE) {
				returningToCenter = false;
				villager.getNavigation().stop();
				return;
			}
			villager.getNavigation().moveTo(cx, cy, cz, RefugeeConfig.guardWalkSpeed);
			return;
		}
		villager.getNavigation().stop();
	}

	static ServerPlayer resolveFollowPlayer(Villager villager, RefugeeVillagerData data) {
		if (!data.isFollowing()) {
			return null;
		}
		if (villager.level().getPlayerByUUID(data.followPlayerId()) instanceof ServerPlayer player && player.isAlive()) {
			return player;
		}
		return null;
	}

	static LivingEntity resolveFollowEntity(Villager villager, RefugeeVillagerData data) {
		if (!data.isFollowingEntity() || !(villager.level() instanceof ServerLevel level)) {
			return null;
		}
		Entity entity = level.getEntity(data.followEntityId());
		if (entity instanceof LivingEntity living && living.isAlive() && living != villager) {
			return living;
		}
		return null;
	}

	static Vec3 resolveGuardCenter(Villager villager) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.isFollowing()) {
			ServerPlayer player = resolveFollowPlayer(villager, data);
			return player == null ? null : player.position();
		}
		if (data.isFollowingEntity()) {
			LivingEntity target = resolveFollowEntity(villager, data);
			return target == null ? null : target.position();
		}
		BlockPos pos = data.guardCenter();
		if (pos == null) {
			pos = villager.blockPosition();
		}
		return Vec3.atBottomCenterOf(pos);
	}

	static boolean isWithinGuardRadius(LivingEntity entity, Vec3 center) {
		double radius = RefugeeConfig.guardRadius;
		return entity.distanceToSqr(center) <= radius * radius;
	}
}
