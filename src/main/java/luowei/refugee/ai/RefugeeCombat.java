package luowei.refugee.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import luowei.refugee.Refugee;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.livability.LivabilityData;
import luowei.refugee.livability.LivabilityRules;
import luowei.refugee.livability.LivabilityService;
import luowei.refugee.talk.RefugeeBubble;
import luowei.refugee.warehouse.WarehouseService;

/**
 * 统一战斗状态机：IDLE / COMBAT / 慌乱（FLEE、RECOVER、LAST_STAND）。
 */
public final class RefugeeCombat {
	private static final ResourceLocation SHIELD_SLOW_ID = Refugee.id("shield_block");
	private static final int FLEE_ONCE_TICKS = 100;

	public enum Mood {
		IDLE,
		COMBAT,
		FLEE,
		RECOVER,
		LAST_STAND;

		public boolean isPanic() {
			return this == FLEE || this == RECOVER || this == LAST_STAND;
		}

		public boolean isBusy() {
			return this != IDLE;
		}

		public boolean returnsToCenter() {
			return this == IDLE;
		}

		public boolean canAcquireTarget() {
			return this == COMBAT || this == LAST_STAND;
		}
	}

	private RefugeeCombat() {
	}

	public static Mood mood(Villager villager) {
		return RefugeeAttachments.get(villager).combatMood();
	}

