package luowei.refugee.staff;

/**
 * 指挥杖饼图扇区。根页展示顺序见客户端 ROOT_SLICES；仓库/工作/建筑/战斗/集结/卫队。
 * 只追加，以免网络 ordinal 错位。
 */
public enum StaffPieAction {
	WAREHOUSE,
	ZONE,
	IMPORT,
	SELECT,
	COMBAT,
	FOLLOW_ENTITY,
	PATROL,
	FORMATION,
	RALLY,
	WAREHOUSE_BLOCKS,
	WAREHOUSE_FOOD,
	ZONE_BOX,
	ZONE_ADVANCE,
	ZONE_REPAIR,
	ZONE_BUILD,
	RALLY_MELEE,
	RALLY_RANGED,
	RALLY_WORKER,
	RALLY_ALL,
	RALLY_CIVILIAN,
	EQUIP_GEAR,
	WAREHOUSE_SMELT,
	ZONE_SMELT,
	GUARD,
	GUARD_ADD,
	GUARD_RALLY_NEAR,
	GUARD_RALLY_ALL,
	GUARD_REMOVE,
	BUILD,
	WAREHOUSE_FARM,
	WAREHOUSE_GEAR,
	WAREHOUSE_SMELT_RESULT,
	RALLY_SPECIAL;

	public static StaffPieAction byOrdinal(int ordinal) {
		StaffPieAction[] values = values();
		if (ordinal < 0 || ordinal >= values.length) {
			return null;
		}
		return values[ordinal];
	}
}
