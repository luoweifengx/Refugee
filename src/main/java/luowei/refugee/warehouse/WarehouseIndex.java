package luowei.refugee.warehouse;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * 单箱扫描辅助：整理/关箱重建账本时按槽遍历；热路径请走 {@link WarehouseLedger}。
 */
public final class WarehouseIndex {
	private int log;
	private int planks;
	private int stone;
	private int soil;
	private int seed;
	private int misc;

	public WarehouseIndex() {
	}

	@FunctionalInterface
	public interface SlotVisitor {
		void accept(int slot, ItemStack stack);
	}

	public static void forEachOccupied(Container container, SlotVisitor visitor) {
		if (container == null || visitor == null) {
			return;
		}
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty()) {
				visitor.accept(i, stack);
			}
		}
	}

	public static WarehouseIndex scan(Container container) {
		WarehouseIndex index = new WarehouseIndex();
		if (container == null) {
			return index;
		}
		for (int i = 0; i < container.getContainerSize(); i++) {
			index.add(container.getItem(i));
		}
		return index;
	}

	public void add(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		int count = stack.getCount();
		switch (MaterialCategory.of(stack)) {
			case LOG -> log += count;
			case PLANKS -> planks += count;
			case STONE -> stone += count;
			case SOIL -> soil += count;
			case SEED -> seed += count;
			case MISC -> misc += count;
			default -> {
			}
		}
	}

	public int get(MaterialCategory category) {
		return switch (category) {
			case LOG -> log;
			case PLANKS -> planks;
			case STONE -> stone;
			case SOIL -> soil;
			case SEED -> seed;
			case MISC -> misc;
			default -> 0;
		};
	}

	public int log() {
		return log;
	}

	public int planks() {
		return planks;
	}

	public int stone() {
		return stone;
	}

	public int soil() {
		return soil;
	}

	public int seed() {
		return seed;
	}

	public int misc() {
		return misc;
	}
}
