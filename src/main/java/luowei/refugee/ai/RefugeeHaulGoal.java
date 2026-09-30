package luowei.refugee.ai;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.logistics.OrgLogisticsData.ContainerRef;

/**
 * 走到 128 格内能收背包物品的容器旁，5 格内打开并放入。
 * 15 秒内没有靠近 5 格就换下一只。都试过或范围内没有，就进入等待。
 */
public class RefugeeHaulGoal extends Goal {
	private static final double OPEN_SQ = WorkerCargo.OPEN_DISTANCE * WorkerCargo.OPEN_DISTANCE;

	private final Villager villager;
	private final Set<ContainerRef> tried = new HashSet<>();
	private ContainerRef target;
	private double baselineDist = -1.0;
	private long baselineTick;
	private BlockPos opened;
	private boolean deposited;
	private int openHold;
	private long lastPathTick;

	public RefugeeHaulGoal(Villager villager) {
		this.villager = villager;
		this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return WorkerCargo.wantsHaul(villager);
	}

	@Override
	public boolean canContinueToUse() {
		return WorkerCargo.continueHaul(villager);
	}

	@Override
	public void start() {
		tried.clear();
		target = null;
		baselineDist = -1.0;
		opened = null;
		deposited = false;
		openHold = 0;
	}

	@Override
	public void stop() {
		closeOpen();
		villager.getNavigation().stop();
		target = null;
		tried.clear();
	}

	@Override
	public void tick() {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		if (!WorkerCargo.hasDepositable(villager)) {
			WorkerCargo.clearHaulFlags(villager);
			return;
		}
		if (!(deposited && openHold > 0)
				&& (target == null || tried.contains(target) || !WorkerCargo.accepts(villager, level, target))) {
			target = next(level);
			if (target == null) {
				WorkerCargo.enterWait(villager);
				return;
			}
			baselineDist = distance(target.pos());
			baselineTick = level.getGameTime();
			lastPathTick = 0L;
			deposited = false;
			openHold = 0;
			closeOpen();
		}
		BlockPos pos = target.pos();
		villager.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		double dist = distance(pos);
		if (dist > WorkerCargo.OPEN_DISTANCE) {
			if (level.getGameTime() - baselineTick >= WorkerCargo.STALL_TICKS
					&& baselineDist - dist < WorkerCargo.STALL_CLOSER) {
				tried.add(target);
				target = null;
				return;
			}
			if (baselineDist - dist >= WorkerCargo.STALL_CLOSER) {
				baselineDist = dist;
				baselineTick = level.getGameTime();
			}
			var nav = villager.getNavigation();
			if (nav.isDone() && level.getGameTime() - lastPathTick >= 20L) {
				nav.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RefugeeConfig.builderWalkSpeed);
				lastPathTick = level.getGameTime();
			}
			return;
		}
		open(level, pos);
		if (!deposited) {
			WorkerCargo.depositMatching(villager, level, target);
			deposited = true;
			openHold = 20;
		}
		if (openHold > 0) {
			openHold--;
			return;
		}
		tried.add(target);
		target = null;
		closeOpen();
	}

	private ContainerRef next(ServerLevel level) {
		List<ContainerRef> candidates = WorkerCargo.candidates(villager);
		ContainerRef best = null;
		double bestDist = Double.MAX_VALUE;
		for (ContainerRef ref : candidates) {
			if (tried.contains(ref) || !WorkerCargo.accepts(villager, level, ref)) {
				continue;
			}
			double dist = distance(ref.pos());
			if (dist < bestDist) {
				bestDist = dist;
				best = ref;
			}
		}
		return best;
	}

	private double distance(BlockPos pos) {
		return Math.sqrt(villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
	}

	private void open(ServerLevel level, BlockPos pos) {
		if (pos.equals(opened)) {
			return;
		}
		closeOpen();
		setLid(level, pos, true);
		opened = pos.immutable();
	}

	private void closeOpen() {
		if (opened == null || !(villager.level() instanceof ServerLevel level)) {
			opened = null;
			return;
		}
		setLid(level, opened, false);
		opened = null;
	}

	private static void setLid(ServerLevel level, BlockPos pos, boolean open) {
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		if (block instanceof ChestBlock) {
			level.blockEvent(pos, block, 1, open ? 1 : 0);
			play(level, pos, open, true, false);
			if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
				Direction facing = ChestBlock.getConnectedDirection(state);
				BlockPos other = pos.relative(facing);
				BlockState otherState = level.getBlockState(other);
				if (otherState.getBlock() instanceof ChestBlock) {
					level.blockEvent(other, otherState.getBlock(), 1, open ? 1 : 0);
				}
			}
			return;
		}
		if (block instanceof BarrelBlock && state.hasProperty(BarrelBlock.OPEN)) {
			level.setBlock(pos, state.setValue(BarrelBlock.OPEN, open), 3);
			play(level, pos, open, false, false);
			return;
		}
		if (block instanceof ShulkerBoxBlock) {
			level.blockEvent(pos, block, 1, open ? 1 : 0);
			play(level, pos, open, false, true);
		}
	}

	private static void play(ServerLevel level, BlockPos pos, boolean open, boolean chest, boolean shulker) {
		level.playSound(
				null,
				pos,
				shulker
						? (open ? SoundEvents.SHULKER_BOX_OPEN : SoundEvents.SHULKER_BOX_CLOSE)
						: chest
								? (open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE)
								: (open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE),
				SoundSource.BLOCKS,
				0.5F,
				level.random.nextFloat() * 0.1F + 0.9F
		);
	}
}
