package luowei.refugee.spawn;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import luowei.refugee.blueprint.RuinBlueprints;

/**
 * 地表废墟：区块生成时在高度图上放置蓝图，不在主线程强制加载远区块。
 */
public class RuinStructure extends Structure {
	public static final MapCodec<RuinStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			settingsCodec(instance),
			Codec.STRING.fieldOf("template").forGetter(structure -> structure.templateId)
	).apply(instance, RuinStructure::new));

	private final String templateId;

	public RuinStructure(StructureSettings settings, String templateId) {
		super(settings);
		this.templateId = templateId;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		RuinBlueprints.Spec spec = RuinBlueprints.spec(templateId);
		if (spec == null) {
			return Optional.empty();
		}
		int x = context.chunkPos().getMiddleBlockX();
		int z = context.chunkPos().getMiddleBlockZ();
		int ground = context.chunkGenerator().getFirstOccupiedHeight(
				x,
				z,
				Heightmap.Types.WORLD_SURFACE_WG,
				context.heightAccessor(),
				context.randomState()
		);
		if (ground <= context.heightAccessor().getMinY() + 1
				|| ground + spec.sizeY() >= context.heightAccessor().getMinY() + context.heightAccessor().getHeight() - 2) {
			return Optional.empty();
		}
		NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState());
		BlockState surface = column.getBlock(ground);
		if (surface.isAir() || !surface.getFluidState().isEmpty()) {
			return Optional.empty();
		}
		BlockPos origin = new BlockPos(x - spec.sizeX() / 2, ground, z - spec.sizeZ() / 2);
		return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new RuinPiece(templateId, origin, spec))));
	}

	@Override
	public StructureType<?> type() {
		return RefugeeStructures.RUIN;
	}
}
