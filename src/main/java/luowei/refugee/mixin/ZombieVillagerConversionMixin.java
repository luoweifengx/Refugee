package luowei.refugee.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 禁止金苹果 / 虚弱+金苹果把僵尸村民救回村民。
 */
@Mixin(ZombieVillager.class)
public abstract class ZombieVillagerConversionMixin {
	@Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
	private void refugee$blockGappleInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		ItemStack held = player.getItemInHand(hand);
		if (held.is(Items.GOLDEN_APPLE) || held.is(Items.ENCHANTED_GOLDEN_APPLE)) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "startConverting", at = @At("HEAD"), cancellable = true)
	private void refugee$blockConversion(UUID converter, int time, CallbackInfo ci) {
		ci.cancel();
	}

	@Inject(method = "finishConversion", at = @At("HEAD"), cancellable = true)
	private void refugee$blockFinishConversion(ServerLevel level, CallbackInfo ci) {
		ci.cancel();
	}
}
