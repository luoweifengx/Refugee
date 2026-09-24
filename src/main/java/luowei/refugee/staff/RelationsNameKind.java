package luowei.refugee.staff;

/**
 * 人员关系命名框。只追加，以免网络 ordinal 错位。
 */
public enum RelationsNameKind {
	CREATE_ORG,
	RENAME_TERRITORY,
	RENAME_PERSONAL;

	public static RelationsNameKind byOrdinal(int ordinal) {
		RelationsNameKind[] values = values();
		if (ordinal < 0 || ordinal >= values.length) {
			return null;
		}
		return values[ordinal];
	}
}
