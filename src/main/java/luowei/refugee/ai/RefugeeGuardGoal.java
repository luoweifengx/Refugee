package luowei.refugee.ai;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
 * 守卫与战斗总控：IDLE 让路给工具 AI；COMBAT 主手作战；慌乱不回岗。
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
		if (RefugeeCombat.isBusy(villager)) {
			return true;
		}
		boolean assigned = RefugeeRoles.isGuard(villager) || RefugeeRoles.isBuilder(villager);
		if (assigned && RefugeeCombat.hasHostilesInGuardRadius(villager)) {
			return true;
		}
		if (!RefugeeRoles.isGuard(villager)) {
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (!data.isFollowing() && RefugeeRoles.isBuilder(villager) && hasAssignedWork(data)) {
			return false;
		}
		return true;
	}

	private boolean hasAssignedWork(RefugeeVillagerData data) {
		if (data.isBuilding()) {
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
		if (RefugeeRoles.isGuard(villager) && !data.isFollowing() && data.guardCenter() == null) {
			data.setGuardCenter(villager.blockPosition());
			RefugeeAttachments.markDirty(villager, data);
		}
	}

	@Override
	public void stop() {
		returningToCenter = false;
		villager.getNavigation().stop();
		villager.stopUsingItem();
	}

	@Override
	public void tick() {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.tickCombatCooldowns();
		RefugeeCombat.Mood mood = data.combatMood();
		if (mood.isPanic()) {
			tickPanic(data, mood);
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

	private void tickPanic(RefugeeVillagerData data, RefugeeCombat.Mood mood) {
		returningToCenter = false;
		if (mood == RefugeeCombat.Mood.FLEE) {
			if (!RefugeeRoles.hasFood(villager)) {
				RefugeeCombat.logPanic(villager, "FLEE tick: no food -> LAST_STAND");
				RefugeeCombat.setMood(villager, RefugeeCombat.Mood.LAST_STAND);
				tickCombat(true);
				return;
			}
			Monster nearby = RefugeeCombat.nearestHostile(villager, villager.position(), RefugeeConfig.panicClearRadius);
			if (nearby == null) {
				RefugeeCombat.logPanic(villager, "FLEE tick: no monster in panicClearRadius -> RECOVER");
				RefugeeCombat.setMood(villager, RefugeeCombat.Mood.RECOVER);
				villager.getNavigation().stop();
				RefugeeCombat.tryEat(villager, (float) RefugeeConfig.recoverHealthRatio);
				return;
			}
			RefugeeCombat.flee(villager, nearby);
			return;
		}
		if (mood == RefugeeCombat.Mood.RECOVER) {
			if (!RefugeeRoles.hasFood(villager)) {
				RefugeeCombat.logPanic(villager, "RECOVER tick: no food -> LAST_STAND");
				RefugeeCombat.setMood(villager, RefugeeCombat.Mood.LAST_STAND);
				tickCombat(true);
				return;
			}
			villager.setTarget(null);
			villager.getNavigation().stop();
			RefugeeCombat.tickShield(villager, false);
			RefugeeCombat.tryEat(villager, (float) RefugeeConfig.recoverHealthRatio);
			if (RefugeeCombat.healthAtLeast(villager, (float) RefugeeConfig.recoverHealthRatio)) {
				if (RefugeeCombat.hasHostilesInGuardRadius(villager)) {
					RefugeeCombat.logPanic(villager, "RECOVER done, hostiles in guard radius -> COMBAT");
					RefugeeCombat.setMood(villager, RefugeeCombat.Mood.COMBAT);
				} else {
					RefugeeCombat.logPanic(villager, "RECOVER done, no hostiles -> IDLE");
					RefugeeCombat.setMood(villager, RefugeeCombat.Mood.IDLE);
				}
			}
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
		BlockPos stationCenter = data.guardCenter();
		if (stationCenter == null) {
			data.setGuardCenter(villager.blockPosition());
			RefugeeAttachments.markDirty(villager, data);
			stationCenter = data.guardCenter();
		}
		tickReturnToCenter(stationCenter);
	}

	private void tickFollowPlayer(ServerPlayer player) {
		villager.getLookControl().setLookAt(player, 10.0f, villager.getMaxHeadXRot());
		double distSq = villager.distanceToSqr(player);
		double teleport = RefugeeConfig.guardReturnTeleportDistance;
		if (distSq > teleport * teleport) {
			villager.teleportTo(player.getX(), player.getY(), player.getZ());
			villager.getNavigation().stop();
			return;
		}
		if (villager.distanceTo(player) > RefugeeFollowGoal.FOLLOW_STAY_DISTANCE) {
			villager.getNavigation().moveTo(player, RefugeeConfig.followSpeed);
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

	static Vec3 resolveGuardCenter(Villager villager) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.isFollowing()) {
			ServerPlayer player = resolveFollowPlayer(villager, data);
			return player == null ? null : player.position();
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
