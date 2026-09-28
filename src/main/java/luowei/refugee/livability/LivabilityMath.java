package luowei.refugee.livability;

/**
 * 宜居度纯计算。饥饿值与饱食度是同一个 0–20 的数：越高越饱。
 * 居住舒适度 = 基准 + 床间隔变化 + 连片床数变化，再夹到 0–20。
 * 没有床时居住舒适度为 0。
 */
public final class LivabilityMath {
	private LivabilityMath() {
	}

	public static double clampStat(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	/** 整数个面包的累计补充；超出表长后，每多一个的增量按尾部衰减。 */
	public static double foodGain(double breads, double[] cumulative, double tailDecay) {
		if (breads <= 0.0 || cumulative == null || cumulative.length == 0) {
			return 0.0;
		}
		double whole = Math.floor(breads);
		double fraction = breads - whole;
		double atWhole = cumulativeAt((int) whole, cumulative, tailDecay);
		if (fraction <= 0.0) {
			return atWhole;
		}
		double atNext = cumulativeAt((int) whole + 1, cumulative, tailDecay);
		return atWhole + fraction * (atNext - atWhole);
	}

	public static double cumulativeAt(int count, double[] cumulative, double tailDecay) {
		if (count <= 0) {
			return 0.0;
		}
		if (count <= cumulative.length) {
			return cumulative[count - 1];
		}
		double last = cumulative[cumulative.length - 1];
		double step = cumulative.length == 1 ? last : last - cumulative[cumulative.length - 2];
		double decay = clampStat(tailDecay, 0.0, 1.0);
		double extra = 0.0;
		double increment = step;
		for (int i = cumulative.length; i < count; i++) {
			increment *= decay;
			extra += increment;
		}
		return last + extra;
	}

	/**
	 * 区间内取端点值，区间之间线性插值。
	 * 0 → -1；(1–5) → -0.5；(6–10) → -0.1；(11–15) → 0.1；(16–20) → 0.25。
	 */
	public static double hungerFactor(double satiety, double[][] knots) {
		double x = clampStat(satiety, 0.0, 20.0);
		if (knots == null || knots.length == 0) {
			return 0.0;
		}
		if (x <= knots[0][0]) {
			return knots[0][1];
		}
		for (int i = 1; i < knots.length; i++) {
			double x0 = knots[i - 1][0];
			double x1 = knots[i][0];
			if (x <= x1) {
				double span = x1 - x0;
				if (span <= 0.0) {
					return knots[i][1];
				}
				double t = (x - x0) / span;
				return knots[i - 1][1] + t * (knots[i][1] - knots[i - 1][1]);
			}
		}
		return knots[knots.length - 1][1];
	}

	public static double satietyDelta(boolean worked, boolean slept, double breads, LivabilityRules rules) {
		double delta = rules.satietyBase;
		if (worked) {
			delta += rules.satietyLabor;
		}
		if (!slept) {
			delta += rules.satietyNoSleep;
		}
		delta += foodGain(breads, rules.foodGains, rules.foodTailDecay);
		return delta;
	}

	public static double nextDailySatiety(double current, boolean slept, LivabilityRules rules) {
		double delta = rules.satietyBase;
		if (!slept) {
			delta += rules.satietyNoSleep;
		}
		return clampStat(current + delta, rules.statMin, rules.statMax);
	}

	public static double staminaDelta(double satiety, boolean worked, boolean slept, LivabilityRules rules) {
		double beta = hungerFactor(satiety, rules.hungerKnots);
		double labor = worked
				? rules.staminaLaborScale * (-1.0 + beta)
				: rules.staminaRestScale * (1.0 + beta);
		double sleep = slept
				? rules.staminaSleptScale * (1.0 + beta)
				: rules.staminaAwakeScale * (-1.0 + beta);
		return labor + sleep;
	}

	public static double nextStamina(double current, double satiety, boolean worked, boolean slept, LivabilityRules rules) {
		return clampStat(current + staminaDelta(satiety, worked, slept, rules), rules.statMin, rules.statMax);
	}

	/** 没有床时返回 0。有床时为基准加两项变化量。 */
	public static double livingComfort(boolean hasBed, int gap, int clusterSize, LivabilityRules rules) {
		if (!hasBed) {
			return 0.0;
		}
		double value = rules.comfortBase + bedGapDelta(gap, rules) + bedClusterDelta(clusterSize, rules);
		return clampStat(value, rules.statMin, rules.statMax);
	}

	public static double bedGapDelta(int gap, LivabilityRules rules) {
		if (gap <= 0) {
			return rules.gap0;
		}
		if (gap == 1) {
			return rules.gap1;
		}
		if (gap == 2) {
			return rules.gap2;
		}
		return rules.gapFar;
	}

	public static double bedClusterDelta(int size, LivabilityRules rules) {
		int count = Math.max(1, size);
		if (count <= 1) {
			return rules.cluster1;
		}
		if (count == 2) {
			return rules.cluster2;
		}
		if (count <= 4) {
			return rules.cluster4;
		}
		if (count <= 8) {
			return rules.cluster8;
		}
		if (count <= 16) {
			return rules.cluster16;
		}
		if (count <= 32) {
			return rules.cluster32;
		}
		if (count <= 64) {
			return rules.cluster64;
		}
		return rules.cluster65;
	}

	public static double nextComfort(double current, double living, LivabilityRules rules) {
		double mixed = current * rules.comfortKeep + living * rules.comfortBlend;
		return clampStat(mixed, rules.statMin, rules.statMax);
	}

	/** 公式原值，可以为负。 */
	public static double laborEfficiency(double stamina, double satiety, double comfort, LivabilityRules rules) {
		double raw = rules.effectBase
				+ (stamina - rules.laborStaminaOrigin) * rules.laborStaminaWeight
				+ (satiety - rules.laborSatietyOrigin) * rules.laborSatietyWeight
				+ (comfort - rules.laborComfortOrigin) * rules.laborComfortWeight;
		return raw / rules.effectDivisor;
	}

	/** (体力-5)×0.2 + (饱食-5)×0.08 + (舒适-10)×0.12。可以为负。 */
	public static double healEfficiency(double stamina, double satiety, double comfort, LivabilityRules rules) {
		return (stamina - rules.healStaminaOrigin) * rules.healStaminaWeight
				+ (satiety - rules.healSatietyOrigin) * rules.healSatietyWeight
				+ (comfort - rules.healComfortOrigin) * rules.healComfortWeight;
	}

	/** 真正拿去改间隔、回血时不为负。 */
	public static double appliedEfficiency(double raw) {
		return Math.max(0.0, raw);
	}

	public static double loyalty(double satiety, double stamina, double comfort, LivabilityRules rules) {
		return capPositive(satiety - rules.loyaltySatietyOrigin, rules.loyaltyCap)
				+ capPositive(stamina - rules.loyaltyStaminaOrigin, rules.loyaltyCap)
				+ capPositive(comfort - rules.loyaltyComfortOrigin, rules.loyaltyCap);
	}

	private static double capPositive(double value, double cap) {
		return Math.min(cap, value);
	}

	/** 忠诚度 ≥ 0 时为 0。接近下限时按幂次升到 1。 */
	public static double rebellionChance(double loyalty, LivabilityRules rules) {
		if (loyalty >= 0.0) {
			return 0.0;
		}
		double span = Math.max(0.0001, rules.loyaltyFloorMagnitude);
		double t = clampStat(-loyalty / span, 0.0, 1.0);
		return Math.pow(t, Math.max(0.01, rules.rebellionExponent));
	}

	public static int scaledInterval(int baseTicks, double efficiency) {
		double applied = appliedEfficiency(efficiency);
		if (applied <= 0.05) {
			applied = 0.05;
		}
		return Math.max(1, (int) Math.round(baseTicks / applied));
	}

	public static void main(String[] args) {
		LivabilityRules rules = LivabilityRules.defaults();
		System.out.println("=== food gain (breads) ===");
		for (int n = 0; n <= 8; n++) {
			System.out.printf("breads %d gain %.3f%n", n, foodGain(n, rules.foodGains, rules.foodTailDecay));
		}
		System.out.println("=== hunger factor ===");
		for (int s = 0; s <= 20; s++) {
			System.out.printf("satiety %d beta %.3f%n", s, hungerFactor(s, rules.hungerKnots));
		}
		System.out.println("=== one day from 10/10/10 ===");
		boolean[] flags = {false, true};
		for (boolean worked : flags) {
			for (boolean slept : flags) {
				for (int breads : new int[] {0, 1, 2, 5}) {
					double sat = nextDailySatiety(10, slept, rules);
					double sta = nextStamina(10, sat, worked, slept, rules);
					System.out.printf(
							"work=%s sleep=%s breads=%d -> satiety %.2f stamina %.2f (delta sta %.2f)%n",
							worked, slept, breads, sat, sta, staminaDelta(sat, worked, slept, rules)
					);
				}
			}
		}
		System.out.println("=== housing equilibrium (start 10, 30 days) ===");
		int[] gaps = {0, 1, 2, 3, 4};
		int[] clusters = {1, 2, 4, 8, 16, 32, 64, 65};
		for (int gap : gaps) {
			for (int cluster : clusters) {
				double living = livingComfort(true, gap, cluster, rules);
				double comfort = 10;
				for (int day = 0; day < 30; day++) {
					comfort = nextComfort(comfort, living, rules);
				}
				System.out.printf("gap %d cluster %d living %.1f comfort30 %.2f%n", gap, cluster, living, comfort);
			}
		}
		double homeless = 10;
		for (int day = 0; day < 30; day++) {
			homeless = nextComfort(homeless, 0, rules);
		}
		System.out.printf("no bed comfort30 %.2f%n", homeless);
		System.out.println("=== efficiency and loyalty corners ===");
		int[] samples = {0, 5, 10, 20};
		for (int sta : samples) {
			for (int sat : samples) {
				for (int com : samples) {
					double labor = laborEfficiency(sta, sat, com, rules);
					double heal = healEfficiency(sta, sat, com, rules);
					double loyal = loyalty(sat, sta, com, rules);
					System.out.printf(
							"sta %d sat %d com %d labor %.3f heal %.3f loyalty %.1f revolt %.3f%n",
							sta, sat, com, labor, heal, loyal, rebellionChance(loyal, rules)
					);
				}
			}
		}
		System.out.println("=== 7 day traces ===");
		trace("rest sleep 1 bread private", rules, false, true, 1, true, 4, 1);
		trace("work sleep 1 bread private", rules, true, true, 1, true, 4, 1);
		trace("work no-sleep 0 bread barracks", rules, true, false, 0, true, 0, 32);
		trace("work no-sleep 0 bread homeless", rules, true, false, 0, false, 0, 0);
		trace("rest sleep 5 bread private", rules, false, true, 5, true, 4, 1);
	}

	private static void trace(
			String name,
			LivabilityRules rules,
			boolean worked,
			boolean slept,
			int breads,
			boolean hasBed,
			int gap,
			int cluster
	) {
		double sat = 10;
		double sta = 10;
		double com = 10;
		System.out.println("-- " + name);
		for (int day = 1; day <= 7; day++) {
			sat = nextDailySatiety(sat, slept, rules);
			sta = nextStamina(sta, sat, worked, slept, rules);
			double living = livingComfort(hasBed, gap, cluster, rules);
			com = nextComfort(com, living, rules);
			double loyal = loyalty(sat, sta, com, rules);
			System.out.printf(
					"day %d sat %.2f sta %.2f com %.2f labor %.3f heal %.3f loyalty %.2f revolt %.3f%n",
					day,
					sat,
					sta,
					com,
					laborEfficiency(sta, sat, com, rules),
					healEfficiency(sta, sat, com, rules),
					loyal,
					rebellionChance(loyal, rules)
			);
		}
	}
}
