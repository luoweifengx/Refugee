package luowei.refugee.blueprint;

import net.minecraft.resources.ResourceLocation;

/**
 * 蓝图目录条目：注册 id、界面显示名，以及是否为可删的导入结构。
 */
public record BlueprintCatalogEntry(ResourceLocation id, String displayName, boolean imported) {
	public BlueprintCatalogEntry(ResourceLocation id, String displayName) {
		this(id, displayName, false);
	}
}
