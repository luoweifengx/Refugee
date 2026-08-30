package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;

import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.interact.SelectionService;

/**
 * 敲钟选中半径内村民跟随。PBS 无钟钩子。
 */
@Mixin(BellBlock.class)
public abstract class BellBlockMixin {
	@Inject(
			method = "attemptToRing(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
			at = @At("RETURN")
	)
	private void refugee$selectOnRing(
			Entity entity,
			Level level,
			BlockPos pos,
			Direction direction,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (!cir.getReturnValue() || level.isClientSide() || !(entity instanceof ServerPlayer player)) {
			return;
		}
		SelectionService.selectAround(player, RefugeeConfig.hornBellRadius);
	}
}
