package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import luowei.refugee.build.BuildHealth;
import luowei.refugee.warehouse.WarehouseService;

/**
 * 仓库格被换成另一种方块时从名单剔除；建筑 AABB 内方块种类变化则给任务打脏。
 * 挖掘、爆炸、活塞、指令等最终都走 {@link LevelChunk#setBlockState}；区块卸载不会。
 */
@Mixin(LevelChunk.class)
public abstract class WarehouseReplaceMixin {
	@Shadow
	public abstract Level getLevel();

	@Inject(
			method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;",
			at = @At("RETURN")
	)
	private void refugee$dropWarehouseOnReplace(
			BlockPos pos,
			BlockState newState,
			int flags,
			CallbackInfoReturnable<BlockState> cir
	) {
		if (!(getLevel() instanceof ServerLevel serverLevel)) {
			return;
		}
		WarehouseService.onBlockReplaced(serverLevel, pos, cir.getReturnValue(), newState);
		BuildHealth.onBlockChanged(serverLevel, pos, cir.getReturnValue(), newState);
	}
}
