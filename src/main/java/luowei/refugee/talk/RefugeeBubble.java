package luowei.refugee.talk;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.interact.RefugeeRoles;

/**
 * 服务端计算并同步头顶气泡表情：愤怒锁 &gt; 战斗/低血 &gt; 工作 &gt; 繁殖爱恋/流汗 &gt; 对话/选中 &gt; 无。
 */
public final class RefugeeBubble {
	public static final int TALK_DURATION_TICKS = 100;
	public static final int SELECT_DURATION_TICKS = 100;
	public static final int LOVE_DURATION_TICKS = 80;
	public static final int SWEAT_DURATION_TICKS = 60;
	public static final int FULL_DURATION_TICKS = 60;
	private static final double GUARD_HUNGRY_RANGE = 5.0;
	private static final float LOW_HEALTH_RATIO = 0.30f;

	private RefugeeBubble() {
	}

	public static RefugeeBubbleIcon get(Villager villager) {
		Byte value = villager.getAttached(RefugeeAttachments.BUBBLE_ICON);
		return RefugeeBubbleIcon.byId(value == null ? 0 : value);
	}

	public static void onTalk(Villager villager) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.startTalk(villager.level().getGameTime() + TALK_DURATION_TICKS);
		tick(villager);
	}

	public static void onSelect(Villager villager) {
		if (villager == null) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		RandomSource random = villager.level().getRandom();
		RefugeeBubbleIcon face = RefugeeBubbleIcon.SELECT_FACES[random.nextInt(RefugeeBubbleIcon.SELECT_FACES.length)];
		data.startSelect(face.id(), villager.level().getGameTime() + SELECT_DURATION_TICKS);
		tick(villager);
	}

	public static void startLove(Villager villager) {
		if (villager == null) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.startLove(villager.level().getGameTime() + LOVE_DURATION_TICKS);
		tick(villager);
	}

	public static void startSweat(Villager villager) {
		if (villager == null) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.startSweat(villager.level().getGameTime() + SWEAT_DURATION_TICKS);
		tick(villager);
	}

	public static void tick(Villager villager) {
		tickGuardHealth(villager);
		RefugeeBubbleIcon next = compute(villager);
		if (get(villager) != next) {
			villager.setAttached(RefugeeAttachments.BUBBLE_ICON, next.id());
		}
	}

	private static void tickGuardHealth(Villager villager) {
		if (!RefugeeRoles.isGuard(villager)) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		float health = villager.getHealth();
		float max = villager.getMaxHealth();
		if (max <= 0.0f) {
			return;
		}
		boolean damaged = health < max - 0.01f;
		boolean lowHealth = health / max < LOW_HEALTH_RATIO;
		long gameTime = villager.level().getGameTime();
		if (lowHealth) {
			if (!data.isAngry(gameTime)) {
				data.startAngry(Long.MAX_VALUE);
			}
		} else if (data.isAngry(gameTime)) {
			data.clearAngry();
		}
		if (data.healthWasDamaged() && !damaged) {
			data.startFull(villager.level().getGameTime() + FULL_DURATION_TICKS);
		}
		data.setHealthWasDamaged(damaged);
	}

	private static RefugeeBubbleIcon compute(Villager villager) {
		long gameTime = villager.level().getGameTime();
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (RefugeeRoles.isGuard(villager) && data.isAngry(gameTime)) {
			return RefugeeBubbleIcon.ANGRY;
		}
		if (RefugeeRoles.isGuard(villager)) {
			LivingEntity target = villager.getTarget();
			if (target != null && target.isAlive()) {
				return RefugeeBubbleIcon.WEAPON;
			}
			float max = villager.getMaxHealth();
			if (max > 0.0f && villager.getHealth() / max < LOW_HEALTH_RATIO) {
				if (playerWithin(villager, GUARD_HUNGRY_RANGE)) {
					return RefugeeBubbleIcon.HUNGRY;
				}
				return RefugeeBubbleIcon.ANGRY;
			}
			if (data.isFull(gameTime)) {
				return RefugeeBubbleIcon.FULL;
			}
		}
		if (RefugeeRoles.isBuilder(villager) && data.isBuilding()) {
			return RefugeeBubbleIcon.PICKAXE;
		}
		if (data.isLoving(gameTime)) {
			return RefugeeBubbleIcon.LOVE;
		}
		if (data.isSweating(gameTime)) {
			return RefugeeBubbleIcon.SWEAT;
		}
		if (data.isTalking(gameTime)) {
			return RefugeeBubbleIcon.TALK;
		}
		if (data.isSelecting(gameTime)) {
			return RefugeeBubbleIcon.byId(data.selectIconId());
		}
		return RefugeeBubbleIcon.NONE;
	}

	private static boolean playerWithin(Villager villager, double range) {
		Player nearest = villager.level().getNearestPlayer(villager, range);
		if (nearest instanceof ServerPlayer && nearest.isAlive() && !nearest.isSpectator()) {
			return villager.distanceTo(nearest) <= range;
		}
		return false;
	}
}
