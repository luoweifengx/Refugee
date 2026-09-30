package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 难民和特殊居民不跑收割。无职业的工作包里也有这段。
 */
@Mixin(HarvestFarmland.class)
public class HarvestFarmlandMixin {
	@Inject(
			method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void refugee$skipHarvest(ServerLevel level, Villager villager, CallbackInfoReturnable<Boolean> ci) {
		if (refugee$skip(villager)) {
			ci.setReturnValue(false);
		}
	}

	@Inject(
			method = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void refugee$skipHarvestStart(ServerLevel level, Villager villager, long gameTime, CallbackInfo ci) {
		if (refugee$skip(villager)) {
			ci.cancel();
		}
	}

	@Inject(
			method = "stop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void refugee$skipHarvestStop(ServerLevel level, Villager villager, long gameTime, CallbackInfo ci) {
		if (refugee$skip(villager)) {
			ci.cancel();
		}
	}

	private static boolean refugee$skip(Villager villager) {
		return villager != null
				&& (RefugeeAttachments.isRefugee(villager) || RefugeeSpecialRole.isSpecial(villager));
	}
}
