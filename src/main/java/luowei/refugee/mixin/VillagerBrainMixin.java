package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.ai.RefugeeIdleBrain;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 守卫，以及工人正在干活时跳过 Brain.tick。
 * 闲置工人、闲置散人和特殊 NPC 只跑走动、看向和睡觉。小孩一律原版。
 */
@Mixin(Villager.class)
public abstract class VillagerBrainMixin {
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
