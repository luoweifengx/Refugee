package luowei.refugee.zone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;

/**
 * 工作区：框定（盒内循环）或推进（切面沿轴持续平移，可出框）。无人认领则应从记录中删除。
 */
public final class WorkZone {
	public static final Codec<WorkZone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(WorkZone::id),
			ResourceLocation.CODEC.fieldOf("dimension").forGetter(WorkZone::dimension),
			AreaBox.CODEC.fieldOf("box").forGetter(WorkZone::box),
			UUIDUtil.CODEC.listOf().optionalFieldOf("workers", List.of()).forGetter(zone -> List.copyOf(zone.workers)),
			Codec.STRING.optionalFieldOf("kind", "box").forGetter(WorkZone::kindId),
			Codec.STRING.optionalFieldOf("axis", "y").forGetter(zone -> switch (zone.axis) {
				case X -> "x";
				case Y -> "y";
				case Z -> "z";
			}),
			Codec.BOOL.optionalFieldOf("positive", true).forGetter(WorkZone::positive),
			Codec.INT.optionalFieldOf("layer", 0).forGetter(WorkZone::layerCoord),
			Codec.INT.optionalFieldOf("cell_index", 0).forGetter(WorkZone::cellIndex),
			Codec.BOOL.optionalFieldOf("claimed_any", false).forGetter(WorkZone::claimedAnyThisLayer),
			Claim.CODEC.listOf().optionalFieldOf("claims", List.of()).forGetter(WorkZone::codecClaims)
	).apply(instance, WorkZone::fromCodec));

	private final UUID id;
	private final ResourceLocation dimension;
	private final AreaBox box;
	private final Set<UUID> workers = new LinkedHashSet<>();
	private final boolean advance;
	private final Direction.Axis axis;
	private final boolean positive;
	private int layerCoord;
	private int cellIndex;
	private boolean claimedAnyThisLayer;
	private final Map<UUID, BlockPos> claims = new LinkedHashMap<>();

	public WorkZone(UUID id, ResourceLocation dimension, AreaBox box) {
		this(id, dimension, box, false, Direction.Axis.Y, true, 0, 0, false);
	}

	public static WorkZone advance(
			UUID id,
			ResourceLocation dimension,
			AreaBox box,
			Direction.Axis axis,
			boolean positive
	) {
		Direction.Axis resolved = axis == null ? Direction.Axis.Y : axis;
		int start = startLayer(box, resolved, positive);
		return new WorkZone(id, dimension, box, true, resolved, positive, start, 0, false);
	}

	private WorkZone(
			UUID id,
			ResourceLocation dimension,
			AreaBox box,
			boolean advance,
			Direction.Axis axis,
			boolean positive,
			int layerCoord,
			int cellIndex,
			boolean claimedAnyThisLayer
	) {
		this.id = id;
		this.dimension = dimension;
		this.box = box;
		this.advance = advance;
		this.axis = axis == null ? Direction.Axis.Y : axis;
		this.positive = positive;
		this.layerCoord = layerCoord;
		this.cellIndex = Math.max(0, cellIndex);
		this.claimedAnyThisLayer = claimedAnyThisLayer;
	}

	private static WorkZone fromCodec(
			UUID id,
			ResourceLocation dimension,
			AreaBox box,
			List<UUID> workers,
			String kind,
			String axisId,
			boolean positive,
			int layer,
			int cellIndex,
			boolean claimedAny,
			List<Claim> claims
	) {
		boolean advance = "advance".equals(kind);
		Direction.Axis axis = parseAxis(axisId);
		WorkZone zone = new WorkZone(id, dimension, box, advance, axis, positive, layer, cellIndex, claimedAny);
		if (workers != null) {
			zone.workers.addAll(workers);
		}
		if (claims != null) {
			for (Claim claim : claims) {
				if (claim != null && claim.worker() != null && claim.pos() != null) {
					zone.claims.put(claim.worker(), claim.pos().immutable());
				}
			}
		}
		return zone;
	}

	public UUID id() {
		return id;
	}

	public ResourceLocation dimension() {
		return dimension;
	}

	public AreaBox box() {
		return box;
	}

	public AABB aabb() {
		return box.aabb();
	}

	public BlockPos min() {
		return box.min();
	}

	public BlockPos max() {
		return box.max();
	}

	public boolean isAdvance() {
		return advance;
	}

	public Direction.Axis axis() {
		return axis;
	}

	public boolean positive() {
		return positive;
	}

	public int layerCoord() {
		return layerCoord;
	}

	public int cellIndex() {
		return cellIndex;
	}

	public boolean claimedAnyThisLayer() {
		return claimedAnyThisLayer;
	}

	public Set<UUID> workers() {
		return workers;
	}

	public List<UUID> snapshotWorkers() {
		return new ArrayList<>(workers);
	}

	public boolean hasWorker(UUID villagerId) {
		return villagerId != null && workers.contains(villagerId);
	}

	public boolean addWorker(UUID villagerId) {
		return villagerId != null && workers.add(villagerId);
	}

	public boolean removeWorker(UUID villagerId) {
		if (villagerId == null) {
			return false;
		}
		claims.remove(villagerId);
		return workers.remove(villagerId);
	}

	public boolean isEmpty() {
		return workers.isEmpty();
	}

	public int layerVolume() {
		return uSize() * vSize();
	}

	public AreaBox currentSlice() {
		return sliceAt(layerCoord);
	}

	public boolean isAdvanceCell(BlockPos pos) {
		return pos != null && currentSlice().contains(pos);
	}

	public BlockPos claimOf(UUID villagerId) {
		return villagerId == null ? null : claims.get(villagerId);
	}

	public void clearClaim(UUID villagerId) {
		if (villagerId != null) {
			claims.remove(villagerId);
		}
	}

	public enum AdvanceDispatch {
		WAIT,
		LAYER_DONE
	}

	/**
	 * 共用光标：无人能挖则跳过；有人能挖则分给先查到的空闲工人并下移；能挖的人都在忙或区块未加载则等待。
	 */
	public AdvanceDispatch dispatch(
			ServerLevel level,
			List<Villager> members,
			BiPredicate<Villager, BlockPos> canMine
	) {
		if (level == null || canMine == null) {
			return AdvanceDispatch.WAIT;
		}
		int volume = layerVolume();
		while (cellIndex < volume) {
			BlockPos pos = cellAt(cellIndex);
			if (pos == null) {
				cellIndex++;
				continue;
			}
			if (!level.isLoaded(pos)) {
				return AdvanceDispatch.WAIT;
			}
			Villager assignee = null;
			boolean anyCapable = false;
			if (members != null) {
				for (Villager member : members) {
					if (member == null || !member.isAlive() || !canMine.test(member, pos)) {
						continue;
					}
					anyCapable = true;
					if (!claims.containsKey(member.getUUID())) {
						assignee = member;
						break;
					}
				}
			}
			if (!anyCapable) {
				cellIndex++;
				continue;
			}
			if (assignee == null) {
				return AdvanceDispatch.WAIT;
			}
			claims.put(assignee.getUUID(), pos.immutable());
			cellIndex++;
			claimedAnyThisLayer = true;
			return AdvanceDispatch.WAIT;
		}
		if (!claims.isEmpty()) {
			return AdvanceDispatch.WAIT;
		}
		return AdvanceDispatch.LAYER_DONE;
	}

	/**
	 * 进入下一层。超出世界高度或世界边界返回 false。
	 */
	public boolean advanceLayer(ServerLevel level) {
		int next = layerCoord + (positive ? 1 : -1);
		if (!isLayerInWorld(level, next)) {
			return false;
		}
		layerCoord = next;
		cellIndex = 0;
		claimedAnyThisLayer = false;
		claims.clear();
		return true;
	}

	public boolean isLayerInWorld(ServerLevel level, int layer) {
		if (level == null) {
			return false;
		}
		if (axis == Direction.Axis.Y && (layer < level.getMinY() || layer >= level.getMaxY())) {
			return false;
		}
		AreaBox slice = sliceAt(layer);
		WorldBorder border = level.getWorldBorder();
		return border.isWithinBounds(slice.min().getX() + 0.5, slice.min().getZ() + 0.5)
				&& border.isWithinBounds(slice.max().getX() + 0.5, slice.max().getZ() + 0.5);
	}

	private AreaBox sliceAt(int layer) {
		return switch (axis) {
			case X -> AreaBox.of(
					new BlockPos(layer, box.min().getY(), box.min().getZ()),
					new BlockPos(layer, box.max().getY(), box.max().getZ())
			);
			case Y -> AreaBox.of(
					new BlockPos(box.min().getX(), layer, box.min().getZ()),
					new BlockPos(box.max().getX(), layer, box.max().getZ())
			);
			case Z -> AreaBox.of(
					new BlockPos(box.min().getX(), box.min().getY(), layer),
					new BlockPos(box.max().getX(), box.max().getY(), layer)
			);
		};
	}

	private String kindId() {
		return advance ? "advance" : "box";
	}

	private int uSize() {
		return switch (axis) {
			case X -> box.sizeY();
			case Y, Z -> box.sizeX();
		};
	}

	private int vSize() {
		return switch (axis) {
			case X, Y -> box.sizeZ();
			case Z -> box.sizeY();
		};
	}

	private BlockPos cellAt(int index) {
		int uSpan = uSize();
		if (uSpan <= 0) {
			return null;
		}
		int u = index % uSpan;
		int v = index / uSpan;
		return switch (axis) {
			case X -> new BlockPos(layerCoord, box.min().getY() + u, box.min().getZ() + v);
			case Y -> new BlockPos(box.min().getX() + u, layerCoord, box.min().getZ() + v);
			case Z -> new BlockPos(box.min().getX() + u, box.min().getY() + v, layerCoord);
		};
	}

	private static int startLayer(AreaBox box, Direction.Axis axis, boolean positive) {
		int min = axis.choose(box.min().getX(), box.min().getY(), box.min().getZ());
		int max = axis.choose(box.max().getX(), box.max().getY(), box.max().getZ());
		return positive ? min : max;
	}

	private static Direction.Axis parseAxis(String id) {
		if ("x".equalsIgnoreCase(id)) {
			return Direction.Axis.X;
		}
		if ("z".equalsIgnoreCase(id)) {
			return Direction.Axis.Z;
		}
		return Direction.Axis.Y;
	}

	private List<Claim> codecClaims() {
		List<Claim> result = new ArrayList<>(claims.size());
		for (Map.Entry<UUID, BlockPos> entry : claims.entrySet()) {
			result.add(new Claim(entry.getKey(), entry.getValue()));
		}
		return result;
	}

	public record Claim(UUID worker, BlockPos pos) {
		public static final Codec<Claim> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				UUIDUtil.CODEC.fieldOf("worker").forGetter(Claim::worker),
				BlockPos.CODEC.fieldOf("pos").forGetter(Claim::pos)
		).apply(instance, Claim::new));
	}
}
