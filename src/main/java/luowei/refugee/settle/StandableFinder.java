package luowei.refugee.settle;

import java.util.ArrayDeque;
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
 * 从原点做六连通三维 BFS 找可站立落脚点。
 * 同距离先扩水平（北南东西），再上下；实心与流体不穿过。
 * 可站只看脚下实心、脚与头两格可穿过（雪层等非完整方块算可穿过）。
 */
public final class StandableFinder {
	public static final int SEARCH_RADIUS = 16;
	private static final int MAX_RADIUS = SEARCH_RADIUS * 2;
	private static final int MAX_VISITED = 32768;
	private static final Direction[] BFS_DIRS = {
			Direction.NORTH,
			Direction.SOUTH,
			Direction.EAST,
			Direction.WEST,
			Direction.UP,
			Direction.DOWN
	};

	private StandableFinder() {
	}

	/**
	 * 从原点 BFS 找 {@code needed} 个可站立格。曼哈顿距离最多 32。
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
		List<BlockPos> found = findInChunk(level, chunk, reserved, 1);
		return found.isEmpty() ? null : found.getFirst();
	}

	/**
	 * 入境：在区块内从中心 BFS 找 {@code needed} 个可站立格。
	 */
	public static List<BlockPos> findInChunk(ServerLevel level, ChunkPos chunk, Set<BlockPos> reserved, int needed) {
		if (level == null || chunk == null || needed <= 0) {
			return List.of();
		}
		int x = chunk.getMinBlockX() + 8;
		int z = chunk.getMinBlockZ() + 8;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		return search(level, new BlockPos(x, y, z), reserved, needed, chunk);
	}

	/**
	 * 在目标周围找可站且到目标中心不超过 {@code maxDistSq} 的格，优先靠近 {@code from}。
	 */
	public static BlockPos findStandNear(ServerLevel level, BlockPos target, BlockPos from, double maxDistSq) {
		if (level == null || target == null) {
			return null;
		}
		long started = debugNanos();
		int maxDist = Math.min(MAX_RADIUS, Math.max(1, (int) Math.ceil(Math.sqrt(maxDistSq * 3.0))));
		BlockPos[] best = {null};
		double[] bestFrom = {Double.MAX_VALUE};
		int[] standable = {0};
		int checked = walkBfs(level, target, null, maxDist, (feet, dist) -> {
			if (feet.equals(target) || !isStandable(level, feet) || villagerOccupies(level, feet)) {
				return true;
			}
			double toTarget = distSqCenter(feet, target);
			if (toTarget > maxDistSq) {
				return true;
			}
			standable[0]++;
			double toFrom = from == null ? toTarget : distSqCenter(feet, from);
			if (toFrom < bestFrom[0]) {
				bestFrom[0] = toFrom;
				best[0] = feet;
			}
			return true;
		});
		long elapsed = elapsedNanos(started);
		if (Refugee.LOGGER.isDebugEnabled() && elapsed >= 1_000_000L) {
			Refugee.LOGGER.debug(
					"[refugee standable] findStandNear target={} from={} maxDist={} checked={} standable={} best={} {}ns",
					target.toShortString(),
					from == null ? "none" : from.toShortString(),
					maxDist,
					checked,
					standable[0],
					best[0] == null ? "none" : best[0].toShortString(),
					elapsed
			);
		}
		return best[0];
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
		int[] maxDist = {0};
		long started = debugNanos();
		int visited = walkBfs(level, origin, chunkBound, MAX_RADIUS, (feet, dist) -> {
			maxDist[0] = dist;
			if (usedFeet.contains(feet.asLong()) || !isStandable(level, feet) || villagerOccupies(level, feet)) {
				return result.size() < needed;
			}
			usedFeet.add(feet.asLong());
			result.add(feet);
			return result.size() < needed;
		});
		if (Refugee.LOGGER.isDebugEnabled()) {
			Refugee.LOGGER.debug(
					"[refugee standable] search origin={} needed={} found={} visited={} dist={} chunkBound={} {}ns",
					origin.toShortString(),
					needed,
					result.size(),
					visited,
					maxDist[0],
					chunkBound == null ? "none" : chunkBound.x + "," + chunkBound.z,
					elapsedNanos(started)
			);
		}
		return result;
	}

	/**
	 * 六连通 BFS。原点即使不可穿过也会作为起点扩出；之后只进入脚、头都可穿过的格。
	 *
	 * @return 出队格数；{@code visitor} 返回 false 时提前结束
	 */
	private static int walkBfs(
			ServerLevel level,
			BlockPos origin,
			ChunkPos chunkBound,
			int maxDist,
			BfsVisitor visitor
	) {
		int minY = level.getMinY() + 1;
		int maxY = level.getMinY() + level.getHeight() - 2;
		ArrayDeque<Step> queue = new ArrayDeque<>();
		Set<Long> seen = new HashSet<>();
		queue.add(new Step(origin.getX(), origin.getY(), origin.getZ(), 0));
		seen.add(origin.asLong());
		int checked = 0;
		while (!queue.isEmpty() && seen.size() <= MAX_VISITED) {
			Step step = queue.poll();
			checked++;
			BlockPos feet = new BlockPos(step.x, step.y, step.z);
			if (!visitor.visit(feet, step.dist)) {
				return checked;
			}
			if (step.dist >= maxDist) {
				continue;
			}
			for (Direction dir : BFS_DIRS) {
				int x = step.x + dir.getStepX();
				int y = step.y + dir.getStepY();
				int z = step.z + dir.getStepZ();
				if (y < minY || y > maxY || !inBounds(x, z, chunkBound) || !level.hasChunk(x >> 4, z >> 4)) {
					continue;
				}
				if (!seen.add(BlockPos.asLong(x, y, z))) {
					continue;
				}
				BlockPos next = new BlockPos(x, y, z);
				if (canTraverse(level, next)) {
					queue.add(new Step(x, y, z, step.dist + 1));
				}
			}
		}
		return checked;
	}

	private static long debugNanos() {
		return Refugee.LOGGER.isDebugEnabled() ? System.nanoTime() : 0L;
	}

	private static long elapsedNanos(long started) {
		return started == 0L ? 0L : System.nanoTime() - started;
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
		BlockState floor = level.getBlockState(below);
		if (!isFloor(floor, level, below)) {
			return false;
		}
		return canTraverse(level, feet);
	}

	private static boolean canTraverse(ServerLevel level, BlockPos feet) {
		BlockPos head = feet.above();
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

	@FunctionalInterface
	private interface BfsVisitor {
		boolean visit(BlockPos feet, int dist);
	}

	private record Step(int x, int y, int z, int dist) {
	}
}
