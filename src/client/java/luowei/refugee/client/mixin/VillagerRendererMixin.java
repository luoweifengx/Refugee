package luowei.refugee.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.client.talk.RefugeeBubbleRenderState;
import luowei.refugee.talk.RefugeeBubble;

@Mixin(VillagerRenderer.class)
public abstract class VillagerRendererMixin {
	@Inject(
			method = "extractRenderState(Lnet/minecraft/world/entity/npc/Villager;Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;F)V",
			at = @At("TAIL")
	)
	private void refugee$extractBubble(Villager villager, VillagerRenderState state, float tickDelta, CallbackInfo ci) {
		if (state instanceof RefugeeBubbleRenderState bubbleState) {
			bubbleState.refugee$setBubbleIcon(RefugeeBubble.get(villager));
		}
	}
}
