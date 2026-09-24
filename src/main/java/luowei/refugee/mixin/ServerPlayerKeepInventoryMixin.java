package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import luowei.refugee.interact.RosterService;

/**
 * 名册未清空时，玩家死亡不掉落物品。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerKeepInventoryMixin {
	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
	private void refugee$keepInventory(ServerLevel level, DamageSource source, CallbackInfo ci) {
		if (RosterService.keepsInventoryOnDeath((ServerPlayer) (Object) this)) {
			ci.cancel();
		}
	}
}
