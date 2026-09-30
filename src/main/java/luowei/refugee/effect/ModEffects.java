package luowei.refugee.effect;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.Refugee;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.livability.LivabilityData;
import luowei.refugee.livability.LivabilityService;

/**
 * 死气沉沉和振奋。等级从 1 起，每一级舒适偏移 1 点，持续 24000 tick。
 * 同一种再挂上升一级并重置时间；两种互相顶掉，后上的从 1 级开始。
 */
public final class ModEffects {
	public static final int DURATION = 24000;
	private static final int MAX_AMPLIFIER = 19;

	public static Holder<MobEffect> GLOOM;
	public static Holder<MobEffect> INSPIRED;

	private ModEffects() {
	}

	public static void register() {
		GLOOM = Registry.registerForHolder(
				BuiltInRegistries.MOB_EFFECT,
				Refugee.id("gloom"),
				new MobEffect(MobEffectCategory.HARMFUL, 0x5C6B73) {}
		);
		INSPIRED = Registry.registerForHolder(
				BuiltInRegistries.MOB_EFFECT,
				Refugee.id("inspired"),
				new MobEffect(MobEffectCategory.BENEFICIAL, 0xE6C35C) {}
		);
	}

	public static void grantGloom(Villager villager) {
		grantMood(villager, GLOOM, INSPIRED);
	}

	public static void grantInspired(Villager villager) {
		grantMood(villager, INSPIRED, GLOOM);
	}

	/** 力量和急迫不升级，只把持续时间刷新回 1 天。没有时给予 I 级。 */
	public static void refreshStrengthAndHaste(Villager villager) {
		if (villager == null) {
			return;
		}
		refreshTimed(villager, MobEffects.STRENGTH);
		refreshTimed(villager, MobEffects.HASTE);
	}

	public static void sync(Villager villager) {
		if (villager == null || !RefugeeAttachments.isRefugee(villager) || GLOOM == null) {
			return;
		}
		LivabilityData data = LivabilityService.get(villager);
		if (!data.setMoodOffset(offsetOf(villager))) {
			return;
		}
		LivabilityService.markDirty(villager, data);
	}

	private static void grantMood(Villager villager, Holder<MobEffect> grant, Holder<MobEffect> conflict) {
		if (villager == null || grant == null) {
			return;
		}
		if (villager.hasEffect(conflict)) {
			villager.removeEffect(conflict);
			villager.addEffect(instance(grant, 0));
		} else {
			MobEffectInstance current = villager.getEffect(grant);
			int amplifier = current == null ? 0 : Math.min(MAX_AMPLIFIER, current.getAmplifier() + 1);
			villager.addEffect(instance(grant, amplifier));
		}
		sync(villager);
	}

	private static void refreshTimed(Villager villager, Holder<MobEffect> effect) {
		MobEffectInstance current = villager.getEffect(effect);
		int amplifier = current == null ? 0 : current.getAmplifier();
		villager.addEffect(new MobEffectInstance(effect, DURATION, amplifier, false, true, true));
	}

	private static MobEffectInstance instance(Holder<MobEffect> effect, int amplifier) {
		return new MobEffectInstance(effect, DURATION, amplifier, false, true, true);
	}

	private static int offsetOf(Villager villager) {
		int offset = 0;
		MobEffectInstance gloom = villager.getEffect(GLOOM);
		if (gloom != null) {
			offset -= gloom.getAmplifier() + 1;
		}
		MobEffectInstance inspired = villager.getEffect(INSPIRED);
		if (inspired != null) {
			offset += inspired.getAmplifier() + 1;
		}
		return offset;
	}
}
