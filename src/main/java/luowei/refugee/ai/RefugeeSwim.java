package luowei.refugee.ai;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.npc.Villager;

/**
 * 跳过 Brain 后原版涉水活动不再跑；在水/熔岩里主动上浮。
 */
public final class RefugeeSwim {
	private RefugeeSwim() {
	}

	public static void tick(Villager villager) {
		if (villager == null || villager.level().isClientSide()) {
			return;
		}
		villager.getNavigation().setCanFloat(true);
		if (!shouldFloat(villager)) {
			return;
		}
		if (villager.getRandom().nextFloat() < 0.8F) {
			villager.getJumpControl().jump();
		}
	}

	private static boolean shouldFloat(Villager villager) {
		if (villager.isInLava()) {
			return true;
		}
		return villager.isInWater()
				&& villager.getFluidHeight(FluidTags.WATER) > villager.getFluidJumpThreshold();
	}
}
