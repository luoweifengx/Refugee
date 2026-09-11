package luowei.refugee.crusader;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * 十字军编制：同级剑跟阶段；单独点名的才高一档。
 */
public final class CrusaderLoadout {
	public enum Stage {
		INITIAL,
		IRON,
		DIAMOND,
		PORTAL
	}

	public enum Armor {
		NONE,
		IRON,
		DIAMOND
	}

	public record Kit(Armor armor, Item mainHand, Item offhand) {
	}

	private CrusaderLoadout() {
	}

	public static Stage resolve(boolean hasIron, boolean hasDiamond, boolean hasDemonChunk) {
		if (hasDemonChunk) {
			return Stage.PORTAL;
		}
		if (hasDiamond) {
			return Stage.DIAMOND;
		}
		if (hasIron) {
			return Stage.IRON;
		}
		return Stage.INITIAL;
	}

	public static List<Kit> kits(Stage stage) {
		List<Kit> kits = new ArrayList<>();
		switch (stage == null ? Stage.INITIAL : stage) {
			case INITIAL -> {
				add(kits, 2, Armor.NONE, Items.STONE_SWORD, Items.SHIELD);
				add(kits, 1, Armor.NONE, Items.BOW, Items.BOW);
				add(kits, 2, Armor.NONE, Items.IRON_SWORD, Items.AIR);
			}
			case IRON -> {
				add(kits, 3, Armor.IRON, Items.IRON_SWORD, Items.SHIELD);
				add(kits, 3, Armor.IRON, Items.DIAMOND_SWORD, Items.AIR);
				add(kits, 2, Armor.IRON, Items.IRON_SWORD, Items.BOW);
			}
			case DIAMOND -> {
				add(kits, 8, Armor.DIAMOND, Items.DIAMOND_SWORD, Items.SHIELD);
				add(kits, 4, Armor.DIAMOND, Items.DIAMOND_SWORD, Items.BOW);
				add(kits, 2, Armor.DIAMOND, Items.DIAMOND_SWORD, Items.AIR);
			}
			case PORTAL -> {
				add(kits, 14, Armor.DIAMOND, Items.DIAMOND_SWORD, Items.SHIELD);
				add(kits, 1, Armor.DIAMOND, Items.NETHERITE_SWORD, Items.AIR);
				add(kits, 5, Armor.DIAMOND, Items.DIAMOND_SWORD, Items.BOW);
			}
		}
		return kits;
	}

	private static void add(List<Kit> kits, int count, Armor armor, Item mainHand, Item offhand) {
		for (int i = 0; i < count; i++) {
			kits.add(new Kit(armor, mainHand, offhand));
		}
	}
}
