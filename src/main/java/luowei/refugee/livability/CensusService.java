package luowei.refugee.livability;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;

/**
 * 人数和平均忠诚只在加入、离开和忠诚变化时改，不把未加载的居民拉起来重算。
 */
public final class CensusService {
	private static final double LOYALTY_EPSILON = 1.0e-6;

	private CensusService() {
	}

	public static boolean counts(Villager villager) {
		if (villager == null || !villager.isAlive() || villager.isBaby()) {
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		return data.subjectId() != null && !data.isCrusader();
	}

	/** 加载、成年或换归属时补进统计。死者不要走这里。 */
	public static void ensureMember(Villager villager) {
		if (villager == null || !villager.isAlive()) {
			return;
		}
		MinecraftServer server = villager.level().getServer();
		if (server == null) {
			return;
		}
		CensusSavedData census = CensusSavedData.get(server);
		UUID villagerId = villager.getUUID();
		UUID recorded = census.subjectOf(villagerId);
		if (!counts(villager)) {
			if (recorded != null) {
				LivabilityData data = LivabilityService.get(villager);
				double contributed = data.censusBound() ? data.censusLoyalty() : data.loyalty();
				census.remove(recorded, villagerId, contributed);
			}
			return;
		}
		UUID subjectId = RefugeeAttachments.get(villager).subjectId();
		if (subjectId.equals(recorded)) {
			LivabilityData data = LivabilityService.get(villager);
			if (!data.censusBound()) {
				data.setCensusLoyalty(data.loyalty());
			}
			return;
		}
		LivabilityData data = LivabilityService.get(villager);
		if (recorded != null) {
			double contributed = data.censusBound() ? data.censusLoyalty() : data.loyalty();
			census.remove(recorded, villagerId, contributed);
		}
		data.setCensusLoyalty(data.loyalty());
		census.add(subjectId, villagerId, data.censusLoyalty());
		villager.setAttached(RefugeeAttachments.LIVABILITY, data);
	}

	public static void onLoyaltyChanged(Villager villager, LivabilityData data) {
		if (villager == null || data == null || !villager.isAlive()) {
			return;
		}
		MinecraftServer server = villager.level().getServer();
		if (server == null) {
			return;
		}
		CensusSavedData census = CensusSavedData.get(server);
		UUID recorded = census.subjectOf(villager.getUUID());
		if (recorded == null) {
			return;
		}
		if (!data.censusBound()) {
			data.setCensusLoyalty(data.loyalty());
			return;
		}
		double delta = data.loyalty() - data.censusLoyalty();
		if (Math.abs(delta) <= LOYALTY_EPSILON) {
			return;
		}
		census.adjust(recorded, delta);
		data.setCensusLoyalty(data.loyalty());
	}

	/**
	 * 死者先按当前忠诚对齐总和，再移出统计。
	 *
	 * @return 移出前他所属的归属；本来就不在统计里时为空
	 */
	public static UUID forget(Villager villager) {
		if (villager == null) {
			return null;
		}
		MinecraftServer server = villager.level().getServer();
		if (server == null) {
			return null;
		}
		CensusSavedData census = CensusSavedData.get(server);
		UUID villagerId = villager.getUUID();
		UUID recorded = census.subjectOf(villagerId);
		if (recorded == null) {
			return null;
		}
		LivabilityData data = LivabilityService.get(villager);
		if (!data.censusBound()) {
			data.setCensusLoyalty(data.loyalty());
		}
		double delta = data.loyalty() - data.censusLoyalty();
		if (Math.abs(delta) > LOYALTY_EPSILON) {
			census.adjust(recorded, delta);
			data.setCensusLoyalty(data.loyalty());
		}
		census.remove(recorded, villagerId, data.censusLoyalty());
		return recorded;
	}

	public static void merge(MinecraftServer server, UUID from, UUID to) {
		if (server == null) {
			return;
		}
		CensusSavedData.get(server).mergeInto(from, to);
	}
}
