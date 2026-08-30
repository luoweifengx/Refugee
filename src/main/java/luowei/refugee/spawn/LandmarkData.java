package luowei.refugee.spawn;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 法师塔 / 地狱熔炉是否已在本存档生成。
 */
public final class LandmarkData extends SavedData {
	public static final String FILE_ID = "refugee_landmarks";

	public static final Codec<LandmarkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("mage_tower_placed", false).forGetter(data -> data.mageTowerPlaced),
			BlockPos.CODEC.optionalFieldOf("mage_tower_origin").forGetter(data -> Optional.ofNullable(data.mageTowerOrigin)),
			Codec.BOOL.optionalFieldOf("hell_furnace_placed", false).forGetter(data -> data.hellFurnacePlaced),
			BlockPos.CODEC.optionalFieldOf("hell_furnace_origin").forGetter(data -> Optional.ofNullable(data.hellFurnaceOrigin))
	).apply(instance, LandmarkData::fromCodec));

	public static final SavedDataType<LandmarkData> TYPE = new SavedDataType<>(
			FILE_ID,
			context -> new LandmarkData(),
			context -> LandmarkData.CODEC,
			null
	);

	private boolean mageTowerPlaced;
	private BlockPos mageTowerOrigin;
	private boolean hellFurnacePlaced;
	private BlockPos hellFurnaceOrigin;

	public LandmarkData() {
	}

	private static LandmarkData fromCodec(
			boolean mageTowerPlaced,
			Optional<BlockPos> mageTowerOrigin,
			boolean hellFurnacePlaced,
			Optional<BlockPos> hellFurnaceOrigin
	) {
		LandmarkData data = new LandmarkData();
		data.mageTowerPlaced = mageTowerPlaced;
		data.mageTowerOrigin = mageTowerOrigin.orElse(null);
		data.hellFurnacePlaced = hellFurnacePlaced;
		data.hellFurnaceOrigin = hellFurnaceOrigin.orElse(null);
		return data;
	}

	public static LandmarkData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean isMageTowerPlaced() {
		return mageTowerPlaced;
	}

	public BlockPos mageTowerOrigin() {
		return mageTowerOrigin;
	}

	public void markMageTower(BlockPos origin) {
		this.mageTowerPlaced = true;
		this.mageTowerOrigin = origin == null ? null : origin.immutable();
		setDirty();
	}

	public boolean isHellFurnacePlaced() {
		return hellFurnacePlaced;
	}

	public BlockPos hellFurnaceOrigin() {
		return hellFurnaceOrigin;
	}

	public void markHellFurnace(BlockPos origin) {
		this.hellFurnacePlaced = true;
		this.hellFurnaceOrigin = origin == null ? null : origin.immutable();
		setDirty();
	}
}
