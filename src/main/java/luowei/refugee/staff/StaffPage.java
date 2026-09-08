package luowei.refugee.staff;

/**
 * 指挥杖页面树节点。交互模式由 {@link #worldMode()} 映射到 {@link StaffMode}。
 *
 * <pre>
 * ROOT
 *   PIE（六扇区）
 *     WAREHOUSE → WAREHOUSE_PIE
 *       WAREHOUSE（物块）
 *       FOOD_WAREHOUSE
 *     ZONE
 *     IMPORT
 *       IMPORT_NAME
 *     SELECT → BUILD_CATALOG
 *       BUILD_PREVIEW
 *     COMBAT_PIE（三扇区）
 *       COMBAT_FOLLOW
 *       COMBAT_PATROL
 *     RALLY（即时：号角半径集结）
 * </pre>
 *
 * {@link #BUILD_HUB} 已弃用，保留以免网络 ordinal 错位。新页只追加。
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
	IMPORT_NAME,
	COMBAT_PIE,
	COMBAT_FOLLOW,
	COMBAT_PATROL,
	WAREHOUSE_PIE,
	FOOD_WAREHOUSE;

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
			case FOOD_WAREHOUSE -> StaffMode.FOOD_WAREHOUSE;
			case ZONE -> StaffMode.ZONE;
			case BUILD_PREVIEW -> StaffMode.BUILD;
			case IMPORT, IMPORT_NAME -> StaffMode.IMPORT;
			case COMBAT_FOLLOW -> StaffMode.FOLLOW_ENTITY;
			case COMBAT_PATROL -> StaffMode.PATROL;
			default -> StaffMode.NONE;
		};
	}

	public boolean isRoot() {
		return this == ROOT;
	}

	public boolean isPie() {
		return this == PIE || this == COMBAT_PIE || this == WAREHOUSE_PIE;
	}

	public boolean isWorld() {
		return worldMode() != StaffMode.NONE;
	}

	public boolean isStaffScreen() {
		return isPie() || this == BUILD_CATALOG || this == IMPORT_NAME;
	}
}
