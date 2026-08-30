package luowei.refugee.interact;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.special.RefugeeSpecialRole;
import luowei.refugee.special.SpecialRefugeeService;
import luowei.refugee.staff.CommandStaffItem;
import luowei.refugee.staff.StaffPage;
import luowei.refugee.staff.StaffService;
import luowei.refugee.talk.RefugeeBubble;

/**
 * 玩家对村民 / 方块 / 号角的交互。PBS 没有旗帜、号角、钟钩子，全部在此实现。
 */
public final class RefugeeInteractions {
	private RefugeeInteractions() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hit != null) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			ItemStack held = serverPlayer.getItemInHand(hand);
			if (isGoldenApple(held) && (entity instanceof Villager || entity instanceof ZombieVillager)) {
				return InteractionResult.FAIL;
			}
			if (!(entity instanceof Villager villager)) {
				return InteractionResult.PASS;
			}
			if (level.isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			if (serverPlayer.isShiftKeyDown()) {
				if (held.isEmpty() && RefugeeSpecialRole.isSpecial(villager)) {
					boolean toggled = SelectionService.toggleFollow(serverPlayer, villager);
					if (toggled) {
						RefugeeBubble.onSelect(villager);
					}
					return InteractionResult.SUCCESS;
				}
				return EquipmentService.handleShiftUse(serverPlayer, villager, hand)
						? InteractionResult.SUCCESS
						: InteractionResult.PASS;
			}
			if (SpecialRefugeeService.handleInteract(serverPlayer, villager, held)) {
				return InteractionResult.SUCCESS;
			}
			if (!held.isEmpty()) {
				return InteractionResult.PASS;
			}
			boolean toggled = SelectionService.toggleFollow(serverPlayer, villager);
			if (toggled) {
				RefugeeBubble.onSelect(villager);
			}
			return InteractionResult.SUCCESS;
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer serverPlayer) || level.isClientSide()) {
				return InteractionResult.PASS;
			}
			ItemStack held = serverPlayer.getItemInHand(hand);
			// 方块交互可能先于 Item.use：NONE+持杖时在此打开轮盘
			if (held.getItem() instanceof CommandStaffItem && StaffService.page(serverPlayer) == StaffPage.ROOT) {
				StaffService.onAirUse(serverPlayer);
				return InteractionResult.SUCCESS;
			}
			if (StaffService.handleBlock(serverPlayer, hit.getBlockPos())) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer serverPlayer) || level.isClientSide()) {
				return InteractionResult.PASS;
			}
			ItemStack held = serverPlayer.getItemInHand(hand);
			if (held.is(Items.GOAT_HORN) || held.getItem() instanceof InstrumentItem) {
				SelectionService.selectAround(serverPlayer, RefugeeConfig.hornBellRadius);
			}
			return InteractionResult.PASS;
		});
	}

	private static boolean isGoldenApple(ItemStack stack) {
		return stack != null && (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE));
	}
}
