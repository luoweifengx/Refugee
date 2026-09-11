package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.ai.RefugeeBuffMachine;
import luowei.refugee.ai.RefugeeCombat;
import luowei.refugee.ai.RefugeeDepthCurse;
import luowei.refugee.ai.RefugeeSwim;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.talk.RefugeeBreeding;
import luowei.refugee.talk.RefugeeBubble;

@Mixin(Villager.class)
public abstract class VillagerBubbleMixin {
	@Inject(method = "customServerAiStep", at = @At("HEAD"))
	private void refugee$tickHungerAndCurse(ServerLevel level, CallbackInfo ci) {
		Villager villager = (Villager) (Object) this;
		if (villager.isBaby()) {
			return;
		}
		RefugeeDepthCurse.tick(villager);
		if (RefugeeAttachments.isRefugee(villager) || RefugeeRoles.overridesBrain(villager)) {
			RefugeeCombat.tryEat(villager, 1.0f);
		}
	}

	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void refugee$tickBubble(ServerLevel level, CallbackInfo ci) {
		Villager villager = (Villager) (Object) this;
		if (villager.isBaby()) {
			return;
		}
		if (RefugeeAttachments.isRefugee(villager) || RefugeeRoles.overridesBrain(villager)) {
			RefugeeSwim.tick(villager);
		}
		RefugeeBreeding.tickLook(villager);
		RefugeeBubble.tick(villager);
		RefugeeBuffMachine.tick(villager);
		luowei.refugee.staff.GuardService.tickLocation(villager);
		RefugeeCombat.tickEat(villager);
		RefugeeCombat.tickEncounterReset(villager);
	}
}
