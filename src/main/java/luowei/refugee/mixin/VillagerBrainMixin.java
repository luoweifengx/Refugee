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
 * 跟随 / 守卫 / 建筑时只跳过 Brain.tick，不取消 customServerAiStep。
 * 1.21.5 的 GoalSelector 在 Mob.serverAiStep 里、customServerAiStep 之前 tick；
 * 但 Brain.tick 会在其后改写导航，必须跳过才能让跟随 Goal 生效。
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
