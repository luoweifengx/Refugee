package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.interact.RefugeeRoles;

/**
 * 工人 / 守卫 / 特殊 NPC 以及散人跟随、巡逻、逃逸时跳过 Brain.tick。
 * 闲置散人仍跑原版 Brain。小孩一律原版。
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
		if (!RefugeeRoles.overridesBrain((Villager) (Object) this)) {
			((Brain<LivingEntity>) brain).tick(level, entity);
		}
	}
}
