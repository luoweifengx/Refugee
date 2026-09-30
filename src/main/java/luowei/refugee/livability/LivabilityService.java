package luowei.refugee.livability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import luowei.refugee.ai.BedClaim;
import luowei.refugee.attachment.RefugeeAttachments;

/**
 * 饱食和体力按游戏时间每 3000 tick 结算一次，只在居民加载时走表。
 * 舒适在睡觉时按床的居住分逐 tick 增加。没睡过的人在天数变化时减 1。卸载错过的天数只结算一次。
 */
public final class LivabilityService {
	private static long settledDay = Long.MIN_VALUE;

	private LivabilityService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LivabilityService::onServerTick);
	}

	public static LivabilityData get(Villager villager) {
		return villager.getAttachedOrCreate(RefugeeAttachments.LIVABILITY);
	}

	public static void markDirty(Villager villager, LivabilityData data) {
		CensusService.onLoyaltyChanged(villager, data);
		villager.setAttached(RefugeeAttachments.LIVABILITY, data);
	}

	public static boolean isRebelling(Villager villager) {
		LivabilityData data = villager.getAttached(RefugeeAttachments.LIVABILITY);
		return data != null && data.rebelling();
	}

	/** 体力已经耗到 0。此时会挂上挖掘疲劳 III 和虚弱 III。 */
	public static boolean isSpent(Villager villager) {
		if (villager == null || !RefugeeAttachments.isRefugee(villager)) {
			return false;
		}
		LivabilityData data = villager.getAttached(RefugeeAttachments.LIVABILITY);
		return data != null && data.stamina() <= 0.0;
	}

	public static int workInterval(Villager villager, int baseTicks) {
		LivabilityData data = villager.getAttached(RefugeeAttachments.LIVABILITY);
		double efficiency = data == null ? 0.5 : data.laborEfficiency();
		return LivabilityMath.scaledInterval(baseTicks, efficiency);
	}

	public static float healAmount(Villager villager, float baseHeal) {
		LivabilityData data = villager.getAttached(RefugeeAttachments.LIVABILITY);
		double efficiency = data == null ? 0.5 : LivabilityMath.appliedEfficiency(data.healEfficiency());
		return (float) (baseHeal * efficiency);
	}

	/** 实际掉血立刻扣舒适。睡觉中的加算和没睡的扣减另走。 */
	public static void noteHurt(Villager villager, float damageTaken) {
		if (villager == null || damageTaken <= 0.0f || !RefugeeAttachments.isRefugee(villager)) {
			return;
		}
		double loss = damageTaken * LivabilityRules.CURRENT.hurtComfortScale;
		if (loss == 0.0) {
			return;
		}
		LivabilityData data = get(villager);
		data.addComfort(-loss);
		markDirty(villager, data);
	}

	public static void noteFood(Villager villager, FoodProperties properties) {
		if (properties == null || !RefugeeAttachments.isRefugee(villager)) {
			return;
		}
		LivabilityData data = get(villager);
		data.addSatiety(properties.nutrition());
		markDirty(villager, data);
	}

	public static void noteBlockWork(Villager villager) {
		if (!RefugeeAttachments.isRefugee(villager)) {
			return;
		}
		LivabilityData data = get(villager);
		data.noteBlockWork();
		markDirty(villager, data);
	}

	public static void noteFertilize(Villager villager) {
		if (!RefugeeAttachments.isRefugee(villager)) {
			return;
		}
		LivabilityData data = get(villager);
		data.noteFertilize();
		markDirty(villager, data);
	}

	public static void noteAttack(Villager villager) {
		if (!RefugeeAttachments.isRefugee(villager)) {
			return;
		}
		LivabilityData data = get(villager);
		data.noteAttack();
		markDirty(villager, data);
	}

	public static void noteSlept(Villager villager) {
		if (!RefugeeAttachments.isRefugee(villager)) {
			return;
		}
		LivabilityData data = get(villager);
		data.beginSleep(villager.level().getGameTime());
		markDirty(villager, data);
	}

	public static void noteWake(Villager villager) {
		if (!RefugeeAttachments.isRefugee(villager)) {
			return;
		}
		LivabilityData data = get(villager);
		data.endSleep(villager.level().getGameTime());
		markDirty(villager, data);
	}

	/** 加载着的居民，距上次结算满间隔就做一次饱食和体力。 */
	public static void tickMetabolism(Villager villager) {
		if (villager == null || !RefugeeAttachments.isRefugee(villager)) {
			return;
		}
		LivabilityRules rules = LivabilityRules.CURRENT;
		LivabilityData data = get(villager);
		long now = villager.level().getGameTime();
		int interval = Math.max(1, rules.metabolismInterval);
		if (data.metabolismAt() < 0L) {
			data.setMetabolismAt(now);
			markDirty(villager, data);
			return;
		}
		long elapsed = now - data.metabolismAt();
		if (elapsed < interval) {
			return;
		}
		if (elapsed > interval + 100L) {
			data.setMetabolismAt(now);
			markDirty(villager, data);
			return;
		}
		metabolize(villager, data, rules, now);
	}

	/** 躺着时每个 tick 加 0.0001 × 所认床的居住分。没有床或已经到顶则不加。 */
	public static void tickSleepComfort(Villager villager) {
		if (villager == null || !villager.isSleeping() || !RefugeeAttachments.isRefugee(villager)) {
			return;
		}
		LivabilityRules rules = LivabilityRules.CURRENT;
		double bed = livingAt(villager, rules);
		if (bed <= 0.0) {
			return;
		}
		LivabilityData data = get(villager);
		if (data.comfort() >= rules.statMax) {
			return;
		}
		data.addComfort(bed * 0.0001);
		markDirty(villager, data);
	}

	private static void metabolize(Villager villager, LivabilityData data, LivabilityRules rules, long now) {
		double satiety = data.satiety();
		double stamina = data.stamina();
		double comfort = data.comfort();
		data.addSatiety(LivabilityMath.metabolizedSatiety(satiety, comfort, rules) - satiety);
		data.addStamina(LivabilityMath.metabolizedStamina(stamina, satiety, comfort, rules) - stamina);
		data.setMetabolismAt(now);
		markDirty(villager, data);
	}

	private static void onServerTick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return;
		}
		long day = Math.floorDiv(overworld.getDayTime(), 24000L);
		if (day == settledDay) {
			return;
		}
		if (settledDay == Long.MIN_VALUE) {
			settledDay = day;
			return;
		}
		settledDay = day;
		for (ServerLevel level : server.getAllLevels()) {
			settleLevel(level, day);
		}
		LivabilityAchievements.check(server);
	}

	private static void settleLevel(ServerLevel level, long day) {
		List<Villager> due = new ArrayList<>();
		for (Villager villager : level.getEntities(EntityType.VILLAGER, Entity::isAlive)) {
			if (!RefugeeAttachments.isRefugee(villager)) {
				continue;
			}
			LivabilityData data = get(villager);
			if (data.lastDay() == day) {
				continue;
			}
			if (data.lastDay() < 0L) {
				data.applyDay(data.satiety(), data.stamina(), data.comfort(), day, false);
				markDirty(villager, data);
				continue;
			}
			due.add(villager);
		}
		if (due.isEmpty()) {
			return;
		}
		LivabilityRules rules = LivabilityRules.CURRENT;
		for (Villager villager : due) {
			settleOne(villager, day, rules);
		}
		spreadRebellion(level, due, rules);
	}

	/**
	 * 调试：对已经加载的居民各执行一次饱食和体力结算，并重置下次结算的计时。
	 */
	public static List<ForcedRecovery> forceStaminaRecovery(ServerLevel level, List<Villager> villagers) {
		List<ForcedRecovery> results = new ArrayList<>();
		if (level == null || villagers == null || villagers.isEmpty()) {
			return results;
		}
		LivabilityRules rules = LivabilityRules.CURRENT;
		long now = level.getGameTime();
		for (Villager villager : villagers) {
			if (villager == null || !villager.isAlive() || villager.level() != level || !RefugeeAttachments.isRefugee(villager)) {
				continue;
			}
			LivabilityData data = get(villager);
			double staminaBefore = data.stamina();
			double satietyBefore = data.satiety();
			metabolize(villager, data, rules, now);
			LivabilityData after = get(villager);
			results.add(new ForcedRecovery(
					villager,
					false,
					staminaBefore,
					after.stamina(),
					satietyBefore,
					after.satiety()
			));
		}
		return results;
	}

	public record ForcedRecovery(
			Villager villager,
			boolean slept,
			double staminaBefore,
			double staminaAfter,
			double satietyBefore,
			double satietyAfter
	) {
	}

	/** 清晨不重算舒适。这一晚没躺下就减 1；躺过或此刻还在睡的保持睡觉期间加上的值。 */
	private static void settleOne(Villager villager, long day, LivabilityRules rules) {
		LivabilityData data = get(villager);
		double comfort = data.comfort();
		if (!data.slept() && !villager.isSleeping()) {
			comfort = LivabilityMath.clampStat(comfort - 1.0, rules.statMin, rules.statMax);
		}
		data.applyDay(data.satiety(), data.stamina(), comfort, day, false);
		markDirty(villager, data);
	}

	private static double livingAt(Villager villager, LivabilityRules rules) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return 0.0;
		}
		BlockPos bed = claimedBed(level, villager);
		if (bed == null) {
			return 0.0;
		}
		BedLayout.Stats stats = BedLayout.stats(level, bed);
		return LivabilityMath.livingComfort(true, stats.gap(), stats.density(), rules);
	}

	private static BlockPos claimedBed(ServerLevel level, Villager villager) {
		GlobalPos home = villager.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
		if (home != null && home.dimension().equals(level.dimension()) && BedClaim.keeps(level, villager, home.pos())) {
			return BedClaim.head(level, home.pos());
		}
		if (!villager.isSleeping()) {
			return null;
		}
		BlockPos sleeping = villager.getSleepingPos().orElse(null);
		if (sleeping == null) {
			return null;
		}
		BlockPos head = BedClaim.head(level, sleeping);
		BlockState state = level.getBlockState(head);
		if (!(state.getBlock() instanceof BedBlock)) {
			return null;
		}
		return head;
	}

	/**
	 * 概率到 100% 的人先叛，并划入敌对阵营。再对半径内村民抽签；已经抽过的人再被扫到时再抽一次。
	 * 抽中的再向外传一圈，然后停。
	 */
	private static void spreadRebellion(ServerLevel level, List<Villager> settled, LivabilityRules rules) {
		double radius = Math.max(0.0, rules.rebellionSpreadRadius);
		List<Villager> seeds = new ArrayList<>();
		for (Villager villager : settled) {
			if (LivabilityMath.rebellionChance(get(villager).loyalty(), rules) >= 1.0 - 1.0e-9) {
				seeds.add(villager);
				joinHostileFaction(villager);
			}
		}
		Map<Villager, Integer> judged = new HashMap<>();
		List<Villager> frontier = seeds;
		for (int wave = 0; wave < 2 && !frontier.isEmpty(); wave++) {
			List<Villager> next = new ArrayList<>();
			for (Villager source : frontier) {
				AABB box = source.getBoundingBox().inflate(radius);
				for (Villager other : level.getEntitiesOfClass(Villager.class, box, candidate ->
						candidate.isAlive()
								&& candidate != source
								&& RefugeeAttachments.isRefugee(candidate)
								&& !RefugeeAttachments.get(candidate).isHostileFaction()
								&& candidate.distanceToSqr(source) <= radius * radius
				)) {
					int times = judged.getOrDefault(other, 0);
					if (times >= 2) {
						continue;
					}
					judged.put(other, times + 1);
					double chance = LivabilityMath.rebellionChance(get(other).loyalty(), rules);
					if (other.getRandom().nextDouble() >= chance) {
						continue;
					}
					joinHostileFaction(other);
					next.add(other);
				}
			}
			frontier = next;
		}
	}

	/** 调试：立刻让这名居民叛乱并加入敌对阵营。 */
	public static void forceRebel(Villager villager) {
		if (villager == null || !RefugeeAttachments.isRefugee(villager)) {
			return;
		}
		joinHostileFaction(villager);
	}

	/**
	 * 调试：按当前忠诚度的叛乱概率抽一次。已经叛乱的不再抽。
	 *
	 * @return 抽中并叛乱时为 true
	 */
	public static boolean tryRebelOnce(Villager villager) {
		if (villager == null || !RefugeeAttachments.isRefugee(villager) || isRebelling(villager)) {
			return false;
		}
		double chance = LivabilityMath.rebellionChance(get(villager).loyalty(), LivabilityRules.CURRENT);
		if (villager.getRandom().nextDouble() >= chance) {
			return false;
		}
		joinHostileFaction(villager);
		return true;
	}

	public static double rebellionChance(Villager villager) {
		if (villager == null || !RefugeeAttachments.isRefugee(villager)) {
			return 0.0;
		}
		return LivabilityMath.rebellionChance(get(villager).loyalty(), LivabilityRules.CURRENT);
	}

	private static void joinHostileFaction(Villager villager) {
		LivabilityData live = get(villager);
		live.setRebelling(true);
		markDirty(villager, live);
		luowei.refugee.attachment.RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (!data.isHostileFaction()) {
			data.setHostileFaction(true);
			RefugeeAttachments.markDirty(villager, data);
		}
	}
}
