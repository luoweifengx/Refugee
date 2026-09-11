package luowei.refugee.staff;

/**
 * 指挥杖当前交互模式。只追加，以免网络 ordinal 错位。
 */
public enum StaffMode {
	NONE,
	WAREHOUSE,
	ZONE,
	BUILD,
	IMPORT,
	FOLLOW_ENTITY,
	PATROL,
	FOOD_WAREHOUSE,
	ADVANCE,
	SMELTER,
	FARM_WAREHOUSE,
	GEAR_WAREHOUSE,
	SMELT_RESULT;

	public static StaffMode byOrdinal(int ordinal) {
		StaffMode[] values = values();
		if (ordinal < 0 || ordinal >= values.length) {
			return NONE;
		}
		return values[ordinal];
	}
}
