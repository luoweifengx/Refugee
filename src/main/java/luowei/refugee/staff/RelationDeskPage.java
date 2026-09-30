package luowei.refugee.staff;

/**
 * 关系管理台页面。首页两个大选项，点进去才是具体事项。
 */
public enum RelationDeskPage {
	HOME,
	PEOPLE,
	ORG,
	DIPLOMACY;

	public static RelationDeskPage byOrdinal(int ordinal) {
		RelationDeskPage[] values = values();
		if (ordinal < 0 || ordinal >= values.length) {
			return HOME;
		}
		return values[ordinal];
	}

	public RelationDeskPage back() {
		return switch (this) {
			case ORG -> PEOPLE;
			case PEOPLE, DIPLOMACY -> HOME;
			case HOME -> HOME;
		};
	}
}
