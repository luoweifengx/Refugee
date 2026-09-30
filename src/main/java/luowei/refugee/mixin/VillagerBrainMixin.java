package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.ai.BedClaim;
import luowei.refugee.ai.RefugeeIdleBrain;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 守卫平时跳过 Brain；晚上要找床时改跑睡觉。
 * 工人正在干活时跳过，晚上有床则让出。
 * 闲置的难民和特殊居民跑去掉找职业的原版日程。小孩一律原版。
 */
@Mixin(Villager.class)
public abstract class VillagerBrainMixin {
	@Inject(method = "refreshBrain", at = @At("RETURN"))
	private void refugee$restoreIdleBrain(ServerLevel level, CallbackInfo ci) {
		Villager villager = (Villager) (Object) this;
		if (villager.isBaby()) {
			return;
		}
		if (RefugeeAttachments.isRefugee(villager) || RefugeeSpecialRole.isSpecial(villager)) {
			RefugeeIdleBrain.ensure(villager);
		}
	}

	@Redirect(
			method = "customServerAiStep",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"
			)
	)
	@SuppressWarnings("unchecked")
	private void refugee$skipBrainWhenAssigned(Brain<?> brain, ServerLevel level, LivingEntity entity) {
		Villager villager = (Villager) (Object) this;
		BedClaim.refresh(villager);
		if (RefugeeRoles.overridesBrain(villager)) {
			RefugeeIdleBrain.leave(villager);
			return;
		}
		boolean traceIdle = RefugeeIdleBrain.beginIdle(villager);
		boolean replacedSchedule = false;
		if (!villager.isBaby()
				&& (RefugeeAttachments.isRefugee(villager) || RefugeeSpecialRole.isSpecial(villager))) {
			replacedSchedule = RefugeeIdleBrain.ensure(villager);
		}
		if (traceIdle) {
			RefugeeIdleBrain.noteStopped(villager, replacedSchedule);
		}
		((Brain<LivingEntity>) brain).tick(level, entity);
		if (traceIdle) {
			RefugeeIdleBrain.finishIdle(villager);
		}
	}
}
