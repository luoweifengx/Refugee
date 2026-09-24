package luowei.refugee.special;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import luowei.refugee.Refugee;

/**
 * 制图师探险家地图：村庄 / 林地府邸 / 其他有价值结构，以及古城专用地图。
 */
public final class ExplorerMapService {
	private static final int SEARCH_RADIUS = 100;
	private static final byte ZOOM = 2;
	private static final TagKey<Structure> ANCIENT_CITY = TagKey.create(
			net.minecraft.core.registries.Registries.STRUCTURE,
			Refugee.id("ancient_city")
	);

	private record Target(
			TagKey<Structure> tag,
			Holder<MapDecorationType> icon,
			String nameKey
	) {
	}

	private static final List<Target> POOL = List.of(
			new Target(StructureTags.VILLAGE, MapDecorationTypes.TARGET_X, "filled_map.village"),
			new Target(StructureTags.ON_WOODLAND_EXPLORER_MAPS, MapDecorationTypes.WOODLAND_MANSION, "filled_map.mansion"),
			new Target(StructureTags.ON_OCEAN_EXPLORER_MAPS, MapDecorationTypes.OCEAN_MONUMENT, "filled_map.monument"),
			new Target(StructureTags.ON_TRIAL_CHAMBERS_MAPS, MapDecorationTypes.TRIAL_CHAMBERS, "filled_map.trial_chambers"),
			new Target(StructureTags.ON_JUNGLE_EXPLORER_MAPS, MapDecorationTypes.TARGET_X, "filled_map.explorer_jungle"),
			new Target(StructureTags.ON_SWAMP_EXPLORER_MAPS, MapDecorationTypes.TARGET_X, "filled_map.explorer_swamp"),
			new Target(StructureTags.ON_TREASURE_MAPS, MapDecorationTypes.RED_X, "filled_map.buried_treasure")
	);

	private ExplorerMapService() {
	}

	public static boolean giveRandom(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		List<Target> shuffled = new ArrayList<>(POOL);
		for (int i = shuffled.size(); i > 1; i--) {
			int j = level.random.nextInt(i);
			Target swap = shuffled.get(i - 1);
			shuffled.set(i - 1, shuffled.get(j));
			shuffled.set(j, swap);
		}
		for (Target target : shuffled) {
			if (give(player, target)) {
				return true;
			}
		}
		player.displayClientMessage(Component.translatable("message.refugee.story.map_failed"), true);
		return false;
	}

	public static boolean giveAncientCity(ServerPlayer player) {
		return give(player, new Target(ANCIENT_CITY, MapDecorationTypes.RED_X, "filled_map.buried_treasure"));
	}

	private static boolean give(ServerPlayer player, Target target) {
		if (player == null || target == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		BlockPos found = level.findNearestMapStructure(target.tag(), player.blockPosition(), SEARCH_RADIUS, true);
		if (found == null) {
			return false;
		}
		ItemStack map = MapItem.create(level, found.getX(), found.getZ(), ZOOM, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, found, "+", target.icon());
		map.set(DataComponents.ITEM_NAME, Component.translatable(target.nameKey()));
		if (!player.getInventory().add(map)) {
			player.drop(map, false);
		}
		player.containerMenu.broadcastChanges();
		return true;
	}
}
