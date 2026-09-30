package luowei.refugee.livability;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.attachment.RefugeeAttachments;

/**
 * 体力耗尽时给予挖掘疲劳 III 和虚弱 III。
 * 挖掘系数与玩家相同：III 级先乘 0.0027，再乘属性上的七成。
 * 粒子比效果自带的更密，方便从远处看见。
 */
public final class LivabilityExhaustion {
	/** 效果等级 III。 */
	private static final int AMPLIFIER = 2;
	private static final int PARTICLE_INTERVAL = 4;

	private LivabilityExhaustion() {
	}

	public static void tick(Villager villager) {
		if (villager == null || villager.isBaby() || !RefugeeAttachments.isRefugee(villager)) {
			return;
		}
		if (!LivabilityService.isSpent(villager)) {
			clear(villager);
			return;
		}
		ensure(villager, MobEffects.MINING_FATIGUE);
		ensure(villager, MobEffects.WEAKNESS);
		particles(villager);
	}

	/**
	 * 与玩家 {@code getDestroySpeed} 里的挖掘疲劳相同。
	 * III 级约为原速度的 0.19%。
	 */
	public static float scaleBreakSpeed(Villager villager, float speed) {
		MobEffectInstance effect = villager.getEffect(MobEffects.MINING_FATIGUE);
		if (effect == null || speed <= 0.0f) {
			return speed;
		}
		int amplifier = effect.getAmplifier();
		float fatigue = switch (amplifier) {
			case 0 -> 0.3f;
			case 1 -> 0.09f;
			case 2 -> 0.0027f;
			default -> 8.1E-4f;
		};
		float attribute = Math.max(0.0f, 1.0f - 0.1f * (amplifier + 1));
		return speed * fatigue * attribute;
	}

	private static void ensure(Villager villager, Holder<MobEffect> effect) {
		MobEffectInstance current = villager.getEffect(effect);
		if (current != null && current.getAmplifier() == AMPLIFIER && current.isInfiniteDuration()) {
			return;
		}
		villager.addEffect(new MobEffectInstance(
				effect,
				MobEffectInstance.INFINITE_DURATION,
				AMPLIFIER,
				false,
				true,
				true
		));
	}

	private static void clear(Villager villager) {
		removeIfOurs(villager, MobEffects.MINING_FATIGUE);
		removeIfOurs(villager, MobEffects.WEAKNESS);
	}

	private static void removeIfOurs(Villager villager, Holder<MobEffect> effect) {
		MobEffectInstance current = villager.getEffect(effect);
		if (current != null && current.getAmplifier() == AMPLIFIER && current.isInfiniteDuration()) {
			villager.removeEffect(effect);
		}
	}

	private static void particles(Villager villager) {
		if (villager.tickCount % PARTICLE_INTERVAL != 0 || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		double x = villager.getX();
		double y = villager.getY() + villager.getBbHeight() * 0.75;
		double z = villager.getZ();
		level.sendParticles(ParticleTypes.SMOKE, x, y, z, 8, 0.35, 0.45, 0.35, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.15, z, 4, 0.25, 0.4, 0.25, 0.01);
		level.sendParticles(
				ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, MobEffects.MINING_FATIGUE.value().getColor()),
				x,
				y,
				z,
				6,
				0.4,
				0.55,
				0.4,
				0.0
		);
		level.sendParticles(
				ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, MobEffects.WEAKNESS.value().getColor()),
				x,
				y,
				z,
				6,
				0.4,
				0.55,
				0.4,
				0.0
		);
	}
}
