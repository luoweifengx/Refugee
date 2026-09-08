package luowei.refugee.compat;

import java.lang.reflect.Method;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.server.MinecraftServer;

import luowei.refugee.Refugee;

/**
 * Food（{@code food-health-hunger}）软依赖：反射查地脉仪式是否完成。
 */
public final class FoodCompat {
	public static final String MOD_ID = "food-health-hunger";

	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

	private static Method getData;
	private static Method isCompleted;
	private static boolean resolved;
	private static boolean resolveFailed;
	private static int cacheTick = Integer.MIN_VALUE;
	private static boolean cacheCompleted;

	private FoodCompat() {
	}

	public static boolean isLoaded() {
		return LOADED;
	}

	public static boolean isCurseActive(MinecraftServer server) {
		return LOADED && !isRitualCompleted(server);
	}

	public static boolean isRitualCompleted(MinecraftServer server) {
		if (!LOADED || server == null) {
			return false;
		}
		int tick = server.getTickCount();
		if (cacheTick == tick) {
			return cacheCompleted;
		}
		boolean completed = queryCompleted(server);
		cacheTick = tick;
		cacheCompleted = completed;
		return completed;
	}

	private static boolean queryCompleted(MinecraftServer server) {
		if (resolveFailed) {
			return false;
		}
		try {
			resolve();
			if (getData == null || isCompleted == null) {
				return false;
			}
			Object data = getData.invoke(null, server);
			return Boolean.TRUE.equals(isCompleted.invoke(data));
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to query Food leyline ritual state", exception);
			resolveFailed = true;
			return false;
		}
	}

	private static void resolve() throws Exception {
		if (resolved) {
			return;
		}
		Class<?> clazz = Class.forName("luowei.foodhealthhunger.leyline.LeylineRitualSavedData");
		getData = clazz.getMethod("get", MinecraftServer.class);
		isCompleted = clazz.getMethod("isCompleted");
		resolved = true;
	}
}
