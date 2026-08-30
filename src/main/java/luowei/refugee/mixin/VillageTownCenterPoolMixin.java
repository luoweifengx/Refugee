package luowei.refugee.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * 原版 village/biome/town_centers 同一池里普通中心权重约 50、僵尸中心约 1。
 * 抽选时只保留 location 含 /zombie/ 的 element，后续 jigsaw 会接到 zombie/streets。
 */
@Mixin(StructureTemplatePool.class)
public abstract class VillageTownCenterPoolMixin {
	private static final Map<StructureTemplatePool, Optional<List<StructurePoolElement>>> ZOMBIE_ONLY =
			Collections.synchronizedMap(new IdentityHashMap<>());

	@Inject(method = "getRandomTemplate", at = @At("HEAD"), cancellable = true)
	private void refugee$zombieTownCenter(RandomSource random, CallbackInfoReturnable<StructurePoolElement> cir) {
		List<StructurePoolElement> zombieOnly = zombieTownCenters();
		if (zombieOnly == null || zombieOnly.isEmpty()) {
			return;
		}
		cir.setReturnValue(zombieOnly.get(random.nextInt(zombieOnly.size())));
	}

	@Inject(method = "getShuffledTemplates", at = @At("HEAD"), cancellable = true)
	private void refugee$zombieTownCentersShuffled(RandomSource random, CallbackInfoReturnable<List<StructurePoolElement>> cir) {
		List<StructurePoolElement> zombieOnly = zombieTownCenters();
		if (zombieOnly == null || zombieOnly.isEmpty()) {
			return;
		}
		List<StructurePoolElement> shuffled = new ArrayList<>(zombieOnly);
		for (int i = shuffled.size(); i > 1; i--) {
			Collections.swap(shuffled, i - 1, random.nextInt(i));
		}
		cir.setReturnValue(shuffled);
	}

	private List<StructurePoolElement> zombieTownCenters() {
		StructureTemplatePool self = (StructureTemplatePool) (Object) this;
		return ZOMBIE_ONLY.computeIfAbsent(self, VillageTownCenterPoolMixin::computeZombieOnly).orElse(null);
	}

	private static Optional<List<StructurePoolElement>> computeZombieOnly(StructureTemplatePool pool) {
		boolean hasZombie = false;
		boolean hasNormal = false;
		List<StructurePoolElement> zombies = new ArrayList<>();
		for (StructurePoolElement element : ((StructureTemplatePoolAccessor) pool).refugee$getTemplates()) {
			String path = templatePath(element);
			if (path == null) {
				continue;
			}
			if (path.contains("/zombie/")) {
				hasZombie = true;
				zombies.add(element);
			} else if (path.contains("/town_centers/")) {
				hasNormal = true;
			}
		}
		if (hasZombie && hasNormal) {
			return Optional.of(List.copyOf(zombies));
		}
		return Optional.empty();
	}

	private static String templatePath(StructurePoolElement element) {
		if (!(element instanceof SinglePoolElement single)) {
			return null;
		}
		ResourceLocation id = ((SinglePoolElementAccessor) single).refugee$getTemplate().left().orElse(null);
		return id == null ? null : id.getPath();
	}
}
