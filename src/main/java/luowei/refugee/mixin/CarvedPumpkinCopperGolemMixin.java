package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;

import luowei.refugee.entity.CopperGolemSpawn;

/**
 * 南瓜放上铜块躯干时召唤铜傀儡；不改铁块造铁傀儡。
 */
@Mixin(CarvedPumpkinBlock.class)
public abstract class CarvedPumpkinCopperGolemMixin {
	@Inject(method = "onPlace", at = @At("TAIL"))
	private void refugee$tryCopperGolem(
			BlockState state,
			Level level,
			BlockPos pos,
			BlockState oldState,
			boolean movedByPiston,
			CallbackInfo ci
	) {
		if (!oldState.is(state.getBlock())) {
			CopperGolemSpawn.trySpawn(level, pos);
		}
	}

	@Inject(method = "canSpawnGolem", at = @At("RETURN"), cancellable = true)
	private void refugee$copperGolemPattern(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && CopperGolemSpawn.canSpawn(level, pos)) {
			cir.setReturnValue(true);
		}
	}
}
