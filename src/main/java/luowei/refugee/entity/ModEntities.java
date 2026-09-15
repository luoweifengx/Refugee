package luowei.refugee.entity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import luowei.refugee.Refugee;

public final class ModEntities {
	public static EntityType<ThrownSettlementBanner> THROWN_SETTLEMENT_BANNER;

	private ModEntities() {
	}

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Refugee.id("thrown_settlement_banner"));
		THROWN_SETTLEMENT_BANNER = Registry.register(
				BuiltInRegistries.ENTITY_TYPE,
				key,
				EntityType.Builder.<ThrownSettlementBanner>of(ThrownSettlementBanner::new, MobCategory.MISC)
						.sized(0.5F, 0.5F)
						.clientTrackingRange(4)
						.updateInterval(10)
						.noSummon()
						.noLootTable()
						.build(key)
		);
	}
}
