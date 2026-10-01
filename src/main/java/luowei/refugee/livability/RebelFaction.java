package luowei.refugee.livability;

import java.util.UUID;

/**
 * 叛乱居民的固定归属。不进组织名单，外交选不到。对任何其他归属都是敌对。
 */
public final class RebelFaction {
	public static final UUID SUBJECT = UUID.fromString("b7e1a000-4e1d-4a11-9c00-000000000001");

	private RebelFaction() {
	}

	public static boolean is(UUID subjectId) {
		return SUBJECT.equals(subjectId);
	}
}
