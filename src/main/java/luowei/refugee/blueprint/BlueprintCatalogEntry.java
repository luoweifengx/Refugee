package luowei.refugee.blueprint;

import net.minecraft.resources.ResourceLocation;

/**
 * 蓝图目录条目：注册 id 与界面显示名。只来自 {@code config/refugee/blueprints}，不含原版村庄碎片。
 */
public record BlueprintCatalogEntry(ResourceLocation id, String displayName) {
}
