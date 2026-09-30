package luowei.refugee.livability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 村民当天尚未结算的吃饭、睡觉、劳动，以及上一天结算后的三项状态。
 */
public final class LivabilityData {
	public static final Codec<LivabilityData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.DOUBLE.optionalFieldOf("satiety", 10.0).forGetter(data -> data.satiety),
			Codec.DOUBLE.optionalFieldOf("stamina", 10.0).forGetter(data -> data.stamina),
			Codec.DOUBLE.optionalFieldOf("comfort", 10.0).forGetter(data -> data.comfort),
			Codec.DOUBLE.optionalFieldOf("breads", 0.0).forGetter(data -> data.breads),
			Codec.BOOL.optionalFieldOf("worked", false).forGetter(data -> data.worked),
			Codec.BOOL.optionalFieldOf("slept", false).forGetter(data -> data.slept),
			Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(data -> data.lastDay),
			Codec.BOOL.optionalFieldOf("rebelling", false).forGetter(data -> data.rebelling),
			Codec.DOUBLE.optionalFieldOf("labor", 0.5).forGetter(data -> data.laborEfficiency),
			Codec.DOUBLE.optionalFieldOf("heal", 0.5).forGetter(data -> data.healEfficiency),
			Codec.DOUBLE.optionalFieldOf("loyalty", 0.0).forGetter(data -> data.loyalty),
			Codec.INT.optionalFieldOf("labor_points", 0).forGetter(data -> data.laborPoints),
			Codec.INT.optionalFieldOf("attack_points", 0).forGetter(data -> data.attackPoints),
			Codec.LONG.optionalFieldOf("metabolism_at", -1L).forGetter(data -> data.metabolismAt),
			Codec.LONG.optionalFieldOf("sleep_started", -1L).forGetter(data -> data.sleepStarted),
			Codec.INT.optionalFieldOf("sleep_ticks", 0).forGetter(data -> data.sleepTicks)
	).apply(instance, LivabilityData::new));

	private double satiety;
	private double stamina;
	private double comfort;
	private double breads;
	private boolean worked;
	private boolean slept;
	private long lastDay;
	private boolean rebelling;
	private double laborEfficiency;
	private double healEfficiency;
	private double loyalty;
	private int laborPoints;
	private int attackPoints;
	private long metabolismAt = -1L;
	private long sleepStarted = -1L;
	private int sleepTicks;
	/** 上次写进组织忠诚总和的值。只在这次加载里有效，重新加载时按当前忠诚重新对齐。 */
	private double censusLoyalty;
	private boolean censusBound;
	private int moodOffset;

	public LivabilityData() {
		LivabilityRules rules = LivabilityRules.CURRENT;
		this.satiety = rules.initialSatiety;
		this.stamina = rules.initialStamina;
		this.comfort = rules.initialComfort;
		this.lastDay = -1L;
		refreshEffects();
	}

	public LivabilityData(
			double satiety,
			double stamina,
			double comfort,
			double breads,
			boolean worked,
			boolean slept,
			long lastDay,
			boolean rebelling,
			double laborEfficiency,
			double healEfficiency,
			double loyalty,
			int laborPoints,
			int attackPoints,
			long metabolismAt,
			long sleepStarted,
			int sleepTicks
	) {
		this.satiety = satiety;
		this.stamina = stamina;
		this.comfort = comfort;
		this.breads = breads;
		this.worked = worked;
		this.slept = slept;
		this.lastDay = lastDay;
		this.rebelling = rebelling;
		this.laborEfficiency = laborEfficiency;
		this.healEfficiency = healEfficiency;
		this.loyalty = loyalty;
		this.laborPoints = Math.max(0, laborPoints);
		this.attackPoints = Math.max(0, attackPoints);
		this.metabolismAt = metabolismAt;
		this.sleepStarted = sleepStarted;
		this.sleepTicks = Math.max(0, sleepTicks);
	}

	public double satiety() {
		return satiety;
	}

	public double stamina() {
		return stamina;
	}

	public double comfort() {
		return comfort;
	}

	/** 存着的舒适加上死气沉沉或振奋，再夹到状态上下限。清晨结算仍读 {@link #comfort()}。 */
	public double effectiveComfort() {
		LivabilityRules rules = LivabilityRules.CURRENT;
		return LivabilityMath.clampStat(comfort + moodOffset, rules.statMin, rules.statMax);
	}

	public int moodOffset() {
		return moodOffset;
	}

	/** @return 偏移变了，忠诚已重算 */
	public boolean setMoodOffset(int moodOffset) {
		if (this.moodOffset == moodOffset) {
			return false;
		}
		this.moodOffset = moodOffset;
		refreshEffects();
		return true;
	}

	public double censusLoyalty() {
		return censusLoyalty;
	}

	public void setCensusLoyalty(double censusLoyalty) {
		this.censusLoyalty = censusLoyalty;
		this.censusBound = true;
	}

	public boolean censusBound() {
		return censusBound;
	}

	public double breads() {
		return breads;
	}

	public boolean worked() {
		return worked;
	}

	public boolean slept() {
		return slept;
	}

	public long lastDay() {
		return lastDay;
	}

	public boolean rebelling() {
		return rebelling;
	}

	public void setRebelling(boolean rebelling) {
		this.rebelling = rebelling;
	}

	public double laborEfficiency() {
		return laborEfficiency;
	}

	public double healEfficiency() {
		return healEfficiency;
	}

	public double loyalty() {
		return loyalty;
	}

	public void addSatiety(double amount) {
		LivabilityRules rules = LivabilityRules.CURRENT;
		satiety = LivabilityMath.clampStat(satiety + amount, rules.statMin, rules.statMax);
		refreshEffects();
	}

	public void addStamina(double amount) {
		LivabilityRules rules = LivabilityRules.CURRENT;
		stamina = LivabilityMath.clampStat(stamina + amount, rules.statMin, rules.statMax);
		refreshEffects();
	}

	public void addComfort(double amount) {
		LivabilityRules rules = LivabilityRules.CURRENT;
		comfort = LivabilityMath.clampStat(comfort + amount, rules.statMin, rules.statMax);
		refreshEffects();
	}

	/** 挖、放或催熟记 1，共用一条计数。满阈值立刻扣 1 点体力和一笔饱食，余数留下。 */
	public void noteBlockWork() {
		worked = true;
		laborPoints++;
		LivabilityRules rules = LivabilityRules.CURRENT;
		int threshold = Math.max(1, rules.laborBlockThreshold);
		if (laborPoints >= threshold) {
			int steps = laborPoints / threshold;
			laborPoints -= steps * threshold;
			addStamina(-steps);
			addSatiety(-steps * rules.laborSatietyCost);
		}
	}

	/** 真正打出一次。满阈值立刻饱食和体力各扣 1，余数留下。 */
	public void noteAttack() {
		attackPoints++;
		int threshold = Math.max(1, LivabilityRules.CURRENT.attackThreshold);
		if (attackPoints >= threshold) {
			int steps = attackPoints / threshold;
			attackPoints -= steps * threshold;
			addSatiety(-steps);
			addStamina(-steps);
		}
	}

	public void noteFertilize() {
		noteBlockWork();
	}

	public void markWorked() {
		worked = true;
	}

	public void markSlept() {
		slept = true;
	}

	public void beginSleep(long now) {
		slept = true;
		if (sleepStarted < 0L) {
			sleepStarted = now;
		}
	}

	public void endSleep(long now) {
		if (sleepStarted >= 0L) {
			addSleepElapsed(now - sleepStarted);
			sleepStarted = -1L;
		}
		slept = true;
	}

	/** 把到现在为止的睡眠并进累计，然后交出累计值。人还在睡就把起点改到现在。 */
	public int consumeSleep(long now, boolean stillSleeping) {
		if (sleepStarted >= 0L) {
			addSleepElapsed(now - sleepStarted);
			sleepStarted = stillSleeping ? now : -1L;
		}
		int taken = Math.max(0, sleepTicks);
		sleepTicks = 0;
		return taken;
	}

	public long metabolismAt() {
		return metabolismAt;
	}

	public void setMetabolismAt(long metabolismAt) {
		this.metabolismAt = metabolismAt;
	}

	private void addSleepElapsed(long elapsed) {
		if (elapsed <= 0L) {
			return;
		}
		long sum = (long) sleepTicks + elapsed;
		sleepTicks = (int) Math.min(sum, Integer.MAX_VALUE);
	}

	public void applyDay(double nextSatiety, double nextStamina, double nextComfort, long day, boolean rebelling) {
		this.satiety = nextSatiety;
		this.stamina = nextStamina;
		this.comfort = nextComfort;
		this.lastDay = day;
		this.rebelling = rebelling;
		this.breads = 0.0;
		this.worked = false;
		this.slept = false;
		this.sleepTicks = 0;
		refreshEffects();
	}

	public void refreshEffects() {
		LivabilityRules rules = LivabilityRules.CURRENT;
		laborEfficiency = LivabilityMath.laborEfficiency(stamina, satiety, comfort, rules);
		healEfficiency = LivabilityMath.healEfficiency(stamina, satiety, comfort, rules);
		loyalty = LivabilityMath.loyalty(satiety, stamina, effectiveComfort(), rules);
	}
}
