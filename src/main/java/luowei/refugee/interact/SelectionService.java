package luowei.refugee.interact;

import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import luowei.refugee.attachment.PlayerSelectionData;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.item.ItemData;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.staff.StaffService;
import luowei.refugee.talk.RefugeeBubble;

/**
 * 按玩家选中表切换跟随；右键选中时没有安顿旗则发一面，选中表空则收旗。
 */
public final class SelectionService {
	private SelectionService() {
	}

	/**
	 * 仅所属玩家或同组织成员可指挥。无主村民不可接管。
	 */
	public static boolean canCommand(ServerPlayer player, Villager villager) {
		if (player == null || villager == null) {
			return false;
		}
		UUID subjectId = RefugeeAttachments.get(villager).subjectId();
		if (subjectId == null) {
			return false;
		}
		return subjectId.equals(player.getUUID()) || subjectId.equals(PbsAdapter.resolveSubject(player));
	}

	private static void denyCommand(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.refugee.command.denied"), true);
	}

	/**
	 * 空手右键：以玩家选中表为准切换。表中无则选中并跟随；表中有则取消。
	 *
	 * @return 是否发生了选中/取消
	 */
	public static boolean toggleFollow(ServerPlayer player, Villager villager) {
		if (!canCommand(player, villager)) {
			denyCommand(player);
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		UUID villagerId = villager.getUUID();
		if (selection.isSelected(villagerId)) {
			if (RefugeeRoles.isGuard(villager)) {
				data.stopFollowing(villager.blockPosition());
			} else {
				data.stopFollowing();
			}
			selection.removeSelected(villagerId);
			RefugeeAttachments.markDirty(villager, data);
			RefugeeAttachments.markDirty(player, selection);
			collectBannersIfEmpty(player);
			player.displayClientMessage(Component.translatable("message.refugee.follow.stop"), true);
			return true;
		}
		selectFollow(player, villager, data, selection);
		giveBanner(player);
		player.displayClientMessage(Component.translatable("message.refugee.follow.start"), true);
		return true;
	}

	public static int selectAround(ServerPlayer player, double radius) {
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		boolean wasEmpty = selection.selectedVillagers().isEmpty();
		AABB box = player.getBoundingBox().inflate(radius);
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, box, Villager::isAlive);
		int count = 0;
		boolean denied = false;
		for (Villager villager : villagers) {
			if (villager.distanceTo(player) > radius || selection.isSelected(villager.getUUID())) {
				continue;
			}
			if (!canCommand(player, villager)) {
				denied = true;
				continue;
			}
			RefugeeVillagerData data = RefugeeAttachments.get(villager);
			selectFollow(player, villager, data, selection);
			count++;
		}
		if (count > 0) {
			if (wasEmpty) {
				giveBanner(player);
			}
			RefugeeAttachments.markDirty(player, selection);
			player.displayClientMessage(Component.translatable("message.refugee.follow.area", count), true);
		} else if (denied) {
			denyCommand(player);
		}
		return count;
	}

	private static void selectFollow(
			ServerPlayer player,
			Villager villager,
			RefugeeVillagerData data,
			PlayerSelectionData selection
	) {
		removeFromOtherSelections(player, villager.getUUID());
		if (data.isBuilding()) {
			StaffService.unbindWorker(villager);
		}
		data.startFollowing(player.getUUID());
		selection.addSelected(villager.getUUID());
		RefugeeAttachments.markDirty(villager, data);
		RefugeeAttachments.markDirty(player, selection);
		RefugeeBubble.onSelect(villager);
	}

	private static void removeFromOtherSelections(ServerPlayer newOwner, UUID villagerId) {
		MinecraftServer server = newOwner.level().getServer();
		if (server == null) {
			return;
		}
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other.getUUID().equals(newOwner.getUUID())) {
				continue;
			}
			PlayerSelectionData otherSelection = RefugeeAttachments.get(other);
			if (otherSelection.removeSelected(villagerId)) {
				RefugeeAttachments.markDirty(other, otherSelection);
				collectBannersIfEmpty(other);
			}
		}
	}

	/**
	 * 确保背包/副手至少有一面安顿旗；已有则不再发放。
	 */
	public static void giveBanner(ServerPlayer player) {
		if (hasSettlementBanner(player)) {
			return;
		}
		ItemStack banner = ItemData.createSettlementBanner();
		if (!player.getInventory().add(banner)) {
			player.drop(banner, false);
		}
	}

	public static void collectBannersIfEmpty(ServerPlayer player) {
		if (RefugeeAttachments.get(player).selectedVillagers().isEmpty()) {
			removeSettlementBanners(player);
		}
	}

	/**
	 * 从背包与副手移除所有安顿旗，不留叠堆。
	 */
	public static void removeSettlementBanners(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (ItemData.isSettlementBanner(inventory.getItem(i))) {
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
		if (ItemData.isSettlementBanner(player.getOffhandItem())) {
			player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		}
	}

	private static boolean hasSettlementBanner(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (ItemData.isSettlementBanner(inventory.getItem(i))) {
				return true;
			}
		}
		return ItemData.isSettlementBanner(player.getOffhandItem());
	}

	public static void onVillagerRemoved(UUID villagerId, ServerLevel level) {
		RosterService.onVillagerGone(villagerId, level);
		luowei.refugee.staff.StaffService.onVillagerGone(villagerId, level.getServer());
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			PlayerSelectionData selection = RefugeeAttachments.get(player);
			if (selection.removeSelected(villagerId)) {
				RefugeeAttachments.markDirty(player, selection);
				collectBannersIfEmpty(player);
			}
		}
	}

	/**
	 * 列队：对选中表里所有人执行现有取消选中（停跟随、守卫就地设岗、收旗）。
	 *
	 * @return 实际处理的人数
	 */
	public static int deselectAll(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return 0;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		List<UUID> selected = selection.snapshotSelected();
		if (selected.isEmpty()) {
			return 0;
		}
		int count = 0;
		for (UUID villagerId : selected) {
			if (deselectOne(player, level, villagerId)) {
				count++;
			}
		}
		if (count > 0 || !selection.selectedVillagers().isEmpty()) {
			selection.clearSelected();
			RefugeeAttachments.markDirty(player, selection);
			collectBannersIfEmpty(player);
		}
		return count;
	}

	private static boolean deselectOne(ServerPlayer player, ServerLevel level, UUID villagerId) {
		Entity entity = level.getEntity(villagerId);
		if (!(entity instanceof Villager villager) || !villager.isAlive()) {
			return false;
		}
		if (!canCommand(player, villager)) {
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (RefugeeRoles.isGuard(villager)) {
			data.stopFollowing(villager.blockPosition());
		} else {
			data.stopFollowing();
		}
		RefugeeAttachments.markDirty(villager, data);
		return true;
	}

	/**
	 * 只从选中表拿掉，不改村民 AI（跟随生物 / 巡逻写完状态后用）。
	 */
	public static void dropFromSelection(ServerPlayer player, List<UUID> villagerIds) {
		if (player == null || villagerIds == null || villagerIds.isEmpty()) {
			return;
		}
		PlayerSelectionData selection = RefugeeAttachments.get(player);
		boolean changed = false;
		for (UUID villagerId : villagerIds) {
			if (selection.removeSelected(villagerId)) {
				changed = true;
			}
		}
		if (changed) {
			RefugeeAttachments.markDirty(player, selection);
			collectBannersIfEmpty(player);
		}
	}
}
