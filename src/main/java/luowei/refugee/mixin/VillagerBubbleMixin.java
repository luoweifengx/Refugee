package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.ai.RefugeeBuffMachine;
import luowei.refugee.talk.RefugeeBreeding;
import luowei.refugee.talk.RefugeeBubble;

@Mixin(Villager.class)
public abstract class VillagerBubbleMixin {
	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void refugee$tickBubble(ServerLevel level, CallbackInfo ci) {
		Villager villager = (Villager) (Object) this;
		RefugeeBreeding.tickLook(villager);
		RefugeeBubble.tick(villager);
		RefugeeBuffMachine.tick(villager);
	}
}
