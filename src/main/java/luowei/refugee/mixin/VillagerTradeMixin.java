package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 禁止原版交易界面与报价，但不拦截 mobInteract，以免挡住跟随 / 给装备。
 * 附魔师保留自定义报价，且不走补货与声望折扣。
 */
@Mixin(Villager.class)
public abstract class VillagerTradeMixin {
	@Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
	private void refugee$noOffers(CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (!RefugeeSpecialRole.is(self, RefugeeSpecialRole.ENCHANTER)) {
			self.getOffers().clear();
		}
		ci.cancel();
	}

	@Inject(method = "startTrading", at = @At("HEAD"), cancellable = true)
	private void refugee$noStartTrading(Player player, CallbackInfo ci) {
		ci.cancel();
	}

	@Inject(method = "updateSpecialPrices", at = @At("HEAD"), cancellable = true)
	private void refugee$noDiscounts(Player player, CallbackInfo ci) {
		if (RefugeeSpecialRole.isSpecial((Villager) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "shouldRestock", at = @At("HEAD"), cancellable = true)
	private void refugee$noRestock(CallbackInfoReturnable<Boolean> cir) {
		if (RefugeeSpecialRole.isSpecial((Villager) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canRestock", at = @At("HEAD"), cancellable = true)
	private void refugee$cannotRestock(CallbackInfoReturnable<Boolean> cir) {
		if (RefugeeSpecialRole.isSpecial((Villager) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
