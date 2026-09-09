package luowei.refugee.client;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * 范围推进：Tab 选轴，滚轮选 +/-。
 */
public final class ClientAdvanceSelection {
	private static Direction.Axis axis = Direction.Axis.Y;
	private static boolean positive = true;

	private ClientAdvanceSelection() {
	}

	public static void reset() {
		axis = Direction.Axis.Y;
		positive = true;
	}

	public static Direction.Axis axis() {
		return axis == null ? Direction.Axis.Y : axis;
	}

	public static boolean positive() {
		return positive;
	}

	public static void nextAxis() {
		Direction.Axis[] values = Direction.Axis.values();
		axis = values[(axis().ordinal() + 1) % values.length];
	}

	public static void previousAxis() {
		Direction.Axis[] values = Direction.Axis.values();
		axis = values[(axis().ordinal() + values.length - 1) % values.length];
	}

	public static void setPositive(boolean next) {
		positive = next;
	}

	public static Component hintLabel() {
		return Component.translatable(
				"message.refugee.staff.advance.hint",
				axis().name(),
				positive ? "+" : "-"
		);
	}
}
