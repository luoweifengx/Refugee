package luowei.refugee.settle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import luowei.refugee.Refugee;

/**
 * 从原点做三维切比雪夫 BFS：一圈一圈含高度向外找可站立落脚点。
 * 可站只看脚下实心、脚与头两格可穿过（雪层等非完整方块算可穿过）。
 */
public final class StandableFinder {
	public static final int SEARCH_RADIUS = 16;
	private static final int MAX_RADIUS = SEARCH_RADIUS * 2;
	private static final int MAX_VISITED = 32768;

	private StandableFinder() {
	}

	/**
	 * 从原点 BFS 找 {@code needed} 个可站立格。半径 16 不够时继续扩到 32。
	 */
	public static List<BlockPos> findStandable(
			ServerLevel level,
			BlockPos origin,
			Set<BlockPos> reserved,
			int needed
	) {
		return search(level, origin, reserved, needed, null);
	}

	/**
	 * 入境：在区块内从中心 BFS 找一个可站立格。
	 */
	public static BlockPos findInChunk(ServerLevel level, ChunkPos chunk, Set<BlockPos> reserved) {
		int x = chunk.getMinBlockX() + 8;
		int z = chunk.getMinBlockZ() + 8;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		List<BlockPos> found = search(level, new BlockPos(x, y, z), reserved, 1, chunk);
		return found.isEmpty() ? null : found.getFirst();
	}

