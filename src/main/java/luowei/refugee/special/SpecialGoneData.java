package luowei.refugee.special;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 已死亡/转化的特殊难民 UUID。用于离线成员登录后清绑，并允许按原条件再来一个。
 */
public final class SpecialGoneData extends SavedData {
	private static final String DATA_ID = "refugee_special_gone";

	public static final Codec<SpecialGoneData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.listOf().optionalFieldOf("gone", List.of())
					.forGetter(data -> List.copyOf(data.gone))
	).apply(instance, SpecialGoneData::fromCodec));

	public static final SavedDataType<SpecialGoneData> TYPE = new SavedDataType<>(
			DATA_ID,
			context -> new SpecialGoneData(),
			context -> SpecialGoneData.CODEC,
			null
	);

	private final Set<UUID> gone = new LinkedHashSet<>();

	public SpecialGoneData() {
	}

	private static SpecialGoneData fromCodec(List<UUID> gone) {
		SpecialGoneData data = new SpecialGoneData();
		if (gone != null) {
			data.gone.addAll(gone);
		}
		return data;
	}

	public static SpecialGoneData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean isGone(UUID villagerId) {
		return villagerId != null && gone.contains(villagerId);
	}

	public void markGone(UUID villagerId) {
		if (villagerId != null && gone.add(villagerId)) {
			setDirty();
		}
	}
}
