package luowei.refugee.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import luowei.refugee.interact.RefugeeRoles;

/**
 * 守卫站岗、追击、去睡觉时开关木门。不占移动标记，好和别的目标一起跑。
 */
public class RefugeeDoorGoal extends Goal {
	private static final int HOLD_TICKS = 30;
	private static final double CLOSE_DISTANCE_SQR = 4.0;

	private final Villager villager;
	private BlockPos held;
	private long closeAt = -1L;

	public RefugeeDoorGoal(Villager villager) {
		this.villager = villager;
	}

	@Override
	public boolean canUse() {
		return active();
	}

	@Override
	public boolean canContinueToUse() {
		return active();
	}

	private boolean active() {
		return !villager.isBaby() && !villager.isSleeping() && RefugeeRoles.isGuard(villager);
	}

	@Override
	public void stop() {
		closeHeld();
	}

	@Override
	public void tick() {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		consider(level, villager.blockPosition());
		consider(level, villager.blockPosition().relative(villager.getDirection()));
		PathNavigation navigation = villager.getNavigation();
		Path path = navigation.getPath();
		if (path != null && !path.isDone()) {
			consider(level, path.getNextNodePos());
		}
		if (held != null && (level.getGameTime() >= closeAt || villager.distanceToSqr(Vec3.atCenterOf(held)) > CLOSE_DISTANCE_SQR)) {
			closeHeld();
		}
	}

	private void consider(ServerLevel level, BlockPos pos) {
		BlockPos doorPos = lower(level, pos);
		if (doorPos == null) {
			return;
		}
		BlockState state = level.getBlockState(doorPos);
		if (!(state.getBlock() instanceof DoorBlock door) || !door.type().canOpenByHand()) {
			return;
		}
		if (state.getValue(DoorBlock.OPEN)) {
			return;
		}
		door.setOpen(villager, level, state, doorPos, true);
		held = doorPos.immutable();
		closeAt = level.getGameTime() + HOLD_TICKS;
	}

	private void closeHeld() {
		if (held == null || !(villager.level() instanceof ServerLevel level)) {
			held = null;
			return;
		}
		BlockState state = level.getBlockState(held);
		if (state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand() && state.getValue(DoorBlock.OPEN)) {
			door.setOpen(villager, level, state, held, false);
		}
		held = null;
		closeAt = -1L;
	}

	private static BlockPos lower(ServerLevel level, BlockPos pos) {
		if (pos == null || !level.isLoaded(pos)) {
			return null;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof DoorBlock)) {
			BlockPos below = pos.below();
			if (!level.isLoaded(below)) {
				return null;
			}
			state = level.getBlockState(below);
			pos = below;
		}
		if (!(state.getBlock() instanceof DoorBlock)) {
			return null;
		}
		if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
			return pos.below();
		}
		return pos;
	}
}
