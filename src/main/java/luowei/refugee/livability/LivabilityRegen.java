package luowei.refugee.livability;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.attachment.RefugeeAttachments;

/**
 * 按当前回血效率给予生命再生。效率 5.4 为 5 级（效果等级 V，amplifier 4）。
 * 效率低于 1 时去掉。体力、饱食或舒适一变，下一拍就会换成新等级。
 */
public final class LivabilityRegen {
	private LivabilityRegen() {
	}

	public static void tick(Villager villager) {
		if (villager == null || villager.isBaby() || !RefugeeAttachments.isRefugee(villager)) {
			return;
		}
		LivabilityData data = villager.getAttached(RefugeeAttachments.LIVABILITY);
		double efficiency = data == null ? 0.0 : data.healEfficiency();
		int level = (int) Math.floor(efficiency);
		Holder<MobEffect> effect = MobEffects.REGENERATION;
		if (level < 1) {
			if (villager.hasEffect(effect)) {
				villager.removeEffect(effect);
			}
			return;
		}
		int amplifier = level - 1;
		MobEffectInstance current = villager.getEffect(effect);
		if (current != null && current.getAmplifier() == amplifier && current.isInfiniteDuration()) {
			return;
		}
		villager.addEffect(new MobEffectInstance(
				effect,
				MobEffectInstance.INFINITE_DURATION,
				amplifier,
				true,
				false,
				true
		));
	}
}
