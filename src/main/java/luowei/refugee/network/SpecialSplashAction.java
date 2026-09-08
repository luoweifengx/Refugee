package luowei.refugee.network;

/**
 * 开屏选项里需要服务端执行的动作。再见只在客户端关闭界面。
 */
public enum SpecialSplashAction {
	TALK,
	HEAL,
	MAP,
	TRADE,
	INTRO_ADVANCE,
	INTRO_FINISH,
	INTRO_INTERRUPT;

	public static SpecialSplashAction byId(int id) {
		SpecialSplashAction[] values = values();
		if (id < 0 || id >= values.length) {
			return null;
		}
		return values[id];
	}
}
