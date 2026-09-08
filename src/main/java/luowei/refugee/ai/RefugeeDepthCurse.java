package luowei.refugee.ai;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

import luowei.refugee.Refugee;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.compat.FoodCompat;
import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 地脉未破咒时，主世界按 Y 档用 gameTime 扣血，并挂饥饿作为标记。
 */
public final class RefugeeDepthCurse {
	public static final ResourceKey<DamageType> DAMAGE_TYPE =
			ResourceKey.create(Registries.DAMAGE_TYPE, Refugee.id("leyline_curse"));

	private static final long DAY_TICKS = 24000L;
	private static final int HUNGER_REFRESH_TICKS = 80;

	private RefugeeDepthCurse() {
	}

	public static boolean isLeylineDamage(DamageSource source) {
		return source != null && source.is(DAMAGE_TYPE);
	}

	public static void tick(Villager villager) {
		if (villager == null || villager.isBaby() || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		if (!RefugeeAttachments.isRefugee(villager) || RefugeeSpecialRole.isSpecial(villager)) {
			clearHunger(villager);
			return;
		}
		if (level.dimension() != Level.OVERWORLD || !FoodCompat.isCurseActive(level.getServer())) {
			clearHunger(villager);
			resetClock(villager, level);
			return;
		}
		int interval = intervalForY(villager.getY());
		if (interval <= 0) {
			clearHunger(villager);
			resetClock(villager, level);
			return;
		}
		applyHunger(villager, amplifierForY(villager.getY()));
		applyDamage(villager, level, interval);
	}

	private static void applyDamage(Villager villager, ServerLevel level, int interval) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		long now = level.getGameTime();
		long last = data.lastDepthCurseTick();
		if (last <= 0L) {
			data.setLastDepthCurseTick(now);
			RefugeeAttachments.markDirty(villager, data);
			return;
		}
		long elapsed = now - last;
		if (elapsed < interval) {
			return;
		}
		int hits = (int) Math.min(elapsed / interval, 40L);
		long consumed = (long) hits * interval;
		data.setLastDepthCurseTick(last + consumed);
		RefugeeAttachments.markDirty(villager, data);
		if (hits > 0 && villager.isAlive()) {
			villager.hurtServer(level, leyline(level), hits);
		}
	}

	private static DamageSource leyline(ServerLevel level) {
		return level.damageSources().source(DAMAGE_TYPE);
	}

	private static void applyHunger(Villager villager, int amplifier) {
		MobEffectInstance current = villager.getEffect(MobEffects.HUNGER);
		if (current != null
				&& current.getAmplifier() == amplifier
				&& current.getDuration() > 20) {
			return;
		}
		villager.addEffect(new MobEffectInstance(
				MobEffects.HUNGER,
				HUNGER_REFRESH_TICKS,
				amplifier,
				true,
				true,
				true
		));
	}

	private static void clearHunger(Villager villager) {
		if (villager.hasEffect(MobEffects.HUNGER)) {
			villager.removeEffect(MobEffects.HUNGER);
		}
	}

	private static void resetClock(Villager villager, ServerLevel level) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.lastDepthCurseTick() == 0L) {
			return;
		}
		data.setLastDepthCurseTick(0L);
		RefugeeAttachments.markDirty(villager, data);
	}

	/**
	 * @return 每滴间隔的 gameTime；浅层返回 0 表示不扣。
	 */
	public static int intervalForY(double y) {
		if (y >= 32.0) {
			return 0;
		}
		if (y >= 0.0) {
			return (int) DAY_TICKS;
		}
		if (y >= -16.0) {
			return (int) (DAY_TICKS / 2L);
		}
		if (y >= -32.0) {
			return (int) (DAY_TICKS / 3L);
		}
		if (y >= -46.0) {
			return (int) (DAY_TICKS / 4L);
		}
		return (int) (DAY_TICKS / 5L);
	}

	private static int amplifierForY(double y) {
		if (y >= 32.0) {
			return 0;
		}
		if (y >= 0.0) {
			return 0;
		}
		if (y >= -16.0) {
			return 1;
		}
		if (y >= -32.0) {
			return 2;
		}
		if (y >= -46.0) {
			return 3;
		}
		return 4;
	}
}
