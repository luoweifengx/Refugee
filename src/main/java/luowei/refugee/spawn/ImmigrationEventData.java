package luowei.refugee.spawn;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 入境事件上次触发的游戏日，避免每天 4000 之后每 tick 重复刷。
 */
public final class ImmigrationEventData extends SavedData {
	public static final String FILE_ID = "refugee_immigration_event";

	public static final Codec<ImmigrationEventData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.optionalFieldOf("last_fired_day", -1L).forGetter(data -> data.lastFiredDay)
	).apply(instance, ImmigrationEventData::fromCodec));

	public static final SavedDataType<ImmigrationEventData> TYPE = new SavedDataType<>(
			FILE_ID,
			context -> new ImmigrationEventData(),
			context -> ImmigrationEventData.CODEC,
			null
	);

	private long lastFiredDay = -1L;

	public ImmigrationEventData() {
	}

	private static ImmigrationEventData fromCodec(long lastFiredDay) {
		ImmigrationEventData data = new ImmigrationEventData();
		data.lastFiredDay = lastFiredDay;
		return data;
	}

	public static ImmigrationEventData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public long lastFiredDay() {
		return lastFiredDay;
	}

	public void markFired(long dayNumber) {
		this.lastFiredDay = dayNumber;
		setDirty();
	}
}
