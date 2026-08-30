package luowei.refugee.staff;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * 指挥杖：ROOT 时右键打开扇形菜单（空气或方块）；仓库/工作区/建筑模式在 {@link StaffService} 中处理。
 */
public class CommandStaffItem extends Item {
	public CommandStaffItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		HitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
		if (player instanceof ServerPlayer serverPlayer) {
			StaffPage page = StaffService.page(serverPlayer);
			// 世界内点选模式瞄到方块：交给 handleBlock，不开轮盘
			if (page.isWorld() && hit.getType() == HitResult.Type.BLOCK) {
				return InteractionResult.PASS;
			}
			StaffService.onAirUse(serverPlayer);
			return InteractionResult.SUCCESS;
		}
		// 客户端瞄到方块时由 UseBlockCallback 消费，避免原版开箱/放置
		if (hit.getType() == HitResult.Type.BLOCK) {
			return InteractionResult.PASS;
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
		tooltip.accept(Component.translatable("item.refugee.command_staff.hint").withStyle(ChatFormatting.GRAY));
	}
}
