package luowei.refugee.settle;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.attachment.PlayerSelectionData;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.interact.SelectionService;

/**
 * 放置安顿旗帜后先拆旗，再以旗帜格为第一落脚点三维 BFS 传送选中村民。
 */
public final class SettlementService {
	private SettlementService() {
	}

	public static void settle(ServerPlayer player, BlockPos bannerPos) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		List<UUID> selected = selection.snapshotSelected();
		if (selected.isEmpty()) {
			destroyBanner(level, bannerPos);
			SelectionService.removeSettlementBanners(player);
			player.displayClientMessage(Component.translatable("message.refugee.settle.none"), true);
			return;
		}
		destroyBanner(level, bannerPos);
		List<BlockPos> spots = StandableFinder.findStandable(level, bannerPos, Set.of(), selected.size());
		int teleported = 0;
		for (int i = 0; i < selected.size(); i++) {
			Entity entity = findLoaded(level, selected.get(i));
			if (!(entity instanceof Villager villager) || !villager.isAlive()) {
				continue;
			}
			BlockPos dest = i < spots.size() ? spots.get(i) : null;
			if (dest == null) {
				continue;
			}
			StandableFinder.snapToStandable(villager, level, dest);
			RefugeeVillagerData data = RefugeeAttachments.get(villager);
			if (RefugeeRoles.isGuard(villager)) {
				data.stopFollowing(dest);
			} else {
				data.stopFollowing();
			}
			RefugeeAttachments.markDirty(villager, data);
			teleported++;
		}
		selection.clearSelected();
		RefugeeAttachments.markDirty(player, selection);
		SelectionService.removeSettlementBanners(player);
		player.displayClientMessage(Component.translatable("message.refugee.settle.done", teleported), true);
	}

	private static void destroyBanner(ServerLevel level, BlockPos bannerPos) {
		level.destroyBlock(bannerPos, false);
	}

	private static Entity findLoaded(ServerLevel prefer, UUID id) {
		Entity local = prefer.getEntity(id);
		if (local != null) {
			return local;
		}
		for (ServerLevel level : prefer.getServer().getAllLevels()) {
			Entity entity = level.getEntity(id);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}
}
