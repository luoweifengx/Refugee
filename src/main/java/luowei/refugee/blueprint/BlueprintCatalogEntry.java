package luowei.refugee.blueprint;

import net.minecraft.resources.ResourceLocation;

/**
 * 蓝图目录条目：注册 id、界面显示名、是否为导入/划入结构，以及当前玩家是否为所有者。
 */
public record BlueprintCatalogEntry(ResourceLocation id, String displayName, boolean imported, boolean owned) {
	public BlueprintCatalogEntry(ResourceLocation id, String displayName) {
		this(id, displayName, false, false);
	}

	public BlueprintCatalogEntry(ResourceLocation id, String displayName, boolean imported) {
		this(id, displayName, imported, imported);
	}

	public BlueprintCatalogEntry withOwned(boolean owned) {
		return this.owned == owned ? this : new BlueprintCatalogEntry(id, displayName, imported, owned);
	}
}
