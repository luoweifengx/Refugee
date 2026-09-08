package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.config.RefugeeConfig;

/**
 * 按配置禁止僵尸击杀村民时转化成僵尸村民。
 */
@Mixin(Zombie.class)
public abstract class ZombieInfectVillagerMixin {
	@Inject(method = "convertVillagerToZombieVillager", at = @At("HEAD"), cancellable = true)
	private void refugee$blockInfect(ServerLevel level, Villager villager, CallbackInfoReturnable<Boolean> cir) {
		if (RefugeeConfig.blockVillagerZombieConversion) {
			cir.setReturnValue(false);
		}
	}
}
