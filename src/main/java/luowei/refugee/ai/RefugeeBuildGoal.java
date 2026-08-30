package luowei.refugee.ai;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.blueprint.BlueprintBlocks;
import luowei.refugee.blueprint.BlueprintRegistry;
import luowei.refugee.build.BuildJob;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.logistics.OrgLogisticsData;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.staff.StaffService;
import luowei.refugee.warehouse.WarehouseService;

/**
 * 建筑：不靠近箱子，从组织仓库宽松取料，按组织级任务的共享光标放置。
 */
public class RefugeeBuildGoal extends Goal {
	private static final int SWING_INTERVAL = 6;

	private final Villager villager;
	private int placeCooldown;
	private BlockPos minePos;
	private float mineProgress;
	private int lastCrack = -1;

	public RefugeeBuildGoal(Villager villager) {
		this.villager = villager;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return RefugeeRoles.isBuilder(villager) && RefugeeAttachments.get(villager).isBuilding();
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
			minePos = null;
		}
	}

	@Override
	public void tick() {
		if (placeCooldown > 0) {
			if (minePos != null && villager.level() instanceof ServerLevel cooldownLevel) {
				abortMining(cooldownLevel);
			}
			placeCooldown--;
			return;
		}
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		BuildJob job = resolveJob(level, data);
		if (job == null) {
			abortMining(level);
			if (data.jobId() != null || data.isBuilding()) {
				data.clearBuild();
				RefugeeAttachments.markDirty(villager, data);
			}
			return;
		}
		List<StructureTemplate.StructureBlockInfo> blocks = loadBlocks(level, job);
		if (blocks.isEmpty()) {
			abortMining(level);
			return;
		}
		int index = job.nextIndex();
		while (index < blocks.size()) {
			StructureTemplate.StructureBlockInfo info = blocks.get(index);
			if (shouldSkip(info)) {
				abortMining(level);
				index++;
				job.setNextIndex(index);
				OrgLogisticsData.get(level.getServer()).setDirty();
				continue;
			}
			BlockPos dest = info.pos();
			if (minePos != null && !minePos.equals(dest)) {
				abortMining(level);
			}
			BlockState target = info.state();
			BlockState current = level.getBlockState(dest);
			if (!current.isAir() && current.equals(target)) {
				abortMining(level);
				index++;
				job.setNextIndex(index);
				OrgLogisticsData.get(level.getServer()).setDirty();
				continue;
			}
			if (villager.distanceToSqr(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5) > 9.0) {
				abortMining(level);
				villager.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, RefugeeConfig.builderWalkSpeed);
				return;
			}
			Item material = target.getBlock().asItem();
			if (material == Items.AIR) {
				abortMining(level);
				index++;
				job.setNextIndex(index);
				OrgLogisticsData.get(level.getServer()).setDirty();
				continue;
			}
			current = level.getBlockState(dest);
			if (!current.isAir() && current.equals(target)) {
				abortMining(level);
				index++;
				job.setNextIndex(index);
				OrgLogisticsData.get(level.getServer()).setDirty();
				continue;
			}
			UUID subjectId = data.subjectId();
			if (subjectId == null || !WarehouseService.hasForBuild(level, subjectId, material)) {
				abortMining(level);
				placeCooldown = RefugeeConfig.buildPlaceIntervalTicks;
				return;
			}
			if (!current.isAir()) {
				if (current.getDestroySpeed(level, dest) < 0.0f) {
					abortMining(level);
					index++;
					job.setNextIndex(index);
					OrgLogisticsData.get(level.getServer()).setDirty();
					continue;
				}
				villager.getLookControl().setLookAt(dest.getX() + 0.5, dest.getY() + 0.5, dest.getZ() + 0.5);
				villager.getNavigation().stop();
				if (!tickBreakObstacle(level, dest, current, subjectId)) {
					return;
				}
				current = level.getBlockState(dest);
				if (!current.isAir() && current.equals(target)) {
					abortMining(level);
					index++;
					job.setNextIndex(index);
					OrgLogisticsData.get(level.getServer()).setDirty();
					placeCooldown = RefugeeConfig.buildPlaceIntervalTicks;
					return;
				}
			}
			if (!WarehouseService.tryConsumeForBuild(level, subjectId, material)) {
				placeCooldown = RefugeeConfig.buildPlaceIntervalTicks;
				return;
			}
			level.setBlock(dest, target, 3);
			if (info.nbt() != null) {
				BlockEntity blockEntity = level.getBlockEntity(dest);
				if (blockEntity != null) {
					blockEntity.loadWithComponents(info.nbt(), level.registryAccess());
				}
			}
			if (subjectId != null) {
				PbsAdapter.notifyVillagerPlaced(level, dest, subjectId);
			}
			index++;
			job.setNextIndex(index);
			OrgLogisticsData.get(level.getServer()).setDirty();
			placeCooldown = RefugeeConfig.buildPlaceIntervalTicks;
			return;
		}
		abortMining(level);
		StaffService.finishWorker(level, villager, job);
	}

	/**
	 * 按玩家破坏公式清障；未挖完返回 false，本 tick 不放置。
	 */
	private boolean tickBreakObstacle(ServerLevel level, BlockPos pos, BlockState state, UUID subjectId) {
		if (minePos == null || !minePos.equals(pos)) {
			abortMining(level);
			minePos = pos.immutable();
		}
		ItemStack tool = villager.getMainHandItem();
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0f) {
			abortMining(level);
			return false;
		}
		if (hardness == 0.0f) {
			breakAndDeposit(level, pos, state, subjectId);
			finishClear(level, pos);
			return true;
		}
		float speed = tool.getDestroySpeed(state);
		if (speed > 1.0f && villager.getAttributes().hasAttribute(Attributes.MINING_EFFICIENCY)) {
			speed += (float) villager.getAttributeValue(Attributes.MINING_EFFICIENCY);
		}
		boolean canHarvest = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
		float perTick = speed / hardness / (canHarvest ? 30.0f : 100.0f);
		if (perTick >= 1.0f) {
			breakAndDeposit(level, pos, state, subjectId);
			finishClear(level, pos);
			return true;
		}
		mineProgress += perTick;
		int stage = Math.min(9, (int) (mineProgress * 10.0f));
		if (stage != lastCrack) {
			level.destroyBlockProgress(villager.getId(), pos, stage);
			lastCrack = stage;
		}
		if (mineProgress == perTick || villager.tickCount % SWING_INTERVAL == 0) {
			villager.swing(InteractionHand.MAIN_HAND);
			playHitSound(level, pos, state);
		}
		if (mineProgress >= 1.0f) {
			breakAndDeposit(level, pos, state, subjectId);
			finishClear(level, pos);
			return true;
		}
		return false;
	}

	/** 与原版玩家挖掘击打声同公式，挥镐时广播给附近玩家。 */
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

	private void finishClear(ServerLevel level, BlockPos pos) {
		level.destroyBlockProgress(villager.getId(), pos, -1);
		mineProgress = 0.0f;
		lastCrack = -1;
		minePos = null;
	}

	private void abortMining(ServerLevel level) {
		if (minePos != null && lastCrack >= 0) {
			level.destroyBlockProgress(villager.getId(), minePos, -1);
		}
		mineProgress = 0.0f;
		lastCrack = -1;
		minePos = null;
	}

	private void breakAndDeposit(ServerLevel level, BlockPos dest, BlockState current, UUID subjectId) {
		BlockEntity blockEntity = level.getBlockEntity(dest);
		List<ItemStack> drops = Block.getDrops(
				current,
				level,
				dest,
				blockEntity,
				villager,
				villager.getMainHandItem()
		);
		level.destroyBlock(dest, false);
		WarehouseService.depositLoot(level, villager, subjectId, drops);
	}

	private static boolean shouldSkip(StructureTemplate.StructureBlockInfo info) {
		return BlueprintBlocks.shouldSkip(info);
	}

	private BuildJob resolveJob(ServerLevel level, RefugeeVillagerData data) {
		if (data.jobId() != null) {
			BuildJob job = OrgLogisticsData.get(level.getServer()).job(data.jobId());
			if (job != null) {
				return job;
			}
		}
		return StaffService.migrateLegacy(level, villager, data);
	}

	private static List<StructureTemplate.StructureBlockInfo> loadBlocks(ServerLevel level, BuildJob job) {
		StructureTemplate template = BlueprintRegistry.get(job.structureId());
		if (template == null) {
			template = level.getServer().getStructureManager().get(job.structureId()).orElse(null);
		}
		if (template == null) {
			return List.of();
		}
		return job.placedBlocks(template);
	}
}
