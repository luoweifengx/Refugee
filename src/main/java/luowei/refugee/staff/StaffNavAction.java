package luowei.refugee.staff;

/**
 * 指挥杖页面导航。E / Esc 均回到根。
 */
public enum StaffNavAction {
	/** @deprecated 与 {@link #RESET} 相同；保留以免 ordinal 错位。 */
	@Deprecated
	POP,
	RESET,
	ADVANCE_NEXT_AXIS,
	ADVANCE_PREV_AXIS,
	ADVANCE_POSITIVE,
	ADVANCE_NEGATIVE;

	public static StaffNavAction byOrdinal(int ordinal) {
		StaffNavAction[] values = values();
		if (ordinal < 0 || ordinal >= values.length) {
			return null;
		}
		return values[ordinal];
	}
}
