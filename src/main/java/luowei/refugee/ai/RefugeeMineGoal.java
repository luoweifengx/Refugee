package luowei.refugee.ai;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import luowei.refugee.Refugee;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.logistics.OrgLogisticsData;
import luowei.refugee.settle.StandableFinder;
import luowei.refugee.warehouse.MaterialCategory;
import luowei.refugee.warehouse.WarehouseService;
import luowei.refugee.zone.AreaBox;
import luowei.refugee.zone.WorkZone;

/**
 * 镐采石、斧伐木、铲挖泥沙、锄维护耕地并种地。掉落先入村民背包再漏斗入仓。
 * 镐/斧/铲按玩家破坏公式按 tick 累加进度，并用 {@code destroyBlockProgress} 向客户端同步裂纹。
 */
public class RefugeeMineGoal extends Goal {
	private static final double REACH = 9.0;
	/** 斧的 distanceToSqr 阈值，约 100 格欧氏。 */
	private static final double AXE_REACH = 10000.0;
	/** 斧/锄身边优先扫描半径；镐/铲不用。 */
	private static final int NEAR_SCAN = 12;
	private static final int SWING_INTERVAL = 6;
	private static final int LOG_INTERVAL = 20;
	private static final int LAYER_SCAN_BUDGET = 4096;
	private static final int WALK_RETRY_TICKS = 10;

	private final Villager villager;
	private BlockPos target;
	private int scanCursor;
	/** 镐/铲当前扫描层 Y；MIN_VALUE 表示尚未开始或已复位。 */
	private int scanLayerY = Integer.MIN_VALUE;
	/** 当前层扫描中水平最近的合法块，大层跨 tick 续扫。 */
	private BlockPos layerBest;
	private long layerBestHoriz = Long.MAX_VALUE;
	private int walkFailCooldown;
	private String lastLogPhase = "";
	private int lastLogTick;
	/** 当前方块的破坏进度，满 1 后拆块。 */
	private float mineProgress;
	/** 上次发给客户端的裂纹阶段 0–9；-1 表示未在播裂纹。 */
	private int lastCrack = -1;

