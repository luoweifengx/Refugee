package luowei.refugee.warehouse;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.logistics.OrgLogisticsData;
import luowei.refugee.logistics.OrgLogisticsData.ContainerRef;
import luowei.refugee.staff.StaffService;
import luowei.refugee.warehouse.WarehouseLedger.SlotLoc;

/**
 * 组织仓库：存按漏斗逻辑；取料在合并大类时宽松，关闭合并时按精确物品。
 * 槽位账本与区块强制加载均经由此类，避免热路径全表扫描。
 */
public final class WarehouseService {
	private static final ThreadLocal<Integer> SILENT_RESCAN = ThreadLocal.withInitial(() -> 0);
	private static final Map<ChunkKey, Integer> FORCED_CHUNKS = new HashMap<>();
	private static final Map<ContainerRef, Integer> OPEN_COUNTS = new HashMap<>();
	private static boolean booted;

	private WarehouseService() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(WarehouseService::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(WarehouseService::onServerStopped);
		ServerWorldEvents.LOAD.register((server, level) -> onWorldLoad(level));
	}

	public static boolean add(ServerLevel level, UUID subjectId, BlockPos pos) {
		if (level == null || subjectId == null || pos == null) {
			return false;
		}
		ResourceLocation dimension = level.dimension().location();
		OrgLogisticsData data = OrgLogisticsData.get(level.getServer());
		if (!data.addWarehouse(subjectId, dimension, pos)) {
			return false;
		}
		ContainerRef ref = new ContainerRef(dimension, pos.immutable());
		retainChunk(level, ref);
		organize(level, pos);
		return true;
	}

	public static boolean remove(ServerLevel level, UUID subjectId, BlockPos pos) {
		if (level == null || subjectId == null || pos == null) {
			return false;
		}
		ResourceLocation dimension = level.dimension().location();
		OrgLogisticsData data = OrgLogisticsData.get(level.getServer());
		if (!data.removeWarehouse(subjectId, dimension, pos)) {
			return false;
		}
		ContainerRef ref = new ContainerRef(dimension, pos.immutable());
		WarehouseLedger.instance().removeChest(subjectId, ref);
		releaseChunk(level, ref);
		if (!isTracked(level, pos)) {
			OPEN_COUNTS.remove(ref);
		}
		return true;
	}

	public static ItemStack deposit(ServerLevel level, UUID subjectId, ItemStack stack) {
		if (level == null || subjectId == null || stack == null || stack.isEmpty()) {
			return stack == null ? ItemStack.EMPTY : stack;
		}
		OrgLogisticsData data = OrgLogisticsData.get(level.getServer());
		ItemStack[] remaining = { stack.copy() };
		runSilent(() -> {
			for (ContainerRef ref : data.warehouses(subjectId)) {
				if (remaining[0].isEmpty()) {
					break;
				}
				if (isOccupied(ref)) {
					continue;
				}
				Container container = containerAt(level.getServer(), ref);
				if (container == null) {
					continue;
				}
				List<Integer> changed = new ArrayList<>();
				remaining[0] = hopperInsert(container, remaining[0], changed);
				for (int slot : changed) {
					WarehouseLedger.instance().updateSlot(subjectId, ref, slot, container.getItem(slot));
				}
				container.setChanged();
			}
		});
		data.setDirty();
		return remaining[0].isEmpty() ? ItemStack.EMPTY : remaining[0];
	}

	/**
	 * 掉落物直接按仓库列表顺序漏斗式入箱，不经过村民背包；仍放不下则掉在村民脚下。
	 */
	public static void depositLoot(ServerLevel level, Villager villager, UUID subjectId, List<ItemStack> drops) {
		if (level == null || drops == null || drops.isEmpty()) {
			return;
		}
		for (ItemStack drop : drops) {
			if (drop == null || drop.isEmpty()) {
				continue;
			}
			ItemStack remaining = drop.copy();
			if (subjectId != null) {
				remaining = deposit(level, subjectId, remaining);
			}
			if (!remaining.isEmpty() && villager != null) {
				villager.spawnAtLocation(level, remaining);
			}
		}
	}

	public static boolean tryConsume(ServerLevel level, UUID subjectId, MaterialCategory category) {
		if (level == null || subjectId == null || category == null || !category.isWarehouseCategory()) {
			return false;
		}
		return consumeFirst(level, subjectId, category, null);
	}

