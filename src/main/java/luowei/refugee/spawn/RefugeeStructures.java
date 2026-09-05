package luowei.refugee.spawn;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

import luowei.refugee.Refugee;

/**
 * 废墟走世界生成：注册结构类型与片段。法师塔 / 地狱熔炉仍由 {@link LandmarkSpawnService} 后续放置。
 */
public final class RefugeeStructures {
	public static StructureType<RuinStructure> RUIN;
	public static StructurePieceType RUIN_PIECE;

	private RefugeeStructures() {
	}

	public static void register() {
		RUIN_PIECE = Registry.register(
				BuiltInRegistries.STRUCTURE_PIECE,
				ResourceKey.create(Registries.STRUCTURE_PIECE, Refugee.id("ruin")),
				RuinPiece::new
		);
		RUIN = Registry.register(
				BuiltInRegistries.STRUCTURE_TYPE,
				ResourceKey.create(Registries.STRUCTURE_TYPE, Refugee.id("ruin")),
				() -> RuinStructure.CODEC
		);
	}
}
