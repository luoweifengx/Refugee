package luowei.refugee.staff;

/**
 * 指挥杖饼图四个象限。ordinal：WAREHOUSE=0，ZONE=1，IMPORT=2，SELECT=3。
 */
public enum StaffPieAction {
	WAREHOUSE,
	ZONE,
	IMPORT,
	SELECT;

	public static StaffPieAction byOrdinal(int ordinal) {
		StaffPieAction[] values = values();
		if (ordinal < 0 || ordinal >= values.length) {
			return null;
		}
		return values[ordinal];
	}
}
