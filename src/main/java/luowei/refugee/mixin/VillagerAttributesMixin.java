package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;

/**
 * 原版村民没有 {@link Attributes#ATTACK_DAMAGE}，守卫近战 {@code doHurtTarget} 会崩溃。
 */
@Mixin(Villager.class)
public abstract class VillagerAttributesMixin {
	@Inject(method = "createAttributes", at = @At("RETURN"))
	private static void refugee$addAttackDamage(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
		cir.getReturnValue().add(Attributes.ATTACK_DAMAGE, 1.0);
	}
}
