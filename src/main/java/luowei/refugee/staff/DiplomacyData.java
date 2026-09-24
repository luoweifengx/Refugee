package luowei.refugee.staff;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 组织与未入组玩家之间的关系。缺省为中立，不落盘。
 */
public final class DiplomacyData extends SavedData {
	private static final String DATA_ID = "refugee_diplomacy";

	public static final Codec<DiplomacyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Bond.CODEC.listOf().optionalFieldOf("bonds", List.of()).forGetter(data -> List.copyOf(data.bonds))
	).apply(instance, DiplomacyData::fromCodec));

	public static final SavedDataType<DiplomacyData> TYPE = new SavedDataType<>(
			DATA_ID,
			context -> new DiplomacyData(),
			context -> DiplomacyData.CODEC,
			null
	);

	private final List<Bond> bonds = new ArrayList<>();

	public DiplomacyData() {
	}

	private static DiplomacyData fromCodec(List<Bond> bonds) {
		DiplomacyData data = new DiplomacyData();
		if (bonds != null) {
			data.bonds.addAll(bonds);
		}
		return data;
	}

	public static DiplomacyData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public Bond find(UUID left, UUID right) {
		UUID low = low(left, right);
		UUID high = high(left, right);
		for (Bond bond : bonds) {
			if (bond.low().equals(low) && bond.high().equals(high)) {
				return bond;
			}
		}
		return null;
	}

	public void put(Bond bond) {
		bonds.removeIf(existing -> existing.low().equals(bond.low()) && existing.high().equals(bond.high()));
		if (bond.stance() != 0 || bond.pending() != 0) {
			bonds.add(bond);
		}
		setDirty();
	}

	public List<Bond> bonds() {
		return bonds;
	}

	public static UUID low(UUID left, UUID right) {
		return left.toString().compareTo(right.toString()) <= 0 ? left : right;
	}

	public static UUID high(UUID left, UUID right) {
		return left.toString().compareTo(right.toString()) <= 0 ? right : left;
	}

	public record Bond(UUID low, UUID high, int stance, int pending, UUID pendingFrom) {
		public static final Codec<Bond> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				UUIDUtil.STRING_CODEC.fieldOf("low").forGetter(Bond::low),
				UUIDUtil.STRING_CODEC.fieldOf("high").forGetter(Bond::high),
				Codec.INT.optionalFieldOf("stance", 0).forGetter(Bond::stance),
				Codec.INT.optionalFieldOf("pending", 0).forGetter(Bond::pending),
				UUIDUtil.STRING_CODEC.optionalFieldOf("from", new UUID(0L, 0L)).forGetter(Bond::pendingFrom)
		).apply(instance, Bond::new));
	}
}
