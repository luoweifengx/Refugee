package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import luowei.refugee.item.ItemData;
import luowei.refugee.settle.SettlementService;

/**
 * 放置带 refugee_settlement 标记的原版白旗后触发安顿。
 * {@link BlockPlaceContext#getClickedPos()} 在 placeBlock 中即为实际放置格。
 */
@Mixin(BlockItem.class)
public abstract class SettlementBannerPlaceMixin {
	@Inject(method = "placeBlock", at = @At("RETURN"))
	private void refugee$settleMarkedBanner(
			BlockPlaceContext context,
			BlockState state,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (!cir.getReturnValue() || !(context.getPlayer() instanceof ServerPlayer player)) {
			return;
		}
		if (!state.is(Blocks.WHITE_BANNER) && !state.is(Blocks.WHITE_WALL_BANNER)) {
			return;
		}
		ItemStack stack = context.getItemInHand();
		if (!ItemData.isSettlementBanner(stack)) {
			return;
		}
		SettlementService.settle(player, context.getClickedPos());
	}
}
