package luowei.refugee.staff;

/**
 * 指挥杖当前交互模式。
 */
public enum StaffMode {
	NONE,
	WAREHOUSE,
	ZONE,
	BUILD,
	IMPORT;

	public static StaffMode byOrdinal(int ordinal) {
		StaffMode[] values = values();
		if (ordinal < 0 || ordinal >= values.length) {
			return NONE;
		}
		return values[ordinal];
	}
}
