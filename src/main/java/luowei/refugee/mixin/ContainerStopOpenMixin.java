package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

import luowei.refugee.warehouse.WarehouseService;

/**
 * 开箱占用仓库容器；最后一人关箱后整理。
 */
@Mixin({ChestBlockEntity.class, BarrelBlockEntity.class, ShulkerBoxBlockEntity.class})
public abstract class ContainerStopOpenMixin {
	@Inject(method = "startOpen", at = @At("TAIL"))
	private void refugee$lockWarehouseOnOpen(Player player, CallbackInfo ci) {
		if (player == null || player.isSpectator()) {
			return;
		}
		BlockEntity self = (BlockEntity) (Object) this;
		Level level = self.getLevel();
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		WarehouseService.onStartOpen(serverLevel, self.getBlockPos());
	}

	@Inject(method = "stopOpen", at = @At("TAIL"))
	private void refugee$organizeWarehouseOnClose(Player player, CallbackInfo ci) {
		BlockEntity self = (BlockEntity) (Object) this;
		Level level = self.getLevel();
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		WarehouseService.onStopOpen(serverLevel, self.getBlockPos());
	}
}
