package luowei.refugee.client;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

import luowei.refugee.blueprint.BlueprintTransforms;
import luowei.refugee.staff.PreviewChannel;

/**
 * 客户端持有的结构选定与建造预览变换（确认前为会话状态）。
 * 右键钉住的原点只存在客户端，不跟准星走，Tab / 滚轮仍改偏移与旋转。
 */
public final class ClientBlueprintSelection {
	private static ResourceLocation structureId;
	private static BlockPos buildOrigin;
	private static BlockPos lockedOrigin;
	private static int offsetX;
	private static int offsetY;
	private static int offsetZ;
	private static Rotation rotation = Rotation.NONE;
	private static PreviewChannel channel = PreviewChannel.Y;

	private ClientBlueprintSelection() {
	}

	public static void apply(Optional<ResourceLocation> structure, Optional<BlockPos> origin) {
		ResourceLocation next = structure == null ? null : structure.orElse(null);
		if (next == null || !next.equals(structureId)) {
			lockedOrigin = null;
		}
		structureId = next;
		buildOrigin = origin == null ? null : origin.orElse(null);
	}

	public static ResourceLocation structureId() {
		return structureId;
	}

	public static BlockPos buildOrigin() {
		return lockedOrigin != null ? lockedOrigin : buildOrigin;
	}

	public static boolean originLocked() {
		return lockedOrigin != null;
	}

	public static void lockOrigin(BlockPos origin) {
		lockedOrigin = origin == null ? null : origin.immutable();
	}

	public static void unlockOrigin() {
		lockedOrigin = null;
	}

	public static int offsetX() {
		return offsetX;
	}

	public static int offsetY() {
		return offsetY;
	}

	public static int offsetZ() {
		return offsetZ;
	}

	public static Rotation rotation() {
		return rotation == null ? Rotation.NONE : rotation;
	}

	public static PreviewChannel channel() {
		return channel == null ? PreviewChannel.Y : channel;
	}

	public static void nextChannel() {
		channel = channel().next();
	}

	public static void previousChannel() {
		channel = channel().previous();
	}

	public static void adjust(int delta) {
		if (delta == 0) {
			return;
		}
		int step = delta > 0 ? 1 : -1;
		switch (channel()) {
			case Y -> offsetY += step;
			case X -> offsetX += step;
			case Z -> offsetZ += step;
			case ROTATION -> rotation = rotation().getRotated(step > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90);
		}
	}

	public static void clearPreview() {
		lockedOrigin = null;
		offsetX = 0;
		offsetY = 0;
		offsetZ = 0;
		rotation = Rotation.NONE;
		channel = PreviewChannel.Y;
	}

	public static void clear() {
		structureId = null;
		buildOrigin = null;
		clearPreview();
	}

	public static Component channelLabel() {
		return switch (channel()) {
			case Y -> Component.translatable("message.refugee.staff.preview.channel.y", signed(offsetY));
			case X -> Component.translatable("message.refugee.staff.preview.channel.x", signed(offsetX));
			case Z -> Component.translatable("message.refugee.staff.preview.channel.z", signed(offsetZ));
			case ROTATION -> Component.translatable(
					"message.refugee.staff.preview.channel.rot",
					BlueprintTransforms.rotationDegrees(rotation())
			);
		};
	}

	public static Component hintLabel() {
		return Component.translatable(
				originLocked()
						? "message.refugee.staff.preview.hint.locked"
						: "message.refugee.staff.preview.hint"
		);
	}

	private static String signed(int value) {
		return value > 0 ? "+" + value : Integer.toString(value);
	}
}
