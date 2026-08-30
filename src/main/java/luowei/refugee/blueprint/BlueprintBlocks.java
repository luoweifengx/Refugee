package luowei.refugee.blueprint;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * 蓝图方块过滤：工人放置与客户端幽灵预览共用，避免两处漂移。
 */
public final class BlueprintBlocks {
	private BlueprintBlocks() {
	}

	/**
	 * 跳过空气、结构/命令方块、拼图、屏障、光源等不可建造格。
	 */
	public static boolean shouldSkip(StructureTemplate.StructureBlockInfo info) {
		if (info == null || info.state() == null) {
			return true;
		}
		return shouldSkip(info.state());
	}

	public static boolean shouldSkip(BlockState state) {
		if (state.isAir()
				|| state.is(Blocks.STRUCTURE_VOID)
				|| state.is(Blocks.STRUCTURE_BLOCK)
				|| state.is(Blocks.JIGSAW)
				|| state.is(Blocks.BARRIER)
				|| state.is(Blocks.LIGHT)
				|| state.is(Blocks.COMMAND_BLOCK)
				|| state.is(Blocks.CHAIN_COMMAND_BLOCK)
				|| state.is(Blocks.REPEATING_COMMAND_BLOCK)) {
			return true;
		}
		return state.getBlock().asItem() == Items.AIR;
	}
}
