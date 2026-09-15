package luowei.refugee.blueprint;

import java.util.UUID;

/**
 * 蓝图分享对象：玩家或 PBS 组织，附带显示名与所属领土名。
 */
public record BlueprintShareTarget(UUID id, String name, String territoryName, boolean organization) {
	public BlueprintShareTarget {
		name = name == null ? "" : name;
		territoryName = territoryName == null ? "" : territoryName;
	}
}