	/**
	 * 从分类中取出一件（宽松，同类可互换），返回取出的那件。
	 */
	public static ItemStack takeOne(ServerLevel level, UUID subjectId, MaterialCategory category) {
		if (level == null || subjectId == null || category == null || !category.isWarehouseCategory()) {
			return ItemStack.EMPTY;
		}
		return takeFirst(level, subjectId, category, null);
	}

	public static boolean tryConsume(ServerLevel level, UUID subjectId, Item item) {
		if (level == null || subjectId == null || item == null) {
			return false;
		}
		return consumeFirst(level, subjectId, null, item);
	}

	/**
	 * 建筑取料：合并大类时木头/木板/石头/泥沙/杂项宽松，种子与珍贵物精确；关闭合并时一律精确。
	 */
	public static boolean tryConsumeForBuild(ServerLevel level, UUID subjectId, Item material) {
		if (!RefugeeConfig.warehouseMergeCategories) {
			return tryConsume(level, subjectId, material);
		}
		MaterialCategory category = MaterialCategory.of(material);
		if (category == MaterialCategory.SEED) {
			return tryConsume(level, subjectId, material);
		}
		if (category.isWarehouseCategory()) {
			return tryConsume(level, subjectId, category);
		}
		return tryConsume(level, subjectId, material);
	}

	public static boolean hasForBuild(ServerLevel level, UUID subjectId, Item material) {
		if (level == null || subjectId == null || material == null) {
			return false;
		}
		if (!RefugeeConfig.warehouseMergeCategories) {
			return countExact(level.getServer(), subjectId, material) > 0;
		}
		MaterialCategory category = MaterialCategory.of(material);
		if (category == MaterialCategory.SEED) {
			return countExact(level.getServer(), subjectId, material) > 0;
		}
		if (category.isWarehouseCategory()) {
			return count(level.getServer(), subjectId, category) > 0;
		}
		return countExact(level.getServer(), subjectId, material) > 0;
	}

	public static int count(MinecraftServer server, UUID subjectId, MaterialCategory category) {
		return WarehouseLedger.instance().count(subjectId, category);
	}

	public static int countExact(MinecraftServer server, UUID subjectId, Item item) {
		return WarehouseLedger.instance().countExact(subjectId, item);
	}

