package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * 禁止原版村庄自然生成铁傀儡。
 */
@Mixin(Villager.class)
public abstract class VillagerGolemSpawnMixin {
	@Inject(method = "spawnGolemIfNeeded", at = @At("HEAD"), cancellable = true)
	private void refugee$noVillageGolem(ServerLevel level, long gameTime, int required, CallbackInfo ci) {
		ci.cancel();
	}

	@Inject(method = "wantsToSpawnGolem", at = @At("HEAD"), cancellable = true)
	private void refugee$neverWantsGolem(long gameTime, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(false);
	}
}
