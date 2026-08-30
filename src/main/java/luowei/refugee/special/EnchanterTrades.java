package luowei.refugee.special;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import net.minecraft.core.Holder;
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
}
