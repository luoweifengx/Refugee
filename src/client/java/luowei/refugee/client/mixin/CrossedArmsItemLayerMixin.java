package luowei.refugee.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;

import luowei.refugee.client.model.RefugeeVillagerArmState;

@Mixin(CrossedArmsItemLayer.class)
public class CrossedArmsItemLayerMixin {
	@Inject(
			method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/HoldingEntityRenderState;FF)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void refugee$skipIndependentArms(
			PoseStack pose,
			MultiBufferSource buffer,
			int packedLight,
			HoldingEntityRenderState state,
			float yRot,
			float xRot,
			CallbackInfo ci
	) {
		if (state instanceof RefugeeVillagerArmState arms && arms.refugee$independentArms()) {
			ci.cancel();
		}
	}
}
