package luowei.refugee.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import luowei.refugee.interact.RefugeeRoles;

/**
 * 可堆叠制式套装：右键拆除为全套盔甲与配剑；潜行交给居民后装备。
 */
public class ArmorKitItem extends Item {
	public enum Kind {
		LEATHER,
		CHAIN,
		IRON,
		DIAMOND
	}

	private final Kind kind;

	public ArmorKitItem(Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	public static boolean isKit(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.getItem() instanceof ArmorKitItem;
	}

	public static boolean give(Player player, Villager villager, ItemStack held) {
		if (!(held.getItem() instanceof ArmorKitItem kit) || player == null || villager == null) {
			return false;
		}
		ItemStack[] pieces = kit.pieces();
		EquipmentSlot[] slots = {
				EquipmentSlot.HEAD,
				EquipmentSlot.CHEST,
				EquipmentSlot.LEGS,
				EquipmentSlot.FEET
		};
		for (int i = 0; i < slots.length; i++) {
			giveBack(player, villager.getItemBySlot(slots[i]));
			villager.setItemSlot(slots[i], pieces[i]);
		}
		giveBack(player, RefugeeRoles.logicalMainHand(villager));
		RefugeeRoles.setLogicalMainHand(villager, kit.sword());
		held.shrink(1);
		return true;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (!(held.getItem() instanceof ArmorKitItem kit)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		for (ItemStack piece : kit.contents()) {
			giveBack(player, piece);
		}
		if (!player.hasInfiniteMaterials()) {
			held.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(
			ItemStack stack,
			Item.TooltipContext context,
			TooltipDisplay display,
			Consumer<Component> tooltip,
			TooltipFlag flag
	) {
		tooltip.accept(Component.translatable("item.refugee.armor_kit.hint").withStyle(ChatFormatting.GRAY));
	}

	private ItemStack[] contents() {
		ItemStack[] armor = pieces();
		return new ItemStack[] { armor[0], armor[1], armor[2], armor[3], sword() };
	}

	private ItemStack sword() {
		return switch (kind) {
			case LEATHER -> new ItemStack(Items.WOODEN_SWORD);
			case CHAIN -> new ItemStack(Items.STONE_SWORD);
			case IRON -> new ItemStack(Items.IRON_SWORD);
			case DIAMOND -> new ItemStack(Items.DIAMOND_SWORD);
		};
	}

	private ItemStack[] pieces() {
		return switch (kind) {
			case LEATHER -> new ItemStack[] {
					new ItemStack(Items.LEATHER_HELMET),
					new ItemStack(Items.LEATHER_CHESTPLATE),
					new ItemStack(Items.LEATHER_LEGGINGS),
					new ItemStack(Items.LEATHER_BOOTS)
			};
			case CHAIN -> new ItemStack[] {
					new ItemStack(Items.CHAINMAIL_HELMET),
					new ItemStack(Items.CHAINMAIL_CHESTPLATE),
					new ItemStack(Items.CHAINMAIL_LEGGINGS),
					new ItemStack(Items.CHAINMAIL_BOOTS)
			};
			case IRON -> new ItemStack[] {
					new ItemStack(Items.IRON_HELMET),
					new ItemStack(Items.IRON_CHESTPLATE),
					new ItemStack(Items.IRON_LEGGINGS),
					new ItemStack(Items.IRON_BOOTS)
			};
			case DIAMOND -> new ItemStack[] {
					new ItemStack(Items.DIAMOND_HELMET),
					new ItemStack(Items.DIAMOND_CHESTPLATE),
					new ItemStack(Items.DIAMOND_LEGGINGS),
					new ItemStack(Items.DIAMOND_BOOTS)
			};
		};
	}

	private static void giveBack(Player player, ItemStack previous) {
		if (previous == null || previous.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(previous.copy())) {
			player.drop(previous.copy(), false);
		}
	}
}
