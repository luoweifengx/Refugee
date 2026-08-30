package luowei.refugee.attachment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 玩家侧选定状态、难民名册、延迟击杀与开局/失败标记。
 */
public final class PlayerSelectionData {
	public static final Codec<PlayerSelectionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.optionalFieldOf("container").forGetter(data -> Optional.ofNullable(data.containerPos)),
			BlockPos.CODEC.optionalFieldOf("build_origin").forGetter(data -> Optional.ofNullable(data.buildOrigin)),
			Codec.STRING.optionalFieldOf("structure_id", "").forGetter(data -> data.structureId == null ? "" : data.structureId.toString()),
			UUIDUtil.CODEC.listOf().optionalFieldOf("selected", List.of()).forGetter(data -> List.copyOf(data.selectedVillagers)),
			RosterEntry.CODEC.listOf().optionalFieldOf("roster", List.of()).forGetter(data -> List.copyOf(data.roster.values())),
			UUIDUtil.CODEC.listOf().optionalFieldOf("pending_kill", List.of()).forGetter(data -> List.copyOf(data.pendingKills)),
			Codec.BOOL.optionalFieldOf("starter_granted", false).forGetter(data -> data.starterGranted),
			Codec.BOOL.optionalFieldOf("defeated", false).forGetter(data -> data.defeated),
			UUIDUtil.CODEC.optionalFieldOf("guide").forGetter(data -> Optional.ofNullable(data.guideId)),
			UUIDUtil.CODEC.optionalFieldOf("nurse").forGetter(data -> Optional.ofNullable(data.nurseId)),
			UUIDUtil.CODEC.optionalFieldOf("cartographer").forGetter(data -> Optional.ofNullable(data.cartographerId)),
			UUIDUtil.CODEC.optionalFieldOf("enchanter").forGetter(data -> Optional.ofNullable(data.enchanterId)),
			Codec.BOOL.optionalFieldOf("had_lapis", false).forGetter(data -> data.hadLapis),
			Codec.BOOL.optionalFieldOf("nurse_granted", false).forGetter(data -> data.nurseGranted),
			Codec.BOOL.optionalFieldOf("cartographer_granted", false).forGetter(data -> data.cartographerGranted),
			Codec.BOOL.optionalFieldOf("enchanter_granted", false).forGetter(data -> data.enchanterGranted)
	).apply(instance, PlayerSelectionData::fromCodec));

	private BlockPos containerPos;
	private BlockPos buildOrigin;
	private ResourceLocation structureId;
	private final Set<UUID> selectedVillagers = new LinkedHashSet<>();
	private final Map<UUID, RosterEntry> roster = new LinkedHashMap<>();
	private final Set<UUID> pendingKills = new LinkedHashSet<>();
	private boolean starterGranted;
	private boolean defeated;
	private UUID guideId;
	private UUID nurseId;
	private UUID cartographerId;
	private UUID enchanterId;
	private boolean hadLapis;
	private boolean nurseGranted;
	private boolean cartographerGranted;
	private boolean enchanterGranted;

	public PlayerSelectionData() {
	}

	private static PlayerSelectionData fromCodec(
			Optional<BlockPos> container,
			Optional<BlockPos> origin,
			String structureId,
			List<UUID> selected,
			List<RosterEntry> roster,
			List<UUID> pendingKill,
			boolean starterGranted,
			boolean defeated,
			Optional<UUID> guide,
			Optional<UUID> nurse,
			Optional<UUID> cartographer,
			Optional<UUID> enchanter,
			boolean hadLapis,
			boolean nurseGranted,
			boolean cartographerGranted,
			boolean enchanterGranted
	) {
		PlayerSelectionData data = new PlayerSelectionData();
		data.containerPos = container.orElse(null);
		data.buildOrigin = origin.orElse(null);
		data.structureId = structureId == null || structureId.isBlank() ? null : ResourceLocation.tryParse(structureId);
		data.selectedVillagers.addAll(selected);
		for (RosterEntry entry : roster) {
			if (entry != null && entry.villagerId() != null) {
				data.roster.put(entry.villagerId(), entry);
			}
		}
		data.pendingKills.addAll(pendingKill);
		data.starterGranted = starterGranted;
		data.defeated = defeated;
		data.guideId = guide.orElse(null);
		data.nurseId = nurse.orElse(null);
		data.cartographerId = cartographer.orElse(null);
		data.enchanterId = enchanter.orElse(null);
		data.hadLapis = hadLapis;
		data.nurseGranted = nurseGranted;
		data.cartographerGranted = cartographerGranted;
		data.enchanterGranted = enchanterGranted;
		return data;
	}

	public BlockPos containerPos() {
		return containerPos;
	}

	public void setContainerPos(BlockPos containerPos) {
		this.containerPos = containerPos == null ? null : containerPos.immutable();
	}

	public BlockPos buildOrigin() {
		return buildOrigin;
	}

	public void setBuildOrigin(BlockPos buildOrigin) {
		this.buildOrigin = buildOrigin == null ? null : buildOrigin.immutable();
	}

	public ResourceLocation structureId() {
		return structureId;
	}

	public void setStructureId(ResourceLocation structureId) {
		this.structureId = structureId;
	}

	public Set<UUID> selectedVillagers() {
		return selectedVillagers;
	}

	public boolean isSelected(UUID villagerId) {
		return villagerId != null && selectedVillagers.contains(villagerId);
	}

	public boolean addSelected(UUID villagerId) {
		return villagerId != null && selectedVillagers.add(villagerId);
	}

	public boolean removeSelected(UUID villagerId) {
		return villagerId != null && selectedVillagers.remove(villagerId);
	}

	public void clearSelected() {
		selectedVillagers.clear();
	}

	public boolean isStarterGranted() {
		return starterGranted;
	}

	public void setStarterGranted(boolean starterGranted) {
		this.starterGranted = starterGranted;
	}

	public boolean isDefeated() {
		return defeated;
	}

	public void setDefeated(boolean defeated) {
		this.defeated = defeated;
	}

	public boolean isRosterEmpty() {
		return roster.isEmpty();
	}

	public int rosterSize() {
		return roster.size();
	}

	public boolean hasRoster(UUID villagerId) {
		return villagerId != null && roster.containsKey(villagerId);
	}

	public Collection<RosterEntry> rosterEntries() {
		return roster.values();
	}

	public List<UUID> snapshotRoster() {
		return new ArrayList<>(roster.keySet());
	}

	public boolean addRoster(UUID villagerId, ResourceLocation dimension, BlockPos pos) {
		if (villagerId == null) {
			return false;
		}
		RosterEntry existing = roster.get(villagerId);
		if (existing != null) {
			existing.setLocation(dimension, pos);
			return false;
		}
		roster.put(villagerId, new RosterEntry(villagerId, dimension, pos));
		return true;
	}

	public boolean addRoster(Entity entity) {
		if (entity == null) {
			return false;
		}
		Level level = entity.level();
		return addRoster(
				entity.getUUID(),
				level == null ? null : level.dimension().location(),
				entity.blockPosition()
		);
	}

	public boolean removeRoster(UUID villagerId) {
		return villagerId != null && roster.remove(villagerId) != null;
	}

	/**
	 * 取出并删除用于玩家死亡献祭的一名难民：先普通难民，再按护士→绘图师→附魔师→向导。
	 */
	public RosterEntry pollSacrificeRoster() {
		if (roster.isEmpty()) {
			return null;
		}
		Set<UUID> protectedIds = specialIds();
		for (UUID id : roster.keySet()) {
			if (!protectedIds.contains(id)) {
				return roster.remove(id);
			}
		}
		UUID[] specialOrder = { nurseId, cartographerId, enchanterId, guideId };
		for (UUID id : specialOrder) {
			if (id != null && roster.containsKey(id)) {
				return roster.remove(id);
			}
		}
		return pollFirstRoster();
	}

	/**
	 * 取出并删除最早登记的一名难民。
	 */
	public RosterEntry pollFirstRoster() {
		if (roster.isEmpty()) {
			return null;
		}
		UUID first = roster.keySet().iterator().next();
		return roster.remove(first);
	}

	public void updateRosterLocation(UUID villagerId, ResourceLocation dimension, BlockPos pos) {
		RosterEntry entry = roster.get(villagerId);
		if (entry != null) {
			entry.setLocation(dimension, pos);
		}
	}

	public Set<UUID> pendingKills() {
		return pendingKills;
	}

	public List<UUID> snapshotPendingKills() {
		return new ArrayList<>(pendingKills);
	}

	public boolean addPendingKill(UUID villagerId) {
		return villagerId != null && pendingKills.add(villagerId);
	}

	public boolean removePendingKill(UUID villagerId) {
		return villagerId != null && pendingKills.remove(villagerId);
	}

	public boolean hasPendingKill(UUID villagerId) {
		return villagerId != null && pendingKills.contains(villagerId);
	}

	/**
	 * 重置上一级选定：先清建造位置，再清容器。
	 *
	 * @return 是否重置了某一级
	 */
	public boolean resetLastStep() {
		if (buildOrigin != null) {
			buildOrigin = null;
			return true;
		}
		if (structureId != null) {
			structureId = null;
			return true;
		}
		if (containerPos != null) {
			containerPos = null;
			return true;
		}
		return false;
	}

	public List<UUID> snapshotSelected() {
		return new ArrayList<>(selectedVillagers);
	}

	public UUID guideId() {
		return guideId;
	}

	public UUID nurseId() {
		return nurseId;
	}

	public UUID cartographerId() {
		return cartographerId;
	}

	public UUID enchanterId() {
		return enchanterId;
	}

	public boolean hadLapis() {
		return hadLapis;
	}

	public void setHadLapis(boolean hadLapis) {
		this.hadLapis = hadLapis;
	}

	public boolean isNurseGranted() {
		return nurseGranted;
	}

	public boolean isCartographerGranted() {
		return cartographerGranted;
	}

	public boolean isEnchanterGranted() {
		return enchanterGranted;
	}

	public UUID specialId(RefugeeSpecialRole role) {
		if (role == null) {
			return null;
		}
		return switch (role) {
			case GUIDE -> guideId;
			case NURSE -> nurseId;
			case CARTOGRAPHER -> cartographerId;
			case ENCHANTER -> enchanterId;
		};
	}

	public void bindSpecial(RefugeeSpecialRole role, UUID villagerId) {
		if (role == null || villagerId == null) {
			return;
		}
		switch (role) {
			case GUIDE -> guideId = villagerId;
			case NURSE -> {
				nurseId = villagerId;
				nurseGranted = true;
			}
			case CARTOGRAPHER -> {
				cartographerId = villagerId;
				cartographerGranted = true;
			}
			case ENCHANTER -> {
				enchanterId = villagerId;
				enchanterGranted = true;
			}
		}
	}

	public boolean clearSpecialBinding(UUID villagerId) {
		if (villagerId == null) {
			return false;
		}
		boolean changed = false;
		if (villagerId.equals(guideId)) {
			guideId = null;
			changed = true;
		}
		if (villagerId.equals(nurseId)) {
			nurseId = null;
			changed = true;
		}
		if (villagerId.equals(cartographerId)) {
			cartographerId = null;
			changed = true;
		}
		if (villagerId.equals(enchanterId)) {
			enchanterId = null;
			changed = true;
		}
		return changed;
	}

	public Set<UUID> specialIds() {
		Set<UUID> ids = new LinkedHashSet<>();
		if (guideId != null) {
			ids.add(guideId);
		}
		if (nurseId != null) {
			ids.add(nurseId);
		}
		if (cartographerId != null) {
			ids.add(cartographerId);
		}
		if (enchanterId != null) {
			ids.add(enchanterId);
		}
		return ids;
	}

	public void absorbRoster(PlayerSelectionData other) {
		if (other == null) {
			return;
		}
		for (RosterEntry entry : other.roster.values()) {
			if (entry != null && entry.villagerId() != null) {
				addRoster(entry.villagerId(), entry.dimension(), entry.pos());
			}
		}
	}

	public void copySpecialBindingsFrom(PlayerSelectionData keeper) {
		if (keeper == null) {
			return;
		}
		guideId = keeper.guideId;
		nurseId = keeper.nurseId;
		cartographerId = keeper.cartographerId;
		enchanterId = keeper.enchanterId;
		nurseGranted = nurseGranted || keeper.nurseGranted || keeper.nurseId != null;
		cartographerGranted = cartographerGranted || keeper.cartographerGranted || keeper.cartographerId != null;
		enchanterGranted = enchanterGranted || keeper.enchanterGranted || keeper.enchanterId != null;
		hadLapis = hadLapis || keeper.hadLapis;
	}

	public int specialBindingCount() {
		int n = 0;
		if (guideId != null) {
			n++;
		}
		if (nurseId != null) {
			n++;
		}
		if (cartographerId != null) {
			n++;
		}
		if (enchanterId != null) {
			n++;
		}
		return n;
	}

	/**
	 * 名册条目：村民 UUID，以及便于日后定位的最后已知维度/坐标。
	 */
	public static final class RosterEntry {
		public static final Codec<RosterEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				UUIDUtil.CODEC.fieldOf("id").forGetter(RosterEntry::villagerId),
				ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(data -> Optional.ofNullable(data.dimension)),
				BlockPos.CODEC.optionalFieldOf("pos").forGetter(data -> Optional.ofNullable(data.pos))
		).apply(instance, RosterEntry::fromCodec));

		private final UUID villagerId;
		private ResourceLocation dimension;
		private BlockPos pos;

		public RosterEntry(UUID villagerId, ResourceLocation dimension, BlockPos pos) {
			this.villagerId = villagerId;
			this.dimension = dimension;
			this.pos = pos == null ? null : pos.immutable();
		}

		private static RosterEntry fromCodec(UUID id, Optional<ResourceLocation> dimension, Optional<BlockPos> pos) {
			return new RosterEntry(id, dimension.orElse(null), pos.orElse(null));
		}

		public UUID villagerId() {
			return villagerId;
		}

		public ResourceLocation dimension() {
			return dimension;
		}

		public BlockPos pos() {
			return pos;
		}

		public void setLocation(ResourceLocation dimension, BlockPos pos) {
			this.dimension = dimension;
			this.pos = pos == null ? null : pos.immutable();
		}
	}
}
