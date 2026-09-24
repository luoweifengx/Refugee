package luowei.refugee.staff;

/**
 * 指挥杖页面树节点。交互模式由 {@link #worldMode()} 映射到 {@link StaffMode}。
 *
 * <pre>
 * ROOT
 *   PIE（六扇区）
 *     WAREHOUSE → WAREHOUSE_PIE
 *       WAREHOUSE（物块）
 *       FARM_WAREHOUSE
 *       GEAR_WAREHOUSE
 *       FOOD_WAREHOUSE
 *       SMELTER（熔炼处）
 *       SMELT_RESULT
 *     ZONE → ZONE_PIE（工作）
 *       ZONE（范围框定）
 *       ZONE_ADVANCE
 *       （修复 / 建筑 / 熔炼：即时派工）
 *     BUILD → BUILD_PIE
 *       SELECT → BUILD_CATALOG → BUILD_PREVIEW
 *                BUILD_CATALOG 内：上传 / 分享 / 删除
 *                SHARE → BUILD_SHARE
 *       IMPORT → IMPORT_NAME（划为蓝图）
 *     COMBAT_PIE（战斗）
 *       COMBAT_FOLLOW
 *       COMBAT_PATROL
 *       （列队 / 派发装备：即时）
 *     RALLY → RALLY_PIE
 *       近战 / 远程 / 工人 / 散人 / 特殊 / 全部
 *     GUARD → GUARD_PIE
 *       添加 / 范围召集 / 全部召集 / 除名
 *     RELATIONS → RELATIONS_PIE
 *       创建组织 / 邀请 / 邀请管理 / 组织管理 / 人员救助
 *       ORG_MANAGE → ORG_MANAGE_PIE
 *         信息 / 离开 / 踢人 / 移交 / 改领地名
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
	FOOD_WAREHOUSE,
	ZONE_PIE,
	ZONE_ADVANCE,
	RALLY_PIE,
	SMELTER,
	GUARD_PIE,
	BUILD_PIE,
	FARM_WAREHOUSE,
	GEAR_WAREHOUSE,
	SMELT_RESULT,
	BUILD_SHARE,
	RELATIONS_PIE,
	ORG_MANAGE_PIE,
	ORG_CREATE,
	ORG_INVITE,
	ORG_INVITES,
	ORG_KICK,
	ORG_TRANSFER,
	ORG_RENAME,
	ORG_RESCUE,
	TERRITORY_MINE,
	DIPLOMACY;

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
			case FARM_WAREHOUSE -> StaffMode.FARM_WAREHOUSE;
			case GEAR_WAREHOUSE -> StaffMode.GEAR_WAREHOUSE;
			case SMELT_RESULT -> StaffMode.SMELT_RESULT;
			case SMELTER -> StaffMode.SMELTER;
			case ZONE -> StaffMode.ZONE;
			case ZONE_ADVANCE -> StaffMode.ADVANCE;
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
		return this == PIE || this == COMBAT_PIE || this == WAREHOUSE_PIE || this == ZONE_PIE
				|| this == RALLY_PIE || this == GUARD_PIE || this == BUILD_PIE
				|| this == RELATIONS_PIE || this == ORG_MANAGE_PIE;
	}

	public boolean isWorld() {
		return worldMode() != StaffMode.NONE;
	}

	public boolean isStaffScreen() {
		return isPie() || this == BUILD_CATALOG || this == IMPORT_NAME || this == BUILD_SHARE
				|| this == ORG_CREATE || this == ORG_INVITE || this == ORG_INVITES
				|| this == ORG_KICK || this == ORG_TRANSFER || this == ORG_RENAME || this == ORG_RESCUE
				|| this == TERRITORY_MINE || this == DIPLOMACY;
	}
}
