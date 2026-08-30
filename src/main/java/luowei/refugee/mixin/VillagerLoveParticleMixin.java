package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.talk.RefugeeBreeding;

/**
 * 客户端：难民不播原版繁殖心形/失败水滴粒子。
 */
@Mixin(Entity.class)
public abstract class VillagerLoveParticleMixin {
	@Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
	private void refugee$cancelLoveParticles(byte id, CallbackInfo ci) {
		if ((Object) this instanceof Villager villager && RefugeeBreeding.interceptEntityEvent(villager, id)) {
			ci.cancel();
		}
	}
}
