package luowei.refugee.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 没标记的床谁都能认。标了职业的床只有对应职业能认。
 * 职业是 worker、guard、civilian，特殊居民用各自的 id。拿锄头的人算工人。
 */
public final class BedClaim {
	private BedClaim() {
	}

	public static String roleId(Villager villager) {
		RefugeeSpecialRole special = RefugeeSpecialRole.of(villager);
		if (special != null) {
			return special.id();
		}
		if (RefugeeRoles.isBuilder(villager)) {
			return "worker";
		}
		if (RefugeeRoles.isGuard(villager)) {
			return "guard";
		}
		return "civilian";
	}

	/** 可以新认这张床：空着，不是玩家出生点，标记对得上或没有标记。 */
	public static boolean canTake(ServerLevel level, Villager villager, BlockPos pos) {
		BlockPos head = head(level, pos);
		if (!freeBed(level, head) || isSpawnBed(level, head)) {
			return false;
		}
		return roleMatches(level, villager, head);
	}

	/** 已经认下的床还能不能留着。人正睡在上面时也算。 */
	public static boolean keeps(ServerLevel level, Villager villager, BlockPos pos) {
		BlockPos head = head(level, pos);
		BlockState state = level.getBlockState(head);
		if (!(state.getBlock() instanceof BedBlock) || isSpawnBed(level, head)) {
			return false;
		}
		return roleMatches(level, villager, head);
	}

	public static void refresh(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		long time = Math.floorMod(level.getDayTime(), 24000L);
		if (villager.isSleeping() && time >= 10L && time < WorkerSleep.REST_START) {
			villager.stopSleeping();
		}
		dropHome(villager);
	}

	public static void evict(ServerLevel level, BlockPos head) {
		BlockPos bed = head(level, head);
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof Villager villager) {
				dropIfHome(villager, bed);
			}
		}
	}

	public static BlockPos head(ServerLevel level, BlockPos pos) {
		return head(pos, level.getBlockState(pos));
	}

	public static BlockPos head(BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
			return pos.relative(state.getValue(BedBlock.FACING)).immutable();
		}
		return pos.immutable();
	}

	private static boolean roleMatches(ServerLevel level, Villager villager, BlockPos head) {
		String mark = BedMarks.roleAt(level, head);
		return mark == null || mark.equals(roleId(villager));
	}

	private static boolean freeBed(ServerLevel level, BlockPos head) {
		BlockState state = level.getBlockState(head);
		return state.getBlock() instanceof BedBlock
				&& state.getValue(BedBlock.PART) == BedPart.HEAD
				&& !state.getValue(BedBlock.OCCUPIED);
	}

	private static boolean isSpawnBed(ServerLevel level, BlockPos head) {
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			ServerPlayer.RespawnConfig config = player.getRespawnConfig();
			if (config == null || !config.dimension().equals(level.dimension())) {
				continue;
			}
			if (head(level, config.pos()).equals(head)) {
				return true;
			}
		}
		return false;
	}

	private static void dropHome(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		GlobalPos home = villager.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
		if (home == null || !home.dimension().equals(level.dimension())) {
			return;
		}
		if (keeps(level, villager, home.pos())) {
			return;
		}
		release(level, villager, head(level, home.pos()));
	}

	private static void dropIfHome(Villager villager, BlockPos head) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		GlobalPos home = villager.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
		boolean claimed = home != null
				&& home.dimension().equals(level.dimension())
				&& head(level, home.pos()).equals(head);
		boolean sleepingHere = sleepingAt(level, villager, head);
		if (!claimed && !sleepingHere) {
			return;
		}
		if (keeps(level, villager, head)) {
			return;
		}
		release(level, villager, head);
	}

	private static void release(ServerLevel level, Villager villager, BlockPos head) {
		// 床拆掉后原版已经删掉兴趣点，再 release 会抛「POI never registered」。
		if (level.getPoiManager().exists(head, type -> true)) {
			level.getPoiManager().release(head);
		}
		GlobalPos home = villager.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
		if (home != null && home.dimension().equals(level.dimension()) && head(level, home.pos()).equals(head)) {
			villager.getBrain().eraseMemory(MemoryModuleType.HOME);
		}
		if (sleepingAt(level, villager, head)) {
			villager.stopSleeping();
		}
	}

	private static boolean sleepingAt(ServerLevel level, Villager villager, BlockPos head) {
		if (!villager.isSleeping()) {
			return false;
		}
		BlockPos sleeping = villager.getSleepingPos().orElse(null);
		return sleeping != null && head(level, sleeping).equals(head);
	}
}
