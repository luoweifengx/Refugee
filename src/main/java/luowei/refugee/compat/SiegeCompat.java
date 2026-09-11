package luowei.refugee.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import luowei.refugee.Refugee;
import luowei.refugee.pbs.PbsAdapter;

/**
 * Fortress Besieged 软依赖：读存活围城集群与阶段旗帜。
 */
public final class SiegeCompat {
	public static final String MOD_ID = "fortress-besieged";

	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

	private static Method clustersMethod;
	private static Method isDisbanded;
	private static Method getLevel;
	private static Method getSiegeTargetId;
	private static Method getTargetChunk;
	private static Method getAnchorChunk;
	private static Method refreshProgression;
	private static Method hasIronMethod;
	private static Method hasDiamondMethod;
	private static boolean resolved;
	private static boolean resolveFailed;

	private SiegeCompat() {
	}

	public static boolean isLoaded() {
		return LOADED;
	}

	public static List<ClusterView> clusters() {
		if (!LOADED) {
			return List.of();
		}
		try {
			resolve();
			if (clustersMethod == null) {
				return List.of();
			}
			Object raw = clustersMethod.invoke(null);
			if (!(raw instanceof Collection<?> collection)) {
				return List.of();
			}
			List<ClusterView> views = new ArrayList<>();
			for (Object cluster : collection) {
				ClusterView view = toView(cluster);
				if (view != null) {
					views.add(view);
				}
			}
			return views;
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to query Fortress Besieged clusters", exception);
			resolveFailed = true;
			return List.of();
		}
	}

	public static void refreshProgression(MinecraftServer server, UUID targetId) {
		if (!LOADED || server == null || targetId == null) {
			return;
		}
		try {
			resolve();
			if (refreshProgression != null) {
				refreshProgression.invoke(null, server, targetId);
			}
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to refresh Fortress Besieged progression", exception);
			resolveFailed = true;
		}
	}

	public static boolean hasIron(MinecraftServer server, UUID targetId) {
		return queryFlag(() -> hasIronMethod, server, targetId);
	}

	public static boolean hasDiamond(MinecraftServer server, UUID targetId) {
		return queryFlag(() -> hasDiamondMethod, server, targetId);
	}

	private static boolean queryFlag(FlagGetter getter, MinecraftServer server, UUID targetId) {
		if (!LOADED || server == null || targetId == null) {
			return false;
		}
		try {
			resolve();
			Method method = getter.get();
			if (method == null) {
				return false;
			}
			return Boolean.TRUE.equals(method.invoke(null, server, targetId));
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to query Fortress Besieged progression flag", exception);
			resolveFailed = true;
			return false;
		}
	}

	@FunctionalInterface
	private interface FlagGetter {
		Method get();
	}

	private static ClusterView toView(Object cluster) throws Exception {
		if (cluster == null || Boolean.TRUE.equals(isDisbanded.invoke(cluster))) {
			return null;
		}
		if (!(getLevel.invoke(cluster) instanceof ServerLevel level)) {
			return null;
		}
		UUID targetId = (UUID) getSiegeTargetId.invoke(cluster);
		ChunkPos targetChunk = (ChunkPos) getTargetChunk.invoke(cluster);
		ChunkPos anchorChunk = (ChunkPos) getAnchorChunk.invoke(cluster);
		if (targetId == null) {
			ChunkPos occupied = targetChunk != null ? targetChunk : anchorChunk;
			targetId = occupied == null ? null : PbsAdapter.occupyingSubject(level, occupied).orElse(null);
		}
		if (targetId == null) {
			return null;
		}
		return new ClusterView(level, targetId, targetChunk, anchorChunk);
	}

	private static void resolve() throws Exception {
		if (resolved || resolveFailed) {
			return;
		}
		Class<?> manager = Class.forName("luowei.fortress_besieged.siege.SiegeClusterManager");
		clustersMethod = manager.getMethod("clusters");
		Class<?> cluster = Class.forName("luowei.fortress_besieged.siege.SiegeCluster");
		isDisbanded = cluster.getMethod("isDisbanded");
		getLevel = cluster.getMethod("getLevel");
		getSiegeTargetId = cluster.getMethod("getSiegeTargetId");
		getTargetChunk = cluster.getMethod("getTargetChunk");
		getAnchorChunk = cluster.getMethod("getAnchorChunk");
		Class<?> flags = Class.forName("luowei.fortress_besieged.siege.SiegeProgressionFlags");
		refreshProgression = flags.getMethod("refresh", MinecraftServer.class, UUID.class);
		hasIronMethod = flags.getMethod("hasIron", MinecraftServer.class, UUID.class);
		hasDiamondMethod = flags.getMethod("hasDiamond", MinecraftServer.class, UUID.class);
		resolved = true;
	}

	public record ClusterView(ServerLevel level, UUID targetId, ChunkPos targetChunk, ChunkPos anchorChunk) {
	}
}
