package luowei.refugee.zone;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * 非建筑工作区：AABB + 认领该区域的工人 UUID。无人认领则应从记录中删除。
 */
public final class WorkZone {
	public static final Codec<WorkZone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(WorkZone::id),
			ResourceLocation.CODEC.fieldOf("dimension").forGetter(WorkZone::dimension),
			AreaBox.CODEC.fieldOf("box").forGetter(WorkZone::box),
			UUIDUtil.CODEC.listOf().optionalFieldOf("workers", List.of()).forGetter(zone -> List.copyOf(zone.workers))
	).apply(instance, WorkZone::fromCodec));

	private final UUID id;
	private final ResourceLocation dimension;
	private final AreaBox box;
	private final Set<UUID> workers = new LinkedHashSet<>();

	public WorkZone(UUID id, ResourceLocation dimension, AreaBox box) {
		this.id = id;
		this.dimension = dimension;
		this.box = box;
	}

	private static WorkZone fromCodec(UUID id, ResourceLocation dimension, AreaBox box, List<UUID> workers) {
		WorkZone zone = new WorkZone(id, dimension, box);
		if (workers != null) {
			zone.workers.addAll(workers);
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
		return villagerId != null && workers.remove(villagerId);
	}

	public boolean isEmpty() {
		return workers.isEmpty();
	}
}
