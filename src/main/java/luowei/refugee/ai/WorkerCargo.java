package luowei.refugee.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.build.BuildHealth;
import luowei.refugee.build.BuildJob;
import luowei.refugee.blueprint.BlueprintBlocks;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.logistics.OrgLogisticsData;
import luowei.refugee.logistics.OrgLogisticsData.ContainerRef;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.warehouse.MaterialCategory;
import luowei.refugee.warehouse.WarehouseService;

/**
 * 工人 9 格工作背包：只收仓库分类和食物，树苗不收。
 * 食物只有农民送进农作仓，其他人留着吃。
 */
public final class WorkerCargo {
	public static final double HAUL_RANGE = 128.0;
	public static final double OPEN_DISTANCE = 5.0;
	public static final int STALL_TICKS = 15 * 20;
	public static final double STALL_CLOSER = 5.0;
	private static final double ABSORB_RANGE = 2.0;
	private static final double HAUL_RANGE_SQ = HAUL_RANGE * HAUL_RANGE;

	public enum Dest {
		BLOCK,
		FARM,
		SMELT
	}

	private WorkerCargo() {
	}

	public static boolean isSapling(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.is(ItemTags.SAPLINGS);
	}

	/** 仓库分类或食物可以进背包。树苗即使是方块也不收。 */
	public static boolean canPickup(ItemStack stack) {
		if (stack == null || stack.isEmpty() || isSapling(stack)) {
			return false;
		}
		if (RefugeeRoles.isFood(stack)) {
			return true;
		}
		MaterialCategory category = MaterialCategory.of(stack);
		return category.isWarehouseCategory() || MaterialCategory.isSmeltCargo(stack);
	}

	/**
	 * 要送进仓库的去向。非农民的食物留在背包，返回 null。
	 */
	public static Dest destOf(ItemStack stack, Villager villager) {
		if (!canPickup(stack)) {
			return null;
		}
		if (RefugeeRoles.isFood(stack)) {
			return RefugeeRoles.isHoe(RefugeeRoles.workTool(villager)) ? Dest.FARM : null;
		}
		if (MaterialCategory.isSmeltCargo(stack)) {
			return Dest.SMELT;
		}
		if (MaterialCategory.of(stack) == MaterialCategory.SEED) {
			return Dest.FARM;
		}
		if (MaterialCategory.of(stack).isWarehouseCategory()) {
			return Dest.BLOCK;
		}
		return null;
	}

