package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ServerLevelAccessor;

import luowei.refugee.interact.SelectionService;

/**
 * 刷怪蛋 / 发射器 / 结构生成的村民标为可认领。
 */
@Mixin(Villager.class)
public abstract class VillagerClaimableSpawnMixin {
	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void refugee$markClaimable(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData spawnGroupData,
			CallbackInfoReturnable<SpawnGroupData> cir
	) {
		if (level.isClientSide()) {
			return;
		}
		SelectionService.markClaimableFromSpawn((Villager) (Object) this, spawnReason);
	}
}