	public RefugeeMineGoal(Villager villager) {
		this.villager = villager;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!RefugeeRoles.isBuilder(villager)) {
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.isBuilding()) {
			debug("skip", "isBuilding");
			return false;
		}
		if (data.isFollowing()) {
			debug("skip", "isFollowing");
			return false;
		}
		if (zone() == null) {
			debug("skip", "no-zone");
			return false;
		}
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		debug("start", "goal-activated");
	}

	@Override
	public void stop() {
		debug("stop", "goal-stopped");
		if (villager.level() instanceof ServerLevel level) {
			abortMining(level);
		} else {
			mineProgress = 0.0f;
			lastCrack = -1;
		}
		target = null;
		walkFailCooldown = 0;
		resetLayerScan();
		villager.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		WorkZone zone = zone();
		if (zone == null) {
			abortMining(level);
			return;
		}
		if (!level.dimension().location().equals(zone.dimension())) {
			abortMining(level);
			return;
		}
		AreaBox box = zone.box();
		BlockPos feet = villager.blockPosition();
		if (isFarFromZone(box, feet)) {
			abortMining(level);
			BlockPos dest = findZoneApproach(level, box);
			issueWalk(level, dest, "walk-zone",
					"box=" + box.min().toShortString() + ".." + box.max().toShortString());
			return;
		}
		if (target == null || !box.contains(target) || !isValidTarget(level, target)) {
			abortMining(level);
			target = findTarget(level, box);
		}
		if (target == null) {
			if (usesTopDownScan() && scanLayerY != Integer.MIN_VALUE) {
				debug("scan-layer", "y=" + scanLayerY + " cursor=" + scanCursor
						+ " box=" + box.min().toShortString() + ".." + box.max().toShortString());
			} else {
				debug("no-target", "box=" + box.min().toShortString() + ".." + box.max().toShortString()
						+ " size=" + box.sizeX() + "x" + box.sizeY() + "x" + box.sizeZ());
			}
			return;
		}
		villager.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
		ItemStack tool = villager.getMainHandItem();
		double reach = RefugeeRoles.isAxe(tool) ? AXE_REACH : REACH;
		double distSq = villager.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
		if (distSq > reach) {
			abortMining(level);
			BlockPos dest = findStandNearTarget(level, target);
			issueWalk(level, dest, "walk-target",
					"target=" + target.toShortString()
							+ " targetY=" + target.getY()
							+ " block=" + level.getBlockState(target).getBlock()
							+ " distSq=" + String.format("%.1f", distSq)
							+ " reach=" + reach);
			return;
		}
		villager.getNavigation().stop();
		walkFailCooldown = 0;
		BlockState state = level.getBlockState(target);
		debug("mine", "target=" + target.toShortString()
				+ " targetY=" + target.getY()
				+ " block=" + state.getBlock()
				+ " distSq=" + String.format("%.1f", distSq)
				+ " progress=" + String.format("%.2f", mineProgress));
		if (tryHoeInstant(level, target, state, tool)) {
			target = null;
			return;
		}
		tickMineProgress(level, target, state, tool);
	}

	private void debug(String phase, String detail) {
		if (phase.equals(lastLogPhase) && villager.tickCount - lastLogTick < LOG_INTERVAL) {
			return;
		}
		lastLogPhase = phase;
		lastLogTick = villager.tickCount;
		ItemStack hand = villager.getMainHandItem();
		String tool = hand.isEmpty() ? "empty" : hand.getItem().toString();
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		Refugee.LOGGER.info(
				"[mine/{}] id={} tool={} pos={} follow={} building={} hasZone={} {}",
				phase,
				villager.getUUID().toString().substring(0, 8),
				tool,
				villager.blockPosition().toShortString(),
				data.isFollowing(),
				data.isBuilding(),
				zone() != null,
				detail
		);
	}

	private WorkZone zone() {
		if (villager.level().getServer() == null) {
			return null;
		}
		return OrgLogisticsData.get(villager.level().getServer()).zoneOfWorker(villager.getUUID());
	}

	private BlockPos findTarget(ServerLevel level, AreaBox box) {
		if (usesTopDownScan()) {
			return findLayerTarget(level, box);
		}
		if (scanLayerY != Integer.MIN_VALUE) {
			resetLayerScan();
		}
		BlockPos near = scanNear(level, box, villager.blockPosition(), NEAR_SCAN);
		if (near != null) {
			return near;
		}
		return scanBox(level, box);
	}

	private boolean usesTopDownScan() {
		ItemStack tool = villager.getMainHandItem();
		return RefugeeRoles.isPickaxe(tool) || RefugeeRoles.isShovel(tool);
	}

	/**
	 * 镐/铲：工作区内从最高层往下找合法块，同层取水平最近。
	 * 大层按 {@link #LAYER_SCAN_BUDGET} 跨 tick 扫完再下一层，避免每 tick 从角落扫爆。
	 */
	private BlockPos findLayerTarget(ServerLevel level, AreaBox box) {
		int maxY = box.max().getY();
		int minY = box.min().getY();
		if (scanLayerY == Integer.MIN_VALUE || scanLayerY > maxY) {
			scanLayerY = maxY;
			scanCursor = 0;
			layerBest = null;
			layerBestHoriz = Long.MAX_VALUE;
		}
		if (scanLayerY < minY) {
			resetLayerScan();
			return null;
		}
		int minX = box.min().getX();
		int minZ = box.min().getZ();
		int sizeX = box.sizeX();
		int sizeZ = box.sizeZ();
		int layerSize = sizeX * sizeZ;
		int originX = villager.blockPosition().getX();
		int originZ = villager.blockPosition().getZ();
		int budget = LAYER_SCAN_BUDGET;
		while (budget > 0 && scanLayerY >= minY) {
			while (scanCursor < layerSize && budget > 0) {
				int i = scanCursor++;
				budget--;
				int x = minX + (i % sizeX);
				int z = minZ + (i / sizeX);
				BlockPos pos = new BlockPos(x, scanLayerY, z);
				if (!isValidTarget(level, pos)) {
					continue;
				}
				long dx = (long) x - originX;
				long dz = (long) z - originZ;
				long horiz = dx * dx + dz * dz;
				if (horiz < layerBestHoriz) {
					layerBestHoriz = horiz;
					layerBest = pos;
				}
			}
			if (scanCursor < layerSize) {
				return null;
			}
			if (layerBest != null) {
				BlockPos found = layerBest;
				resetLayerScan();
				return found;
			}
			scanLayerY--;
			scanCursor = 0;
			layerBest = null;
			layerBestHoriz = Long.MAX_VALUE;
		}
		if (scanLayerY < minY) {
			resetLayerScan();
		}
		return null;
	}

	private void resetLayerScan() {
		scanLayerY = Integer.MIN_VALUE;
		scanCursor = 0;
		layerBest = null;
		layerBestHoriz = Long.MAX_VALUE;
	}

	/** 脚在盒内，或站在顶层上方一格且水平落在区内，算出勤。 */
	private static boolean isOnDuty(AreaBox box, BlockPos pos) {
		if (box.contains(pos)) {
			return true;
		}
		return pos.getY() == box.max().getY() + 1
				&& pos.getX() >= box.min().getX() && pos.getX() <= box.max().getX()
				&& pos.getZ() >= box.min().getZ() && pos.getZ() <= box.max().getZ();
	}

	/** 离工作区过远才走近；区内或顶上一格、以及紧贴盒子都不算远。 */
	private boolean isFarFromZone(AreaBox box, BlockPos pos) {
		if (isOnDuty(box, pos)) {
			return false;
		}
		return !box.aabb().inflate(2.0).contains(villager.position());
	}

	private void issueWalk(ServerLevel level, BlockPos dest, String phase, String extra) {
		var nav = villager.getNavigation();
		if (dest == null) {
			debug(phase, extra + " dest=none destY=none moveTo=false navDone=" + nav.isDone());
			return;
		}
		if (!nav.isDone()) {
			debug(phase, extra + " dest=" + dest.toShortString()
					+ " destY=" + dest.getY()
					+ " moveTo=hold navDone=false");
			return;
		}
		if (walkFailCooldown > 0) {
			walkFailCooldown--;
			debug(phase, extra + " dest=" + dest.toShortString()
					+ " destY=" + dest.getY()
					+ " moveTo=wait navDone=true");
			return;
		}
		double standY = StandableFinder.standY(level, dest);
		boolean moved = nav.moveTo(
				dest.getX() + 0.5,
				standY,
				dest.getZ() + 0.5,
				RefugeeConfig.builderWalkSpeed
		);
		if (!moved || nav.isDone()) {
			walkFailCooldown = WALK_RETRY_TICKS;
		}
		debug(phase, extra + " dest=" + dest.toShortString()
				+ " destY=" + dest.getY()
				+ " moveTo=" + moved
				+ " navDone=" + nav.isDone());
	}

	/** 优先站到区顶上方空气，避免走进盒子中心实心块。 */
	private BlockPos findZoneApproach(ServerLevel level, AreaBox box) {
		int standY = box.max().getY() + 1;
		int minX = box.min().getX();
		int maxX = box.max().getX();
		int minZ = box.min().getZ();
		int maxZ = box.max().getZ();
		int cx = (minX + maxX) / 2;
		int cz = (minZ + maxZ) / 2;
		BlockPos centerTop = new BlockPos(cx, standY, cz);
		if (StandableFinder.isStandable(level, centerTop)) {
			return centerTop;
		}
		int maxR = Math.max(maxX - minX, maxZ - minZ);
		for (int r = 1; r <= maxR; r++) {
			for (int dx = -r; dx <= r; dx++) {
				int adx = Math.abs(dx);
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(adx, Math.abs(dz)) != r) {
						continue;
					}
					int x = cx + dx;
					int z = cz + dz;
					if (x < minX || x > maxX || z < minZ || z > maxZ) {
						continue;
					}
					BlockPos feet = new BlockPos(x, standY, z);
					if (StandableFinder.isStandable(level, feet)) {
						return feet;
					}
				}
			}
		}
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos side = centerTop.relative(dir, Math.max(box.sizeX(), box.sizeZ()) / 2 + 1);
			BlockPos feet = new BlockPos(side.getX(), standY, side.getZ());
			if (StandableFinder.isStandable(level, feet)) {
				return feet;
			}
		}
		return null;
	}

	/** 可站格：目标上方或四邻（含邻格上方），脚下须实心。 */
	private BlockPos findStandNearTarget(ServerLevel level, BlockPos mineAt) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos above = mineAt.above();
		if (StandableFinder.isStandable(level, above)) {
			return above;
		}
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos side = mineAt.relative(dir);
			BlockPos sideAbove = side.above();
			if (StandableFinder.isStandable(level, side)) {
				double dist = villager.distanceToSqr(side.getX() + 0.5, side.getY(), side.getZ() + 0.5);
				if (dist < bestDist) {
					bestDist = dist;
					best = side;
				}
			}
			if (StandableFinder.isStandable(level, sideAbove)) {
				double dist = villager.distanceToSqr(sideAbove.getX() + 0.5, sideAbove.getY(), sideAbove.getZ() + 0.5);
				if (dist < bestDist) {
					bestDist = dist;
					best = sideAbove;
				}
			}
		}
		return best;
	}

	private BlockPos scanNear(ServerLevel level, AreaBox box, BlockPos origin, int radius) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		BlockPos min = new BlockPos(
				Math.max(box.min().getX(), origin.getX() - radius),
				Math.max(box.min().getY(), origin.getY() - radius),
				Math.max(box.min().getZ(), origin.getZ() - radius)
		);
		BlockPos max = new BlockPos(
				Math.min(box.max().getX(), origin.getX() + radius),
				Math.min(box.max().getY(), origin.getY() + radius),
				Math.min(box.max().getZ(), origin.getZ() + radius)
		);
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			if (!isValidTarget(level, pos)) {
				continue;
			}
			double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			if (dist < bestDist) {
				bestDist = dist;
				best = pos.immutable();
			}
		}
		return best;
	}

	private BlockPos scanBox(ServerLevel level, AreaBox box) {
		int scanned = 0;
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
			scanned++;
			if (scanned < scanCursor) {
				continue;
			}
			if (scanned - scanCursor > 4096) {
				scanCursor = scanned;
				break;
			}
			if (!isValidTarget(level, pos)) {
				continue;
			}
			double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			if (dist < bestDist) {
				bestDist = dist;
				best = pos.immutable();
			}
		}
		if (best == null && scanCursor > 0 && scanned >= volumeHint(box)) {
			scanCursor = 0;
		}
		if (best != null) {
			scanCursor = 0;
		}
		return best;
	}

	private static long volumeHint(AreaBox box) {
		return Math.max(1L, box.volume());
	}

	private boolean isValidTarget(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		if (level.getBlockEntity(pos) instanceof BaseContainerBlockEntity) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0f) {
			return false;
		}
		ItemStack tool = villager.getMainHandItem();
		if (RefugeeRoles.isPickaxe(tool)) {
			return state.is(BlockTags.MINEABLE_WITH_PICKAXE);
		}
		if (RefugeeRoles.isAxe(tool)) {
			return state.is(BlockTags.LOGS);
		}
		if (RefugeeRoles.isShovel(tool)) {
			return MaterialCategory.ofBlock(state) == MaterialCategory.SOIL;
		}
		if (RefugeeRoles.isHoe(tool)) {
			return isTillable(state) || isHarvestable(state) || isPlantableSpot(level, pos);
		}
		return false;
	}

	/** 锄：犁地、收获并补种、对空耕地种仓库种子。 */
	private boolean tryHoeInstant(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
		if (!RefugeeRoles.isHoe(tool)) {
			return false;
		}
		if (isTillable(state)) {
			level.setBlock(pos, Blocks.FARMLAND.defaultBlockState(), 3);
			level.levelEvent(2001, pos, Block.getId(state));
			return true;
		}
		if (isHarvestable(state)) {
			BlockState soil = level.getBlockState(pos.below());
			breakAndDeposit(level, pos, state);
			tryPlant(level, pos, soil);
			return true;
		}
		if (isPlantable(level, pos)) {
			return tryPlant(level, pos.above(), state);
		}
		return false;
	}

	private boolean tryPlant(ServerLevel level, BlockPos cropPos, BlockState soil) {
		if (!level.getBlockState(cropPos).isAir()) {
			return false;
		}
		UUID subjectId = RefugeeAttachments.get(villager).subjectId();
		if (subjectId == null) {
			return false;
		}
		ItemStack seed = WarehouseService.takeOne(level, subjectId, MaterialCategory.SEED);
		if (seed.isEmpty()) {
			return false;
		}
		BlockState crop = cropStateForSeed(seed.getItem());
		if (crop == null || !canPlantOn(soil, seed.getItem())) {
			WarehouseService.deposit(level, subjectId, seed);
			return false;
		}
		level.setBlock(cropPos, crop, 3);
		level.levelEvent(2001, cropPos, Block.getId(crop));
		villager.swing(InteractionHand.MAIN_HAND);
		return true;
	}

	/**
	 * 按玩家破坏公式累加进度并同步裂纹；满进度或本 tick 即可破时才拆块入仓。
	 */
	private void tickMineProgress(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0f) {
			abortMining(level);
			target = null;
			return;
		}
		if (hardness == 0.0f) {
			breakAndDeposit(level, pos, state);
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
			breakAndDeposit(level, pos, state);
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
			villager.swing(InteractionHand.MAIN_HAND);
			playHitSound(level, pos, state);
		}
		if (mineProgress >= 1.0f) {
			breakAndDeposit(level, pos, state);
			finishMining(level, pos);
		}
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

	/** 取消挖掘并清裂纹，不丢掉当前 {@link #target}（寻路/换目标前调用）。 */
	private void abortMining(ServerLevel level) {
		if (target != null && lastCrack >= 0) {
			level.destroyBlockProgress(villager.getId(), target, -1);
		}
		mineProgress = 0.0f;
		lastCrack = -1;
	}

	/** 挖完：清裂纹、丢掉目标，下一 tick 立刻找下一块。 */
	private void finishMining(ServerLevel level, BlockPos pos) {
		level.destroyBlockProgress(villager.getId(), pos, -1);
		mineProgress = 0.0f;
		lastCrack = -1;
		target = null;
	}

	private void breakAndDeposit(ServerLevel level, BlockPos pos, BlockState state) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		UUID subjectId = data.subjectId();
		BlockEntity blockEntity = level.getBlockEntity(pos);
		List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, villager, villager.getMainHandItem());
		level.destroyBlock(pos, false);
		WarehouseService.depositLoot(level, villager, subjectId, drops);
	}

	private boolean isPlantableSpot(ServerLevel level, BlockPos pos) {
		if (!isPlantable(level, pos)) {
			return false;
		}
		UUID subjectId = RefugeeAttachments.get(villager).subjectId();
		return subjectId != null && WarehouseService.count(level.getServer(), subjectId, MaterialCategory.SEED) > 0;
	}

	private static boolean isTillable(BlockState state) {
		return state.is(Blocks.DIRT)
				|| state.is(Blocks.GRASS_BLOCK)
				|| state.is(Blocks.DIRT_PATH)
				|| state.is(Blocks.COARSE_DIRT);
	}

	private static boolean isHarvestable(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		if (block instanceof NetherWartBlock) {
			return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
		}
		if (block instanceof SweetBerryBushBlock) {
			return state.getValue(BlockStateProperties.AGE_3) >= 3;
		}
		return false;
	}

	private static boolean isPlantable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!level.getBlockState(pos.above()).isAir()) {
			return false;
		}
		return state.is(Blocks.FARMLAND) || state.is(Blocks.SOUL_SAND);
	}

	private static boolean canPlantOn(BlockState soil, Item seed) {
		if (seed == Items.NETHER_WART) {
			return soil.is(Blocks.SOUL_SAND);
		}
		return soil.is(Blocks.FARMLAND);
	}

	private static BlockState cropStateForSeed(Item seed) {
		if (seed == Items.WHEAT_SEEDS) {
			return Blocks.WHEAT.defaultBlockState();
		}
		if (seed == Items.BEETROOT_SEEDS) {
			return Blocks.BEETROOTS.defaultBlockState();
		}
		if (seed == Items.PUMPKIN_SEEDS) {
			return Blocks.PUMPKIN_STEM.defaultBlockState();
		}
		if (seed == Items.MELON_SEEDS) {
			return Blocks.MELON_STEM.defaultBlockState();
		}
		if (seed == Items.TORCHFLOWER_SEEDS) {
			return Blocks.TORCHFLOWER_CROP.defaultBlockState();
		}
		if (seed == Items.PITCHER_POD) {
			return Blocks.PITCHER_CROP.defaultBlockState();
		}
		if (seed == Items.NETHER_WART) {
			return Blocks.NETHER_WART.defaultBlockState();
		}
		if (seed == Items.CARROT) {
			return Blocks.CARROTS.defaultBlockState();
		}
		if (seed == Items.POTATO) {
			return Blocks.POTATOES.defaultBlockState();
		}
		if (seed instanceof net.minecraft.world.item.BlockItem blockItem) {
			Block block = blockItem.getBlock();
			if (block instanceof CropBlock || block instanceof NetherWartBlock) {
				return block.defaultBlockState();
			}
		}
		return null;
	}
}
