package luowei.refugee.ai;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.interact.RefugeeRoles;

/**
 * 守卫：战斗优先。索敌与战斗相对跟随玩家或驻守标定点；近战追击、远程停步射击。
 * 脱战后：跟随时贴身跟随玩家；驻守则按走回/传送距离回标定点。
 */
public class RefugeeGuardGoal extends Goal {
	private static final double ARRIVED_AT_CENTER_DISTANCE = 1.5;

	private final Villager villager;
	private int attackCooldown;
	private boolean inCombat;
	private boolean returningToCenter;
	private int combatScanCooldown;

	public RefugeeGuardGoal(Villager villager) {
		this.villager = villager;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return RefugeeRoles.isGuard(villager);
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		inCombat = false;
		returningToCenter = false;
		combatScanCooldown = 0;
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (!data.isFollowing() && data.guardCenter() == null) {
			data.setGuardCenter(villager.blockPosition());
			RefugeeAttachments.markDirty(villager, data);
		}
	}

	@Override
	public void stop() {
		inCombat = false;
		returningToCenter = false;
		combatScanCooldown = 0;
		villager.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (attackCooldown > 0) {
			attackCooldown--;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		ServerPlayer followPlayer = null;
		Vec3 center;
		BlockPos stationCenter = null;
		if (data.isFollowing()) {
			followPlayer = resolveFollowPlayer(villager, data);
			if (followPlayer == null) {
				villager.getNavigation().stop();
				return;
			}
			center = followPlayer.position();
		} else {
			stationCenter = data.guardCenter();
			if (stationCenter == null) {
				data.setGuardCenter(villager.blockPosition());
				RefugeeAttachments.markDirty(villager, data);
				stationCenter = data.guardCenter();
			}
			center = Vec3.atBottomCenterOf(stationCenter);
		}
		updateCombatState(center);
		if (inCombat) {
			returningToCenter = false;
			tickCombat(center);
			return;
		}
		if (followPlayer != null) {
			tickFollowPlayer(followPlayer);
		} else {
			tickReturnToCenter(stationCenter);
		}
	}

	private void updateCombatState(Vec3 center) {
		LivingEntity target = villager.getTarget();
		if (isValidCombatTarget(target, center)) {
			inCombat = true;
			if (combatScanCooldown > 0) {
				combatScanCooldown--;
			}
			return;
		}
		boolean scanNow = combatScanCooldown <= 0 || inCombat;
		if (scanNow) {
			inCombat = hasNearbyHostiles(center);
			combatScanCooldown = RefugeeConfig.guardCombatScanIntervalTicks;
		} else {
			combatScanCooldown--;
		}
	}

	private void tickCombat(Vec3 center) {
		LivingEntity target = villager.getTarget();
		if (!isValidCombatTarget(target, center)) {
			villager.getNavigation().stop();
			return;
		}
		villager.getLookControl().setLookAt(target, 30.0f, 30.0f);
		if (RefugeeRoles.isRangedWeapon(villager.getMainHandItem())) {
			villager.getNavigation().stop();
			tryRangedAttack(target);
			return;
		}
		villager.getNavigation().moveTo(target, RefugeeConfig.guardWalkSpeed);
		tryMeleeAttack(target);
	}

	private void tickFollowPlayer(ServerPlayer player) {
		if (villager.getTarget() != null) {
			villager.setTarget(null);
		}
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
		if (villager.getTarget() != null) {
			villager.setTarget(null);
		}
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

	private boolean isValidCombatTarget(LivingEntity target, Vec3 center) {
		return target != null && target.isAlive() && isWithinGuardRadius(target, center);
	}

	private boolean hasNearbyHostiles(Vec3 center) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return false;
		}
		double radius = RefugeeConfig.guardRadius;
		AABB box = new AABB(center, center).inflate(radius);
		return !level.getEntitiesOfClass(Monster.class, box, monster ->
				monster.isAlive() && isWithinGuardRadius(monster, center)
		).isEmpty();
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

	private ServerLevel serverLevel() {
		return (ServerLevel) villager.level();
	}

	private void tryMeleeAttack(LivingEntity target) {
		if (attackCooldown > 0 || villager.distanceTo(target) >= 2.2) {
			return;
		}
		if (villager instanceof Mob mob) {
			mob.doHurtTarget(serverLevel(), target);
			attackCooldown = RefugeeConfig.meleeAttackIntervalTicks;
		}
	}

	private void tryRangedAttack(LivingEntity target) {
		if (attackCooldown > 0 || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		villager.getLookControl().setLookAt(target, 30.0f, 30.0f);
		ItemStack weapon = villager.getMainHandItem();
		ItemStack ammo = new ItemStack(Items.ARROW);
		Arrow arrow = new Arrow(level, villager, ammo, weapon.copy());
		// 对齐 AbstractSkeleton.performRangedAttack：用目标三坐标差设速度，不经实体航角。
		double dx = target.getX() - villager.getX();
		double dy = target.getY(1.0 / 3.0) - arrow.getY();
		double dz = target.getZ() - villager.getZ();
		double horiz = Math.sqrt(dx * dx + dz * dz);
		float inaccuracy = (float) (14 - level.getDifficulty().getId() * 4);
		arrow.shoot(dx, dy + horiz * 0.2F, dz, 1.6F, inaccuracy);
		arrow.setBaseDamage(2.0);
		level.addFreshEntity(arrow);
		weapon.hurtAndBreak(1, villager, villager.getEquipmentSlotForItem(weapon));
		attackCooldown = RefugeeConfig.rangedAttackIntervalTicks;
	}
}
