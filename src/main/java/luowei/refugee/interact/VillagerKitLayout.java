package luowei.refugee.interact;

/**
 * 村民装具窗像素布局：上排 7 格装具，下排原版背包。不使用箱子 9×3 底图。
 */
public final class VillagerKitLayout {
	public static final int WIDTH = 176;
	public static final int HEIGHT = 196;
	public static final int TITLE_X = 8;
	public static final int TITLE_Y = 6;
	public static final int ARMOR_LABEL_X = 26;
	public static final int ARMOR_LABEL_Y = 18;
	public static final int HANDS_LABEL_X = 80;
	public static final int HANDS_LABEL_Y = 18;
	public static final int FOOD_LABEL_X = 116;
	public static final int FOOD_LABEL_Y = 18;
	public static final int HEAD_X = 26;
	public static final int HEAD_Y = 28;
	public static final int CHEST_X = 26;
	public static final int CHEST_Y = 46;
	public static final int LEGS_X = 26;
	public static final int LEGS_Y = 64;
	public static final int FEET_X = 26;
	public static final int FEET_Y = 82;
	public static final int MAIN_X = 80;
	public static final int MAIN_Y = 46;
	public static final int OFF_X = 80;
	public static final int OFF_Y = 64;
	public static final int FOOD_X = 116;
	public static final int FOOD_Y = 46;
	public static final int HEALTH_X = 80;
	public static final int HEALTH_Y = 88;
	public static final int INV_LABEL_X = 8;
	public static final int INV_LABEL_Y = 106;
	public static final int INV_X = 8;
	public static final int INV_Y = 114;
	public static final int HOTBAR_X = 8;
	public static final int HOTBAR_Y = 172;
	public static final int SLOT = 18;
	/** 槽底图 18px 含 1px 边，物品/高亮从内沿起算。 */
	public static final int SLOT_PAD = 1;

	private VillagerKitLayout() {
	}

	public static int slotX(int bgX) {
		return bgX + SLOT_PAD;
	}

	public static int slotY(int bgY) {
		return bgY + SLOT_PAD;
	}
}
