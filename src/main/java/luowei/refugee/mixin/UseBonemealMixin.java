package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.UseBonemeal;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 施肥会在开始时把主手换成骨粉，结束时把主手清空。无职业也会走这段。
 * 难民和特殊居民直接跳过，切换日程时的收尾也不会清主手。
 */
@Mixin(UseBonemeal.class)
public class UseBonemealMixin {
	@Inject(
			method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void refugee$skipBonemeal(ServerLevel level, Villager villager, CallbackInfoReturnable<Boolean> ci) {
		if (refugee$skip(villager)) {
			ci.setReturnValue(false);
		}
	}

	@Inject(
			method = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void refugee$skipBonemealStart(ServerLevel level, Villager villager, long gameTime, CallbackInfo ci) {
		if (refugee$skip(villager)) {
			ci.cancel();
		}
	}

	@Inject(
			method = "stop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void refugee$skipBonemealStop(ServerLevel level, Villager villager, long gameTime, CallbackInfo ci) {
		if (refugee$skip(villager)) {
			ci.cancel();
		}
	}

	private static boolean refugee$skip(Villager villager) {
		return villager != null
				&& (RefugeeAttachments.isRefugee(villager) || RefugeeSpecialRole.isSpecial(villager));
	}
}
