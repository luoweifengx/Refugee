package luowei.refugee.item;

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * 锁链甲总值改为皮甲(7)与铁甲(15)的中间值 11：头 2 / 胸 4 / 腿 4 / 靴 1。
 */
public final class ChainmailDefense {
	private ChainmailDefense() {
	}

	public static void register() {
		DefaultItemComponentEvents.MODIFY.register(context -> {
			context.modify(Items.CHAINMAIL_HELMET, builder -> builder.set(
					DataComponents.ATTRIBUTE_MODIFIERS,
					armor(EquipmentSlotGroup.HEAD, 2.0)
			));
			context.modify(Items.CHAINMAIL_CHESTPLATE, builder -> builder.set(
					DataComponents.ATTRIBUTE_MODIFIERS,
					armor(EquipmentSlotGroup.CHEST, 4.0)
			));
			context.modify(Items.CHAINMAIL_LEGGINGS, builder -> builder.set(
					DataComponents.ATTRIBUTE_MODIFIERS,
					armor(EquipmentSlotGroup.LEGS, 4.0)
			));
			context.modify(Items.CHAINMAIL_BOOTS, builder -> builder.set(
					DataComponents.ATTRIBUTE_MODIFIERS,
					armor(EquipmentSlotGroup.FEET, 1.0)
			));
		});
	}

	private static ItemAttributeModifiers armor(EquipmentSlotGroup group, double amount) {
		return ItemAttributeModifiers.builder()
				.add(
						Attributes.ARMOR,
						new AttributeModifier(
								ResourceLocation.withDefaultNamespace("armor." + group.getSerializedName()),
								amount,
								AttributeModifier.Operation.ADD_VALUE
						),
						group
				)
				.build();
	}
}
