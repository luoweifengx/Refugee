package luowei.refugee.staff;

/**
 * 建造预览微调通道：Y → X → Z → 旋转。
 */
public enum PreviewChannel {
	Y,
	X,
	Z,
	ROTATION;

	public static PreviewChannel byOrdinal(int ordinal) {
		PreviewChannel[] values = values();
		if (ordinal < 0 || ordinal >= values.length) {
			return Y;
		}
		return values[ordinal];
	}

	public PreviewChannel next() {
		PreviewChannel[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public PreviewChannel previous() {
		PreviewChannel[] values = values();
		return values[(ordinal() + values.length - 1) % values.length];
	}
}
