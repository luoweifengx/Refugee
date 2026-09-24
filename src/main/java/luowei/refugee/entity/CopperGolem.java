package luowei.refugee.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;

/**
 * 铜傀儡：铁傀儡同模，数值更弱，用铜块 + 南瓜摆出来。
 */
public class CopperGolem extends IronGolem {
	public CopperGolem(EntityType<? extends IronGolem> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 50.0)
				.add(Attributes.MOVEMENT_SPEED, 0.22)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
				.add(Attributes.ATTACK_DAMAGE, 8.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}
}
