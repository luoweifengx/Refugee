package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

import luowei.refugee.ai.EnchanterTableRoamGoal;
import luowei.refugee.ai.RefugeeBuildGoal;
import luowei.refugee.ai.RefugeeFollowGoal;
import luowei.refugee.ai.RefugeeGuardGoal;
import luowei.refugee.ai.RefugeeGuardTargetGoal;
import luowei.refugee.ai.RefugeeMineGoal;

@Mixin(Villager.class)
public abstract class VillagerGoalInitMixin extends AbstractVillager {
	protected VillagerGoalInitMixin(EntityType<? extends AbstractVillager> type, Level level) {
		super(type, level);
	}

	@Inject(
			method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V",
			at = @At("TAIL")
	)
	private void refugee$addGoals(EntityType<? extends Villager> type, Level level, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		this.targetSelector.addGoal(1, new RefugeeGuardTargetGoal(self));
		this.goalSelector.addGoal(1, new RefugeeFollowGoal(self));
		this.goalSelector.addGoal(2, new RefugeeGuardGoal(self));
		this.goalSelector.addGoal(3, new RefugeeBuildGoal(self));
		this.goalSelector.addGoal(4, new RefugeeMineGoal(self));
		this.goalSelector.addGoal(5, new EnchanterTableRoamGoal(self));
	}
}
