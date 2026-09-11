package luowei.refugee.block;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.settle.StandableFinder;
import luowei.refugee.spawn.RefugeeImmigration;

/**
 * 祭坛关箱结算：绿宝石与饥饿值各自按 32 折算人数后相加，只扣对应量。
 */
public final class AltarService {
	public static final int COST = 32;
	public static final int SIZE = 27;

	private AltarService() {
	}

	public static boolean isOffering(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return stack.is(Items.EMERALD) || foodNutrition(stack) > 0;
	}

	public static int foodNutrition(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		FoodProperties food = stack.get(DataComponents.FOOD);
		return food == null ? 0 : Math.max(0, food.nutrition());
	}

	public static void trySummon(ServerPlayer player, AltarBlockEntity altar) {
		if (player == null || altar == null || altar.isRemoved()) {
			return;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		int emeralds = countEmeralds(altar);
		int hunger = countHunger(altar);
		int fromEmeralds = emeralds / COST;
		int fromFood = hunger / COST;
		int wanted = fromEmeralds + fromFood;
		if (wanted <= 0) {
			return;
		}
		BlockPos origin = altar.getBlockPos().above();
		List<BlockPos> spots = StandableFinder.findStandable(level, origin, null, wanted);
		int canSpawn = spots.size();
		if (canSpawn <= 0) {
			player.displayClientMessage(Component.translatable("message.refugee.altar.no_standable"), true);
			return;
		}
		int actual = Math.min(wanted, canSpawn);
		int emeraldVillagers = Math.min(fromEmeralds, actual);
		int foodVillagers = actual - emeraldVillagers;
		consumeEmeralds(altar, emeraldVillagers * COST);
		consumeHunger(altar, foodVillagers * COST);
		altar.setChanged();
		UUID subjectId = PbsAdapter.resolveSubject(player);
		int spawned = 0;
		for (int i = 0; i < actual; i++) {
			if (RefugeeImmigration.spawnOwned(level, subjectId, spots.get(i)) != null) {
				spawned++;
			}
		}
		if (spawned <= 0) {
			player.displayClientMessage(Component.translatable("message.refugee.altar.failed"), true);
			return;
		}
		level.playSound(null, altar.getBlockPos(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 1.0F, 1.0F);
		player.displayClientMessage(Component.translatable("message.refugee.altar.summoned", spawned), false);
	}

	public static boolean isAltar(BlockEntity entity) {
		return entity instanceof AltarBlockEntity;
	}

	private static int countEmeralds(AltarBlockEntity altar) {
		int total = 0;
		for (int slot = 0; slot < altar.getContainerSize(); slot++) {
			ItemStack stack = altar.getItem(slot);
			if (stack.is(Items.EMERALD)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static int countHunger(AltarBlockEntity altar) {
		int total = 0;
		for (int slot = 0; slot < altar.getContainerSize(); slot++) {
			ItemStack stack = altar.getItem(slot);
			int nutrition = foodNutrition(stack);
			if (nutrition > 0) {
				total += nutrition * stack.getCount();
			}
		}
		return total;
	}

	private static void consumeEmeralds(AltarBlockEntity altar, int needed) {
		int remaining = needed;
		for (int slot = 0; slot < altar.getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = altar.getItem(slot);
			if (!stack.is(Items.EMERALD)) {
				continue;
			}
			int take = Math.min(remaining, stack.getCount());
			stack.shrink(take);
			remaining -= take;
			if (stack.isEmpty()) {
				altar.setItem(slot, ItemStack.EMPTY);
			} else {
				altar.setItem(slot, stack);
			}
		}
	}

	private static void consumeHunger(AltarBlockEntity altar, int needed) {
		int remaining = needed;
		for (int slot = 0; slot < altar.getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = altar.getItem(slot);
			int nutrition = foodNutrition(stack);
			if (nutrition <= 0) {
				continue;
			}
			while (!stack.isEmpty() && remaining > 0) {
				remaining -= nutrition;
				stack.shrink(1);
			}
			if (stack.isEmpty()) {
				altar.setItem(slot, ItemStack.EMPTY);
			} else {
				altar.setItem(slot, stack);
			}
		}
	}
}
