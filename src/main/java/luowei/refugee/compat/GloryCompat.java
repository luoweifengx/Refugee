package luowei.refugee.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import luowei.refugee.Refugee;

/**
 * Glory 软依赖：末地演出是否已打完（{@code bossCleared}）。
 */
public final class GloryCompat {
	public static final String MOD_ID = "glory";

	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

	private static Method dataMethod;
	private static Field bossCleared;
	private static boolean resolved;
	private static boolean resolveFailed;
	private static int cacheTick = Integer.MIN_VALUE;
	private static boolean cacheCleared;

	private GloryCompat() {
	}

	public static boolean isLoaded() {
		return LOADED;
	}

	public static boolean isBossCleared(MinecraftServer server) {
		if (!LOADED || server == null) {
			return false;
		}
		int tick = server.getTickCount();
		if (cacheTick == tick) {
			return cacheCleared;
		}
		boolean cleared = queryCleared(server);
		cacheTick = tick;
		cacheCleared = cleared;
		return cleared;
	}

	private static boolean queryCleared(MinecraftServer server) {
		if (resolveFailed) {
			return false;
		}
		try {
			resolve();
			if (dataMethod == null || bossCleared == null) {
				return false;
			}
			ServerLevel end = server.getLevel(Level.END);
			if (end == null) {
				return false;
			}
			Object data = dataMethod.invoke(null, end);
			return data != null && Boolean.TRUE.equals(bossCleared.get(data));
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to query Glory end spectacle", exception);
			resolveFailed = true;
			return false;
		}
	}

	private static void resolve() throws Exception {
		if (resolved) {
			return;
		}
		Class<?> spectacle = Class.forName("luowei.glory.end.EndSpectacle");
		dataMethod = spectacle.getMethod("data", ServerLevel.class);
		Class<?> dataClass = Class.forName("luowei.glory.end.EndSpectacleData");
		bossCleared = dataClass.getDeclaredField("bossCleared");
		bossCleared.setAccessible(true);
		resolved = true;
	}
}
