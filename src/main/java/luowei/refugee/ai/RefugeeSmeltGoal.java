package luowei.refugee.ai;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.logistics.OrgLogisticsData;
import luowei.refugee.logistics.OrgLogisticsData.ContainerRef;
import luowei.refugee.warehouse.WarehouseService;

/**
 * 熔炼：轮流照料已标记熔炉。输入槽空时才从熔炼仓整组补矿，燃料槽空时才整组补燃料
 * （先熔炼仓的煤，再物块仓的岩浆桶等），产出和空桶送回熔炼仓。
 * 一座炉装完即换下一座空输入炉，避免整组矿堆在同一座里。
 * 距离由 {@link RefugeeConfig#workReachLimit} 控制，与挖/放相同。
 */
public class RefugeeSmeltGoal extends Goal {
	private static final int SLOT_INPUT = 0;
	private static final int SLOT_FUEL = 1;
	private static final int SLOT_RESULT = 2;
	private static final Map<FurnaceKey, UUID> CLAIMS = new ConcurrentHashMap<>();

	private final Villager villager;
	private BlockPos furnacePos;

	public RefugeeSmeltGoal(Villager villager) {
		this.villager = villager;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (villager.isBaby() || !RefugeeRoles.isBuilder(villager)) {
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (!data.isSmelterDuty() || data.isFollowing() || data.isFollowingEntity() || data.isPatrolling()) {
			return false;
		}
		return !RefugeeCombat.isEating(villager);
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void stop() {
		releaseClaim();
		furnacePos = null;
	}

	@Override
	public void tick() {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		UUID subjectId = data.subjectId();
		if (subjectId == null) {
			releaseClaim();
			furnacePos = null;
			return;
		}
		AbstractFurnaceBlockEntity furnace = resolveFurnace(level, subjectId);
		if (furnace == null) {
			return;
		}
		BlockPos pos = furnace.getBlockPos();
		villager.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		WorkMove.moveToward(villager, pos);
		if (RefugeeConfig.workReachLimit && !WorkMove.inReach(villager, pos)) {
			return;
		}
		if (operate(level, subjectId, furnace)) {
			villager.swing(InteractionHand.MAIN_HAND);
		}
		if (!needsService(level, subjectId, furnace)) {
			releaseClaim();
			furnacePos = null;
		}
	}

	private AbstractFurnaceBlockEntity resolveFurnace(ServerLevel level, UUID subjectId) {
		if (furnacePos != null) {
			AbstractFurnaceBlockEntity current = furnaceAt(level, furnacePos);
			if (current != null
					&& ownsClaim(level, furnacePos)
					&& stillListed(level, subjectId, furnacePos)
					&& needsService(level, subjectId, current)) {
				CLAIMS.put(key(level, furnacePos), villager.getUUID());
				return current;
			}
			releaseClaim();
			furnacePos = null;
		}
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		ResourceLocation dimension = level.dimension().location();
		for (ContainerRef ref : OrgLogisticsData.get(level.getServer()).smelters(subjectId)) {
			if (!dimension.equals(ref.dimension())) {
				continue;
			}
			AbstractFurnaceBlockEntity furnace = furnaceAt(level, ref.pos());
			if (furnace == null || !needsService(level, subjectId, furnace)) {
				continue;
			}
			UUID owner = CLAIMS.get(key(level, ref.pos()));
			if (owner != null && !owner.equals(villager.getUUID())) {
				continue;
			}
			double dist = villager.distanceToSqr(ref.pos().getX() + 0.5, ref.pos().getY() + 0.5, ref.pos().getZ() + 0.5);
			if (dist < bestDist) {
				bestDist = dist;
				best = ref.pos().immutable();
			}
		}
		if (best == null) {
			return null;
		}
		furnacePos = best;
		CLAIMS.put(key(level, best), villager.getUUID());
		return furnaceAt(level, best);
	}

	/** 产出待取、燃料槽空桶、输入槽空且熔炼仓有矿、或燃料槽空且仓里有燃料。 */
	private boolean needsService(ServerLevel level, UUID subjectId, AbstractFurnaceBlockEntity furnace) {
		ItemStack result = furnace.getItem(SLOT_RESULT);
		if (!result.isEmpty()) {
			return true;
		}
		ItemStack fuel = furnace.getItem(SLOT_FUEL);
		if (fuel.is(Items.BUCKET)) {
			return true;
		}
		ItemStack input = furnace.getItem(SLOT_INPUT);
		if (input.isEmpty()
				&& findSmeltStack(level, subjectId, stack -> canInsertOre(level, furnace, input, stack)) != null) {
			return true;
		}
		return fuel.isEmpty()
				&& findFuelStack(level, subjectId, stack -> canInsertFuel(furnace, fuel, stack)) != null;
	}

	private boolean operate(ServerLevel level, UUID subjectId, AbstractFurnaceBlockEntity furnace) {
		boolean changed = extractSlot(level, subjectId, furnace, SLOT_RESULT, stack -> true);
		changed |= extractSlot(level, subjectId, furnace, SLOT_FUEL, stack -> stack.is(Items.BUCKET));
		changed |= fillInput(level, subjectId, furnace);
		changed |= fillFuel(level, subjectId, furnace);
		if (changed) {
			furnace.setChanged();
		}
		return changed;
	}

	private static boolean extractSlot(
			ServerLevel level,
			UUID subjectId,
			AbstractFurnaceBlockEntity furnace,
			int slot,
			Predicate<ItemStack> match
	) {
		ItemStack stack = furnace.getItem(slot);
		if (stack.isEmpty() || !match.test(stack)) {
			return false;
		}
		ItemStack leftover = WarehouseService.depositSmeltResult(level, subjectId, stack.copy());
		if (leftover.getCount() == stack.getCount()) {
			return false;
		}
		furnace.setItem(slot, leftover);
		return true;
	}

	private boolean fillInput(ServerLevel level, UUID subjectId, AbstractFurnaceBlockEntity furnace) {
		ItemStack existing = furnace.getItem(SLOT_INPUT);
		if (!existing.isEmpty()) {
			return false;
		}
		SlotPick pick = findSmeltStack(level, subjectId, stack -> canInsertOre(level, furnace, existing, stack));
		if (pick == null) {
			return false;
		}
		ItemStack taken = WarehouseService.takeStackAt(level, subjectId, pick.ref(), pick.slot());
		if (taken.isEmpty()) {
			return false;
		}
		ItemStack leftover = insert(furnace, SLOT_INPUT, taken);
		if (!leftover.isEmpty()) {
			returnLeftover(level, subjectId, pick.ref(), leftover);
		}
		return leftover.getCount() != taken.getCount();
	}

	private boolean fillFuel(ServerLevel level, UUID subjectId, AbstractFurnaceBlockEntity furnace) {
		ItemStack existing = furnace.getItem(SLOT_FUEL);
		if (!existing.isEmpty()) {
			return false;
		}
		SlotPick pick = findFuelStack(level, subjectId, stack -> canInsertFuel(furnace, existing, stack));
		if (pick == null) {
			return false;
		}
		ItemStack taken = WarehouseService.takeStackAt(level, subjectId, pick.ref(), pick.slot());
		if (taken.isEmpty()) {
			return false;
		}
		ItemStack leftover = insert(furnace, SLOT_FUEL, taken);
		if (!leftover.isEmpty()) {
			returnLeftover(level, subjectId, pick.ref(), leftover);
		}
		return leftover.getCount() != taken.getCount();
	}

	private static SlotPick findSmeltStack(ServerLevel level, UUID subjectId, Predicate<ItemStack> match) {
		return findStack(level, subjectId, match, WarehouseService::forEachSmeltResultSlot);
	}

	private static SlotPick findFuelStack(ServerLevel level, UUID subjectId, Predicate<ItemStack> match) {
		SlotPick smelt = findSmeltStack(level, subjectId, match);
		if (smelt != null) {
			return smelt;
		}
		return findStack(level, subjectId, match, WarehouseService::forEachBlockSlot);
	}

	private static SlotPick findStack(
			ServerLevel level,
			UUID subjectId,
			Predicate<ItemStack> match,
			SlotScanner scanner
	) {
		SlotPick[] found = { null };
		scanner.scan(level, subjectId, (ref, slot, stack) -> {
			if (stack.isEmpty() || !match.test(stack)) {
				return true;
			}
			found[0] = new SlotPick(ref, slot);
			return false;
		});
		return found[0];
	}

	private static void returnLeftover(ServerLevel level, UUID subjectId, ContainerRef ref, ItemStack leftover) {
		if (leftover == null || leftover.isEmpty() || ref == null) {
			return;
		}
		if (OrgLogisticsData.get(level.getServer()).hasSmeltResult(subjectId, ref.dimension(), ref.pos())) {
			WarehouseService.depositSmeltResult(level, subjectId, leftover);
			return;
		}
		WarehouseService.deposit(level, subjectId, leftover);
	}

	private static ItemStack insert(AbstractFurnaceBlockEntity furnace, int slot, ItemStack incoming) {
		if (incoming == null || incoming.isEmpty() || !furnace.canPlaceItem(slot, incoming)) {
			return incoming == null ? ItemStack.EMPTY : incoming;
		}
		ItemStack existing = furnace.getItem(slot);
		if (existing.isEmpty()) {
			furnace.setItem(slot, incoming.copy());
			return ItemStack.EMPTY;
		}
		if (!ItemStack.isSameItemSameComponents(existing, incoming)) {
			return incoming;
		}
		int space = Math.min(existing.getMaxStackSize(), furnace.getMaxStackSize()) - existing.getCount();
		if (space <= 0) {
			return incoming;
		}
		int put = Math.min(space, incoming.getCount());
		existing.grow(put);
		furnace.setItem(slot, existing);
		ItemStack leftover = incoming.copy();
		leftover.shrink(put);
		return leftover.isEmpty() ? ItemStack.EMPTY : leftover;
	}

	private static boolean canInsertOre(
			ServerLevel level,
			AbstractFurnaceBlockEntity furnace,
			ItemStack existing,
			ItemStack candidate
	) {
		if (!isOreLike(candidate) || !hasFurnaceRecipe(level, furnace, candidate) || !furnace.canPlaceItem(SLOT_INPUT, candidate)) {
			return false;
		}
		return existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, candidate);
	}

	private static boolean canInsertFuel(AbstractFurnaceBlockEntity furnace, ItemStack existing, ItemStack candidate) {
		if (!isSmeltFuel(candidate) || !furnace.canPlaceItem(SLOT_FUEL, candidate)) {
			return false;
		}
		return existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, candidate);
	}

