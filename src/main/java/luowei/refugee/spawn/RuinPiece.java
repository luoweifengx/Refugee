package luowei.refugee.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import luowei.refugee.blueprint.RuinBlueprints;
import luowei.refugee.blueprint.WorldgenBlueprints;

/**
 * 把废墟蓝图写入正在生成的区块。
 */
public class RuinPiece extends StructurePiece {
	private final String templateId;
	private final BlockPos origin;

	public RuinPiece(String templateId, BlockPos origin, RuinBlueprints.Spec spec) {
		super(
				RefugeeStructures.RUIN_PIECE,
				0,
				new BoundingBox(
						origin.getX(),
						origin.getY(),
						origin.getZ(),
						origin.getX() + spec.sizeX() - 1,
						origin.getY() + spec.sizeY() - 1,
						origin.getZ() + spec.sizeZ() - 1
				)
		);
		this.templateId = templateId;
		this.origin = origin.immutable();
	}

	public RuinPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(RefugeeStructures.RUIN_PIECE, tag);
		this.templateId = tag.getStringOr("Template", RuinBlueprints.HUT);
		this.origin = new BlockPos(tag.getIntOr("TX", 0), tag.getIntOr("TY", 0), tag.getIntOr("TZ", 0));
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("Template", templateId);
		tag.putInt("TX", origin.getX());
		tag.putInt("TY", origin.getY());
		tag.putInt("TZ", origin.getZ());
	}

	@Override
	public void postProcess(
			WorldGenLevel level,
			StructureManager structureManager,
			ChunkGenerator generator,
			RandomSource random,
			BoundingBox box,
			ChunkPos chunkPos,
			BlockPos pivot
	) {
		RuinBlueprints.Spec spec = RuinBlueprints.spec(templateId);
		CompoundTag nbt = WorldgenBlueprints.nbt(templateId);
		if (spec == null || nbt == null) {
			return;
		}
		StructureTemplate template = new StructureTemplate();
		template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), nbt);
		StructurePlaceSettings settings = new StructurePlaceSettings()
				.setIgnoreEntities(true)
				.setKnownShape(true)
				.setBoundingBox(box);
		template.placeInWorld(level, origin, origin, settings, random, Block.UPDATE_CLIENTS);
	}
}
