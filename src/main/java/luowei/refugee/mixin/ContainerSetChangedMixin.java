package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import luowei.refugee.warehouse.WarehouseService;

/**
 * 仓库箱内容变化（玩家手改、原版漏斗等）时重扫该箱账本。
 */
@Mixin(BlockEntity.class)
public abstract class ContainerSetChangedMixin {
	@Inject(method = "setChanged()V", at = @At("TAIL"))
	private void refugee$rescanWarehouse(CallbackInfo ci) {
		BlockEntity self = (BlockEntity) (Object) this;
		if (!(self instanceof Container)) {
			return;
		}
		Level level = self.getLevel();
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		WarehouseService.onContainerChanged(serverLevel, self.getBlockPos());
	}
}
