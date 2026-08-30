package luowei.refugee.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * 客户端蓝图结构缓存（由目录同步包填充）。
 */
public final class ClientBlueprintTemplates {
	private static final Map<ResourceLocation, StructureTemplate> TEMPLATES = new HashMap<>();

	private ClientBlueprintTemplates() {
	}

	public static void replaceAll(Map<ResourceLocation, CompoundTag> nbts) {
		TEMPLATES.clear();
		if (nbts == null || nbts.isEmpty()) {
			return;
		}
		for (Map.Entry<ResourceLocation, CompoundTag> entry : nbts.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			try {
				StructureTemplate template = new StructureTemplate();
				template.load(BuiltInRegistries.BLOCK, entry.getValue());
				TEMPLATES.put(entry.getKey(), template);
			} catch (Exception ignored) {
				// 单个坏模板跳过，不影响其余预览
			}
		}
	}

	public static StructureTemplate get(ResourceLocation id) {
		return id == null ? null : TEMPLATES.get(id);
	}

	public static void clear() {
		TEMPLATES.clear();
	}
}
