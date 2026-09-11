package luowei.refugee.crusader;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 十字军波次：本周期已出兵的主体，以及下一次 4000 点名时刻。
 */
public final class CrusaderSavedData extends SavedData {
	private static final String DATA_ID = "refugee_crusaders";

	private static final Codec<SquadKey> SQUAD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("dimension").forGetter(SquadKey::dimension),
			UUIDUtil.CODEC.fieldOf("subject").forGetter(SquadKey::subject)
	).apply(instance, SquadKey::new));

	public static final Codec<CrusaderSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.optionalFieldOf("last_despawn_day", -1L).forGetter(data -> data.lastDespawnDay),
			Codec.LONG.optionalFieldOf("next_despawn_at", 0L).forGetter(data -> data.nextDespawnAt),
			SQUAD_CODEC.listOf().optionalFieldOf("squads", List.of()).forGetter(data -> List.copyOf(data.squads))
	).apply(instance, CrusaderSavedData::fromCodec));

	public static final SavedDataType<CrusaderSavedData> TYPE = new SavedDataType<>(
			DATA_ID,
			context -> new CrusaderSavedData(),
			context -> CrusaderSavedData.CODEC,
			null
	);

	private long lastDespawnDay = -1L;
	private long nextDespawnAt;
	private final Set<SquadKey> squads = new LinkedHashSet<>();

	public CrusaderSavedData() {
	}

	private static CrusaderSavedData fromCodec(long lastDespawnDay, long nextDespawnAt, List<SquadKey> squads) {
		CrusaderSavedData data = new CrusaderSavedData();
		data.lastDespawnDay = lastDespawnDay;
		data.nextDespawnAt = nextDespawnAt;
		if (squads != null) {
			data.squads.addAll(squads);
		}
		return data;
	}

	public static CrusaderSavedData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public long lastDespawnDay() {
		return lastDespawnDay;
	}

	public long nextDespawnAt() {
		return nextDespawnAt;
	}

	public boolean hasSquad(ResourceKey<Level> dimension, UUID subjectId) {
		return dimension != null && subjectId != null
				&& squads.contains(new SquadKey(dimension.location().toString(), subjectId));
	}

	public boolean hasAnySquad() {
		return !squads.isEmpty();
	}

	public void markSquad(ResourceKey<Level> dimension, UUID subjectId, long dayTime) {
		if (dimension == null || subjectId == null) {
			return;
		}
		boolean added = squads.add(new SquadKey(dimension.location().toString(), subjectId));
		if (nextDespawnAt <= 0L) {
			nextDespawnAt = CrusaderService.nextMorning4000(dayTime);
			added = true;
		}
		if (added) {
			setDirty();
		}
	}

	public void markDespawned(long dayNumber) {
		lastDespawnDay = dayNumber;
		nextDespawnAt = 0L;
		squads.clear();
		setDirty();
	}

	public record SquadKey(String dimension, UUID subject) {
	}
}
