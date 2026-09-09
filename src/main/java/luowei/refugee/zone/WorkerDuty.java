package luowei.refugee.zone;

/**
 * 工人持久工种：修复/建筑是状态；框定/推进走 {@link WorkZone} 认领。
 */
public enum WorkerDuty {
	NONE,
	BUILDER,
	REPAIRER;

	public static WorkerDuty fromId(String id) {
		if (id == null || id.isBlank()) {
			return NONE;
		}
		return switch (id) {
			case "builder" -> BUILDER;
			case "repairer" -> REPAIRER;
			default -> NONE;
		};
	}

	public String id() {
		return switch (this) {
			case BUILDER -> "builder";
			case REPAIRER -> "repairer";
			case NONE -> "";
		};
	}

	public boolean isBuilder() {
		return this == BUILDER;
	}

	public boolean isRepairer() {
		return this == REPAIRER;
	}

	public boolean isAssigned() {
		return this != NONE;
	}
}