	/**
	 * 加入箱子后整理：合并大类时按分类归堆；关闭合并时只把相同物品压叠。
	 */
	public static void organize(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null || !(level.getBlockEntity(pos) instanceof Container container)) {
			return;
		}
		runSilent(() -> {
			List<ItemStack> ordered;
			if (RefugeeConfig.warehouseMergeCategories) {
				ordered = organizeByCategory(container);
			} else {
				ordered = organizeExact(container);
			}
			int slot = 0;
			for (ItemStack stack : ordered) {
				if (slot >= container.getContainerSize()) {
					net.minecraft.world.Containers.dropItemStack(
							level,
							pos.getX() + 0.5,
							pos.getY() + 0.5,
							pos.getZ() + 0.5,
							stack
					);
					continue;
				}
				container.setItem(slot++, stack);
			}
			container.setChanged();
		});
		if (level.getServer() != null) {
			OrgLogisticsData data = OrgLogisticsData.get(level.getServer());
			data.setDirty();
			ContainerRef ref = new ContainerRef(level.dimension().location(), pos.immutable());
			for (UUID subjectId : data.subjectIds()) {
				if (data.hasWarehouse(subjectId, ref.dimension(), ref.pos())) {
					WarehouseLedger.instance().rebuildChest(subjectId, ref, container);
				}
			}
		}
	}

	/**
	 * 玩家开箱：仓库入仓/取料跳过该容器，避免和菜单抢槽。
	 */
	public static void onStartOpen(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null || !isTracked(level, pos)) {
			return;
		}
		ContainerRef ref = new ContainerRef(level.dimension().location(), pos.immutable());
		OPEN_COUNTS.merge(ref, 1, Integer::sum);
	}

	/**
	 * 最后一人关箱后整理并重建账本。
	 */
	public static void onStopOpen(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return;
		}
		ContainerRef ref = new ContainerRef(level.dimension().location(), pos.immutable());
		Integer current = OPEN_COUNTS.get(ref);
		if (current != null) {
			if (current <= 1) {
				OPEN_COUNTS.remove(ref);
			} else {
				OPEN_COUNTS.put(ref, current - 1);
				return;
			}
		}
		if (isTracked(level, pos)) {
			organize(level, pos);
		}
	}

	/**
	 * {@code LevelChunk#setBlockState} 之后：旧方块是容器且换成了另一种 Block 则注销该格。
	 * 同一 Block 只改属性（双箱对接、含水）保留名单；新方块仍是容器也不继承。
	 */
	public static void onBlockReplaced(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
		if (level == null || pos == null || oldState == null || newState == null) {
			return;
		}
		if (oldState.getBlock() == newState.getBlock()) {
			return;
		}
		if (!isContainerBlock(oldState, pos)) {
			return;
		}
		onContainerDestroyed(level, pos);
	}

	/**
	 * 容器被破坏（非区块卸载）：从存档名单、账本、强制加载一并剔除。
	 */
	public static void onContainerDestroyed(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null || level.getServer() == null) {
			return;
		}
		ResourceLocation dimension = level.dimension().location();
		OrgLogisticsData data = OrgLogisticsData.get(level.getServer());
		List<UUID> owners = new ArrayList<>();
		for (UUID subjectId : data.subjectIds()) {
			if (data.hasWarehouse(subjectId, dimension, pos)) {
				owners.add(subjectId);
			}
		}
		if (owners.isEmpty()) {
			OPEN_COUNTS.remove(new ContainerRef(dimension, pos.immutable()));
			return;
		}
		for (UUID subjectId : owners) {
			remove(level, subjectId, pos);
			StaffService.syncSubject(level.getServer(), subjectId);
		}
	}

	public static boolean isOccupied(ContainerRef ref) {
		return ref != null && OPEN_COUNTS.getOrDefault(ref, 0) > 0;
	}

	/**
	 * 玩家手改或原版漏斗写入后，对该箱重扫账本。开箱占用时不扫，关箱整理后再建。
	 */
	public static void onContainerChanged(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null || isSilent()) {
			return;
		}
		ContainerRef ref = new ContainerRef(level.dimension().location(), pos.immutable());
		if (isOccupied(ref)) {
			return;
		}
		Set<UUID> owners = WarehouseLedger.instance().ownersOf(ref);
		if (owners.isEmpty()) {
			return;
		}
		Container container = level.getBlockEntity(pos) instanceof Container c ? c : null;
		for (UUID subjectId : owners) {
			if (container == null) {
				WarehouseLedger.instance().removeChest(subjectId, ref);
			} else {
				WarehouseLedger.instance().rebuildChest(subjectId, ref, container);
			}
		}
	}

	private static List<ItemStack> organizeByCategory(Container container) {
		EnumMap<MaterialCategory, List<ItemStack>> buckets = new EnumMap<>(MaterialCategory.class);
		List<ItemStack> rest = new ArrayList<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			MaterialCategory category = MaterialCategory.of(stack);
			if (category.isWarehouseCategory()) {
				buckets.computeIfAbsent(category, key -> new ArrayList<>()).add(stack.copy());
			} else {
				rest.add(stack.copy());
			}
			container.setItem(i, ItemStack.EMPTY);
		}
		List<ItemStack> ordered = new ArrayList<>();
		for (MaterialCategory category : List.of(
				MaterialCategory.LOG,
				MaterialCategory.PLANKS,
				MaterialCategory.STONE,
				MaterialCategory.SOIL,
				MaterialCategory.SEED,
				MaterialCategory.MISC
		)) {
			ordered.addAll(compact(buckets.getOrDefault(category, List.of())));
		}
		ordered.addAll(compact(rest));
		return ordered;
	}

	private static List<ItemStack> organizeExact(Container container) {
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			stacks.add(stack.copy());
			container.setItem(i, ItemStack.EMPTY);
		}
		return compact(stacks);
	}

	private static List<ItemStack> compact(List<ItemStack> items) {
		List<ItemStack> result = new ArrayList<>();
		for (ItemStack in : items) {
			if (in == null || in.isEmpty()) {
				continue;
			}
			int left = in.getCount();
			for (ItemStack existing : result) {
				if (left <= 0) {
					break;
				}
				if (!ItemStack.isSameItemSameComponents(existing, in)) {
					continue;
				}
				int space = existing.getMaxStackSize() - existing.getCount();
				if (space <= 0) {
					continue;
				}
				int put = Math.min(space, left);
				existing.grow(put);
				left -= put;
			}
			if (left > 0) {
				result.add(in.copyWithCount(left));
			}
		}
		return result;
	}

	/**
	 * 原版漏斗入箱：从上往下第一个可合堆或空位。
	 */
	public static ItemStack hopperInsert(Container dest, ItemStack stack) {
		return hopperInsert(dest, stack, null);
	}

	private static ItemStack hopperInsert(Container dest, ItemStack stack, List<Integer> changedSlots) {
		if (dest == null || stack == null || stack.isEmpty()) {
			return stack == null ? ItemStack.EMPTY : stack;
		}
		ItemStack remaining = stack.copy();
		int[] slots;
		WorldlyContainer worldly = dest instanceof WorldlyContainer worldlyContainer ? worldlyContainer : null;
		if (worldly != null) {
			slots = worldly.getSlotsForFace(Direction.UP);
		} else {
			slots = new int[dest.getContainerSize()];
			for (int i = 0; i < slots.length; i++) {
				slots[i] = i;
			}
		}
		for (int slot : slots) {
			if (remaining.isEmpty()) {
				break;
			}
			int before = remaining.getCount();
			remaining = tryMoveInItem(dest, worldly, remaining, slot);
			if (remaining.getCount() != before && changedSlots != null && !changedSlots.contains(slot)) {
				changedSlots.add(slot);
			}
		}
		return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
	}

	private static ItemStack tryMoveInItem(Container dest, WorldlyContainer worldly, ItemStack stack, int slot) {
		if (!dest.canPlaceItem(slot, stack)) {
			return stack;
		}
		if (worldly != null && !worldly.canPlaceItemThroughFace(slot, stack, Direction.UP)) {
			return stack;
		}
		ItemStack existing = dest.getItem(slot);
		int max = Math.min(dest.getMaxStackSize(), stack.getMaxStackSize());
		if (existing.isEmpty()) {
			int put = Math.min(max, stack.getCount());
			dest.setItem(slot, stack.copyWithCount(put));
			return copyShrunk(stack, put);
		}
		if (!ItemStack.isSameItemSameComponents(existing, stack)) {
			return stack;
		}
		int space = Math.min(max, existing.getMaxStackSize()) - existing.getCount();
		if (space <= 0) {
			return stack;
		}
		int put = Math.min(space, stack.getCount());
		existing.grow(put);
		dest.setItem(slot, existing);
		return copyShrunk(stack, put);
	}

	private static ItemStack copyShrunk(ItemStack stack, int removed) {
		ItemStack remaining = stack.copy();
		remaining.shrink(removed);
		return remaining.getCount() <= 0 ? ItemStack.EMPTY : remaining;
	}

	private static boolean consumeFirst(ServerLevel level, UUID subjectId, MaterialCategory category, Item exact) {
		return !takeFirst(level, subjectId, category, exact).isEmpty();
	}

	private static ItemStack takeFirst(ServerLevel level, UUID subjectId, MaterialCategory category, Item exact) {
		OrgLogisticsData data = OrgLogisticsData.get(level.getServer());
		WarehouseLedger ledger = WarehouseLedger.instance();
		int guard = 0;
		while (guard++ < 256) {
			SlotLoc loc = category != null
					? ledger.first(subjectId, category, exact, WarehouseService::isOccupied)
					: ledger.firstExact(subjectId, exact, WarehouseService::isOccupied);
			if (loc == null) {
				return ItemStack.EMPTY;
			}
			Container container = containerAt(level.getServer(), loc.ref());
			if (container == null) {
				ledger.removeSlot(subjectId, loc.ref(), loc.slot());
				continue;
			}
			ItemStack stack = container.getItem(loc.slot());
			boolean match = !stack.isEmpty()
					&& (exact == null ? MaterialCategory.of(stack) == category : stack.is(exact));
			if (!match) {
				ledger.rebuildChest(subjectId, loc.ref(), container);
				continue;
			}
			ItemStack taken = stack.copyWithCount(1);
			runSilent(() -> {
				stack.shrink(1);
				container.setChanged();
			});
			ledger.updateSlot(subjectId, loc.ref(), loc.slot(), container.getItem(loc.slot()));
			data.setDirty();
			return taken;
		}
		return ItemStack.EMPTY;
	}

	private static Container containerAt(MinecraftServer server, ContainerRef ref) {
		if (server == null || ref == null) {
			return null;
		}
		ServerLevel level = levelOf(server, ref.dimension());
		if (level == null) {
			return null;
		}
		if (!level.isLoaded(ref.pos())) {
			level.getChunkAt(ref.pos());
		}
		BlockEntity entity = level.getBlockEntity(ref.pos());
		return entity instanceof Container container ? container : null;
	}

	private static ServerLevel levelOf(MinecraftServer server, ResourceLocation dimension) {
		if (server == null || dimension == null) {
			return null;
		}
		ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
		return server.getLevel(key);
	}

	private static void onServerStarted(MinecraftServer server) {
		clearForceState();
		WarehouseLedger.instance().clear();
		OPEN_COUNTS.clear();
		booted = true;
		applyAll(server);
	}

	private static void onServerStopped(MinecraftServer server) {
		booted = false;
		WarehouseLedger.instance().clear();
		OPEN_COUNTS.clear();
		clearForceState();
	}

	private static void onWorldLoad(ServerLevel level) {
		if (!booted || level == null || level.getServer() == null) {
			return;
		}
		applyDimension(level);
	}

	private static void applyAll(MinecraftServer server) {
		OrgLogisticsData data = OrgLogisticsData.get(server);
		for (UUID subjectId : data.subjectIds()) {
			for (ContainerRef ref : data.warehouses(subjectId)) {
				ServerLevel level = levelOf(server, ref.dimension());
				if (level == null) {
					continue;
				}
				retainChunk(level, ref);
				Container container = containerAt(server, ref);
				if (container != null) {
					WarehouseLedger.instance().rebuildChest(subjectId, ref, container);
				}
			}
		}
	}

	private static void applyDimension(ServerLevel level) {
		ResourceLocation dimension = level.dimension().location();
		OrgLogisticsData data = OrgLogisticsData.get(level.getServer());
		for (UUID subjectId : data.subjectIds()) {
			for (ContainerRef ref : data.warehouses(subjectId)) {
				if (!dimension.equals(ref.dimension())) {
					continue;
				}
				retainChunk(level, ref);
				Container container = containerAt(level.getServer(), ref);
				if (container != null) {
					WarehouseLedger.instance().rebuildChest(subjectId, ref, container);
				}
			}
		}
	}

	private static void retainChunk(ServerLevel level, ContainerRef ref) {
		if (level == null || ref == null) {
			return;
		}
		syncChunkForce(level, new ChunkPos(ref.pos()));
	}

	private static void releaseChunk(ServerLevel level, ContainerRef ref) {
		if (level == null || ref == null) {
			return;
		}
		syncChunkForce(level, new ChunkPos(ref.pos()));
	}

	/**
	 * 同一区块多箱只强制一次；该区块已无仓库箱则取消。按当前存档列表重算引用。
	 */
	private static void syncChunkForce(ServerLevel level, ChunkPos chunk) {
		ResourceLocation dimension = level.dimension().location();
		int n = 0;
		OrgLogisticsData data = OrgLogisticsData.get(level.getServer());
		for (UUID subjectId : data.subjectIds()) {
			for (ContainerRef ref : data.warehouses(subjectId)) {
				if (dimension.equals(ref.dimension()) && new ChunkPos(ref.pos()).equals(chunk)) {
					n++;
				}
			}
		}
		ChunkKey key = new ChunkKey(dimension, chunk.x, chunk.z);
		if (n > 0) {
			FORCED_CHUNKS.put(key, n);
			level.setChunkForced(chunk.x, chunk.z, true);
			level.getChunk(chunk.x, chunk.z);
		} else {
			FORCED_CHUNKS.remove(key);
			level.setChunkForced(chunk.x, chunk.z, false);
		}
	}

	private static void clearForceState() {
		FORCED_CHUNKS.clear();
	}

	private static void runSilent(Runnable action) {
		SILENT_RESCAN.set(SILENT_RESCAN.get() + 1);
		try {
			action.run();
		} finally {
			SILENT_RESCAN.set(SILENT_RESCAN.get() - 1);
		}
	}

	private static boolean isSilent() {
		return SILENT_RESCAN.get() > 0;
	}

	private static boolean isContainerBlock(BlockState state, BlockPos pos) {
		if (state == null || pos == null || !state.hasBlockEntity()) {
			return false;
		}
		if (!(state.getBlock() instanceof EntityBlock entityBlock)) {
			return false;
		}
		return entityBlock.newBlockEntity(pos, state) instanceof BaseContainerBlockEntity;
	}

	private static boolean isTracked(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null || level.getServer() == null) {
			return false;
		}
		ContainerRef ref = new ContainerRef(level.dimension().location(), pos.immutable());
		if (!WarehouseLedger.instance().ownersOf(ref).isEmpty()) {
			return true;
		}
		OrgLogisticsData data = OrgLogisticsData.get(level.getServer());
		for (UUID subjectId : data.subjectIds()) {
			if (data.hasWarehouse(subjectId, ref.dimension(), ref.pos())) {
				return true;
			}
		}
		return false;
	}

	private record ChunkKey(ResourceLocation dimension, int x, int z) {
	}
}
