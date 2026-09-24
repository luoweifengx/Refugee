package luowei.refugee.special;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;

/**
 * 附魔师自定义交易：每个游戏日刷新 3 本非宝藏附魔书。附魔台改为首次交谈赠予。
 */
public final class EnchanterTrades {
	private static final int BOOK_BASE_PRICE = 28;
	private static final int BOOK_PRICE_PER_LEVEL = 8;
	private static final int MAX_USES = 8;

	private EnchanterTrades() {
	}

	public static void open(ServerPlayer player, Villager villager) {
		if (player == null || villager == null || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		refreshIfNeeded(villager, level);
		applyOffers(villager);
		villager.setTradingPlayer(player);
		OptionalInt syncId = player.openMenu(new SimpleMenuProvider(
				(id, inventory, opener) -> new MerchantMenu(id, inventory, villager),
				Component.translatable("merchant.refugee.enchanter")
		));
		if (syncId.isPresent()) {
			player.sendMerchantOffers(syncId.getAsInt(), villager.getOffers(), 1, 0, false, false);
		}
	}

	public static void refreshIfNeeded(Villager villager, ServerLevel level) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		int day = currentDay(level);
		if (data.enchantDay() == day && data.enchantBooks().size() == 3) {
			return;
		}
		data.setEnchantDay(day);
		data.setEnchantBooks(rollBooks(level));
		RefugeeAttachments.markDirty(villager, data);
		applyOffers(villager);
	}

	private static int currentDay(ServerLevel level) {
		return (int) (level.getDayTime() / 24000L);
	}

	private static void applyOffers(Villager villager) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		MerchantOffers offers = new MerchantOffers();
		for (ItemStack book : data.enchantBooks()) {
			if (book == null || book.isEmpty()) {
				continue;
			}
			offers.add(emeraldOffer(book.copy(), bookPrice(book)));
		}
		for (MerchantOffer offer : offers) {
			offer.setSpecialPriceDiff(0);
		}
		villager.overrideOffers(offers);
	}

	private static MerchantOffer emeraldOffer(ItemStack result, int emeralds) {
		return new MerchantOffer(
				new ItemCost(Items.EMERALD, Math.max(1, Math.min(64, emeralds))),
				result,
				MAX_USES,
				0,
				0.0f
		);
	}

	private static int bookPrice(ItemStack book) {
		int level = 1;
		var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(book);
		for (Holder<Enchantment> holder : enchantments.keySet()) {
			level = Math.max(level, enchantments.getLevel(holder));
		}
		return Math.min(64, BOOK_BASE_PRICE + BOOK_PRICE_PER_LEVEL * level);
	}

	private static List<ItemStack> rollBooks(ServerLevel level) {
		List<Holder<Enchantment>> pool = new ArrayList<>();
		level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(holder -> {
			if (holder.is(EnchantmentTags.TREASURE) || holder.is(EnchantmentTags.CURSE)) {
				return;
			}
			pool.add(holder);
		});
		if (pool.isEmpty()) {
			level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(pool::add);
		}
		RandomSource random = level.random;
		List<ItemStack> books = new ArrayList<>();
		List<Holder<Enchantment>> remaining = new ArrayList<>(pool);
		for (int i = 0; i < 3 && !remaining.isEmpty(); i++) {
			Holder<Enchantment> chosen = remaining.remove(random.nextInt(remaining.size()));
			int maxLevel = Math.max(1, chosen.value().getMaxLevel());
			int enchantLevel = 1 + random.nextInt(maxLevel);
			books.add(EnchantmentHelper.createBook(new EnchantmentInstance(chosen, enchantLevel)));
		}
		return books;
	}

	public static void divine(ServerPlayer player, Villager villager) {
		if (player == null || villager == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos table = nearestTable(level, villager.blockPosition(), player.blockPosition());
		if (table == null) {
			player.displayClientMessage(Component.translatable("message.refugee.enchanter.no_table"), true);
			return;
		}
		int power = bookshelfPower(level, table);
		ItemStack book = new ItemStack(Items.BOOK);
		int slot = level.random.nextInt(3);
		int cost = EnchantmentHelper.getEnchantmentCost(level.random, slot, power, book);
		if (cost <= 0) {
			player.displayClientMessage(Component.translatable("message.refugee.enchanter.divine_fail"), true);
			return;
		}
		var lookup = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<EnchantmentInstance> list = EnchantmentHelper.selectEnchantment(
				level.random,
				book,
				cost,
				lookup.listElements().map(holder -> (Holder<Enchantment>) holder)
		);
		if (list == null || list.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.refugee.enchanter.divine_fail"), true);
			return;
		}
		ItemStack result = EnchantmentHelper.createBook(list.getFirst());
		if (!player.getInventory().add(result)) {
			player.drop(result, false);
		}
		player.containerMenu.broadcastChanges();
		player.displayClientMessage(Component.translatable("message.refugee.enchanter.divined"), false);
	}

	public static void giveBook(ServerPlayer player, net.minecraft.world.item.Item cost, int count) {
		if (player == null || cost == null || count <= 0) {
			return;
		}
		if (countItems(player, cost) < count) {
			player.displayClientMessage(Component.translatable("message.refugee.enchanter.book_need", count), true);
			return;
		}
		consumeItems(player, cost, count);
		ItemStack book = new ItemStack(Items.BOOK);
		if (!player.getInventory().add(book)) {
			player.drop(book, false);
		}
		player.containerMenu.broadcastChanges();
	}

	private static BlockPos nearestTable(ServerLevel level, BlockPos a, BlockPos b) {
		BlockPos origin = a == null ? b : a;
		if (origin == null) {
			return null;
		}
		BlockPos best = null;
		int bestDist = 32 * 32;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dy = -8; dy <= 8; dy++) {
			for (int dz = -16; dz <= 16; dz++) {
				for (int dx = -16; dx <= 16; dx++) {
					cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					if (!level.getBlockState(cursor).is(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE)) {
						continue;
					}
					int dist = dx * dx + dy * dy + dz * dz;
					if (dist < bestDist) {
						bestDist = dist;
						best = cursor.immutable();
					}
				}
			}
		}
		return best;
	}

	private static int bookshelfPower(ServerLevel level, BlockPos table) {
		int power = 0;
		for (int dy = 0; dy <= 1; dy++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dx = -2; dx <= 2; dx++) {
					if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
						continue;
					}
					if (level.getBlockState(table.offset(dx, dy, dz)).is(net.minecraft.world.level.block.Blocks.BOOKSHELF)) {
						power++;
					}
				}
			}
		}
		return Math.min(15, power);
	}

	private static int countItems(ServerPlayer player, net.minecraft.world.item.Item item) {
		int total = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void consumeItems(ServerPlayer player, net.minecraft.world.item.Item item, int needed) {
		int remaining = needed;
		for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.is(item)) {
				continue;
			}
			int take = Math.min(remaining, stack.getCount());
			stack.shrink(take);
			remaining -= take;
		}
	}
}
