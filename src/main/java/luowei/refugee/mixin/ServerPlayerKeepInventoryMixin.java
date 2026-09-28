package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;

import luowei.refugee.interact.RosterService;

/**
 * 原版只在保留物品规则或旁观时才把背包拷到重生后的玩家。这里按名册规则补上同样的拷贝。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerKeepInventoryMixin {
	@Inject(method = "restoreFrom", at = @At("RETURN"))
	private void refugee$copyKeptInventory(ServerPlayer oldPlayer, boolean keepEverything, CallbackInfo ci) {
		if (keepEverything || oldPlayer == null || !RosterService.keepsInventoryOnDeath(oldPlayer)) {
			return;
		}
		ServerPlayer revived = (ServerPlayer) (Object) this;
		revived.getInventory().replaceWith(oldPlayer.getInventory());
		revived.experienceLevel = oldPlayer.experienceLevel;
		revived.totalExperience = oldPlayer.totalExperience;
		revived.experienceProgress = oldPlayer.experienceProgress;
		revived.setScore(oldPlayer.getScore());
	}
}
