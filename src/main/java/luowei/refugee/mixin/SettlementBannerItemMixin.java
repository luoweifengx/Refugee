package luowei.refugee.mixin;

// import java.util.function.Consumer;
//
// import org.spongepowered.asm.mixin.injection.At;
// import org.spongepowered.asm.mixin.injection.Inject;
// import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
// import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//
// import net.minecraft.ChatFormatting;
// import net.minecraft.network.chat.Component;
// import net.minecraft.server.level.ServerPlayer;
// import net.minecraft.world.InteractionHand;
// import net.minecraft.world.InteractionResult;
// import net.minecraft.world.entity.LivingEntity;
// import net.minecraft.world.entity.player.Player;
// import net.minecraft.world.item.ItemStack;
// import net.minecraft.world.item.ItemUseAnimation;
// import net.minecraft.world.item.TooltipFlag;
// import net.minecraft.world.item.component.TooltipDisplay;
// import net.minecraft.world.level.Level;
//
// import luowei.refugee.item.ItemData;
// import luowei.refugee.settle.SettlementBannerService;

// import org.spongepowered.asm.mixin.Mixin;
// import net.minecraft.world.item.Item;

/**
 * 投掷 / 样式编辑已停用，恢复右键放置。整段 inject 先留在注释里。
 */
// @Mixin(Item.class)
public abstract class SettlementBannerItemMixin {
	// @Inject(method = "use", at = @At("HEAD"), cancellable = true)
	// private void refugee$settlementBannerUse(
	// 		Level level,
	// 		Player player,
	// 		InteractionHand hand,
	// 		CallbackInfoReturnable<InteractionResult> cir
	// ) {
	// 	ItemStack stack = player.getItemInHand(hand);
	// 	if (!ItemData.isSettlementBanner(stack)) {
	// 		return;
	// 	}
	// 	if (player.isShiftKeyDown()) {
	// 		if (player instanceof ServerPlayer serverPlayer) {
	// 			SettlementBannerService.openEditor(serverPlayer, hand);
	// 		}
	// 		cir.setReturnValue(InteractionResult.SUCCESS);
	// 		return;
	// 	}
	// 	player.startUsingItem(hand);
	// 	cir.setReturnValue(InteractionResult.CONSUME);
	// }
	//
	// @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
	// private void refugee$settlementBannerAnim(ItemStack stack, CallbackInfoReturnable<ItemUseAnimation> cir) {
	// 	if (ItemData.isSettlementBanner(stack)) {
	// 		cir.setReturnValue(ItemUseAnimation.SPEAR);
	// 	}
	// }
	//
	// @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
	// private void refugee$settlementBannerDuration(
	// 		ItemStack stack,
	// 		LivingEntity entity,
	// 		CallbackInfoReturnable<Integer> cir
	// ) {
	// 	if (ItemData.isSettlementBanner(stack)) {
	// 		cir.setReturnValue(72000);
	// 	}
	// }
	//
	// @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
	// private void refugee$settlementBannerRelease(
	// 		ItemStack stack,
	// 		Level level,
	// 		LivingEntity entity,
	// 		int timeLeft,
	// 		CallbackInfoReturnable<Boolean> cir
	// ) {
	// 	if (!ItemData.isSettlementBanner(stack)) {
	// 		return;
	// 	}
	// 	cir.setReturnValue(SettlementBannerService.throwBanner(stack, level, entity, timeLeft));
	// }
	//
	// @Inject(method = "appendHoverText", at = @At("TAIL"))
	// private void refugee$settlementBannerHint(
	// 		ItemStack stack,
	// 		Item.TooltipContext context,
	// 		TooltipDisplay display,
	// 		Consumer<Component> tooltip,
	// 		TooltipFlag flag,
	// 		CallbackInfo ci
	// ) {
	// 	if (ItemData.isSettlementBanner(stack)) {
	// 		tooltip.accept(Component.translatable("item.refugee.settlement_banner.hint").withStyle(ChatFormatting.GRAY));
	// 	}
	// }
}
