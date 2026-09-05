package luowei.refugee.ai;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.config.RefugeeConfig;

/**
 * 按配置对账难民 buff：进入类型则确保效果，离开则清掉不再属于新类型的本模组效果。
 */
public final class RefugeeBuffMachine {
	private RefugeeBuffMachine() {
	}

	public static void tick(Villager villager) {
		if (villager.isBaby() || !RefugeeConfig.villagerBuffsEnabled || !RefugeeAttachments.isRefugee(villager)) {
			stripManaged(villager, Set.of());
			return;
		}
		reconcile(villager, RefugeeConfig.resolvedBuffs(RefugeeBuffState.of(villager)));
	}

	private static void reconcile(Villager villager, List<RefugeeConfig.ResolvedBuff> desired) {
		Set<Holder<MobEffect>> wanted = new HashSet<>();
		for (RefugeeConfig.ResolvedBuff buff : desired) {
			wanted.add(buff.effect());
		}
		stripManaged(villager, wanted);
		for (RefugeeConfig.ResolvedBuff buff : desired) {
			ensure(villager, buff);
		}
	}

	private static void stripManaged(Villager villager, Set<Holder<MobEffect>> keep) {
		for (MobEffectInstance instance : List.copyOf(villager.getActiveEffects())) {
			Holder<MobEffect> effect = instance.getEffect();
			if (RefugeeConfig.isManagedEffect(effect) && !keep.contains(effect)) {
				villager.removeEffect(effect);
			}
		}
	}

	private static void ensure(Villager villager, RefugeeConfig.ResolvedBuff buff) {
		MobEffectInstance current = villager.getEffect(buff.effect());
		if (current != null
				&& current.getAmplifier() == buff.amplifier()
				&& current.isInfiniteDuration()) {
			return;
		}
		villager.addEffect(new MobEffectInstance(
				buff.effect(),
				MobEffectInstance.INFINITE_DURATION,
				buff.amplifier(),
				true,
				false,
				true
		));
	}
}
