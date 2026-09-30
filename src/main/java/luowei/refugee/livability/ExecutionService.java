package luowei.refugee.livability;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.effect.ModEffects;

/**
 * 忠诚不超过 5 的居民死亡后，按全体平均忠诚给死亡点附近的同归属居民挂状态。
 */
public final class ExecutionService {
	public static final double RADIUS = 30.0;
	public static final double LOW_LOYALTY = 5.0;
	public static final double GLOOM_BELOW = 6.0;
	public static final double INSPIRED_FROM = 15.0;
	public static final int CROWD_MIN = 40;
	public static final double CROWD_FRACTION = 0.3;

	private ExecutionService() {
	}

	public static void onVillagerRemoved(Villager villager) {
		if (villager == null || villager.level().getServer() == null) {
			return;
		}
		ModEffects.sync(villager);
		LivabilityData data = LivabilityService.get(villager);
		double loyalty = data.loyalty();
		UUID subjectId = CensusService.forget(villager);
		if (subjectId == null || loyalty > LOW_LOYALTY) {
			return;
		}
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		resolve(level, villager, subjectId);
	}

	private static void resolve(ServerLevel level, Villager dead, UUID subjectId) {
		CensusSavedData census = CensusSavedData.get(level.getServer());
		int total = census.count(subjectId);
		if (total <= 0) {
			return;
		}
		List<Villager> crowd = crowd(level, dead, subjectId);
		int inRange = crowd.size();
		if (inRange < CROWD_MIN && inRange < total * CROWD_FRACTION) {
			return;
		}
		double average = census.average(subjectId);
		if (average < GLOOM_BELOW) {
			for (Villager villager : crowd) {
				ModEffects.grantGloom(villager);
			}
			return;
		}
		if (average < INSPIRED_FROM) {
			return;
		}
		for (Villager villager : crowd) {
			ModEffects.grantInspired(villager);
			ModEffects.refreshStrengthAndHaste(villager);
		}
	}

	private static List<Villager> crowd(ServerLevel level, Villager dead, UUID subjectId) {
		AABB box = dead.getBoundingBox().inflate(RADIUS);
		List<Villager> crowd = new ArrayList<>();
		for (Villager candidate : level.getEntitiesOfClass(Villager.class, box, Villager::isAlive)) {
			if (candidate == dead || candidate.isBaby() || candidate.distanceToSqr(dead) > RADIUS * RADIUS) {
				continue;
			}
			RefugeeVillagerData data = RefugeeAttachments.get(candidate);
			if (data.isCrusader() || !subjectId.equals(data.subjectId())) {
				continue;
			}
			crowd.add(candidate);
		}
		return crowd;
	}
}
