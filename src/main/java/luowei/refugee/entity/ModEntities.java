package luowei.refugee.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import luowei.refugee.Refugee;

public final class ModEntities {
	public static EntityType<CopperGolem> COPPER_GOLEM;

	private ModEntities() {
	}

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Refugee.id("copper_golem"));
		COPPER_GOLEM = Registry.register(
				BuiltInRegistries.ENTITY_TYPE,
				key,
				EntityType.Builder.of(CopperGolem::new, MobCategory.MISC)
						.sized(1.4F, 2.7F)
						.eyeHeight(2.6F)
						.passengerAttachments(1.4795F)
						.clientTrackingRange(10)
						.build(key)
		);
		FabricDefaultAttributeRegistry.register(COPPER_GOLEM, CopperGolem.createAttributes());
	}
}
