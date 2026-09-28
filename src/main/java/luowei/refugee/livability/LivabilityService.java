package luowei.refugee.livability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.phys.AABB;

import luowei.refugee.attachment.RefugeeAttachments;

/**
 * 白天记下睡觉、劳动和进食。第二天清晨用前一天的记录结算饱食、体力和舒适。
 * 卸载期间错过的天数只结算一次，不把空日子叠加上去。
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
		villager.setAttached(RefugeeAttachments.LIVABILITY, data);
	}

	public static boolean isRebelling(Villager villager) {
		LivabilityData data = villager.getAttached(RefugeeAttachments.LIVABILITY);
		return data != null && data.rebelling();
	}

	/** 体力已经耗到 0，不再劳作、不再出手。 */
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
		data.markSlept();
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
		Map<Villager, LivabilityBeds.Housing> housing = LivabilityBeds.score(level, due, rules);
		for (Villager villager : due) {
			LivabilityData data = get(villager);
			double taken = data.satiety() * rules.satietyToStaminaRate;
			double satiety = LivabilityMath.clampStat(data.satiety() - taken, rules.statMin, rules.statMax);
			double stamina = data.stamina() + rules.dawnStaminaGain + taken;
			if (!data.slept()) {
				stamina -= rules.missedSleepStamina;
			}
			stamina = LivabilityMath.clampStat(stamina, rules.statMin, rules.statMax);
			LivabilityBeds.Housing home = housing.get(villager);
			double living = LivabilityMath.livingComfort(
					home != null && home.hasBed(),
					home == null ? 0 : home.gap(),
					home == null ? 0 : home.clusterSize(),
					rules
			);
			double comfort = LivabilityMath.nextComfort(data.comfort(), living, rules);
			data.applyDay(satiety, stamina, comfort, day, false);
			markDirty(villager, data);
		}
		spreadRebellion(level, due, rules);
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
