package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import luowei.refugee.talk.RefugeeBreeding;

/**
 * 难民繁殖不向客户端广播原版心形/失败粒子。
 */
@Mixin(Level.class)
public abstract class LevelBreedParticleMixin {
	@Inject(method = "broadcastEntityEvent", at = @At("HEAD"), cancellable = true)
	private void refugee$suppressRefugeeLoveParticles(Entity entity, byte event, CallbackInfo ci) {
		if (RefugeeBreeding.interceptEntityEvent(entity, event)) {
			ci.cancel();
		}
	}
}
