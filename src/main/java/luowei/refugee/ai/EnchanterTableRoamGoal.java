package luowei.refugee.ai;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.settle.StandableFinder;
import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 附魔师在附近已放置的附魔台周围走动；跟随或交易时让路。
 */
public class EnchanterTableRoamGoal extends Goal {
	private static final int SEARCH_RADIUS = 24;
	private static final int SEARCH_Y = 8;
	private static final int ROAM_RADIUS = 6;
	private static final int MIN_TABLE_DISTANCE_SQ = 4;
	private static final int RESEARCH_INTERVAL = 80;
	private static final int WANDER_MIN = 40;
	private static final int WANDER_MAX = 100;

	private final Villager villager;
	private BlockPos tablePos;
	private BlockPos roamTarget;
	private int searchCooldown;
	private int wanderCooldown;
	private int lookCooldown;

	public EnchanterTableRoamGoal(Villager villager) {
		this.villager = villager;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!RefugeeSpecialRole.is(villager, RefugeeSpecialRole.ENCHANTER)) {
			return false;
		}
		if (RefugeeAttachments.get(villager).isFollowing() || villager.getTradingPlayer() != null) {
			return false;
		}
		return findTable() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		wanderCooldown = 0;
		lookCooldown = 0;
		pickRoamTarget();
	}

	@Override
	public void stop() {
		tablePos = null;
		roamTarget = null;
		villager.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (!(villager.level() instanceof ServerLevel)) {
			return;
		}
		BlockPos table = findTable();
		if (table == null) {
			villager.getNavigation().stop();
			return;
		}
		if (lookCooldown > 0) {
			lookCooldown--;
		} else {
			villager.getLookControl().setLookAt(
					table.getX() + 0.5,
					table.getY() + 1.0,
					table.getZ() + 0.5,
					10.0f,
					villager.getMaxHeadXRot()
			);
			lookCooldown = 20 + villager.getRandom().nextInt(40);
		}
		if (wanderCooldown > 0) {
			wanderCooldown--;
			if (roamTarget != null && villager.distanceToSqr(roamTarget.getX() + 0.5, roamTarget.getY(), roamTarget.getZ() + 0.5) > 1.0) {
				villager.getNavigation().moveTo(
						roamTarget.getX() + 0.5,
						roamTarget.getY(),
						roamTarget.getZ() + 0.5,
						RefugeeConfig.followSpeed
				);
			}
			return;
		}
		pickRoamTarget();
	}

	private BlockPos findTable() {
		if (!(villager.level() instanceof ServerLevel level)) {
			return null;
		}
		if (tablePos != null && level.getBlockState(tablePos).is(Blocks.ENCHANTING_TABLE)) {
			return tablePos;
		}
		tablePos = null;
		if (searchCooldown > 0) {
			searchCooldown--;
			return null;
		}
		searchCooldown = RESEARCH_INTERVAL;
		BlockPos origin = villager.blockPosition();
		BlockPos closest = null;
		int best = SEARCH_RADIUS * SEARCH_RADIUS + 1;
		for (BlockPos pos : BlockPos.betweenClosed(
				origin.offset(-SEARCH_RADIUS, -SEARCH_Y, -SEARCH_RADIUS),
				origin.offset(SEARCH_RADIUS, SEARCH_Y, SEARCH_RADIUS)
		)) {
			if (!level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE)) {
				continue;
			}
			int dx = pos.getX() - origin.getX();
			int dy = pos.getY() - origin.getY();
			int dz = pos.getZ() - origin.getZ();
			int dist = dx * dx + dy * dy + dz * dz;
			if (dist < best) {
				best = dist;
				closest = pos.immutable();
			}
		}
		tablePos = closest;
		return tablePos;
	}

	private void pickRoamTarget() {
		if (!(villager.level() instanceof ServerLevel level) || tablePos == null) {
			return;
		}
		RandomSource random = villager.getRandom();
		BlockPos chosen = null;
		for (int i = 0; i < 12; i++) {
			int dx = random.nextInt(ROAM_RADIUS * 2 + 1) - ROAM_RADIUS;
			int dz = random.nextInt(ROAM_RADIUS * 2 + 1) - ROAM_RADIUS;
			if (dx * dx + dz * dz < MIN_TABLE_DISTANCE_SQ) {
				continue;
			}
			int x = tablePos.getX() + dx;
			int z = tablePos.getZ() + dz;
			for (int dy = 1; dy >= -2; dy--) {
				BlockPos candidate = new BlockPos(x, tablePos.getY() + dy, z);
				if (StandableFinder.isStandable(level, candidate)) {
					chosen = candidate;
					break;
				}
			}
			if (chosen != null) {
				break;
			}
		}
		if (chosen == null) {
			chosen = tablePos.north();
		}
		roamTarget = chosen;
		wanderCooldown = WANDER_MIN + random.nextInt(Math.max(1, WANDER_MAX - WANDER_MIN));
		villager.getNavigation().moveTo(
				roamTarget.getX() + 0.5,
				StandableFinder.standY(level, roamTarget),
				roamTarget.getZ() + 0.5,
				RefugeeConfig.followSpeed
		);
	}
}
