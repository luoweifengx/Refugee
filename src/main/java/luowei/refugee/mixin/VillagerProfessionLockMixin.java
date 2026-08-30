package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;

import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 特殊难民锁定职业皮肤，禁止工作站点改职。
 */
@Mixin(Villager.class)
public abstract class VillagerProfessionLockMixin {
	@ModifyVariable(method = "setVillagerData", at = @At("HEAD"), argsOnly = true)
	private VillagerData refugee$lockProfession(VillagerData incoming) {
		Villager self = (Villager) (Object) this;
		ResourceKey<VillagerProfession> locked = RefugeeSpecialRole.lockedProfession(self);
		if (locked == null || incoming.profession().is(locked)) {
			return incoming;
		}
		return incoming.withProfession(self.registryAccess(), locked);
	}
}
