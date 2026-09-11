package luowei.refugee.item;

import java.util.function.Function;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import luowei.refugee.Refugee;
import luowei.refugee.block.ModBlocks;
import luowei.refugee.staff.CommandStaffItem;

public final class ModItems {
	public static final ResourceKey<CreativeModeTab> TAB_KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Refugee.id("main"));

	public static Item COMMAND_STAFF;
	public static Item LEATHER_KIT;
	public static Item CHAIN_KIT;
	public static Item IRON_KIT;
	public static Item DIAMOND_KIT;

	private ModItems() {
	}

	public static void register() {
		COMMAND_STAFF = registerItem("command_staff", props -> new CommandStaffItem(props.stacksTo(1)));
		LEATHER_KIT = registerItem("leather_kit", props -> new ArmorKitItem(props, ArmorKitItem.Kind.LEATHER));
		CHAIN_KIT = registerItem("chain_kit", props -> new ArmorKitItem(props, ArmorKitItem.Kind.CHAIN));
		IRON_KIT = registerItem("iron_kit", props -> new ArmorKitItem(props, ArmorKitItem.Kind.IRON));
		DIAMOND_KIT = registerItem("diamond_kit", props -> new ArmorKitItem(props, ArmorKitItem.Kind.DIAMOND));
		ChainmailDefense.register();

		Registry.register(
				BuiltInRegistries.CREATIVE_MODE_TAB,
				TAB_KEY,
				FabricItemGroup.builder()
						.title(Component.translatable("itemGroup.refugee.main"))
						.icon(() -> new ItemStack(COMMAND_STAFF))
						.displayItems((params, output) -> {
							output.accept(COMMAND_STAFF);
							output.accept(ModBlocks.ALTAR_ITEM);
							output.accept(LEATHER_KIT);
							output.accept(CHAIN_KIT);
							output.accept(IRON_KIT);
							output.accept(DIAMOND_KIT);
						})
						.build()
		);

		ItemGroupEvents.modifyEntriesEvent(TAB_KEY).register(entries -> {
			entries.accept(COMMAND_STAFF);
			entries.accept(ModBlocks.ALTAR_ITEM);
			entries.accept(LEATHER_KIT);
			entries.accept(CHAIN_KIT);
			entries.accept(IRON_KIT);
			entries.accept(DIAMOND_KIT);
		});
	}

	private static Item registerItem(String path, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Refugee.id(path));
		Item item = factory.apply(new Item.Properties().setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
