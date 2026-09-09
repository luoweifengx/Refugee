package luowei.refugee.staff;

/**
 * 指挥杖饼图扇区。根页展示顺序见客户端 ROOT_SLICES；战斗指挥子页为跟随/巡逻/列队。
 * 集结子页为近战/远程/工人/散人/全部。只追加，以免网络 ordinal 错位。
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
	RALLY_CIVILIAN;

	public static StaffPieAction byOrdinal(int ordinal) {
		StaffPieAction[] values = values();
		if (ordinal < 0 || ordinal >= values.length) {
			return null;
		}
		return values[ordinal];
	}
}
