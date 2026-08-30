package luowei.refugee.blueprint;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import luowei.refugee.mixin.StructureTemplateAccessor;
import luowei.refugee.zone.AreaBox;

/**
 * 蓝图世界变换：预览与工人放置共用。
 * {@code worldPos = origin + offset + rotate(localPos, rotation)}，
 * {@code state = localState.rotate(rotation)}。
 */
public final class BlueprintTransforms {
	private BlueprintTransforms() {
	}

	public static Rotation rotation(Rotation rotation) {
		return rotation == null ? Rotation.NONE : rotation;
	}

	public static StructurePlaceSettings settings(Rotation rotation) {
		return new StructurePlaceSettings()
				.setRotation(rotation(rotation))
				.setIgnoreEntities(true);
	}

	public static BlockPos anchor(BlockPos origin, int offsetX, int offsetY, int offsetZ) {
		if (origin == null) {
			return BlockPos.ZERO.offset(offsetX, offsetY, offsetZ);
		}
		return origin.offset(offsetX, offsetY, offsetZ);
	}

	public static BlockPos worldPos(BlockPos origin, int offsetX, int offsetY, int offsetZ, Rotation rotation, BlockPos localPos) {
		BlockPos rotated = localPos == null ? BlockPos.ZERO : localPos.rotate(rotation(rotation));
		return anchor(origin, offsetX, offsetY, offsetZ).offset(rotated);
	}

	public static BlockState worldState(BlockState state, Rotation rotation) {
		if (state == null) {
			return Blocks.AIR.defaultBlockState();
		}
		return state.rotate(rotation(rotation));
	}

	/**
	 * 返回的 {@code StructureBlockInfo.pos} 已是世界坐标，{@code state} 已旋转。
	 * 从 palette 取全量方块；{@code filterBlocks(..., Blocks.AIR)} 只会留下空气格。
	 */
	public static List<StructureTemplate.StructureBlockInfo> placedBlocks(
			StructureTemplate template,
			BlockPos origin,
			int offsetX,
			int offsetY,
			int offsetZ,
			Rotation rotation
	) {
		if (template == null || origin == null) {
			return List.of();
		}
		List<StructureTemplate.Palette> palettes = ((StructureTemplateAccessor) template).refugee$getPalettes();
		if (palettes == null || palettes.isEmpty()) {
			return List.of();
		}
		Rotation rot = rotation(rotation);
		List<StructureTemplate.StructureBlockInfo> result = new ArrayList<>();
		for (StructureTemplate.StructureBlockInfo info : palettes.getFirst().blocks()) {
			if (BlueprintBlocks.shouldSkip(info)) {
				continue;
			}
			result.add(new StructureTemplate.StructureBlockInfo(
					worldPos(origin, offsetX, offsetY, offsetZ, rot, info.pos()),
					worldState(info.state(), rot),
					info.nbt()
			));
		}
		return result;
	}

	public static AreaBox bounds(
			StructureTemplate template,
			BlockPos origin,
			int offsetX,
			int offsetY,
			int offsetZ,
			Rotation rotation
	) {
		BlockPos start = anchor(origin, offsetX, offsetY, offsetZ);
		if (template == null) {
			return AreaBox.of(start, start);
		}
		Rotation rot = rotation(rotation);
		Vec3i size = template.getSize();
		int lastX = Math.max(0, size.getX() - 1);
		int lastY = Math.max(0, size.getY() - 1);
		int lastZ = Math.max(0, size.getZ() - 1);
		BlockPos a = start.offset(BlockPos.ZERO.rotate(rot));
		BlockPos b = start.offset(new BlockPos(lastX, lastY, lastZ).rotate(rot));
		return AreaBox.of(a, b);
	}

	public static int rotationDegrees(Rotation rotation) {
		return switch (rotation(rotation)) {
			case CLOCKWISE_90 -> 90;
			case CLOCKWISE_180 -> 180;
			case COUNTERCLOCKWISE_90 -> 270;
			default -> 0;
		};
	}
}