	/**
	 * 在目标周围找可站且到目标中心不超过 {@code maxDistSq} 的格，优先靠近 {@code from}。
	 */
	public static BlockPos findStandNear(ServerLevel level, BlockPos target, BlockPos from, double maxDistSq) {
		if (level == null || target == null) {
			return null;
		}
		long started = debugNanos();
		int maxR = Math.max(1, (int) Math.ceil(Math.sqrt(maxDistSq)));
		BlockPos best = null;
		double bestFrom = Double.MAX_VALUE;
		int minY = level.getMinY() + 1;
		int maxY = level.getMinY() + level.getHeight() - 2;
		int checked = 0;
		int standable = 0;
		for (int r = 0; r <= maxR; r++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (chebyshev(dx, dy, dz) != r) {
							continue;
						}
						int x = target.getX() + dx;
						int y = target.getY() + dy;
						int z = target.getZ() + dz;
						if (y < minY || y > maxY || !level.hasChunk(x >> 4, z >> 4)) {
							continue;
						}
						checked++;
						BlockPos feet = new BlockPos(x, y, z);
						if (feet.equals(target) || !isStandable(level, feet) || villagerOccupies(level, feet)) {
							continue;
						}
						standable++;
						double toTarget = distSqCenter(feet, target);
						if (toTarget > maxDistSq) {
							continue;
						}
						double toFrom = from == null ? toTarget : distSqCenter(feet, from);
						if (toFrom < bestFrom) {
							bestFrom = toFrom;
							best = feet;
						}
					}
				}
			}
		}
		long elapsed = elapsedNanos(started);
		if (Refugee.LOGGER.isDebugEnabled() && elapsed >= 1_000_000L) {
			Refugee.LOGGER.debug(
					"[refugee standable] findStandNear target={} from={} maxR={} checked={} standable={} best={} {}ns",
					target.toShortString(),
					from == null ? "none" : from.toShortString(),
					maxR,
					checked,
					standable,
					best == null ? "none" : best.toShortString(),
					elapsed
			);
		}
		return best;
	}

	/**
	 * 脚底世界坐标：地板碰撞盒顶面。完整方块等于 {@code feet.getY()}。
	 */
	public static double standY(ServerLevel level, BlockPos feet) {
		if (level == null || feet == null) {
			return 0.0;
		}
		BlockPos below = feet.below();
		VoxelShape shape = level.getBlockState(below).getCollisionShape(level, below);
		if (shape.isEmpty()) {
			return feet.getY();
		}
		double top = shape.max(Direction.Axis.Y);
		if (Double.isNaN(top) || top <= 0.0) {
			return feet.getY();
		}
		return below.getY() + top;
	}

	public static void snapToStandable(Entity entity, ServerLevel level, BlockPos feet) {
		snapToStandable(entity, level, feet, entity.getYRot(), entity.getXRot());
	}

	public static void snapToStandable(Entity entity, ServerLevel level, BlockPos feet, float yRot, float xRot) {
		entity.snapTo(feet.getX() + 0.5, standY(level, feet), feet.getZ() + 0.5, yRot, xRot);
	}

	private static List<BlockPos> search(
			ServerLevel level,
			BlockPos origin,
			Set<BlockPos> reserved,
			int needed,
			ChunkPos chunkBound
	) {
		List<BlockPos> result = new ArrayList<>();
		if (level == null || origin == null || needed <= 0) {
			return result;
		}
		Set<Long> usedFeet = new HashSet<>();
		if (reserved != null) {
			for (BlockPos pos : reserved) {
				if (pos != null) {
					usedFeet.add(pos.asLong());
				}
			}
		}
		int minY = level.getMinY() + 1;
		int maxY = level.getMinY() + level.getHeight() - 2;
		int visited = 0;
		int radius = 0;
		long started = debugNanos();
		for (int r = 0; r <= MAX_RADIUS && result.size() < needed && visited < MAX_VISITED; r++) {
			radius = r;
			for (int dy = -r; dy <= r && result.size() < needed && visited < MAX_VISITED; dy++) {
				int y = origin.getY() + dy;
				if (y < minY || y > maxY) {
					continue;
				}
				for (int dx = -r; dx <= r && result.size() < needed && visited < MAX_VISITED; dx++) {
					for (int dz = -r; dz <= r && result.size() < needed && visited < MAX_VISITED; dz++) {
						if (chebyshev(dx, dy, dz) != r) {
							continue;
						}
						int x = origin.getX() + dx;
						int z = origin.getZ() + dz;
						if (!inBounds(x, z, chunkBound) || !level.hasChunk(x >> 4, z >> 4)) {
							continue;
						}
						visited++;
						BlockPos feet = new BlockPos(x, y, z);
						if (usedFeet.contains(feet.asLong()) || !isStandable(level, feet) || villagerOccupies(level, feet)) {
							continue;
						}
						usedFeet.add(feet.asLong());
						result.add(feet);
					}
				}
			}
		}
		if (Refugee.LOGGER.isDebugEnabled()) {
			Refugee.LOGGER.debug(
					"[refugee standable] search origin={} needed={} found={} visited={} radius={} chunkBound={} {}ns",
					origin.toShortString(),
					needed,
					result.size(),
					visited,
					radius,
					chunkBound == null ? "none" : chunkBound.x + "," + chunkBound.z,
					elapsedNanos(started)
			);
		}
		return result;
	}

	private static long debugNanos() {
		return Refugee.LOGGER.isDebugEnabled() ? System.nanoTime() : 0L;
	}

	private static long elapsedNanos(long started) {
		return started == 0L ? 0L : System.nanoTime() - started;
	}

	private static int chebyshev(int dx, int dy, int dz) {
		return Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
	}

	private static double distSqCenter(BlockPos a, BlockPos b) {
		double dx = (a.getX() + 0.5) - (b.getX() + 0.5);
		double dy = (a.getY() + 0.5) - (b.getY() + 0.5);
		double dz = (a.getZ() + 0.5) - (b.getZ() + 0.5);
		return dx * dx + dy * dy + dz * dz;
	}

	private static boolean inBounds(int x, int z, ChunkPos chunkBound) {
		if (chunkBound == null) {
			return true;
		}
		return x >= chunkBound.getMinBlockX()
				&& x <= chunkBound.getMaxBlockX()
				&& z >= chunkBound.getMinBlockZ()
				&& z <= chunkBound.getMaxBlockZ();
	}

	public static boolean isStandable(ServerLevel level, BlockPos feet) {
		if (level == null || feet == null || !level.hasChunk(feet.getX() >> 4, feet.getZ() >> 4)) {
			return false;
		}
		BlockPos below = feet.below();
		BlockPos head = feet.above();
		BlockState floor = level.getBlockState(below);
		if (!isFloor(floor, level, below)) {
			return false;
		}
		return isOpen(level.getBlockState(feet), level, feet)
				&& isOpen(level.getBlockState(head), level, head);
	}

	private static boolean isFloor(BlockState state, ServerLevel level, BlockPos pos) {
		return state.isFaceSturdy(level, pos, Direction.UP) || state.isCollisionShapeFullBlock(level, pos);
	}

	private static boolean isOpen(BlockState state, ServerLevel level, BlockPos pos) {
		if (!state.getFluidState().isEmpty()) {
			return false;
		}
		return !state.isCollisionShapeFullBlock(level, pos);
	}

	public static boolean villagerOccupies(ServerLevel level, BlockPos feet) {
		AABB box = new AABB(feet).inflate(0.1, 0.1, 0.1);
		for (Villager villager : level.getEntitiesOfClass(Villager.class, box, Entity::isAlive)) {
			if (villager.blockPosition().equals(feet)) {
				return true;
			}
		}
		return false;
	}
}
