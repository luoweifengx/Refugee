package luowei.refugee.warehouse;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 全局物品分类器。仓库内存维护木头/木板/石头/泥沙/种子/杂项；PRECIOUS 仅用于从杂项里剥离，建筑时必须精确扣除。
 */
public enum MaterialCategory {
	LOG,
	PLANKS,
	STONE,
	SOIL,
	SEED,
	MISC,
	PRECIOUS,
	NONE;

	public boolean isWarehouseCategory() {
		return this == LOG || this == PLANKS || this == STONE || this == SOIL || this == SEED || this == MISC;
	}

	public static MaterialCategory of(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return NONE;
		}
		return of(stack.getItem());
	}

	public static MaterialCategory of(Item item) {
		if (item == null || item == Items.AIR) {
			return NONE;
		}
		if (isPrecious(item)) {
			return PRECIOUS;
		}
		if (isLog(item)) {
			return LOG;
		}
		if (isPlanksFamily(item)) {
			return PLANKS;
		}
		if (isSeed(item)) {
			return SEED;
		}
		if (isSoilFamily(item)) {
			return SOIL;
		}
		if (isStoneFamily(item)) {
			return STONE;
		}
		if (item instanceof BlockItem) {
			return MISC;
		}
		return NONE;
	}

	public static MaterialCategory ofBlock(BlockState state) {
		if (state == null || state.isAir()) {
			return NONE;
		}
		return of(state.getBlock().asItem());
	}

	private static boolean isSeed(Item item) {
		if (item == Items.WHEAT_SEEDS
				|| item == Items.BEETROOT_SEEDS
				|| item == Items.PUMPKIN_SEEDS
				|| item == Items.MELON_SEEDS
				|| item == Items.TORCHFLOWER_SEEDS
				|| item == Items.PITCHER_POD
				|| item == Items.NETHER_WART
				|| item == Items.CARROT
				|| item == Items.POTATO) {
			return true;
		}
		return tagged(item, ItemTags.VILLAGER_PLANTABLE_SEEDS);
	}

	/**
	 * 铲挖的松软土沙：泥土、草方块、沙子、粘土、砂砾等。不含石头/圆石/砂岩。
	 */
	private static boolean isSoilFamily(Item item) {
		if (item == Items.DIRT
				|| item == Items.GRASS_BLOCK
				|| item == Items.COARSE_DIRT
				|| item == Items.PODZOL
				|| item == Items.MYCELIUM
				|| item == Items.ROOTED_DIRT
				|| item == Items.DIRT_PATH
				|| item == Items.FARMLAND
				|| item == Items.MUD
				|| item == Items.CLAY
				|| item == Items.CLAY_BALL
				|| item == Items.SAND
				|| item == Items.RED_SAND
				|| item == Items.GRAVEL
				|| item == Items.SUSPICIOUS_SAND
				|| item == Items.SUSPICIOUS_GRAVEL
				|| item == Items.SOUL_SAND
				|| item == Items.SOUL_SOIL) {
			return true;
		}
		String path = idPath(item);
		if (path.isEmpty() || path.contains("sandstone") || path.contains("brick")) {
			return false;
		}
		if (path.equals("sand")
				|| path.equals("red_sand")
				|| path.equals("gravel")
				|| path.equals("clay")
				|| path.equals("dirt")
				|| path.equals("mud")) {
			return true;
		}
		return path.endsWith("_sand") && !path.contains("sandstone")
				|| path.endsWith("_dirt")
				|| path.equals("grass_block");
	}

	private static boolean isLog(Item item) {
		if (tagged(item, ItemTags.LOGS) || tagged(item, ItemTags.LOGS_THAT_BURN)) {
			return true;
		}
		if (item == Items.NETHER_WART_BLOCK || item == Items.WARPED_WART_BLOCK) {
			return true;
		}
		BlockState state = blockState(item);
		return state != null && state.is(BlockTags.LOGS);
	}

	private static boolean isPlanksFamily(Item item) {
		if (tagged(item, ItemTags.PLANKS)
				|| tagged(item, ItemTags.WOODEN_SLABS)
				|| tagged(item, ItemTags.WOODEN_STAIRS)
				|| tagged(item, ItemTags.WOODEN_FENCES)) {
			return true;
		}
		BlockState state = blockState(item);
		if (state == null) {
			return false;
		}
		return state.is(BlockTags.PLANKS)
				|| state.is(BlockTags.WOODEN_SLABS)
				|| state.is(BlockTags.WOODEN_STAIRS)
				|| state.is(BlockTags.WOODEN_FENCES)
				|| state.is(BlockTags.FENCE_GATES) && isWoodenFenceGate(item);
	}

	private static boolean isWoodenFenceGate(Item item) {
		String path = idPath(item);
		return !path.contains("nether_brick");
	}

	private static boolean isStoneFamily(Item item) {
		if (tagged(item, ItemTags.STONE_CRAFTING_MATERIALS) || tagged(item, ItemTags.STONE_TOOL_MATERIALS)) {
			return true;
		}
		if (isBrickFamily(item)) {
			return true;
		}
		BlockState state = blockState(item);
		if (state != null) {
			if (state.is(BlockTags.BASE_STONE_OVERWORLD)
					|| state.is(BlockTags.BASE_STONE_NETHER)
					|| state.is(BlockTags.STONE_BRICKS)) {
				return true;
			}
		}
		String path = idPath(item);
		if (path.isEmpty()) {
			return false;
		}
		if (path.contains("redstone")
				|| path.contains("glowstone")
				|| path.contains("lodestone")
				|| path.contains("grindstone")
				|| path.contains("stonecutter")) {
			return false;
		}
		return containsStoneToken(path);
	}

	/**
	 * 各种砖都是石头。石英砖走 PRECIOUS 优先，不会落到这里。
	 */
	private static boolean isBrickFamily(Item item) {
		if (item == Items.BRICK
				|| item == Items.BRICKS
				|| item == Items.BRICK_SLAB
				|| item == Items.BRICK_STAIRS
				|| item == Items.BRICK_WALL
				|| item == Items.MUD_BRICKS
				|| item == Items.MUD_BRICK_SLAB
				|| item == Items.MUD_BRICK_STAIRS
				|| item == Items.MUD_BRICK_WALL
				|| item == Items.PRISMARINE_BRICKS
				|| item == Items.PRISMARINE_BRICK_SLAB
				|| item == Items.PRISMARINE_BRICK_STAIRS) {
			return true;
		}
		return idPath(item).contains("brick");
	}

	private static boolean containsStoneToken(String path) {
		return path.contains("cobblestone")
				|| path.contains("mossy_cobble")
				|| path.equals("stone")
				|| path.startsWith("stone_")
				|| path.contains("_stone") && !path.contains("redstone") && !path.contains("glowstone")
				|| path.contains("granite")
				|| path.contains("diorite")
				|| path.contains("andesite")
				|| path.contains("deepslate")
				|| path.contains("netherrack")
				|| path.contains("nether_brick")
				|| path.contains("blackstone")
				|| path.contains("basalt")
				|| path.contains("tuff")
				|| path.contains("calcite")
				|| path.contains("sandstone")
				|| path.contains("end_stone");
	}

	private static boolean isPrecious(Item item) {
		if (tagged(item, ItemTags.COAL_ORES)
				|| tagged(item, ItemTags.COPPER_ORES)
				|| tagged(item, ItemTags.DIAMOND_ORES)
				|| tagged(item, ItemTags.EMERALD_ORES)
				|| tagged(item, ItemTags.GOLD_ORES)
				|| tagged(item, ItemTags.IRON_ORES)
				|| tagged(item, ItemTags.LAPIS_ORES)
				|| tagged(item, ItemTags.REDSTONE_ORES)
				|| tagged(item, ItemTags.BEACON_PAYMENT_ITEMS)) {
			return true;
		}
		if (item == Items.ANCIENT_DEBRIS
				|| item == Items.NETHER_QUARTZ_ORE
				|| item == Items.QUARTZ
				|| item == Items.QUARTZ_BLOCK
				|| item == Items.QUARTZ_PILLAR
				|| item == Items.QUARTZ_STAIRS
				|| item == Items.QUARTZ_SLAB
				|| item == Items.CHISELED_QUARTZ_BLOCK
				|| item == Items.SMOOTH_QUARTZ
				|| item == Items.SMOOTH_QUARTZ_STAIRS
				|| item == Items.SMOOTH_QUARTZ_SLAB
				|| item == Items.COAL
				|| item == Items.COAL_BLOCK
				|| item == Items.CHARCOAL
				|| item == Items.RAW_IRON
				|| item == Items.RAW_GOLD
				|| item == Items.RAW_COPPER
				|| item == Items.RAW_IRON_BLOCK
				|| item == Items.RAW_GOLD_BLOCK
				|| item == Items.RAW_COPPER_BLOCK
				|| item == Items.IRON_BLOCK
				|| item == Items.GOLD_BLOCK
				|| item == Items.DIAMOND_BLOCK
				|| item == Items.EMERALD_BLOCK
				|| item == Items.LAPIS_BLOCK
				|| item == Items.REDSTONE_BLOCK
				|| item == Items.COPPER_BLOCK
				|| item == Items.CUT_COPPER
				|| item == Items.NETHERITE_BLOCK
				|| item == Items.AMETHYST_BLOCK
				|| item == Items.AMETHYST_SHARD
				|| item == Items.BUDDING_AMETHYST
				|| item == Items.IRON_INGOT
				|| item == Items.GOLD_INGOT
				|| item == Items.COPPER_INGOT
				|| item == Items.NETHERITE_INGOT
				|| item == Items.DIAMOND
				|| item == Items.EMERALD
				|| item == Items.LAPIS_LAZULI
				|| item == Items.REDSTONE
				|| item == Items.GOLD_NUGGET
				|| item == Items.IRON_NUGGET) {
			return true;
		}
		BlockState state = blockState(item);
		if (state != null) {
			if (state.is(BlockTags.COAL_ORES)
					|| state.is(BlockTags.COPPER_ORES)
					|| state.is(BlockTags.DIAMOND_ORES)
					|| state.is(BlockTags.EMERALD_ORES)
					|| state.is(BlockTags.GOLD_ORES)
					|| state.is(BlockTags.IRON_ORES)
					|| state.is(BlockTags.LAPIS_ORES)
					|| state.is(BlockTags.REDSTONE_ORES)) {
				return true;
			}
		}
		String path = idPath(item);
		return path.endsWith("_ore")
				|| path.contains("_ore")
				|| path.startsWith("raw_")
				|| path.endsWith("_ingot")
				|| path.equals("netherite_scrap")
				|| path.contains("quartz")
				|| path.contains("amethyst")
				|| path.endsWith("_block") && isMineralBlockPath(path);
	}

	private static boolean isMineralBlockPath(String path) {
		return path.contains("diamond")
				|| path.contains("emerald")
				|| path.contains("iron")
				|| path.contains("gold")
				|| path.contains("copper")
				|| path.contains("coal")
				|| path.contains("lapis")
				|| path.contains("redstone")
				|| path.contains("netherite")
				|| path.contains("quartz")
				|| path.contains("amethyst");
	}

	private static boolean tagged(Item item, TagKey<Item> tag) {
		return item.builtInRegistryHolder().is(tag);
	}

	private static BlockState blockState(Item item) {
		if (!(item instanceof BlockItem blockItem)) {
			return null;
		}
		Block block = blockItem.getBlock();
		return block == null ? null : block.defaultBlockState();
	}

	private static String idPath(Item item) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
		return id == null ? "" : id.getPath();
	}
}
