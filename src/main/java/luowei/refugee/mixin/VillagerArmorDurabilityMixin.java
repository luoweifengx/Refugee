package luowei.refugee.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

/**
 * 原版 {@code LivingEntity.hurtArmor} 为空，只有玩家会磨甲。村民穿甲后按玩家公式扣耐久。
 */
@Mixin(Villager.class)
public abstract class VillagerArmorDurabilityMixin extends AbstractVillager {
	protected VillagerArmorDurabilityMixin(EntityType<? extends AbstractVillager> type, Level level) {
		super(type, level);
	}

	@Override
	public void hurtArmor(DamageSource source, float amount) {
		this.doHurtEquipment(source, amount, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
	}

	@Override
	public void hurtHelmet(DamageSource source, float amount) {
		this.doHurtEquipment(source, amount, EquipmentSlot.HEAD);
	}
}