	public static boolean hasDepositable(Villager villager) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		for (ItemStack stack : data.cargoSlots()) {
			if (destOf(stack, villager) != null) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasEmptySlot(Villager villager) {
		for (ItemStack stack : RefugeeAttachments.get(villager).cargoSlots()) {
			if (stack.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	public static int countSeeds(Villager villager) {
		int total = 0;
		for (ItemStack stack : RefugeeAttachments.get(villager).cargoSlots()) {
			if (!stack.isEmpty() && MaterialCategory.of(stack) == MaterialCategory.SEED) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public static ItemStack takeOneSeed(Villager villager) {
		List<ItemStack> slots = RefugeeAttachments.get(villager).cargoSlots();
		for (int i = 0; i < slots.size(); i++) {
			ItemStack stack = slots.get(i);
			if (stack.isEmpty() || MaterialCategory.of(stack) != MaterialCategory.SEED) {
				continue;
			}
			ItemStack taken = stack.copyWithCount(1);
			stack.shrink(1);
			if (stack.isEmpty()) {
				slots.set(i, ItemStack.EMPTY);
			}
			RefugeeAttachments.markDirty(villager, RefugeeAttachments.get(villager));
			return taken;
		}
		return ItemStack.EMPTY;
	}

	/** 非农民留在背包里吃的食物，整堆取出。 */
	public static ItemStack takeKeptFood(Villager villager) {
		List<ItemStack> slots = RefugeeAttachments.get(villager).cargoSlots();
		for (int i = 0; i < slots.size(); i++) {
			ItemStack stack = slots.get(i);
			if (stack.isEmpty() || !RefugeeRoles.isFood(stack) || destOf(stack, villager) != null) {
				continue;
			}
			ItemStack taken = stack.copy();
			slots.set(i, ItemStack.EMPTY);
			RefugeeAttachments.markDirty(villager, RefugeeAttachments.get(villager));
			return taken;
		}
		return ItemStack.EMPTY;
	}

	public static ItemStack insert(Villager villager, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		if (!canPickup(stack)) {
			return stack;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		int before = stack.getCount();
		ItemStack remaining = mergeInto(data.cargoSlots(), stack.copy());
		if (remaining.getCount() != before) {
			RefugeeAttachments.markDirty(villager, data);
		}
		return remaining;
	}

	public static boolean fitsPickup(Villager villager, List<ItemStack> drops) {
		if (drops == null || drops.isEmpty()) {
			return true;
		}
		List<ItemStack> slots = copySlots(RefugeeAttachments.get(villager).cargoSlots());
		for (ItemStack drop : drops) {
			if (drop == null || drop.isEmpty() || !canPickup(drop)) {
				continue;
			}
			ItemStack left = mergeInto(slots, drop.copy());
			if (!left.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * 下一次掉落里有要收、但背包放不下、并且身上有可以先存走的东西。
	 * 这时先不拆块。已经在等仓库时照常拆，装不下的掉在脚下。
	 */
	public static boolean shouldHold(Villager villager, List<ItemStack> drops) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.cargoWaiting() || !hasDepositable(villager) || fitsPickup(villager, drops)) {
			return false;
		}
		data.setCargoBlocked(true);
		return !canPlaceNext(villager);
	}

	public static void giveDrops(Villager villager, ServerLevel level, List<ItemStack> drops) {
		if (drops == null || drops.isEmpty()) {
			return;
		}
		for (ItemStack drop : drops) {
			if (drop == null || drop.isEmpty()) {
				continue;
			}
			ItemStack left = drop.copy();
			if (canPickup(left)) {
				left = insert(villager, left);
			}
			if (!left.isEmpty()) {
				villager.spawnAtLocation(level, left);
			}
		}
	}

	public static void absorbNearby(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level) || !RefugeeRoles.isBuilder(villager)) {
			return;
		}
		AABB box = villager.getBoundingBox().inflate(ABSORB_RANGE);
		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
			if (!entity.isAlive() || entity.hasPickUpDelay()) {
				continue;
			}
			ItemStack stack = entity.getItem();
			if (!canPickup(stack)) {
				continue;
			}
			int before = stack.getCount();
			ItemStack left = insert(villager, stack);
			if (left.getCount() == before) {
				continue;
			}
			if (left.isEmpty()) {
				entity.discard();
			} else {
				entity.setItem(left);
			}
		}
	}

	public static boolean hasForBuild(Villager villager, Item material) {
		if (material == null || material == Items.AIR) {
			return false;
		}
		MaterialCategory category = MaterialCategory.of(material);
		if (RefugeeConfig.mergeCategory(category)) {
			return countCategory(villager, category) > 0;
		}
		return countItem(villager, material) > 0;
	}

	public static boolean tryConsumeForBuild(Villager villager, Item material) {
		if (!hasForBuild(villager, material)) {
			return false;
		}
		MaterialCategory category = MaterialCategory.of(material);
		boolean merge = RefugeeConfig.mergeCategory(category);
		List<ItemStack> slots = RefugeeAttachments.get(villager).cargoSlots();
		for (int i = 0; i < slots.size(); i++) {
			ItemStack stack = slots.get(i);
			if (stack.isEmpty()) {
				continue;
			}
			boolean match = merge ? MaterialCategory.of(stack) == category : stack.is(material);
			if (!match) {
				continue;
			}
			stack.shrink(1);
			if (stack.isEmpty()) {
				slots.set(i, ItemStack.EMPTY);
			}
			RefugeeAttachments.markDirty(villager, RefugeeAttachments.get(villager));
			return true;
		}
		return false;
	}

	/** 当前建造位点还能从背包或仓库拿到方块。这种时候先放，不去存仓。 */
	public static boolean canPlaceNext(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.jobId() == null || level.getServer() == null) {
			return false;
		}
		BuildJob job = OrgLogisticsData.get(level.getServer()).job(data.jobId());
		if (job == null || !job.needsWork()) {
			return false;
		}
		List<StructureTemplate.StructureBlockInfo> blocks = BuildHealth.loadBlocks(level, job);
		int index = Math.max(0, job.nextIndex());
		while (index < blocks.size()) {
			StructureTemplate.StructureBlockInfo info = blocks.get(index);
			index++;
			if (BlueprintBlocks.shouldSkip(info)) {
				continue;
			}
			Item material = info.state().getBlock().asItem();
			if (material == Items.AIR) {
				continue;
			}
			if (BuildHealth.matches(info.state(), level.getBlockState(info.pos()))) {
				continue;
			}
			if (hasForBuild(villager, material)) {
				return true;
			}
			UUID subjectId = data.subjectId();
			return subjectId != null && WarehouseService.hasForBuild(level, subjectId, material);
		}
		return false;
	}

	public static boolean wantsHaul(Villager villager) {
		if (!ready(villager) || !hasDepositable(villager)) {
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.cargoWaiting() || canPlaceNext(villager)) {
			return false;
		}
		return data.cargoBlocked() || data.cargoWake() || data.cargoIdle();
	}

	/** 已经在去存仓的路上：一直走到放完，或改去放方块。 */
	public static boolean continueHaul(Villager villager) {
		if (!ready(villager) || !hasDepositable(villager)) {
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		return !data.cargoWaiting() && !canPlaceNext(villager);
	}

	public static void clearHaulFlags(Villager villager) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		data.setCargoBlocked(false);
		data.setCargoIdle(false);
		data.setCargoWake(false);
	}

	/** 玩家把背包某一格拿得不再满堆时，只叫醒这一名等待中的居民。 */
	public static void wakeVillager(Villager villager) {
		if (villager == null) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (!data.cargoWaiting()) {
			return;
		}
		data.setCargoWaiting(false);
		data.setCargoWake(true);
	}

	public static void wake(MinecraftServer server, UUID subjectId) {
		if (server == null || subjectId == null) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Villager villager : level.getEntities(EntityType.VILLAGER, entity -> true)) {
				RefugeeVillagerData data = villager.getAttached(RefugeeAttachments.VILLAGER);
				if (data == null || !subjectId.equals(data.subjectId()) || !data.cargoWaiting()) {
					continue;
				}
				data.setCargoWaiting(false);
				data.setCargoWake(true);
			}
		}
	}

	public static void enterWait(Villager villager) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.cargoWaiting()) {
			return;
		}
		data.setCargoWaiting(true);
		clearHaulFlags(villager);
		data.setCargoWaiting(true);
		notifyNoWarehouse(villager, data.subjectId());
	}

