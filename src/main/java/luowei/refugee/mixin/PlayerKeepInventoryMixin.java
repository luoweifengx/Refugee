package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import luowei.refugee.interact.RosterService;

/**
 * 玩家背包是在 {@code Player.dropEquipment} 里清空的，经验来自 {@code getBaseExperienceReward}。
 */
@Mixin(Player.class)
public abstract class PlayerKeepInventoryMixin {
	@Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
	private void refugee$keepItems(ServerLevel level, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && RosterService.keepsInventoryOnDeath(player)) {
			ci.cancel();
		}
	}

	@Inject(method = "getBaseExperienceReward", at = @At("HEAD"), cancellable = true)
	private void refugee$keepXp(ServerLevel level, CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof ServerPlayer player && RosterService.keepsInventoryOnDeath(player)) {
			cir.setReturnValue(0);
		}
	}
}
