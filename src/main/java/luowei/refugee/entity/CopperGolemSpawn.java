package luowei.refugee.entity;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * 南瓜 + 四个铜块，摆法与铁傀儡相同。
 */
public final class CopperGolemSpawn {
	private static BlockPattern pattern;

	private CopperGolemSpawn() {
	}

	public static boolean canSpawn(LevelReader level, BlockPos pumpkinPos) {
		return pattern().find(level, pumpkinPos) != null;
	}

	public static boolean trySpawn(Level level, BlockPos pumpkinPos) {
		if (level.isClientSide() || ModEntities.COPPER_GOLEM == null) {
			return false;
		}
		BlockPattern.BlockPatternMatch match = pattern().find(level, pumpkinPos);
		if (match == null) {
			return false;
		}
		CopperGolem golem = ModEntities.COPPER_GOLEM.create(level, EntitySpawnReason.TRIGGERED);
		if (golem == null) {
			return false;
		}
		golem.setPlayerCreated(true);
		BlockPos feet = match.getBlock(1, 2, 0).getPos();
		golem.snapTo(feet.getX() + 0.5, feet.getY() + 0.05, feet.getZ() + 0.5, 0.0F, 0.0F);
		CarvedPumpkinBlock.clearPatternBlocks(level, match);
		level.addFreshEntity(golem);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, golem.getBoundingBox().inflate(5.0))) {
			CriteriaTriggers.SUMMONED_ENTITY.trigger(player, golem);
		}
		level.gameEvent(golem, GameEvent.ENTITY_PLACE, golem.blockPosition());
		CarvedPumpkinBlock.updatePatternBlocks(level, match);
		return true;
	}

	private static BlockPattern pattern() {
		if (pattern == null) {
			pattern = BlockPatternBuilder.start()
					.aisle("~^~", "###", "~#~")
					.where('^', BlockInWorld.hasState(CopperGolemSpawn::isPumpkin))
					.where('#', BlockInWorld.hasState(CopperGolemSpawn::isCopper))
					.where('~', BlockInWorld.hasState(BlockState::isAir))
					.build();
		}
		return pattern;
	}

	private static boolean isPumpkin(BlockState state) {
		return state.is(Blocks.CARVED_PUMPKIN) || state.is(Blocks.JACK_O_LANTERN);
	}

	private static boolean isCopper(BlockState state) {
		return state.is(Blocks.COPPER_BLOCK) || state.is(Blocks.WAXED_COPPER_BLOCK);
	}
}
