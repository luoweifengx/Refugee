package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 向玩家展示交易会把主手换成展示物，结束时把主手清空。
 * 晚上切进睡觉时这段会收尾，守卫的武器就没了。难民和特殊居民直接跳过。
 */
@Mixin(ShowTradesToPlayer.class)
public class ShowTradesMixin {
	@Inject(
			method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)Z",
			at = @At("HEAD"),
			cancellable = true
	)
	private void refugee$skipTrades(ServerLevel level, Villager villager, CallbackInfoReturnable<Boolean> ci) {
		if (refugee$skip(villager)) {
			ci.setReturnValue(false);
		}
	}

	@Inject(
			method = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void refugee$skipTradesStart(ServerLevel level, Villager villager, long gameTime, CallbackInfo ci) {
		if (refugee$skip(villager)) {
			ci.cancel();
		}
	}

	@Inject(
			method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void refugee$skipTradesTick(ServerLevel level, Villager villager, long gameTime, CallbackInfo ci) {
		if (refugee$skip(villager)) {
			ci.cancel();
		}
	}

	@Inject(
			method = "stop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void refugee$skipTradesStop(ServerLevel level, Villager villager, long gameTime, CallbackInfo ci) {
		if (refugee$skip(villager)) {
			ci.cancel();
		}
	}

	private static boolean refugee$skip(Villager villager) {
		return villager != null
				&& (RefugeeAttachments.isRefugee(villager) || RefugeeSpecialRole.isSpecial(villager));
	}
}
