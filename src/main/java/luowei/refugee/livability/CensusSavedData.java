package luowei.refugee.livability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 每个归属一份人数和忠诚总和。人数就是成员 UUID 的个数，平均忠诚是总和除以人数。
 */
public final class CensusSavedData extends SavedData {
	private static final String DATA_ID = "refugee_census";

	private static final Codec<OrgDisk> ORG_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("subject").forGetter(OrgDisk::subject),
			Codec.DOUBLE.optionalFieldOf("sum", 0.0).forGetter(OrgDisk::sum),
			UUIDUtil.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(OrgDisk::members)
	).apply(instance, OrgDisk::new));

	public static final Codec<CensusSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ORG_CODEC.listOf().optionalFieldOf("orgs", List.of()).forGetter(CensusSavedData::disks)
	).apply(instance, CensusSavedData::fromCodec));

	public static final SavedDataType<CensusSavedData> TYPE = new SavedDataType<>(
			DATA_ID,
			context -> new CensusSavedData(),
			context -> CensusSavedData.CODEC,
			null
	);

	private final Map<UUID, Org> orgs = new HashMap<>();
	private final Map<UUID, UUID> memberSubject = new HashMap<>();

	public CensusSavedData() {
	}

	public static CensusSavedData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public UUID subjectOf(UUID villagerId) {
		return villagerId == null ? null : memberSubject.get(villagerId);
	}

	public int count(UUID subjectId) {
		Org org = orgs.get(subjectId);
		return org == null ? 0 : org.members.size();
	}

	public double average(UUID subjectId) {
		Org org = orgs.get(subjectId);
		if (org == null || org.members.isEmpty()) {
			return 0.0;
		}
		return org.sum / org.members.size();
	}

	public void add(UUID subjectId, UUID villagerId, double loyalty) {
		if (subjectId == null || villagerId == null) {
			return;
		}
		if (memberSubject.containsKey(villagerId)) {
			return;
		}
		Org org = orgs.computeIfAbsent(subjectId, Org::new);
		if (!org.members.add(villagerId)) {
			return;
		}
		memberSubject.put(villagerId, subjectId);
		org.sum += loyalty;
		setDirty();
	}

	public void remove(UUID subjectId, UUID villagerId, double loyalty) {
		if (subjectId == null || villagerId == null) {
			return;
		}
		Org org = orgs.get(subjectId);
		if (org == null || !org.members.remove(villagerId)) {
			return;
		}
		memberSubject.remove(villagerId, subjectId);
		org.sum -= loyalty;
		if (org.members.isEmpty()) {
			org.sum = 0.0;
			orgs.remove(subjectId);
		}
		setDirty();
	}

	public void adjust(UUID subjectId, double delta) {
		if (subjectId == null || delta == 0.0) {
			return;
		}
		Org org = orgs.get(subjectId);
		if (org == null || org.members.isEmpty()) {
			return;
		}
		org.sum += delta;
		setDirty();
	}

	/** 把来源归属的人数、忠诚总和和成员并进目标归属。 */
	public void mergeInto(UUID from, UUID to) {
		if (from == null || to == null || from.equals(to)) {
			return;
		}
		Org source = orgs.remove(from);
		if (source == null) {
			return;
		}
		Org dest = orgs.computeIfAbsent(to, Org::new);
		for (UUID villagerId : source.members) {
			UUID recorded = memberSubject.get(villagerId);
			if (to.equals(recorded)) {
				continue;
			}
			if (recorded != null && !from.equals(recorded)) {
				continue;
			}
			dest.members.add(villagerId);
			memberSubject.put(villagerId, to);
		}
		dest.sum += source.sum;
		if (dest.members.isEmpty()) {
			orgs.remove(to);
		}
		setDirty();
	}

	private List<OrgDisk> disks() {
		List<OrgDisk> disks = new ArrayList<>();
		for (Org org : orgs.values()) {
			if (org.members.isEmpty()) {
				continue;
			}
			disks.add(new OrgDisk(org.subject, org.sum, List.copyOf(org.members)));
		}
		return disks;
	}

	private static CensusSavedData fromCodec(List<OrgDisk> disks) {
		CensusSavedData data = new CensusSavedData();
		if (disks == null) {
			return data;
		}
		for (OrgDisk disk : disks) {
			if (disk.subject == null || disk.members == null || disk.members.isEmpty()) {
				continue;
			}
			Org org = data.orgs.computeIfAbsent(disk.subject, Org::new);
			for (UUID villagerId : disk.members) {
				if (villagerId == null || !org.members.add(villagerId)) {
					continue;
				}
				data.memberSubject.put(villagerId, disk.subject);
			}
			org.sum += disk.sum;
		}
		return data;
	}

	private record OrgDisk(UUID subject, double sum, List<UUID> members) {
	}

	private static final class Org {
		private final UUID subject;
		private double sum;
		private final Set<UUID> members = new HashSet<>();

		private Org(UUID subject) {
			this.subject = subject;
		}
	}
}
