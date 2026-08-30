package luowei.refugee.warehouse;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import luowei.refugee.logistics.OrgLogisticsData.ContainerRef;

/**
 * 组织仓库的内存槽位账本。不写存档；按 subject UUID 分账。
 */
public final class WarehouseLedger {
	private static final WarehouseLedger INSTANCE = new WarehouseLedger();

	private final Map<UUID, OrgIndex> orgs = new HashMap<>();
	private final Map<ContainerRef, Set<UUID>> owners = new HashMap<>();

	private WarehouseLedger() {
	}

	public static WarehouseLedger instance() {
		return INSTANCE;
	}

	public void clear() {
		orgs.clear();
		owners.clear();
	}

	public Set<UUID> ownersOf(ContainerRef ref) {
		if (ref == null) {
			return Set.of();
		}
		Set<UUID> set = owners.get(ref);
		return set == null || set.isEmpty() ? Set.of() : Set.copyOf(set);
	}

	public int count(UUID subjectId, MaterialCategory category) {
		if (subjectId == null || category == null || !category.isWarehouseCategory()) {
			return 0;
		}
		OrgIndex org = orgs.get(subjectId);
		if (org == null) {
			return 0;
		}
		int total = 0;
		for (SlotEntry entry : org.categories.get(category)) {
			total += entry.count;
		}
		return total;
	}

	public int countExact(UUID subjectId, Item item) {
		if (subjectId == null || item == null) {
			return 0;
		}
		OrgIndex org = orgs.get(subjectId);
		if (org == null) {
			return 0;
		}
		List<SlotEntry> list = org.exact.get(item);
		if (list == null) {
			return 0;
		}
		int total = 0;
		for (SlotEntry entry : list) {
			total += entry.count;
		}
		return total;
	}

	public SlotLoc first(UUID subjectId, MaterialCategory category) {
		return first(subjectId, category, null);
	}

	public SlotLoc first(UUID subjectId, MaterialCategory category, Item exact) {
		return first(subjectId, category, exact, null);
	}

	public SlotLoc first(UUID subjectId, MaterialCategory category, Item exact, Predicate<ContainerRef> exclude) {
		if (subjectId == null || category == null || !category.isWarehouseCategory()) {
			return null;
		}
		OrgIndex org = orgs.get(subjectId);
		if (org == null) {
			return null;
		}
		for (SlotEntry entry : org.categories.get(category)) {
			if (exclude != null && exclude.test(entry.ref)) {
				continue;
			}
			if (exact == null || entry.item == exact) {
				return entry.toLoc();
			}
		}
		return null;
	}

	public SlotLoc firstExact(UUID subjectId, Item item) {
		return firstExact(subjectId, item, null);
	}

	public SlotLoc firstExact(UUID subjectId, Item item, Predicate<ContainerRef> exclude) {
		if (subjectId == null || item == null) {
			return null;
		}
		OrgIndex org = orgs.get(subjectId);
		if (org == null) {
			return null;
		}
		List<SlotEntry> list = org.exact.get(item);
		if (list == null || list.isEmpty()) {
			return null;
		}
		for (SlotEntry entry : list) {
			if (exclude != null && exclude.test(entry.ref)) {
				continue;
			}
			return entry.toLoc();
		}
		return null;
	}

	/**
	 * 写入或清空某一格：同类只改数量并保持原顺序；换类则从旧表删、追加到新表末尾。
	 */
	public void updateSlot(UUID subjectId, ContainerRef ref, int slot, ItemStack stack) {
		if (subjectId == null || ref == null || slot < 0) {
			return;
		}
		OrgIndex org = orgOf(subjectId);
		owners.computeIfAbsent(ref, key -> new HashSet<>()).add(subjectId);
		Map<Integer, SlotEntry> chest = org.byChest.computeIfAbsent(ref, key -> new HashMap<>());
		SlotEntry existing = chest.get(slot);
		if (stack == null || stack.isEmpty()) {
			if (existing != null) {
				removeFromLists(org, existing);
				chest.remove(slot);
			}
			return;
		}
		MaterialCategory category = MaterialCategory.of(stack);
		Item item = stack.getItem();
		int count = stack.getCount();
		if (existing != null && existing.category == category && existing.item == item) {
			existing.count = count;
			return;
		}
		if (existing != null) {
			removeFromLists(org, existing);
		}
		SlotEntry entry = new SlotEntry(ref, slot, count, item, category);
		chest.put(slot, entry);
		addToLists(org, entry);
	}

