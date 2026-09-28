package luowei.refugee.talk;

import net.minecraft.resources.ResourceLocation;

import luowei.refugee.Refugee;

/**
 * 村民头顶显示的表情。{@code NONE} 不渲染。
 */
public enum RefugeeBubbleIcon {
	NONE((byte) 0, null),
	TALK((byte) 1, Refugee.id("textures/talk/icon_talk.png")),
	WEAPON((byte) 2, Refugee.id("textures/talk/icon_weapon.png")),
	PICKAXE((byte) 3, Refugee.id("textures/talk/icon_pickaxe.png")),
	LOVE((byte) 4, Refugee.id("textures/talk/face_love.png")),
	SWEAT((byte) 5, Refugee.id("textures/talk/icon_water.png")),
	ANGRY((byte) 6, Refugee.id("textures/talk/face_angry.png")),
	HUNGRY((byte) 7, Refugee.id("textures/talk/icon_hungry.png")),
	FULL((byte) 8, Refugee.id("textures/talk/icon_full.png")),
	CRYING((byte) 9, Refugee.id("textures/talk/face_crying.png")),
	HAPPY((byte) 10, Refugee.id("textures/talk/face_happy.png")),
	WORRIED((byte) 11, Refugee.id("textures/talk/face_worried.png")),
	EXCLAIM((byte) 12, Refugee.id("textures/talk/icon_exclaim.png"));

	public static final RefugeeBubbleIcon[] SELECT_FACES = {LOVE, ANGRY, CRYING, HAPPY, WORRIED};

	private static final RefugeeBubbleIcon[] BY_ID = values();

	private final byte id;
	private final ResourceLocation iconTexture;

	RefugeeBubbleIcon(byte id, ResourceLocation iconTexture) {
		this.id = id;
		this.iconTexture = iconTexture;
	}

	public byte id() {
		return id;
	}

	public ResourceLocation iconTexture() {
		return iconTexture;
	}

	public boolean visible() {
		return this != NONE && iconTexture != null;
	}

	public static RefugeeBubbleIcon byId(byte id) {
		int index = id & 0xFF;
		if (index >= BY_ID.length) {
			return NONE;
		}
		return BY_ID[index];
	}
}