	public static List<ContainerRef> candidates(Villager villager) {
		List<ContainerRef> found = new ArrayList<>();
		if (!(villager.level() instanceof ServerLevel level) || level.getServer() == null) {
			return found;
		}
		UUID subjectId = RefugeeAttachments.get(villager).subjectId();
		if (subjectId == null) {
			return found;
		}
		OrgLogisticsData data = OrgLogisticsData.get(level.getServer());
		consider(villager, level, data, subjectId, data.warehouses(subjectId), Dest.BLOCK, found);
		consider(villager, level, data, subjectId, data.farmWarehouses(subjectId), Dest.FARM, found);
		consider(villager, level, data, subjectId, data.smeltResults(subjectId), Dest.SMELT, found);
		found.sort((left, right) -> Double.compare(distSq(villager, left.pos()), distSq(villager, right.pos())));
		return found;
	}

	public static Dest destOfRef(ServerLevel level, UUID subjectId, ContainerRef ref) {
		if (level == null || level.getServer() == null || subjectId == null || ref == null) {
			return null;
		}
		OrgLogisticsData data = OrgLogisticsData.get(level.getServer());
		if (data.farmWarehouses(subjectId).contains(ref)) {
			return Dest.FARM;
		}
		if (data.smeltResults(subjectId).contains(ref)) {
			return Dest.SMELT;
		}
		if (data.warehouses(subjectId).contains(ref)) {
			return Dest.BLOCK;
		}
		return null;
	}

	public static boolean accepts(Villager villager, ServerLevel level, ContainerRef ref) {
		Dest dest = destOfRef(level, RefugeeAttachments.get(villager).subjectId(), ref);
		if (dest == null) {
			return false;
		}
		Container container = WarehouseService.peekLoaded(level, ref);
		if (container == null) {
			return false;
		}
		for (ItemStack stack : RefugeeAttachments.get(villager).cargoSlots()) {
			if (destOf(stack, villager) == dest && hasRoom(container, stack)) {
				return true;
			}
		}
		return false;
	}

	public static void depositMatching(Villager villager, ServerLevel level, ContainerRef ref) {
		Dest dest = destOfRef(level, RefugeeAttachments.get(villager).subjectId(), ref);
		if (dest == null) {
			return;
		}
		UUID subjectId = RefugeeAttachments.get(villager).subjectId();
		List<ItemStack> slots = RefugeeAttachments.get(villager).cargoSlots();
		boolean changed = false;
		for (int i = 0; i < slots.size(); i++) {
			ItemStack stack = slots.get(i);
			if (stack.isEmpty() || destOf(stack, villager) != dest) {
				continue;
			}
			ItemStack left = WarehouseService.insertAt(level, subjectId, ref, stack);
			if (left.getCount() != stack.getCount() || left.isEmpty() != stack.isEmpty()) {
				changed = true;
			}
			slots.set(i, left.isEmpty() ? ItemStack.EMPTY : left);
		}
		if (changed) {
			RefugeeAttachments.markDirty(villager, RefugeeAttachments.get(villager));
		}
	}

