package luowei.refugee.logistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import luowei.refugee.build.BuildJob;
import luowei.refugee.zone.WorkZone;

/**
 * 按 PBS 组织 UUID 持久化仓库箱子列表、工作区与建筑任务。
 */
public final class OrgLogisticsData extends SavedData {
	private static final String DATA_ID = "refugee_org_logistics";

	public static final Codec<OrgLogisticsData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, OrgRecord.CODEC)
					.optionalFieldOf("orgs", Map.of())
					.forGetter(OrgLogisticsData::toCodecMap)
	).apply(instance, OrgLogisticsData::fromCodec));

	public static final SavedDataType<OrgLogisticsData> TYPE = new SavedDataType<>(
			DATA_ID,
			context -> new OrgLogisticsData(),
			context -> OrgLogisticsData.CODEC,
			null
	);

	private final Map<UUID, OrgRecord> orgs = new HashMap<>();

	public OrgLogisticsData() {
	}

	private static OrgLogisticsData fromCodec(Map<UUID, OrgRecord> orgs) {
		OrgLogisticsData data = new OrgLogisticsData();
		if (orgs != null) {
			data.orgs.putAll(orgs);
		}
		return data;
	}

	private Map<UUID, OrgRecord> toCodecMap() {
		return Map.copyOf(orgs);
	}

	public static OrgLogisticsData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public OrgRecord org(UUID subjectId) {
		if (subjectId == null) {
			return new OrgRecord();
		}
		return orgs.computeIfAbsent(subjectId, id -> new OrgRecord());
	}

	public boolean hasOrg(UUID subjectId) {
		return subjectId != null && orgs.containsKey(subjectId);
	}

	public List<ContainerRef> warehouses(UUID subjectId) {
		OrgRecord org = orgs.get(subjectId);
		return org == null ? List.of() : List.copyOf(org.warehouses);
	}

	public List<ContainerRef> foodWarehouses(UUID subjectId) {
		OrgRecord org = orgs.get(subjectId);
		return org == null ? List.of() : List.copyOf(org.foodWarehouses);
	}

	public List<ContainerRef> smelters(UUID subjectId) {
		OrgRecord org = orgs.get(subjectId);
		return org == null ? List.of() : List.copyOf(org.smelters);
	}

	public List<ContainerRef> farmWarehouses(UUID subjectId) {
		OrgRecord org = orgs.get(subjectId);
		return org == null ? List.of() : List.copyOf(org.farmWarehouses);
	}

	public List<ContainerRef> gearWarehouses(UUID subjectId) {
		OrgRecord org = orgs.get(subjectId);
		return org == null ? List.of() : List.copyOf(org.gearWarehouses);
	}

	public List<ContainerRef> smeltResults(UUID subjectId) {
		OrgRecord org = orgs.get(subjectId);
		return org == null ? List.of() : List.copyOf(org.smeltResults);
	}

	public Set<UUID> subjectIds() {
		return Set.copyOf(orgs.keySet());
	}

	public boolean addWarehouse(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		if (subjectId == null || dimension == null || pos == null) {
			return false;
		}
		OrgRecord org = org(subjectId);
		ContainerRef ref = new ContainerRef(dimension, pos.immutable());
		if (org.warehouses.contains(ref)) {
			return false;
		}
		detach(org, ref);
		org.warehouses.add(ref);
		setDirty();
		return true;
	}

	public boolean removeWarehouse(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		if (subjectId == null || dimension == null || pos == null) {
			return false;
		}
		OrgRecord org = orgs.get(subjectId);
		if (org == null) {
			return false;
		}
		boolean removed = org.warehouses.remove(new ContainerRef(dimension, pos.immutable()));
		if (removed) {
			setDirty();
		}
		return removed;
	}

	public boolean hasWarehouse(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		OrgRecord org = orgs.get(subjectId);
		return org != null && org.warehouses.contains(new ContainerRef(dimension, pos.immutable()));
	}

	public boolean addFoodWarehouse(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		if (subjectId == null || dimension == null || pos == null) {
			return false;
		}
		OrgRecord org = org(subjectId);
		ContainerRef ref = new ContainerRef(dimension, pos.immutable());
		if (org.foodWarehouses.contains(ref)) {
			return false;
		}
		detach(org, ref);
		org.foodWarehouses.add(ref);
		setDirty();
		return true;
	}

	public boolean removeFoodWarehouse(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		if (subjectId == null || dimension == null || pos == null) {
			return false;
		}
		OrgRecord org = orgs.get(subjectId);
		if (org == null) {
			return false;
		}
		boolean removed = org.foodWarehouses.remove(new ContainerRef(dimension, pos.immutable()));
		if (removed) {
			setDirty();
		}
		return removed;
	}

	public boolean hasFoodWarehouse(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		OrgRecord org = orgs.get(subjectId);
		return org != null && org.foodWarehouses.contains(new ContainerRef(dimension, pos.immutable()));
	}

	public boolean addSmelter(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		if (subjectId == null || dimension == null || pos == null) {
			return false;
		}
		OrgRecord org = org(subjectId);
		ContainerRef ref = new ContainerRef(dimension, pos.immutable());
		if (org.smelters.contains(ref)) {
			return false;
		}
		detach(org, ref);
		org.smelters.add(ref);
		setDirty();
		return true;
	}

	public boolean removeSmelter(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		if (subjectId == null || dimension == null || pos == null) {
			return false;
		}
		OrgRecord org = orgs.get(subjectId);
		if (org == null) {
			return false;
		}
		boolean removed = org.smelters.remove(new ContainerRef(dimension, pos.immutable()));
		if (removed) {
			setDirty();
		}
		return removed;
	}

	public boolean hasSmelter(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		OrgRecord org = orgs.get(subjectId);
		return org != null && org.smelters.contains(new ContainerRef(dimension, pos.immutable()));
	}

	public boolean addFarmWarehouse(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		if (subjectId == null || dimension == null || pos == null) {
			return false;
		}
		OrgRecord org = org(subjectId);
		ContainerRef ref = new ContainerRef(dimension, pos.immutable());
		if (org.farmWarehouses.contains(ref)) {
			return false;
		}
		detach(org, ref);
		org.farmWarehouses.add(ref);
		setDirty();
		return true;
	}

	public boolean removeFarmWarehouse(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		if (subjectId == null || dimension == null || pos == null) {
			return false;
		}
		OrgRecord org = orgs.get(subjectId);
		if (org == null) {
			return false;
		}
		boolean removed = org.farmWarehouses.remove(new ContainerRef(dimension, pos.immutable()));
		if (removed) {
			setDirty();
		}
		return removed;
	}

	public boolean hasFarmWarehouse(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		OrgRecord org = orgs.get(subjectId);
		return org != null && org.farmWarehouses.contains(new ContainerRef(dimension, pos.immutable()));
	}

	public boolean addGearWarehouse(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		if (subjectId == null || dimension == null || pos == null) {
			return false;
		}
		OrgRecord org = org(subjectId);
		ContainerRef ref = new ContainerRef(dimension, pos.immutable());
		if (org.gearWarehouses.contains(ref)) {
			return false;
		}
		detach(org, ref);
		org.gearWarehouses.add(ref);
		setDirty();
		return true;
	}

	public boolean removeGearWarehouse(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		if (subjectId == null || dimension == null || pos == null) {
			return false;
		}
		OrgRecord org = orgs.get(subjectId);
		if (org == null) {
			return false;
		}
		boolean removed = org.gearWarehouses.remove(new ContainerRef(dimension, pos.immutable()));
		if (removed) {
			setDirty();
		}
		return removed;
	}

	public boolean hasGearWarehouse(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		OrgRecord org = orgs.get(subjectId);
		return org != null && org.gearWarehouses.contains(new ContainerRef(dimension, pos.immutable()));
	}

	public boolean addSmeltResult(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		if (subjectId == null || dimension == null || pos == null) {
			return false;
		}
		OrgRecord org = org(subjectId);
		ContainerRef ref = new ContainerRef(dimension, pos.immutable());
		if (org.smeltResults.contains(ref)) {
			return false;
		}
		detach(org, ref);
		org.smeltResults.add(ref);
		setDirty();
		return true;
	}

	public boolean removeSmeltResult(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		if (subjectId == null || dimension == null || pos == null) {
			return false;
		}
		OrgRecord org = orgs.get(subjectId);
		if (org == null) {
			return false;
		}
		boolean removed = org.smeltResults.remove(new ContainerRef(dimension, pos.immutable()));
		if (removed) {
			setDirty();
		}
		return removed;
	}

	public boolean hasSmeltResult(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		OrgRecord org = orgs.get(subjectId);
		return org != null && org.smeltResults.contains(new ContainerRef(dimension, pos.immutable()));
	}

	private static void detach(OrgRecord org, ContainerRef ref) {
		org.warehouses.remove(ref);
		org.foodWarehouses.remove(ref);
		org.farmWarehouses.remove(ref);
		org.gearWarehouses.remove(ref);
		org.smeltResults.remove(ref);
		org.smelters.remove(ref);
	}

	public void addZone(UUID subjectId, WorkZone zone) {
		if (subjectId == null || zone == null) {
			return;
		}
		org(subjectId).zones.add(zone);
		setDirty();
	}

	public List<WorkZone> zones(UUID subjectId) {
		OrgRecord org = orgs.get(subjectId);
		return org == null ? List.of() : List.copyOf(org.zones);
	}

	public WorkZone zoneOfWorker(UUID villagerId) {
		if (villagerId == null) {
			return null;
		}
		for (OrgRecord org : orgs.values()) {
			for (WorkZone zone : org.zones) {
				if (zone.hasWorker(villagerId)) {
					return zone;
				}
			}
		}
		return null;
	}

	public UUID subjectOfWorker(UUID villagerId) {
		if (villagerId == null) {
			return null;
		}
		for (Map.Entry<UUID, OrgRecord> entry : orgs.entrySet()) {
			for (WorkZone zone : entry.getValue().zones) {
				if (zone.hasWorker(villagerId)) {
					return entry.getKey();
				}
			}
			for (BuildJob job : entry.getValue().jobs) {
				if (job.hasWorker(villagerId)) {
					return entry.getKey();
				}
			}
		}
		return null;
	}

	public void addJob(UUID subjectId, BuildJob job) {
		if (subjectId == null || job == null) {
			return;
		}
		org(subjectId).jobs.add(job);
		setDirty();
	}

	/**
	 * 方块点落在未脏任务 AABB 内则标脏。已脏的跳过。
	 */
	public Set<UUID> markBuildDirtyAt(ResourceLocation dimension, BlockPos pos) {
		if (dimension == null || pos == null) {
			return Set.of();
		}
		Set<UUID> subjects = new HashSet<>();
		for (Map.Entry<UUID, OrgRecord> entry : orgs.entrySet()) {
			for (BuildJob job : entry.getValue().jobs) {
				if (job.isDirty() || !dimension.equals(job.dimension())) {
					continue;
				}
				if (!job.bounds().contains(pos)) {
					continue;
				}
				if (job.markDirty()) {
					subjects.add(entry.getKey());
				}
			}
		}
		if (!subjects.isEmpty()) {
			setDirty();
		}
		return subjects;
	}

	/**
	 * 把 from 名下的仓库/工作区/建筑任务并入 to。
	 */
	public void mergeFrom(UUID from, UUID to) {
		if (from == null || to == null || from.equals(to)) {
			return;
		}
		OrgRecord src = orgs.remove(from);
		if (src == null) {
			return;
		}
		OrgRecord dest = org(to);
		mergeChests(src.warehouses, dest, dest.warehouses);
		mergeChests(src.foodWarehouses, dest, dest.foodWarehouses);
		mergeChests(src.farmWarehouses, dest, dest.farmWarehouses);
		mergeChests(src.gearWarehouses, dest, dest.gearWarehouses);
		mergeChests(src.smeltResults, dest, dest.smeltResults);
		mergeChests(src.smelters, dest, dest.smelters);
		dest.zones.addAll(src.zones);
		dest.jobs.addAll(src.jobs);
		setDirty();
	}

	private static void mergeChests(List<ContainerRef> source, OrgRecord dest, List<ContainerRef> target) {
		for (ContainerRef ref : source) {
			if (owns(dest, ref)) {
				continue;
			}
			target.add(ref);
		}
	}

	private static boolean owns(OrgRecord org, ContainerRef ref) {
		return org.warehouses.contains(ref)
				|| org.foodWarehouses.contains(ref)
				|| org.farmWarehouses.contains(ref)
				|| org.gearWarehouses.contains(ref)
				|| org.smeltResults.contains(ref)
				|| org.smelters.contains(ref);
	}

	public boolean hasAnyMark(UUID subjectId, ResourceLocation dimension, BlockPos pos) {
		if (subjectId == null || dimension == null || pos == null) {
			return false;
		}
		OrgRecord org = orgs.get(subjectId);
		if (org == null) {
			return false;
		}
		return owns(org, new ContainerRef(dimension, pos.immutable()));
	}

	public List<BuildJob> jobs(UUID subjectId) {
		OrgRecord org = orgs.get(subjectId);
		return org == null ? List.of() : List.copyOf(org.jobs);
	}

	public BuildJob job(UUID jobId) {
		if (jobId == null) {
			return null;
		}
		for (OrgRecord org : orgs.values()) {
			for (BuildJob job : org.jobs) {
				if (jobId.equals(job.id())) {
					return job;
				}
			}
		}
		return null;
	}

	public BuildJob jobOfWorker(UUID villagerId) {
		if (villagerId == null) {
			return null;
		}
		for (OrgRecord org : orgs.values()) {
			for (BuildJob job : org.jobs) {
				if (job.hasWorker(villagerId)) {
					return job;
				}
			}
		}
		return null;
	}

	public UUID subjectOfJob(UUID jobId) {
		if (jobId == null) {
			return null;
		}
		for (Map.Entry<UUID, OrgRecord> entry : orgs.entrySet()) {
			for (BuildJob job : entry.getValue().jobs) {
				if (jobId.equals(job.id())) {
					return entry.getKey();
				}
			}
		}
		return null;
	}

	public UUID subjectOfZone(UUID zoneId) {
		if (zoneId == null) {
			return null;
		}
		for (Map.Entry<UUID, OrgRecord> entry : orgs.entrySet()) {
			for (WorkZone zone : entry.getValue().zones) {
				if (zoneId.equals(zone.id())) {
					return entry.getKey();
				}
			}
		}
		return null;
	}

	public boolean removeZone(UUID zoneId) {
		if (zoneId == null) {
			return false;
		}
		boolean changed = false;
		for (OrgRecord org : orgs.values()) {
			changed |= org.zones.removeIf(zone -> zoneId.equals(zone.id()));
		}
		if (changed) {
			setDirty();
		}
		return changed;
	}

	public List<BuildJob> jobsWithStructure(ResourceLocation structureId) {
		if (structureId == null) {
			return List.of();
		}
		List<BuildJob> result = new ArrayList<>();
		for (OrgRecord org : orgs.values()) {
			for (BuildJob job : org.jobs) {
				if (structureId.equals(job.structureId())) {
					result.add(job);
				}
			}
		}
		return result;
	}

	public boolean removeJob(UUID jobId) {
		if (jobId == null) {
			return false;
		}
		boolean changed = false;
		for (OrgRecord org : orgs.values()) {
			changed |= org.jobs.removeIf(job -> jobId.equals(job.id()));
		}
		if (changed) {
			setDirty();
		}
		return changed;
	}

	public boolean removeWorker(UUID villagerId) {
		if (villagerId == null) {
			return false;
		}
		boolean changed = false;
		for (OrgRecord org : orgs.values()) {
			Iterator<WorkZone> zones = org.zones.iterator();
			while (zones.hasNext()) {
				WorkZone zone = zones.next();
				if (zone.removeWorker(villagerId)) {
					changed = true;
					if (zone.isEmpty()) {
						zones.remove();
					}
				}
			}
			for (BuildJob job : org.jobs) {
				if (job.removeWorker(villagerId)) {
					changed = true;
				}
			}
		}
		if (changed) {
			setDirty();
		}
		return changed;
	}

	/**
	 * 组织仓库箱子引用：维度 + 坐标。
	 */
	public record ContainerRef(ResourceLocation dimension, BlockPos pos) {
		public static final Codec<ContainerRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ResourceLocation.CODEC.fieldOf("dimension").forGetter(ContainerRef::dimension),
				BlockPos.CODEC.fieldOf("pos").forGetter(ContainerRef::pos)
		).apply(instance, ContainerRef::new));
	}

	/**
	 * 单个组织的仓库、工作区与建筑任务。
	 */
	public static final class OrgRecord {
		public static final Codec<OrgRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ContainerRef.CODEC.listOf().optionalFieldOf("warehouses", List.of()).forGetter(org -> List.copyOf(org.warehouses)),
				ContainerRef.CODEC.listOf().optionalFieldOf("food_warehouses", List.of()).forGetter(org -> List.copyOf(org.foodWarehouses)),
				WorkZone.CODEC.listOf().optionalFieldOf("zones", List.of()).forGetter(org -> List.copyOf(org.zones)),
				BuildJob.CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(org -> List.copyOf(org.jobs)),
				ContainerRef.CODEC.listOf().optionalFieldOf("smelters", List.of()).forGetter(org -> List.copyOf(org.smelters)),
				ContainerRef.CODEC.listOf().optionalFieldOf("farm_warehouses", List.of()).forGetter(org -> List.copyOf(org.farmWarehouses)),
				ContainerRef.CODEC.listOf().optionalFieldOf("gear_warehouses", List.of()).forGetter(org -> List.copyOf(org.gearWarehouses)),
				ContainerRef.CODEC.listOf().optionalFieldOf("smelt_results", List.of()).forGetter(org -> List.copyOf(org.smeltResults))
		).apply(instance, OrgRecord::fromCodec));

		private final List<ContainerRef> warehouses = new ArrayList<>();
		private final List<ContainerRef> foodWarehouses = new ArrayList<>();
		private final List<WorkZone> zones = new ArrayList<>();
		private final List<BuildJob> jobs = new ArrayList<>();
		private final List<ContainerRef> smelters = new ArrayList<>();
		private final List<ContainerRef> farmWarehouses = new ArrayList<>();
		private final List<ContainerRef> gearWarehouses = new ArrayList<>();
		private final List<ContainerRef> smeltResults = new ArrayList<>();

		public OrgRecord() {
		}

		private static OrgRecord fromCodec(
				List<ContainerRef> warehouses,
				List<ContainerRef> foodWarehouses,
				List<WorkZone> zones,
				List<BuildJob> jobs,
				List<ContainerRef> smelters,
				List<ContainerRef> farmWarehouses,
				List<ContainerRef> gearWarehouses,
				List<ContainerRef> smeltResults
		) {
			OrgRecord org = new OrgRecord();
			if (warehouses != null) {
				org.warehouses.addAll(warehouses);
			}
			if (foodWarehouses != null) {
				org.foodWarehouses.addAll(foodWarehouses);
			}
			if (zones != null) {
				org.zones.addAll(zones);
			}
			if (jobs != null) {
				org.jobs.addAll(jobs);
			}
			if (smelters != null) {
				org.smelters.addAll(smelters);
			}
			if (farmWarehouses != null) {
				org.farmWarehouses.addAll(farmWarehouses);
			}
			if (gearWarehouses != null) {
				org.gearWarehouses.addAll(gearWarehouses);
			}
			if (smeltResults != null) {
				org.smeltResults.addAll(smeltResults);
			}
			return org;
		}

		public List<ContainerRef> warehouses() {
			return warehouses;
		}

		public List<ContainerRef> foodWarehouses() {
			return foodWarehouses;
		}

		public List<ContainerRef> smelters() {
			return smelters;
		}

		public List<ContainerRef> farmWarehouses() {
			return farmWarehouses;
		}

		public List<ContainerRef> gearWarehouses() {
			return gearWarehouses;
		}

		public List<ContainerRef> smeltResults() {
			return smeltResults;
		}

		public List<WorkZone> zones() {
			return zones;
		}

		public List<BuildJob> jobs() {
			return jobs;
		}
	}
}
