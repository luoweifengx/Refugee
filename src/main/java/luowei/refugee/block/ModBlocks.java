package luowei.refugee.block;

import java.util.function.Function;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import luowei.refugee.Refugee;

public final class ModBlocks {
	public static Block ALTAR;
	public static Item ALTAR_ITEM;
	public static BlockEntityType<AltarBlockEntity> ALTAR_ENTITY;

	private ModBlocks() {
	}

	public static void register() {
		ALTAR = registerBlock("altar", AltarBlock::new);
		ALTAR_ENTITY = Registry.register(
				BuiltInRegistries.BLOCK_ENTITY_TYPE,
				Refugee.id("altar"),
				FabricBlockEntityTypeBuilder.create(AltarBlockEntity::new, ALTAR).build()
		);
		ALTAR_ITEM = registerBlockItem(ALTAR);
	}

	private static Block registerBlock(String path, Function<BlockBehaviour.Properties, Block> factory) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Refugee.id(path));
		BlockBehaviour.Properties props = BlockBehaviour.Properties.ofFullCopy(Blocks.SCULK_SHRIEKER).setId(key);
		Block block = factory.apply(props);
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	private static Item registerBlockItem(Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		return Registry.register(
				BuiltInRegistries.ITEM,
				key,
				new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key))
		);
	}
}
