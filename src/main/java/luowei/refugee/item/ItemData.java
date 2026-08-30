package luowei.refugee.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * CUSTOM_DATA 读写（1.21.5 Optional NBT）。蓝图结构名与安顿白旗标记共用。
 */
public final class ItemData {
	public static final String STRUCTURE_KEY = "refugee_structure";
	public static final String SETTLEMENT_BANNER_KEY = "refugee_settlement";

	private ItemData() {
	}

	public static CompoundTag tag(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
	}

	public static void setTag(ItemStack stack, CompoundTag tag) {
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	public static ResourceLocation structureId(ItemStack stack) {
		CompoundTag tag = tag(stack);
		if (!tag.contains(STRUCTURE_KEY)) {
			return null;
		}
		return ResourceLocation.tryParse(tag.getString(STRUCTURE_KEY).orElse(""));
	}

	public static void setStructureId(ItemStack stack, ResourceLocation id) {
		CompoundTag tag = tag(stack);
		tag.putString(STRUCTURE_KEY, id.toString());
		setTag(stack, tag);
	}

	public static boolean hasStructure(ItemStack stack) {
		return structureId(stack) != null;
	}

	public static ItemStack createSettlementBanner() {
		ItemStack stack = new ItemStack(Items.WHITE_BANNER);
		CompoundTag tag = tag(stack);
		tag.putBoolean(SETTLEMENT_BANNER_KEY, true);
		setTag(stack, tag);
		return stack;
	}

	public static boolean isSettlementBanner(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.is(Items.WHITE_BANNER)) {
			return false;
		}
		return tag(stack).getBooleanOr(SETTLEMENT_BANNER_KEY, false);
	}
}
