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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 以原点做 XZ 广度优先搜索，按距离分配可站立落脚点。
 * 可站立以村民碰撞盒为准，脚底贴地板碰撞顶面，避免 2 格高空间被顶到屋顶。
 */
public final class StandableFinder {
	public static final int SEARCH_RADIUS = 16;
	private static final int MAX_EXPAND_ROUNDS = 8;
	private static final int MAX_VISITED_COLUMNS = 8192;
	private static final int NEAR_UP = 2;
	private static final int NEAR_DOWN = 6;
	private static final int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1};
	private static final int[] DZ = {-1, -1, -1, 0, 0, 1, 1, 1};

	private StandableFinder() {
	}

	/**
	 * 从原点 BFS 找 {@code needed} 个可站立格。半径 16 不够时以已占用点为种子再向外扩。
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
	 * 入境：在区块内从中心 BFS 找一个可站立格，不扫西北角。
	 */
	public static BlockPos findInChunk(ServerLevel level, ChunkPos chunk, Set<BlockPos> reserved) {
		int x = chunk.getMinBlockX() + 8;
		int z = chunk.getMinBlockZ() + 8;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		List<BlockPos> found = search(level, new BlockPos(x, y, z), reserved, 1, chunk);
		return found.isEmpty() ? null : found.getFirst();
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
		Set<Long> visitedColumns = new HashSet<>();
		List<BlockPos> seeds = new ArrayList<>();
		seeds.add(origin);
		int rounds = 0;
		while (result.size() < needed && !seeds.isEmpty() && rounds <= MAX_EXPAND_ROUNDS) {
			int before = result.size();
			expandFrom(level, seeds, visitedColumns, usedFeet, result, needed, chunkBound);
			if (result.size() >= needed || visitedColumns.size() >= MAX_VISITED_COLUMNS) {
				break;
			}
			if (result.size() == before) {
				break;
			}
			seeds = new ArrayList<>(result.subList(before, result.size()));
			rounds++;
		}
		return result;
	}

	private static void expandFrom(
			ServerLevel level,
			List<BlockPos> seeds,
			Set<Long> visitedColumns,
			Set<Long> usedFeet,
			List<BlockPos> result,
			int needed,
			ChunkPos chunkBound
	) {
		ArrayDeque<Node> queue = new ArrayDeque<>();
		Set<Long> queued = new HashSet<>();
		for (BlockPos seed : seeds) {
			if (seed == null || !inBounds(seed.getX(), seed.getZ(), chunkBound)) {
				continue;
			}
			long key = columnKey(seed.getX(), seed.getZ());
			if (!queued.add(key)) {
				continue;
			}
			queue.add(new Node(seed.getX(), seed.getZ(), seed.getY(), 0));
		}
		while (!queue.isEmpty() && result.size() < needed && visitedColumns.size() < MAX_VISITED_COLUMNS) {
			Node node = queue.poll();
			long col = columnKey(node.x, node.z);
			if (visitedColumns.add(col)) {
				BlockPos feet = findInColumn(level, node.x, node.z, node.y, usedFeet);
				if (feet != null) {
					usedFeet.add(feet.asLong());
					result.add(feet);
					if (result.size() >= needed) {
						return;
					}
				}
			}
			if (node.dist >= SEARCH_RADIUS) {
				continue;
			}
			for (int i = 0; i < DX.length; i++) {
				int nx = node.x + DX[i];
				int nz = node.z + DZ[i];
				if (!inBounds(nx, nz, chunkBound) || !level.hasChunk(nx >> 4, nz >> 4)) {
					continue;
				}
				long nkey = columnKey(nx, nz);
				if (visitedColumns.contains(nkey) || !queued.add(nkey)) {
					continue;
				}
				queue.add(new Node(nx, nz, node.y, node.dist + 1));
			}
		}
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

	private static long columnKey(int x, int z) {
		return BlockPos.asLong(x, 0, z);
	}

	private static BlockPos findInColumn(ServerLevel level, int x, int z, int aroundY, Set<Long> usedFeet) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return null;
		}
		int minY = level.getMinY() + 1;
		int maxY = level.getMinY() + level.getHeight() - 2;
		int nearHigh = clampY(aroundY + NEAR_UP, minY, maxY);
		int nearLow = clampY(aroundY - NEAR_DOWN, minY, maxY);
		BlockPos found = scanClosest(level, x, z, aroundY, nearHigh, nearLow, usedFeet);
		if (found != null) {
			return found;
		}
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		int top = clampY(surface + 1, minY, maxY);
		int bottom = clampY(surface - 4, minY, maxY);
		if (top <= nearHigh && bottom >= nearLow) {
			return null;
		}
		return scanClosest(level, x, z, aroundY, top, bottom, usedFeet);
	}

	private static int clampY(int y, int minY, int maxY) {
		return Math.max(minY, Math.min(maxY, y));
	}

	/**
	 * 在 [yLow, yHigh] 内从 {@code aroundY} 向外扩，先近后远，避免先拿到屋顶。
	 */
	private static BlockPos scanClosest(
			ServerLevel level,
			int x,
			int z,
			int aroundY,
			int yHigh,
			int yLow,
			Set<Long> usedFeet
	) {
		int start = Math.max(yHigh, yLow);
		int end = Math.min(yHigh, yLow);
		int origin = Math.max(end, Math.min(start, aroundY));
		BlockPos atOrigin = tryFeet(level, x, origin, z, usedFeet);
		if (atOrigin != null) {
			return atOrigin;
		}
		int maxDelta = Math.max(start - origin, origin - end);
		for (int delta = 1; delta <= maxDelta; delta++) {
			int up = origin + delta;
			if (up <= start) {
				BlockPos found = tryFeet(level, x, up, z, usedFeet);
				if (found != null) {
					return found;
				}
			}
			int down = origin - delta;
			if (down >= end) {
				BlockPos found = tryFeet(level, x, down, z, usedFeet);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static BlockPos tryFeet(ServerLevel level, int x, int y, int z, Set<Long> usedFeet) {
		BlockPos feet = new BlockPos(x, y, z);
		if (usedFeet.contains(feet.asLong()) || !isStandable(level, feet) || villagerOccupies(level, feet)) {
			return null;
		}
		return feet;
	}

	public static boolean isStandable(ServerLevel level, BlockPos feet) {
		if (level == null || feet == null || !level.hasChunk(feet.getX() >> 4, feet.getZ() >> 4)) {
			return false;
		}
		BlockPos below = feet.below();
		BlockState floor = level.getBlockState(below);
		VoxelShape floorShape = floor.getCollisionShape(level, below);
		double floorTop;
		if (floorShape.isEmpty()) {
			if (!floor.isFaceSturdy(level, below, Direction.UP)) {
				return false;
			}
			floorTop = 1.0;
		} else {
			floorTop = floorShape.max(Direction.Axis.Y);
			if (Double.isNaN(floorTop) || floorTop <= 0.0) {
				return false;
			}
		}
		if (!level.getFluidState(feet).isEmpty()) {
			return false;
		}
		double y = below.getY() + floorTop;
		AABB box = EntityType.VILLAGER.getSpawnAABB(feet.getX() + 0.5, y, feet.getZ() + 0.5);
		return level.noCollision(box);
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

	private record Node(int x, int z, int y, int dist) {
	}
}