	public static void setMood(Villager villager, Mood mood) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		Mood previous = data.combatMood();
		if (previous == mood) {
			return;
		}
		data.setCombatMood(mood);
		if (mood == Mood.COMBAT || mood == Mood.FLEE || mood == Mood.LAST_STAND) {
			cancelEat(villager);
		}
		RefugeeAttachments.markDirty(villager, data);
		logPanic(villager, "mood " + previous + " -> " + mood);
	}

	public static boolean isBusy(Villager villager) {
		return mood(villager).isBusy();
	}

	public static void onDamaged(Villager villager, DamageSource source) {
		if (villager == null || villager.isBaby() || !RefugeeAttachments.isRefugee(villager)) {
			return;
		}
		if (RefugeeDepthCurse.isLeylineDamage(source)) {
			return;
		}
		if (RefugeeRoles.fleesWhenHit(villager)) {
			tryFleeOnce(villager, source);
			return;
		}
		float max = villager.getMaxHealth();
		if (max <= 0.0f) {
			return;
		}
		float ratio = villager.getHealth() / max;
		Mood current = mood(villager);
		if (current.isPanic()) {
			return;
		}
		if (ratio < RefugeeConfig.panicHealthRatio) {
			enterPanic(villager);
		}
	}

	/** 散人/工人被生物打中才逃；同一次遭遇只跑一段。 */
	public static void tryFleeOnce(Villager villager, DamageSource source) {
		if (mood(villager) == Mood.FLEE) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.fledThisEncounter()) {
			return;
		}
		if (!(source.getEntity() instanceof LivingEntity attacker) || !attacker.isAlive()) {
			return;
		}
		Monster nearby = nearestHostile(villager, villager.position(), RefugeeConfig.panicClearRadius);
		LivingEntity threat = nearby != null ? nearby : attacker;
		data.setFledThisEncounter(true);
		data.setFleeOnceTicks(FLEE_ONCE_TICKS);
		setMood(villager, Mood.FLEE);
		flee(villager, threat);
	}

	public static void tickHitAndFlee(Villager villager) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.tickFleeOnce();
		int remaining = data.fleeOnceTicks();
		boolean timedOut = remaining <= 0;
		boolean arrived = villager.getNavigation().isDone() && remaining <= FLEE_ONCE_TICKS - 10;
		if (timedOut || arrived) {
			villager.getNavigation().stop();
			data.setFleeOnceTicks(0);
			setMood(villager, Mood.IDLE);
		}
	}

	/** 附近没有敌对后，允许下次被打再逃一次。 */
	public static void tickEncounterReset(Villager villager) {
		if (!RefugeeRoles.fleesWhenHit(villager)) {
			return;
		}
		Mood current = mood(villager);
		if (current.isBusy() && current != Mood.FLEE) {
			setMood(villager, Mood.IDLE);
			current = Mood.IDLE;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.fledThisEncounter() && current != Mood.FLEE && !hasHostilesInGuardRadius(villager)) {
			data.setFledThisEncounter(false);
		}
	}

	public static void enterPanic(Villager villager) {
		if (leavePanicIfHealthy(villager)) {
			return;
		}
		Monster nearby = nearestHostile(villager, villager.position(), RefugeeConfig.panicClearRadius);
		villager.setTarget(null);
		stopRangedDraw(villager);
		tickShield(villager, false);
		Mood next = panicMoodWhenHurt(villager, nearby);
		logPanic(villager, "enterPanic nearby="
				+ (nearby == null ? "none" : nearby.getType().toShortString())
				+ " food=" + RefugeeRoles.hasFood(villager)
				+ " guard=" + RefugeeRoles.isGuard(villager)
				+ " -> " + next);
		setMood(villager, next);
	}

	/**
	 * 血量已回到恢复线则退出恐慌。
	 * 守卫若自身战斗圈内仍有敌对进 COMBAT，其余回 IDLE。
	 */
	public static boolean leavePanicIfHealthy(Villager villager) {
		if (!healthAtLeast(villager, (float) RefugeeConfig.recoverHealthRatio)) {
			return false;
		}
		villager.setTarget(null);
		villager.getNavigation().stop();
		tickShield(villager, false);
		if (RefugeeRoles.isGuard(villager) && hasHostilesInGuardRadius(villager)) {
			logPanic(villager, "healthy -> COMBAT");
			setMood(villager, Mood.COMBAT);
		} else {
			logPanic(villager, "healthy -> IDLE");
			setMood(villager, Mood.IDLE);
		}
		return true;
	}

	/**
	 * 守卫残血：近处有敌且没食物则 LAST_STAND，否则 RECOVER。
	 * 逃跑分支先停用。
	 */
	public static Mood panicMoodWhenHurt(Villager villager, Monster nearby) {
		boolean hasFood = RefugeeRoles.hasFood(villager);
		if (nearby != null && !hasFood && RefugeeRoles.isGuard(villager)) {
			return Mood.LAST_STAND;
		}
		// if (nearby != null) {
		// 	return Mood.FLEE;
		// }
		return Mood.RECOVER;
	}

	/** 战斗圈圆心：村民自身。岗点 / 跟随玩家只用于 IDLE 回岗。 */
	public static Vec3 combatCenter(Villager villager) {
		return villager == null ? null : villager.position();
	}

	public static boolean hasHostilesInGuardRadius(Villager villager) {
		Vec3 center = RefugeeRoles.isGuard(villager) ? RefugeeGuardGoal.watchCenter(villager) : combatCenter(villager);
		double radius = RefugeeRoles.isGuard(villager) ? RefugeeGuardGoal.WATCH_RADIUS : RefugeeConfig.guardRadius;
		return hasHostilesAround(villager, center, radius)
				|| nearestHostileFaction(villager, center, radius) != null;
	}

	/** 睡着的守卫，警戒圈里出现敌人就起来。 */
	public static void wakeGuardIfThreatened(Villager villager) {
		if (villager == null || !villager.isSleeping() || !RefugeeRoles.isGuard(villager)) {
			return;
		}
		if (!hasHostilesInGuardRadius(villager)) {
			return;
		}
		villager.stopSleeping();
	}

	public static boolean hasHostilesAround(Villager villager, Vec3 center, double radius) {
		if (!(villager.level() instanceof ServerLevel level) || center == null) {
			return false;
		}
		AABB box = new AABB(center, center).inflate(radius);
		return !level.getEntitiesOfClass(Monster.class, box, monster ->
				monster.isAlive() && monster.distanceToSqr(center) <= radius * radius
		).isEmpty();
	}

	public static boolean isFactionEnemy(Villager villager, LivingEntity candidate) {
		if (villager == null || candidate == null || candidate == villager || !candidate.isAlive() || candidate.isRemoved()) {
			return false;
		}
		if (candidate instanceof ArmorStand || (candidate instanceof Player player && player.isSpectator())) {
			return false;
		}
		return !(candidate instanceof Villager other) || !RefugeeAttachments.get(other).isHostileFaction();
	}

	public static LivingEntity nearestOutsider(Villager villager, Vec3 center, double radius) {
		if (!(villager.level() instanceof ServerLevel level) || center == null) {
			return null;
		}
		AABB box = new AABB(center, center).inflate(radius);
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, candidate -> isFactionEnemy(villager, candidate))) {
			double dist = living.distanceToSqr(center);
			if (dist <= radius * radius && dist < bestDist) {
				bestDist = dist;
				best = living;
			}
		}
		return best;
	}

	public static void attackUnarmed(Villager villager, LivingEntity target) {
		if (villager == null || target == null || !target.isAlive() || villager.distanceTo(target) >= 2.2) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.mainAttackCooldown() > 0) {
			return;
		}
		if (!melee(villager, target, InteractionHand.MAIN_HAND)) {
			return;
		}
		setHandCooldown(villager, data, InteractionHand.MAIN_HAND, RefugeeConfig.meleeAttackIntervalTicks);
	}

	/** 守卫要打的最近目标：怪物，或敌对阵营居民。 */
	public static LivingEntity nearestCombatTarget(Villager villager, Vec3 center, double radius) {
		LivingEntity best = nearestHostile(villager, center, radius);
		Villager faction = nearestHostileFaction(villager, center, radius);
		if (faction == null) {
			return best;
		}
		if (best == null || faction.distanceToSqr(center) < best.distanceToSqr(center)) {
			return faction;
		}
		return best;
	}

	public static Villager nearestHostileFaction(Villager villager, Vec3 center, double radius) {
		if (!(villager.level() instanceof ServerLevel level) || center == null) {
			return null;
		}
		AABB box = new AABB(center, center).inflate(radius);
		Villager best = null;
		double bestDist = Double.MAX_VALUE;
		for (Villager other : level.getEntitiesOfClass(Villager.class, box, candidate ->
				candidate != villager
						&& candidate.isAlive()
						&& RefugeeAttachments.get(candidate).isHostileFaction()
						&& candidate.distanceToSqr(center) <= radius * radius
		)) {
			double dist = other.distanceToSqr(center);
			if (dist < bestDist) {
				bestDist = dist;
				best = other;
			}
		}
		return best;
	}

	public static Monster nearestHostile(Villager villager, Vec3 center, double radius) {
		if (!(villager.level() instanceof ServerLevel level) || center == null) {
			return null;
		}
		AABB box = new AABB(center, center).inflate(radius);
		Monster best = null;
		double bestDist = Double.MAX_VALUE;
		for (Monster monster : level.getEntitiesOfClass(Monster.class, box, Monster::isAlive)) {
			double dist = monster.distanceToSqr(center);
			if (dist <= radius * radius && dist < bestDist) {
				bestDist = dist;
				best = monster;
			}
		}
		return best;
	}

	public static void tauntUntargeted(Villager villager) {
		if (!RefugeeRoles.hasShield(villager) || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		double radius = RefugeeConfig.shieldTauntRadius;
		AABB box = villager.getBoundingBox().inflate(radius);
		for (Monster monster : level.getEntitiesOfClass(Monster.class, box, Monster::isAlive)) {
			if (monster.distanceTo(villager) <= radius && monster.getTarget() == null) {
				monster.setTarget(villager);
			}
		}
	}

	public static void preferShieldOffhand(Villager villager) {
		ItemStack main = villager.getMainHandItem();
		ItemStack off = villager.getOffhandItem();
		if (RefugeeRoles.isShield(off) || !RefugeeRoles.isShield(main)) {
			return;
		}
		villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		villager.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		villager.setItemSlot(EquipmentSlot.MAINHAND, off);
		villager.setItemSlot(EquipmentSlot.OFFHAND, main);
	}

	public static void tickShield(Villager villager, boolean hold) {
		if (isEating(villager) || isDrawingRanged(villager)) {
			applyShieldSlow(villager, false);
			return;
		}
		if (!hold) {
			if (villager.isUsingItem()) {
				villager.stopUsingItem();
			}
			applyShieldSlow(villager, false);
			return;
		}
		preferShieldOffhand(villager);
		if (!RefugeeRoles.isShield(villager.getOffhandItem())) {
			applyShieldSlow(villager, false);
			return;
		}
		if (!villager.isUsingItem()) {
			villager.startUsingItem(InteractionHand.OFF_HAND);
		}
		applyShieldSlow(villager, true);
	}

	private static void applyShieldSlow(Villager villager, boolean blocking) {
		AttributeInstance speed = villager.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		speed.removeModifier(SHIELD_SLOW_ID);
		if (!blocking) {
			return;
		}
		double multiplier = RefugeeConfig.shieldMoveMultiplier;
		if (multiplier >= 1.0) {
			return;
		}
		speed.addTransientModifier(new AttributeModifier(
				SHIELD_SLOW_ID,
				multiplier - 1.0,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
		));
	}

	public static boolean isEating(Villager villager) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		return data.isEating() || villager.isUsingItem() && RefugeeRoles.isFood(villager.getUseItem());
	}

	public static boolean isDrawingRanged(Villager villager) {
		return villager.isUsingItem() && RefugeeRoles.isRangedWeapon(villager.getUseItem());
	}

	public static void stopRangedDraw(Villager villager) {
		if (isDrawingRanged(villager)) {
			villager.stopUsingItem();
		}
	}

	public static void tickEat(Villager villager) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.eatCooldown() > 0) {
			data.setEatCooldown(data.eatCooldown() - 1);
		}
		if (!data.isEating()) {
			return;
		}
		if (villager.isUsingItem() && RefugeeRoles.isFood(villager.getUseItem())) {
			data.syncEatWatch(villager.getMainHandItem());
			return;
		}
		ItemStack remaining = villager.getMainHandItem();
		int left = RefugeeRoles.isFood(remaining) ? remaining.getCount() : 0;
		boolean consumed = data.eatWatchCount() > left;
		FoodProperties properties = data.eatWatchFood();
		restoreEatHand(villager, data);
		if (consumed && properties != null) {
			healFromFood(villager, data, properties);
		}
		RefugeeAttachments.markDirty(villager, data);
	}

	public static void cancelEat(Villager villager) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (!data.isEating() && !RefugeeRoles.isFood(villager.getUseItem())) {
			return;
		}
		if (villager.isUsingItem()) {
			villager.stopUsingItem();
		}
		restoreEatHand(villager, data);
		RefugeeAttachments.markDirty(villager, data);
	}

	public static boolean tryEat(Villager villager) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.isEating()) {
			return true;
		}
		Mood current = mood(villager);
		if (current == Mood.COMBAT || current == Mood.FLEE || current == Mood.LAST_STAND) {
			return false;
		}
		if (data.eatCooldown() > 0) {
			return false;
		}
		if (!RefugeeAttachments.isRefugee(villager)) {
			return false;
		}
		LivabilityRules rules = LivabilityRules.CURRENT;
		LivabilityData live = LivabilityService.get(villager);
		double missing = rules.statMax - live.satiety();
		if (missing <= 0.0) {
			return false;
		}
		ItemStack food = data.resourceItem();
		if (!RefugeeRoles.isFood(food)) {
			ItemStack kept = WorkerCargo.takeKeptFood(villager);
			if (RefugeeRoles.isFood(kept)) {
				data.setResourceItem(kept);
				food = kept;
			} else if (villager.level() instanceof ServerLevel level && data.subjectId() != null) {
				ItemStack taken = WarehouseService.takeOneFood(level, data.subjectId());
				if (!taken.isEmpty()) {
					data.setResourceItem(taken);
					food = taken;
				}
			}
		}
		if (!RefugeeRoles.isFood(food)) {
			return false;
		}
		FoodProperties properties = food.get(DataComponents.FOOD);
		if (properties == null) {
			return false;
		}
		if (missing < properties.nutrition()) {
			RefugeeAttachments.markDirty(villager, data);
			return false;
		}
		int duration = food.getUseDuration(villager);
		if (duration <= 1) {
			healFromFood(villager, data, properties);
			food.shrink(1);
			data.setResourceItem(food);
			RefugeeAttachments.markDirty(villager, data);
			return true;
		}
		if (villager.isUsingItem()) {
			villager.stopUsingItem();
		}
		swapEatSlots(villager, data);
		data.setEating(true);
		data.syncEatWatch(villager.getMainHandItem());
		villager.startUsingItem(InteractionHand.MAIN_HAND);
		RefugeeAttachments.markDirty(villager, data);
		return true;
	}

	private static void restoreEatHand(Villager villager, RefugeeVillagerData data) {
		if (!data.isEating()) {
			return;
		}
		swapEatSlots(villager, data);
		data.clearEating();
	}

	private static void swapEatSlots(Villager villager, RefugeeVillagerData data) {
		ItemStack held = villager.getMainHandItem().copy();
		ItemStack stored = data.resourceItem().copy();
		villager.setItemSlot(EquipmentSlot.MAINHAND, stored);
		data.setResourceItem(held);
	}

	private static void healFromFood(Villager villager, RefugeeVillagerData data, FoodProperties properties) {
		LivabilityService.noteFood(villager, properties);
		data.setEatCooldown(RefugeeConfig.eatIntervalTicks);
		RefugeeAttachments.markDirty(villager, data);
		RefugeeBubble.startFull(villager);
	}

	public static boolean healthAtLeast(Villager villager, float ratio) {
		float max = villager.getMaxHealth();
		return max > 0.0f && villager.getHealth() / max >= ratio;
	}

	public static void flee(Villager villager, LivingEntity threat) {
		villager.setTarget(null);
		stopRangedDraw(villager);
		tickShield(villager, false);
		if (threat == null || !threat.isAlive()) {
			villager.getNavigation().stop();
			return;
		}
		Vec3 away = villager.position().subtract(threat.position());
		if (away.lengthSqr() < 0.0001) {
			away = new Vec3(1.0, 0.0, 0.0);
		}
		Vec3 dest = villager.position().add(away.normalize().scale(12.0));
		BlockPos pos = BlockPos.containing(dest.x, villager.getY(), dest.z);
		villager.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RefugeeConfig.followSpeed);
	}

	/**
	 * 战斗时只用主手。空数组表示应战术性逃跑。
	 * 双手同时攻击暂时关闭：需要的武器会先被换到主手。
	 */
	public static InteractionHand[] combatAttackHands(Villager villager, LivingEntity target) {
		preferCombatMainHand(villager, target);
		ItemStack main = villager.getMainHandItem();
		ItemStack off = villager.getOffhandItem();
		boolean mainWeapon = RefugeeRoles.isWeapon(main);
		boolean offWeapon = RefugeeRoles.isWeapon(off);
		if (!mainWeapon && !offWeapon) {
			return new InteractionHand[0];
		}
		boolean mainRanged = mainWeapon && RefugeeRoles.isRangedWeapon(main);
		boolean offRanged = offWeapon && RefugeeRoles.isRangedWeapon(off);
		double distance = target == null ? Double.MAX_VALUE : villager.distanceTo(target);
		if (mainRanged && offRanged && distance < RefugeeConfig.dualRangedFleeDistance) {
			return new InteractionHand[0];
		}
		if (!mainWeapon) {
			return new InteractionHand[0];
		}
		return new InteractionHand[] {InteractionHand.MAIN_HAND};
		/*
		 * 双持同时出手（暂关）：
		 * if (mainRanged && offRanged) {
		 *   return new InteractionHand[] {MAIN_HAND, OFF_HAND};
		 * }
		 * if (hasRanged && hasMelee) {
		 *   return ranged or melee hand by distance;
		 * }
		 * if (mainWeapon && offWeapon) {
		 *   return new InteractionHand[] {MAIN_HAND, OFF_HAND};
		 * }
		 */
	}

	/**
	 * 把当前应当使用的武器换到主手：仅副手有武器则换上；
	 * 一手远程一手近战则按距离选择，远距还要视线通畅。双手近战不换。
	 */
	public static void preferCombatMainHand(Villager villager, LivingEntity target) {
		ItemStack main = villager.getMainHandItem();
		ItemStack off = villager.getOffhandItem();
		boolean mainWeapon = RefugeeRoles.isWeapon(main);
		boolean offWeapon = RefugeeRoles.isWeapon(off);
		if (!mainWeapon && offWeapon) {
			swapHands(villager);
			return;
		}
		if (!mainWeapon || !offWeapon) {
			return;
		}
		boolean mainRanged = RefugeeRoles.isRangedWeapon(main);
		boolean offRanged = RefugeeRoles.isRangedWeapon(off);
		boolean mainMelee = !mainRanged;
		boolean offMelee = !offRanged;
		if (!(mainRanged || offRanged) || !(mainMelee || offMelee)) {
			return;
		}
		double distance = target == null ? Double.MAX_VALUE : villager.distanceTo(target);
		boolean wantRanged = distance > RefugeeConfig.combatRangedDistance;
		if (wantRanged && target != null) {
			wantRanged = rangedShotAllowed(villager, target);
		}
		if (wantRanged && offRanged && !mainRanged) {
			swapHands(villager);
		} else if (!wantRanged && offMelee && !mainMelee) {
			swapHands(villager);
		}
	}

	/** 远距射击要眼睛到目标躯干没有方块挡住。同一目标每 5 tick 查一次。 */
	private static boolean rangedShotAllowed(Villager villager, LivingEntity target) {
		if (target == null) {
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		int tickCount = villager.tickCount;
		if (!data.rangedLosDue(target.getUUID(), tickCount)) {
			return data.rangedLosCanShoot();
		}
		return data.applyRangedLos(target.getUUID(), tickCount, rangedShotClear(villager, target));
	}

	private static boolean rangedShotClear(Villager villager, LivingEntity target) {
		Vec3 from = villager.getEyePosition();
		Vec3 to = target.getBoundingBox().getCenter();
		if (from.distanceToSqr(to) < 1.0E-7) {
			return true;
		}
		return villager.level().clip(new ClipContext(
				from,
				to,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				villager
		)).getType() == HitResult.Type.MISS;
	}

	/** 主手没有武器、副手有时，把武器换到主手。 */
	public static void preferWeaponMainHand(Villager villager) {
		if (RefugeeRoles.isWeapon(villager.getMainHandItem())) {
			return;
		}
		if (RefugeeRoles.isWeapon(villager.getOffhandItem())) {
			swapHands(villager);
		}
	}

	private static void swapHands(Villager villager) {
		ItemStack main = villager.getMainHandItem();
		ItemStack off = villager.getOffhandItem();
		villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		villager.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		villager.setItemSlot(EquipmentSlot.MAINHAND, off);
		villager.setItemSlot(EquipmentSlot.OFFHAND, main);
	}

	public static ItemStack stackIn(Villager villager, InteractionHand hand) {
		return hand == InteractionHand.OFF_HAND ? villager.getOffhandItem() : villager.getMainHandItem();
	}

	public static void attackWith(Villager villager, LivingEntity target, InteractionHand hand) {
		ItemStack weapon = stackIn(villager, hand);
		if (!RefugeeRoles.isWeapon(weapon) || target == null || !target.isAlive()) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (hand == InteractionHand.OFF_HAND) {
			if (data.offAttackCooldown() > 0) {
				return;
			}
		} else if (data.mainAttackCooldown() > 0) {
			return;
		}
		if (RefugeeRoles.isRangedWeapon(weapon)) {
			drawOrShoot(villager, target, weapon, hand, data);
			return;
		}
		if (villager.distanceTo(target) >= 2.2) {
			return;
		}
		if (!melee(villager, target, hand)) {
			return;
		}
		LivabilityService.noteAttack(villager);
		setHandCooldown(villager, data, hand, RefugeeConfig.meleeAttackIntervalTicks);
	}

	private static void setHandCooldown(Villager villager, RefugeeVillagerData data, InteractionHand hand, int ticks) {
		if (hand == InteractionHand.OFF_HAND) {
			data.setOffAttackCooldown(ticks);
		} else {
			data.setMainAttackCooldown(ticks);
		}
		RefugeeAttachments.markDirty(villager, data);
	}

	/** 眼睛到对方眼睛没有方块挡住才出手。挡住时不挥击、不进冷却。 */
	private static boolean melee(Villager villager, LivingEntity target, InteractionHand hand) {
		if (!villager.getSensing().hasLineOfSight(target)) {
			return false;
		}
		villager.getLookControl().setLookAt(target, 30.0f, 30.0f);
		if (villager instanceof Mob mob && villager.level() instanceof ServerLevel level) {
			mob.doHurtTarget(level, target);
		}
		villager.swing(hand);
		return true;
	}

	private static void drawOrShoot(
			Villager villager,
			LivingEntity target,
			ItemStack weapon,
			InteractionHand hand,
			RefugeeVillagerData data
	) {
		villager.getLookControl().setLookAt(target, 30.0f, 30.0f);
		if (!isDrawingRanged(villager) || villager.getUsedItemHand() != hand) {
			if (villager.isUsingItem()) {
				villager.stopUsingItem();
			}
			villager.startUsingItem(hand);
			return;
		}
		if (villager.getTicksUsingItem() < rangedDrawTicks(villager, weapon)) {
			return;
		}
		shoot(villager, target, weapon);
		LivabilityService.noteAttack(villager);
		villager.stopUsingItem();
		setHandCooldown(villager, data, hand, RefugeeConfig.rangedAttackIntervalTicks);
	}

	private static int rangedDrawTicks(Villager villager, ItemStack weapon) {
		if (weapon.getItem() instanceof CrossbowItem) {
			return Math.max(1, CrossbowItem.getChargeDuration(weapon, villager));
		}
		return 20;
	}

	private static void shoot(Villager villager, LivingEntity target, ItemStack weapon) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		villager.getLookControl().setLookAt(target, 30.0f, 30.0f);
		ItemStack ammo = new ItemStack(Items.ARROW);
		net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(level, villager, ammo, weapon.copy());
		double dx = target.getX() - villager.getX();
		double dy = target.getY(1.0 / 3.0) - arrow.getY();
		double dz = target.getZ() - villager.getZ();
		double horiz = Math.sqrt(dx * dx + dz * dz);
		float speed = 1.6F;
		if (weapon.getItem() instanceof BowItem) {
			speed *= BowItem.getPowerForTime(villager.getTicksUsingItem());
		}
		float inaccuracy = (float) (14 - level.getDifficulty().getId() * 4);
		arrow.shoot(dx, dy + horiz * 0.2F, dz, speed, inaccuracy);
		arrow.setBaseDamage(2.0);
		level.addFreshEntity(arrow);
		weapon.hurtAndBreak(1, villager, villager.getEquipmentSlotForItem(weapon));
	}

	static void logPanic(Villager villager, String message) {
		// float max = villager.getMaxHealth();
		// float ratio = max <= 0.0f ? 0.0f : villager.getHealth() / max;
		// Refugee.LOGGER.info(
		// 		"[panic] {} hp={}% mood={} :: {}",
		// 		villager.getUUID(),
		// 		(int) (ratio * 100.0f),
		// 		mood(villager),
		// 		message
		// );
	}
}
