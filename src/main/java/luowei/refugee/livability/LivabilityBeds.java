package luowei.refugee.livability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;

/**
 * 每人最多绑一张半径内的床。连片只数已绑定的居民：床与床的切比雪夫距离不超过半径就接上。
 * 同一片只算一次。没绑到床的人没有居住分。
 */
public final class LivabilityBeds {
	public record Housing(boolean hasBed, int gap, int clusterSize) {
	}

	private record Candidate(Villager villager, BlockPos bed, int distance) {
	}

	private LivabilityBeds() {
	}

	public static Map<Villager, Housing> score(ServerLevel level, List<Villager> villagers, LivabilityRules rules) {
		Map<Villager, Housing> scored = new IdentityHashMap<>();
		if (villagers.isEmpty()) {
			return scored;
		}
		int radius = Math.max(1, rules.bedLinkRadius);
		Set<BlockPos> beds = new HashSet<>();
		for (Villager villager : villagers) {
			level.getPoiManager().findAll(
					poi -> poi.is(PoiTypes.HOME),
					pos -> true,
					villager.blockPosition(),
					radius,
					PoiManager.Occupancy.ANY
			).forEach(pos -> beds.add(pos.immutable()));
		}
		List<Candidate> candidates = new ArrayList<>();
		for (Villager villager : villagers) {
			for (BlockPos bed : beds) {
				int distance = chebyshev(villager.blockPosition(), bed);
				if (distance <= radius) {
					candidates.add(new Candidate(villager, bed, distance));
				}
			}
		}
		candidates.sort(Comparator.comparingInt(Candidate::distance));
		Set<Villager> takenVillagers = new HashSet<>();
		Set<BlockPos> takenBeds = new HashSet<>();
		Map<Villager, BlockPos> bound = new IdentityHashMap<>();
		for (Candidate candidate : candidates) {
			if (takenVillagers.contains(candidate.villager()) || takenBeds.contains(candidate.bed())) {
				continue;
			}
			takenVillagers.add(candidate.villager());
			takenBeds.add(candidate.bed());
			bound.put(candidate.villager(), candidate.bed());
		}
		List<Villager> owners = new ArrayList<>(bound.keySet());
		int count = owners.size();
		int[] cluster = new int[count];
		int[] gap = new int[count];
		boolean[] seen = new boolean[count];
		for (int i = 0; i < count; i++) {
			BlockPos bed = bound.get(owners.get(i));
			int nearest = Integer.MAX_VALUE;
			for (int j = 0; j < count; j++) {
				if (i == j) {
					continue;
				}
				nearest = Math.min(nearest, chebyshev(bed, bound.get(owners.get(j))));
			}
			gap[i] = nearest == Integer.MAX_VALUE ? radius + 1 : Math.max(0, nearest - 1);
		}
		int[] stack = new int[count];
		int[] members = new int[count];
		for (int i = 0; i < count; i++) {
			if (seen[i]) {
				continue;
			}
			int top = 0;
			stack[top++] = i;
			seen[i] = true;
			int size = 0;
			while (top > 0) {
				int current = stack[--top];
				members[size++] = current;
				BlockPos bed = bound.get(owners.get(current));
				for (int j = 0; j < count; j++) {
					if (seen[j]) {
						continue;
					}
					if (chebyshev(bed, bound.get(owners.get(j))) <= radius) {
						seen[j] = true;
						stack[top++] = j;
					}
				}
			}
			for (int k = 0; k < size; k++) {
				cluster[members[k]] = size;
			}
		}
		for (int i = 0; i < count; i++) {
			scored.put(owners.get(i), new Housing(true, gap[i], cluster[i]));
		}
		Housing lowest = new Housing(false, 0, 0);
		for (Villager villager : villagers) {
			scored.putIfAbsent(villager, lowest);
		}
		return scored;
	}

	private static int chebyshev(BlockPos a, BlockPos b) {
		return Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
	}
}
