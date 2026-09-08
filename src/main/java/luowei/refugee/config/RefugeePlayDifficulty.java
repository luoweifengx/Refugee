package luowei.refugee.config;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;

/**
 * 难民开局人数绑定原版难度；入境人数在档位区间上抽完后再乘难度乘数。
 */
public enum RefugeePlayDifficulty {
	HARDCORE,
	HARD,
	NORMAL,
	EASY,
	PEACEFUL;

	public static RefugeePlayDifficulty of(ServerLevel level) {
		if (level == null) {
			return HARD;
		}
		if (level.getLevelData().isHardcore()) {
			return HARDCORE;
		}
		Difficulty difficulty = level.getDifficulty();
		if (difficulty == null) {
			return HARD;
		}
		return switch (difficulty) {
			case PEACEFUL -> PEACEFUL;
			case EASY -> EASY;
			case NORMAL -> NORMAL;
			case HARD -> HARD;
		};
	}

	public int starterRefugeeCount() {
		return switch (this) {
			case HARDCORE -> 0;
			case HARD -> 1;
			case NORMAL -> 4;
			case EASY, PEACEFUL -> 8;
		};
	}

	public boolean spawnGuide() {
		return this != HARDCORE;
	}

	public boolean playerDeathSacrifices() {
		return this != PEACEFUL;
	}

	/** 简单为基准 1；普通 7/6、困难/极限 4/3，抽中人数后再乘并向上取整。 */
	public double arrivalMultiplier() {
		return switch (this) {
			case HARDCORE, HARD -> 4.0 / 3.0;
			case NORMAL -> 7.0 / 6.0;
			case EASY, PEACEFUL -> 1.0;
		};
	}

	public int rollArrivalCount(RandomSource random, int minCount, int maxCount) {
		int lo = Math.max(1, Math.min(minCount, maxCount));
		int hi = Math.max(lo, Math.max(minCount, maxCount));
		int rolled = random == null ? lo : lo + random.nextInt(hi - lo + 1);
		return Math.max(1, (int) Math.ceil(rolled * arrivalMultiplier()));
	}
}
