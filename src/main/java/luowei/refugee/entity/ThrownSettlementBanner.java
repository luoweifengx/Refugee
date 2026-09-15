package luowei.refugee.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.interact.SelectionService;
import luowei.refugee.settle.SettlementService;

/**
 * 安顿旗投射物：落地格作为安顿原点。
 */
public class ThrownSettlementBanner extends ThrowableItemProjectile {
	private boolean settled;

	public ThrownSettlementBanner(EntityType<? extends ThrownSettlementBanner> type, Level level) {
		super(type, level);
	}

	public ThrownSettlementBanner(Level level, LivingEntity shooter, ItemStack stack) {
		super(ModEntities.THROWN_SETTLEMENT_BANNER, shooter, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.WHITE_BANNER;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return false;
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		if (!level().isClientSide() && !settled) {
			settled = true;
			BlockPos land = hit.getBlockPos().relative(hit.getDirection());
			if (getOwner() instanceof ServerPlayer player) {
				SettlementService.settle(player, land);
			}
		}
		super.onHitBlock(hit);
		discard();
	}

	@Override
	public void onRemoval(RemovalReason reason) {
		super.onRemoval(reason);
		if (level().isClientSide() || settled) {
			return;
		}
		settled = true;
		if (getOwner() instanceof ServerPlayer player
				&& !RefugeeAttachments.get(player).selectedVillagers().isEmpty()) {
			SelectionService.giveBanner(player);
		}
	}
}