	private static void consider(
			Villager villager,
			ServerLevel level,
			OrgLogisticsData data,
			UUID subjectId,
			List<ContainerRef> refs,
			Dest dest,
			List<ContainerRef> found
	) {
		boolean needs = false;
		for (ItemStack stack : RefugeeAttachments.get(villager).cargoSlots()) {
			if (destOf(stack, villager) == dest) {
				needs = true;
				break;
			}
		}
		if (!needs) {
			return;
		}
		for (ContainerRef ref : refs) {
			if (ref == null || !level.dimension().location().equals(ref.dimension())) {
				continue;
			}
			if (distSq(villager, ref.pos()) > HAUL_RANGE_SQ) {
				continue;
			}
			if (!accepts(villager, level, ref)) {
				continue;
			}
			if (!found.contains(ref)) {
				found.add(ref);
			}
		}
	}

	private static boolean hasRoom(Container container, ItemStack stack) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (!container.canPlaceItem(slot, stack)) {
				continue;
			}
			ItemStack existing = container.getItem(slot);
			if (existing.isEmpty()) {
				return true;
			}
			if (!ItemStack.isSameItemSameComponents(existing, stack)) {
				continue;
			}
			int max = Math.min(container.getMaxStackSize(), existing.getMaxStackSize());
			if (existing.getCount() < max) {
				return true;
			}
		}
		return false;
	}

	private static void notifyNoWarehouse(Villager villager, UUID subjectId) {
		if (!(villager.level() instanceof ServerLevel level) || subjectId == null || level.getServer() == null) {
			return;
		}
		String name = PbsAdapter.displayName(level.getServer(), subjectId);
		if (name == null || name.isBlank()) {
			name = "组织";
		}
		boolean farmer = RefugeeRoles.isHoe(RefugeeRoles.workTool(villager));
		Component message = Component.translatable(
				farmer ? "message.refugee.cargo.no_warehouse.farmer" : "message.refugee.cargo.no_warehouse",
				name
		);
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			if (subjectId.equals(player.getUUID()) || subjectId.equals(PbsAdapter.resolveSubject(player))) {
				player.sendSystemMessage(message);
			}
		}
	}

	private static boolean ready(Villager villager) {
		if (villager == null || villager.isBaby() || !RefugeeRoles.isBuilder(villager) || WorkerSleep.yields(villager)) {
			return false;
		}
		if (luowei.refugee.livability.LivabilityService.isRebelling(villager) || RefugeeCombat.isEating(villager)) {
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		return data.subjectId() != null
				&& !data.isFollowing()
				&& !data.isFollowingEntity()
				&& !data.isPatrolling();
	}

	private static int countCategory(Villager villager, MaterialCategory category) {
		int total = 0;
		for (ItemStack stack : RefugeeAttachments.get(villager).cargoSlots()) {
			if (!stack.isEmpty() && MaterialCategory.of(stack) == category) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static int countItem(Villager villager, Item item) {
		int total = 0;
		for (ItemStack stack : RefugeeAttachments.get(villager).cargoSlots()) {
			if (!stack.isEmpty() && stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static List<ItemStack> copySlots(List<ItemStack> slots) {
		List<ItemStack> copy = new ArrayList<>(slots.size());
		for (ItemStack stack : slots) {
			copy.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		}
		return copy;
	}

	private static ItemStack mergeInto(List<ItemStack> slots, ItemStack remaining) {
		for (int i = 0; i < slots.size() && !remaining.isEmpty(); i++) {
			ItemStack existing = slots.get(i);
			if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)) {
				continue;
			}
			int space = existing.getMaxStackSize() - existing.getCount();
			if (space <= 0) {
				continue;
			}
			int put = Math.min(space, remaining.getCount());
			existing.grow(put);
			remaining.shrink(put);
		}
		for (int i = 0; i < slots.size() && !remaining.isEmpty(); i++) {
			if (!slots.get(i).isEmpty()) {
				continue;
			}
			int put = Math.min(remaining.getMaxStackSize(), remaining.getCount());
			slots.set(i, remaining.copyWithCount(put));
			remaining.shrink(put);
		}
		return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
	}

	private static double distSq(Villager villager, BlockPos pos) {
		return villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
	}
}
