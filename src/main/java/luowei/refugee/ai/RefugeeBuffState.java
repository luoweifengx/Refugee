package luowei.refugee.ai;

import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.interact.RefugeeRoles;

/**
 * 难民 buff 类型：由手持物推导，不写 NBT。
 * 守卫优先于建筑；守卫内远程优先于近战。
 */
public enum RefugeeBuffState {
	IDLE,
	RANGED,
	MELEE,
	BUILDER;

	public static RefugeeBuffState of(Villager villager) {
		if (RefugeeRoles.isGuard(villager)) {
			if (RefugeeRoles.isRangedWeapon(villager.getMainHandItem())) {
				return RANGED;
			}
			return MELEE;
		}
		if (RefugeeRoles.isBuilder(villager)) {
			return BUILDER;
		}
		return IDLE;
	}
}
