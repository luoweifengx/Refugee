package luowei.refugee.staff;

/**
 * 指挥杖页面树节点。交互模式由 {@link #worldMode()} 映射到 {@link StaffMode}。
 *
 * <pre>
 * ROOT
 *   PIE（四象限）
 *     WAREHOUSE
 *     ZONE
 *     IMPORT
 *       IMPORT_NAME
 *     SELECT → BUILD_CATALOG
 *       BUILD_PREVIEW
 * </pre>
 *
 * {@link #BUILD_HUB} 已弃用，保留以免网络 ordinal 错位。
 */
public enum StaffPage {
	ROOT,
	PIE,
	WAREHOUSE,
	ZONE,
	/** @deprecated 已取消建筑类别页，保留以免 ordinal 错位。 */
	@Deprecated
	BUILD_HUB,
	BUILD_CATALOG,
	BUILD_PREVIEW,
	IMPORT,
	IMPORT_NAME;

	public static StaffPage byOrdinal(int ordinal) {
		StaffPage[] values = values();
		if (ordinal < 0 || ordinal >= values.length) {
			return ROOT;
		}
		return values[ordinal];
	}

	public StaffMode worldMode() {
		return switch (this) {
			case WAREHOUSE -> StaffMode.WAREHOUSE;
			case ZONE -> StaffMode.ZONE;
			case BUILD_PREVIEW -> StaffMode.BUILD;
			case IMPORT, IMPORT_NAME -> StaffMode.IMPORT;
			default -> StaffMode.NONE;
		};
	}

	public boolean isRoot() {
		return this == ROOT;
	}

	public boolean isWorld() {
		return worldMode() != StaffMode.NONE;
	}

	public boolean isStaffScreen() {
		return this == PIE || this == BUILD_CATALOG || this == IMPORT_NAME;
	}
}