	public void removeSlot(UUID subjectId, ContainerRef ref, int slot) {
		updateSlot(subjectId, ref, slot, ItemStack.EMPTY);
	}

	/**
	 * 整理或关箱后：删掉该箱旧条目，再按当前槽位重建。
	 */
	public void rebuildChest(UUID subjectId, ContainerRef ref, Container container) {
		if (subjectId == null || ref == null) {
			return;
		}
		removeChestEntries(subjectId, ref);
		owners.computeIfAbsent(ref, key -> new HashSet<>()).add(subjectId);
		if (container == null) {
			return;
		}
		WarehouseIndex.forEachOccupied(container, (slot, stack) -> updateSlot(subjectId, ref, slot, stack));
	}

	public void removeChest(UUID subjectId, ContainerRef ref) {
		if (subjectId == null || ref == null) {
			return;
		}
		removeChestEntries(subjectId, ref);
		Set<UUID> set = owners.get(ref);
		if (set != null) {
			set.remove(subjectId);
			if (set.isEmpty()) {
				owners.remove(ref);
			}
		}
	}

	private void removeChestEntries(UUID subjectId, ContainerRef ref) {
		OrgIndex org = orgs.get(subjectId);
		if (org == null) {
			return;
		}
		Map<Integer, SlotEntry> chest = org.byChest.remove(ref);
		if (chest == null) {
			return;
		}
		for (SlotEntry entry : chest.values()) {
			removeFromLists(org, entry);
		}
	}

	private OrgIndex orgOf(UUID subjectId) {
		return orgs.computeIfAbsent(subjectId, id -> new OrgIndex());
	}

	private static void addToLists(OrgIndex org, SlotEntry entry) {
		if (entry.category.isWarehouseCategory()) {
			org.categories.get(entry.category).add(entry);
		} else {
			org.exact.computeIfAbsent(entry.item, key -> new ArrayList<>()).add(entry);
		}
	}

	private static void removeFromLists(OrgIndex org, SlotEntry entry) {
		if (entry.category.isWarehouseCategory()) {
			org.categories.get(entry.category).remove(entry);
			return;
		}
		List<SlotEntry> list = org.exact.get(entry.item);
		if (list == null) {
			return;
		}
		list.remove(entry);
		if (list.isEmpty()) {
			org.exact.remove(entry.item);
		}
	}

	/**
	 * 账本里的一格：箱子 + 槽位 + 数量。
	 */
	public record SlotLoc(ContainerRef ref, int slot, int count) {
	}

	private static final class SlotEntry {
		private final ContainerRef ref;
		private final int slot;
		private int count;
		private final Item item;
		private final MaterialCategory category;

		private SlotEntry(ContainerRef ref, int slot, int count, Item item, MaterialCategory category) {
			this.ref = ref;
			this.slot = slot;
			this.count = count;
			this.item = item;
			this.category = category;
		}

		private SlotLoc toLoc() {
			return new SlotLoc(ref, slot, count);
		}
	}

	private static final class OrgIndex {
		private final EnumMap<MaterialCategory, List<SlotEntry>> categories = new EnumMap<>(MaterialCategory.class);
		private final Map<Item, List<SlotEntry>> exact = new HashMap<>();
		private final Map<ContainerRef, Map<Integer, SlotEntry>> byChest = new HashMap<>();

		private OrgIndex() {
			for (MaterialCategory category : MaterialCategory.values()) {
				if (category.isWarehouseCategory()) {
					categories.put(category, new ArrayList<>());
				}
			}
		}
	}
}
