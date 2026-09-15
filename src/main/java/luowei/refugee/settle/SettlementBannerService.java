package luowei.refugee.settle;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import luowei.refugee.attachment.PlayerSelectionData;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.entity.ThrownSettlementBanner;
import luowei.refugee.item.ItemData;
import luowei.refugee.network.BannerStyleSavePayload;
import luowei.refugee.network.OpenBannerStylePayload;

/**
 * 安顿旗：蓄力投掷、样式编辑。
 */
public final class SettlementBannerService {
	private static final int THROW_THRESHOLD_TIME = 10;
	private static final float SHOOT_POWER = 2.5F;

	private SettlementBannerService() {
	}

	public static void openEditor(ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!ItemData.isSettlementBanner(stack)) {
			return;
		}
		ServerPlayNetworking.send(player, new OpenBannerStylePayload(hand, encodeHeld(stack, player)));
	}

	public static boolean throwBanner(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
		if (!ItemData.isSettlementBanner(stack) || !(entity instanceof Player player)) {
			return false;
		}
		int used = stack.getItem().getUseDuration(stack, entity) - timeLeft;
		if (used < THROW_THRESHOLD_TIME) {
			return false;
		}
		level.playSound(
				player,
				player.getX(),
				player.getY(),
				player.getZ(),
				SoundEvents.TRIDENT_THROW,
				SoundSource.PLAYERS,
				1.0F,
				1.0F
		);
		if (!level.isClientSide() && player instanceof ServerPlayer) {
			ThrownSettlementBanner thrown = new ThrownSettlementBanner(level, player, stack.copyWithCount(1));
			thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, SHOOT_POWER, 1.0F);
			level.addFreshEntity(thrown);
		}
		stack.shrink(1);
		return true;
	}

	public static void save(ServerPlayer player, BannerStyleSavePayload payload) {
		InteractionHand hand = payload.hand();
		ItemStack held = player.getItemInHand(hand);
		if (!ItemData.isSettlementBanner(held)) {
			return;
		}
		BannerStyle style;
		if (payload.copyOffhand()) {
			style = BannerStyle.fromBanner(player.getOffhandItem());
			if (style == null) {
				player.displayClientMessage(Component.translatable("message.refugee.banner.style.offhand"), true);
				return;
			}
		} else {
			style = BannerStyle.parse(player, payload.text(), baseOf(held));
			if (style == null) {
				player.displayClientMessage(Component.translatable("message.refugee.banner.style.invalid"), true);
				return;
			}
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		data.setBannerStyle(style.base(), style.patterns());
		RefugeeAttachments.markDirty(player, data);
		player.setItemInHand(hand, ItemData.createSettlementBanner(style.base(), style.patterns()));
		player.displayClientMessage(Component.translatable("message.refugee.banner.style.saved"), true);
	}

	private static String encodeHeld(ItemStack stack, ServerPlayer player) {
		BannerStyle fromItem = BannerStyle.fromBanner(stack);
		if (fromItem != null) {
			return fromItem.encode();
		}
		PlayerSelectionData data = RefugeeAttachments.get(player);
		return BannerStyle.encode(data.bannerBase(), data.bannerPatterns());
	}

	private static DyeColor baseOf(ItemStack stack) {
		if (stack.getItem() instanceof BannerItem banner) {
			return banner.getColor();
		}
		return DyeColor.WHITE;
	}
}
