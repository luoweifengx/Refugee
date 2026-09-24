package luowei.refugee.staff;

/**
 * 人员关系里的玩家列表。只追加，以免网络 ordinal 错位。
 */
public enum RelationsListKind {
	INVITE,
	KICK,
	TRANSFER,
	RESCUE,
	RELATIONS,
	WAR,
	PEACE,
	ALLY,
	PEACE_INBOX;

	public static RelationsListKind byOrdinal(int ordinal) {
		RelationsListKind[] values = values();
		if (ordinal < 0 || ordinal >= values.length) {
			return null;
		}
		return values[ordinal];
	}

	public boolean multi() {
		return this == INVITE;
	}
}
