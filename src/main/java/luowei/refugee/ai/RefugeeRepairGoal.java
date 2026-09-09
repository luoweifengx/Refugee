package luowei.refugee.ai;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.staff.StaffService;
import luowei.refugee.warehouse.WarehouseService;

/**
 * 修复：不走向目标，无距离限制拆除 PBS 侵蚀方块；侵蚀区块列表空则退出工种。
 */
public class RefugeeRepairGoal extends Goal {
	private static final int SWING_INTERVAL = 6;

	private final Villager villager;
	private BlockPos target;
	private float mineProgress;
	private int lastCrack = -1;

	public RefugeeRepairGoal(Villager villager) {
		this.villager = villager;
		this.setFlags(EnumSet.of(Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (villager.isBaby() || !RefugeeRoles.isBuilder(villager)) {
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (!data.isRepairerDuty() || data.isFollowing() || data.isFollowingEntity() || data.isPatrolling()) {
			return false;
		}
		if (RefugeeCombat.isEating(villager)) {
			return false;
		}
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void stop() {
		if (villager.level() instanceof ServerLevel level) {
			abortMining(level);
		} else {
			mineProgress = 0.0f;
			lastCrack = -1;
		}
		target = null;
	}

	@Override
	public void tick() {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		UUID subjectId = data.subjectId();
		if (subjectId == null || PbsAdapter.erodedChunkCount(level, subjectId) <= 0) {
			abortMining(level);
			StaffService.leaveRepairerDuty(villager);
			return;
		}
		if (target == null || !PbsAdapter.isErosionBlock(level, target) || !isBreakable(level, target)) {
			abortMining(level);
			target = findTarget(level, subjectId);
		}
		if (target == null) {
			return;
		}
		villager.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
		tickMineProgress(level, target, level.getBlockState(target), RefugeeRoles.workTool(villager), subjectId);
	}

	private BlockPos findTarget(ServerLevel level, UUID subjectId) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (ChunkPos chunk : PbsAdapter.erodedChunks(level, subjectId)) {
			if (!level.hasChunk(chunk.x, chunk.z)) {
				continue;
			}
			for (BlockPos pos : PbsAdapter.erosionBlocks(level, chunk)) {
				if (!isBreakable(level, pos)) {
					continue;
				}
				double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
				if (dist < bestDist) {
					bestDist = dist;
					best = pos.immutable();
				}
			}
		}
		return best;
	}

	private static boolean isBreakable(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return !state.isAir() && state.getDestroySpeed(level, pos) >= 0.0f;
	}

	private void tickMineProgress(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool, UUID subjectId) {
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0f) {
			abortMining(level);
			target = null;
			return;
		}
		if (hardness == 0.0f) {
			breakAndDeposit(level, pos, state, subjectId);
			finishMining(level, pos);
			return;
		}
		float speed = tool.getDestroySpeed(state);
		if (speed > 1.0f && villager.getAttributes().hasAttribute(Attributes.MINING_EFFICIENCY)) {
			speed += (float) villager.getAttributeValue(Attributes.MINING_EFFICIENCY);
		}
		boolean canHarvest = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
		float perTick = speed / hardness / (canHarvest ? 30.0f : 100.0f);
		if (perTick >= 1.0f) {
			breakAndDeposit(level, pos, state, subjectId);
			finishMining(level, pos);
			return;
		}
		mineProgress += perTick;
		int stage = Math.min(9, (int) (mineProgress * 10.0f));
		if (stage != lastCrack) {
			level.destroyBlockProgress(villager.getId(), pos, stage);
			lastCrack = stage;
		}
		if (mineProgress == perTick || villager.tickCount % SWING_INTERVAL == 0) {
			villager.swing(RefugeeRoles.workHand(villager));
			playHitSound(level, pos, state);
		}
		if (mineProgress >= 1.0f) {
			breakAndDeposit(level, pos, state, subjectId);
			finishMining(level, pos);
		}
	}

	private static void playHitSound(ServerLevel level, BlockPos pos, BlockState state) {
		SoundType sound = state.getSoundType();
		level.playSound(
				null,
				pos,
				sound.getHitSound(),
				SoundSource.BLOCKS,
				(sound.getVolume() + 1.0F) / 8.0F,
				sound.getPitch() * 0.5F
		);
	}

	private void abortMining(ServerLevel level) {
		if (target != null && lastCrack >= 0) {
			level.destroyBlockProgress(villager.getId(), target, -1);
		}
		mineProgress = 0.0f;
		lastCrack = -1;
	}

	private void finishMining(ServerLevel level, BlockPos pos) {
		level.destroyBlockProgress(villager.getId(), pos, -1);
		mineProgress = 0.0f;
		lastCrack = -1;
		target = null;
	}

	private void breakAndDeposit(ServerLevel level, BlockPos pos, BlockState state, UUID subjectId) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, villager, RefugeeRoles.workTool(villager));
		level.destroyBlock(pos, false);
		WarehouseService.depositLoot(level, villager, subjectId, drops);
	}
}
