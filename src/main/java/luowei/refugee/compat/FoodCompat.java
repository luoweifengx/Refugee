package luowei.refugee.compat;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.server.MinecraftServer;

import luowei.refugee.Refugee;

/**
 * Food（{@code food-health-hunger}）软依赖：只反射查地脉仪式是否完成。
 */
public final class FoodCompat {
	public static final String MOD_ID = "food-health-hunger";

	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

	private FoodCompat() {
	}

	public static boolean isLoaded() {
		return LOADED;
	}

	public static boolean isRitualCompleted(MinecraftServer server) {
		if (!LOADED || server == null) {
			return false;
		}
		try {
			Class<?> clazz = Class.forName("luowei.foodhealthhunger.leyline.LeylineRitualSavedData");
			Object data = clazz.getMethod("get", MinecraftServer.class).invoke(null, server);
			return Boolean.TRUE.equals(clazz.getMethod("isCompleted").invoke(data));
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to query Food leyline ritual state", exception);
			return false;
		}
	}
}
