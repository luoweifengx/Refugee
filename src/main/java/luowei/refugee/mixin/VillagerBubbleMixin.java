package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.livability.LivabilityExhaustion;
import luowei.refugee.livability.LivabilityRegen;
import luowei.refugee.ai.RefugeeCombat;
import luowei.refugee.ai.RefugeeDepthCurse;
import luowei.refugee.ai.RefugeeSwim;
import luowei.refugee.ai.WorkerCargo;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.livability.LivabilityService;
import luowei.refugee.talk.RefugeeBreeding;
import luowei.refugee.talk.RefugeeBubble;

@Mixin(Villager.class)
public abstract class VillagerBubbleMixin {
	@Inject(method = "startSleeping", at = @At("HEAD"))
	private void refugee$markSlept(BlockPos pos, CallbackInfo ci) {
		LivabilityService.noteSlept((Villager) (Object) this);
	}

	@Inject(method = "stopSleeping", at = @At("HEAD"))
	private void refugee$markWake(CallbackInfo ci) {
		LivabilityService.noteWake((Villager) (Object) this);
	}

	@Inject(method = "customServerAiStep", at = @At("HEAD"))
	private void refugee$tickHungerAndCurse(ServerLevel level, CallbackInfo ci) {
		Villager villager = (Villager) (Object) this;
		if (villager.isBaby()) {
			return;
		}
		luowei.refugee.effect.ModEffects.sync(villager);
		luowei.refugee.livability.CensusService.ensureMember(villager);
		luowei.refugee.livability.MutualAidService.tickResident(villager);
		RefugeeDepthCurse.tick(villager);
		LivabilityExhaustion.tick(villager);
		LivabilityService.tickMetabolism(villager);
		LivabilityService.tickSleepComfort(villager);
		RefugeeCombat.wakeGuardIfThreatened(villager);
		if (RefugeeAttachments.isRefugee(villager) || RefugeeRoles.overridesBrain(villager)) {
			RefugeeCombat.tryEat(villager);
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
		LivabilityRegen.tick(villager);
		WorkerCargo.absorbNearby(villager);
		luowei.refugee.staff.GuardService.tickLocation(villager);
		RefugeeCombat.tickEat(villager);
		RefugeeCombat.tickEncounterReset(villager);
	}
}
