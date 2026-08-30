package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.VillagerMakeLove;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.talk.RefugeeBreeding;

/**
 * 难民开始繁殖时丢食物、刷新爱恋气泡。失败粒子由 {@link LevelBreedParticleMixin} 改走流汗气泡。
 */
@Mixin(VillagerMakeLove.class)
public abstract class VillagerMakeLoveMixin {
	@Inject(method = "start", at = @At("TAIL"))
	private void refugee$loveStart(ServerLevel level, Villager villager, long time, CallbackInfo ci) {
		RefugeeBreeding.onLoveStart(level, villager);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void refugee$loveTick(ServerLevel level, Villager villager, long time, CallbackInfo ci) {
		RefugeeBreeding.onLoveTick(villager);
	}
}