	private static boolean hasFurnaceRecipe(ServerLevel level, AbstractFurnaceBlockEntity furnace, ItemStack stack) {
		SingleRecipeInput input = new SingleRecipeInput(stack);
		if (furnace instanceof BlastFurnaceBlockEntity) {
			return level.recipeAccess().getRecipeFor(RecipeType.BLASTING, input, level).isPresent();
		}
		return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level).isPresent();
	}

	private static boolean isOreLike(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		Item item = stack.getItem();
		if (item == Items.ANCIENT_DEBRIS
				|| item == Items.RAW_IRON
				|| item == Items.RAW_GOLD
				|| item == Items.RAW_COPPER
				|| item == Items.NETHER_QUARTZ_ORE) {
			return true;
		}
		if (stack.is(ItemTags.COAL_ORES)
				|| stack.is(ItemTags.COPPER_ORES)
				|| stack.is(ItemTags.DIAMOND_ORES)
				|| stack.is(ItemTags.EMERALD_ORES)
				|| stack.is(ItemTags.GOLD_ORES)
				|| stack.is(ItemTags.IRON_ORES)
				|| stack.is(ItemTags.LAPIS_ORES)
				|| stack.is(ItemTags.REDSTONE_ORES)) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();
		return path.endsWith("_ore") || path.contains("_ore") || path.startsWith("raw_");
	}

	private static boolean isSmeltFuel(ItemStack stack) {
		return stack.is(Items.COAL) || stack.is(Items.CHARCOAL) || stack.is(Items.LAVA_BUCKET);
	}

	public static boolean isMarkableFurnace(BlockEntity entity) {
		return entity instanceof FurnaceBlockEntity || entity instanceof BlastFurnaceBlockEntity;
	}

	private static AbstractFurnaceBlockEntity furnaceAt(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null || !level.isLoaded(pos)) {
			return null;
		}
		BlockEntity entity = level.getBlockEntity(pos);
		if (entity instanceof SmokerBlockEntity) {
			return null;
		}
		return entity instanceof AbstractFurnaceBlockEntity furnace ? furnace : null;
	}

	private boolean stillListed(ServerLevel level, UUID subjectId, BlockPos pos) {
		return OrgLogisticsData.get(level.getServer()).hasSmelter(subjectId, level.dimension().location(), pos);
	}

	private boolean ownsClaim(ServerLevel level, BlockPos pos) {
		UUID owner = CLAIMS.get(key(level, pos));
		return owner == null || owner.equals(villager.getUUID());
	}

	private void releaseClaim() {
		if (!(villager.level() instanceof ServerLevel level) || furnacePos == null) {
			CLAIMS.entrySet().removeIf(entry -> villager.getUUID().equals(entry.getValue()));
			return;
		}
		CLAIMS.remove(key(level, furnacePos), villager.getUUID());
	}

	private static FurnaceKey key(ServerLevel level, BlockPos pos) {
		return new FurnaceKey(level.dimension().location(), pos.immutable());
	}

	private record FurnaceKey(ResourceLocation dimension, BlockPos pos) {
	}

	private record SlotPick(ContainerRef ref, int slot) {
	}

	@FunctionalInterface
	private interface SlotScanner {
		void scan(ServerLevel level, UUID subjectId, WarehouseService.BlockSlotVisitor visitor);
	}
}
