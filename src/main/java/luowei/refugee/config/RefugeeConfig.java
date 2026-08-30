package luowei.refugee.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;

import luowei.refugee.Refugee;
import luowei.refugee.ai.RefugeeBuffState;

/**
 * {@code config/refugee.json}：入境、号角/钟、守卫、安顿与村民 buff。缺文件时写出默认值。
 */
public final class RefugeeConfig {
	public static final String FILE_NAME = "refugee.json";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static final List<BuffSpec> DEFAULT_IDLE_BUFFS = List.of(new BuffSpec("minecraft:regeneration", 0));
	private static final List<BuffSpec> DEFAULT_RANGED_BUFFS = List.of(new BuffSpec("minecraft:regeneration", 0));
	private static final List<BuffSpec> DEFAULT_MELEE_BUFFS = List.of(
			new BuffSpec("minecraft:regeneration", 1),
			new BuffSpec("minecraft:resistance", 0)
	);
	private static final List<BuffSpec> DEFAULT_BUILDER_BUFFS = List.of(new BuffSpec("minecraft:regeneration", 0));
	private static final List<ResourceLocation> DEFAULT_IMMIGRATION_DIMENSIONS = List.of(
			ResourceLocation.parse("minecraft:overworld")
	);
	private static final List<ImmigrationTier> DEFAULT_IMMIGRATION_TIERS = List.of(
			new ImmigrationTier(5, 0.20, 3, 5),
			new ImmigrationTier(12, 0.2286, 6, 8),
			new ImmigrationTier(20, 0.24, 10, 15),
			new ImmigrationTier(50, 0.32, 20, 30),
			new ImmigrationTier(100, 0.40, 50, 50),
			new ImmigrationTier(Integer.MAX_VALUE, 0.8333, 60, 60)
	);

	public static int immigrationIntervalTicks = 1000;
	public static List<ResourceLocation> immigrationDimensionWhitelist = DEFAULT_IMMIGRATION_DIMENSIONS;
	public static List<ImmigrationTier> immigrationTiers = DEFAULT_IMMIGRATION_TIERS;
	public static double hornBellRadius = 24.0;
	public static double guardRadius = 16.0;
	public static double guardReturnWalkDistance = 20.0;
	public static double guardReturnTeleportDistance = 50.0;
	public static int guardCombatScanIntervalTicks = 20;
	public static int settleChunkRadius = 1;
	public static int buildPlaceIntervalTicks = 20;
	public static double followSpeed = 0.55;
	public static double guardWalkSpeed = 0.5;
	public static int rangedAttackIntervalTicks = 40;
	public static int meleeAttackIntervalTicks = 10;
	public static double builderWalkSpeed = 0.45;
	public static int starterRefugeeCount = 8;
	public static int importMaxAxis = 32;
	public static int importMaxVolume = 4096;

	public static List<BuffSpec> idleBuffs = DEFAULT_IDLE_BUFFS;
	public static List<BuffSpec> rangedBuffs = DEFAULT_RANGED_BUFFS;
	public static List<BuffSpec> meleeBuffs = DEFAULT_MELEE_BUFFS;
	public static List<BuffSpec> builderBuffs = DEFAULT_BUILDER_BUFFS;

	private static final Map<RefugeeBuffState, List<ResolvedBuff>> resolvedBuffs = new EnumMap<>(RefugeeBuffState.class);
	private static Set<Holder<MobEffect>> managedEffects = Set.of();

	private RefugeeConfig() {
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		try {
			if (!Files.isRegularFile(path)) {
				write(path);
				Refugee.LOGGER.info("Wrote default refugee config to {}", path);
			} else {
				try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
					JsonElement parsed = JsonParser.parseReader(reader);
					if (parsed != null && parsed.isJsonObject()) {
						apply(parsed.getAsJsonObject());
					}
				}
				write(path);
				Refugee.LOGGER.info("Loaded refugee config from {}", path);
			}
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to load {}; using defaults", FILE_NAME, exception);
		}
		resolveBuffs();
	}

	public static List<ResolvedBuff> resolvedBuffs(RefugeeBuffState state) {
		List<ResolvedBuff> buffs = resolvedBuffs.get(state);
		return buffs == null ? List.of() : buffs;
	}

	public static boolean isManagedEffect(Holder<MobEffect> effect) {
		return managedEffects.contains(effect);
	}

	public static boolean isImmigrationDimension(ServerLevel level) {
		if (level == null) {
			return false;
		}
		ResourceLocation id = level.dimension().location();
		for (ResourceLocation allowed : immigrationDimensionWhitelist) {
			if (allowed.equals(id)) {
				return true;
			}
		}
		return false;
	}

	public static ImmigrationTier immigrationTier(int ownedChunks) {
		for (ImmigrationTier tier : immigrationTiers) {
			if (ownedChunks <= tier.maxOwned()) {
				return tier;
			}
		}
		return immigrationTiers.isEmpty() ? null : immigrationTiers.get(immigrationTiers.size() - 1);
	}

	private static void apply(JsonObject json) {
		immigrationIntervalTicks = readIntAtLeast(json, "immigrationIntervalTicks", immigrationIntervalTicks, 1);
		immigrationDimensionWhitelist = readDimensionWhitelist(json);
		immigrationTiers = readImmigrationTiers(json);
		hornBellRadius = readDoubleAtLeast(json, "hornBellRadius", hornBellRadius, 1.0);
		guardRadius = readDoubleAtLeast(json, "guardRadius", guardRadius, 1.0);
		guardReturnWalkDistance = readDoubleAtLeast(json, "guardReturnWalkDistance", guardReturnWalkDistance, 1.0);
		guardReturnTeleportDistance = readDoubleAtLeast(json, "guardReturnTeleportDistance", guardReturnTeleportDistance, 1.0);
		guardCombatScanIntervalTicks = readIntAtLeast(json, "guardCombatScanIntervalTicks", guardCombatScanIntervalTicks, 1);
		settleChunkRadius = readIntAtLeast(json, "settleChunkRadius", settleChunkRadius, 0);
		buildPlaceIntervalTicks = readIntAtLeast(json, "buildPlaceIntervalTicks", buildPlaceIntervalTicks, 1);
		followSpeed = readDoubleAtLeast(json, "followSpeed", followSpeed, 0.05);
		guardWalkSpeed = readDoubleAtLeast(json, "guardWalkSpeed", guardWalkSpeed, 0.05);
		rangedAttackIntervalTicks = readIntAtLeast(json, "rangedAttackIntervalTicks", rangedAttackIntervalTicks, 1);
		meleeAttackIntervalTicks = readIntAtLeast(json, "meleeAttackIntervalTicks", meleeAttackIntervalTicks, 1);
		builderWalkSpeed = readDoubleAtLeast(json, "builderWalkSpeed", builderWalkSpeed, 0.05);
		starterRefugeeCount = readIntAtLeast(json, "starterRefugeeCount", starterRefugeeCount, 0);
		importMaxAxis = readIntAtLeast(json, "importMaxAxis", importMaxAxis, 1);
		importMaxVolume = readIntAtLeast(json, "importMaxVolume", importMaxVolume, 1);
		applyBuffs(json);
	}

	private static void write(Path path) throws IOException {
		Files.createDirectories(path.getParent());
		JsonObject json = new JsonObject();
		json.addProperty("_comment", "Refugee immigration (dimension whitelist, dawn P_day quota, daytime drip), horn/bell, guard, settlement and villager buffs. Restart after editing.");
		json.addProperty("immigrationIntervalTicks", immigrationIntervalTicks);
		json.add("immigrationDimensionWhitelist", writeDimensionWhitelist(immigrationDimensionWhitelist));
		json.add("immigrationTiers", writeImmigrationTiers(immigrationTiers));
		json.addProperty("hornBellRadius", hornBellRadius);
		json.addProperty("guardRadius", guardRadius);
		json.addProperty("guardReturnWalkDistance", guardReturnWalkDistance);
		json.addProperty("guardReturnTeleportDistance", guardReturnTeleportDistance);
		json.addProperty("guardCombatScanIntervalTicks", guardCombatScanIntervalTicks);
		json.addProperty("settleChunkRadius", settleChunkRadius);
		json.addProperty("buildPlaceIntervalTicks", buildPlaceIntervalTicks);
		json.addProperty("followSpeed", followSpeed);
		json.addProperty("guardWalkSpeed", guardWalkSpeed);
		json.addProperty("rangedAttackIntervalTicks", rangedAttackIntervalTicks);
		json.addProperty("meleeAttackIntervalTicks", meleeAttackIntervalTicks);
		json.addProperty("builderWalkSpeed", builderWalkSpeed);
		json.addProperty("starterRefugeeCount", starterRefugeeCount);
		json.addProperty("importMaxAxis", importMaxAxis);
		json.addProperty("importMaxVolume", importMaxVolume);
		JsonObject villagerBuffs = new JsonObject();
		villagerBuffs.add("idle", writeBuffList(idleBuffs));
		villagerBuffs.add("ranged", writeBuffList(rangedBuffs));
		villagerBuffs.add("melee", writeBuffList(meleeBuffs));
		villagerBuffs.add("builder", writeBuffList(builderBuffs));
		json.add("villagerBuffs", villagerBuffs);
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			GSON.toJson(json, writer);
			writer.write(System.lineSeparator());
		}
	}

	private static int readIntAtLeast(JsonObject json, String key, int fallback, int min) {
		if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		return Math.max(min, json.get(key).getAsInt());
	}

	private static double readDoubleAtLeast(JsonObject json, String key, double fallback, double min) {
		if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		return Math.max(min, json.get(key).getAsDouble());
	}

	private static double readChance(JsonObject json, String key, double fallback) {
		if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		return Math.min(1.0, Math.max(0.0, json.get(key).getAsDouble()));
	}

	private static List<ResourceLocation> readDimensionWhitelist(JsonObject json) {
		if (!json.has("immigrationDimensionWhitelist") || !json.get("immigrationDimensionWhitelist").isJsonArray()) {
			return DEFAULT_IMMIGRATION_DIMENSIONS;
		}
		List<ResourceLocation> result = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray("immigrationDimensionWhitelist")) {
			if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
				Refugee.LOGGER.warn("immigrationDimensionWhitelist skipped non-string entry");
				continue;
			}
			String raw = element.getAsString();
			if (raw == null || raw.isBlank()) {
				Refugee.LOGGER.warn("immigrationDimensionWhitelist skipped blank entry");
				continue;
			}
			ResourceLocation id = ResourceLocation.tryParse(raw.trim());
			if (id == null) {
				Refugee.LOGGER.warn("immigrationDimensionWhitelist skipped invalid id {}", raw);
				continue;
			}
			if (!result.contains(id)) {
				result.add(id);
			}
		}
		return List.copyOf(result);
	}

	private static JsonArray writeDimensionWhitelist(List<ResourceLocation> ids) {
		JsonArray array = new JsonArray();
		for (ResourceLocation id : ids) {
			array.add(id.toString());
		}
		return array;
	}

	private static List<ImmigrationTier> readImmigrationTiers(JsonObject json) {
		if (!json.has("immigrationTiers") || !json.get("immigrationTiers").isJsonArray()) {
			return DEFAULT_IMMIGRATION_TIERS;
		}
		List<ImmigrationTier> result = new ArrayList<>();
		for (JsonElement element : json.getAsJsonArray("immigrationTiers")) {
			if (!element.isJsonObject()) {
				Refugee.LOGGER.warn("immigrationTiers skipped non-object entry");
				continue;
			}
			JsonObject obj = element.getAsJsonObject();
			int maxOwned = obj.has("maxOwned")
					? readIntAtLeast(obj, "maxOwned", Integer.MAX_VALUE, 0)
					: Integer.MAX_VALUE;
			double dayChance = readChance(obj, "dayChance", 0.0);
			int minCount = readIntAtLeast(obj, "minCount", 0, 0);
			int maxCount = readIntAtLeast(obj, "maxCount", minCount, 0);
			if (minCount > maxCount) {
				int swap = minCount;
				minCount = maxCount;
				maxCount = swap;
			}
			result.add(new ImmigrationTier(maxOwned, dayChance, minCount, maxCount));
		}
		if (result.isEmpty()) {
			return DEFAULT_IMMIGRATION_TIERS;
		}
		result.sort(Comparator.comparingInt(ImmigrationTier::maxOwned));
		return List.copyOf(result);
	}

	private static JsonArray writeImmigrationTiers(List<ImmigrationTier> tiers) {
		JsonArray array = new JsonArray();
		for (ImmigrationTier tier : tiers) {
			JsonObject obj = new JsonObject();
			if (tier.maxOwned() < Integer.MAX_VALUE) {
				obj.addProperty("maxOwned", tier.maxOwned());
			}
			obj.addProperty("dayChance", tier.dayChance());
			obj.addProperty("minCount", tier.minCount());
			obj.addProperty("maxCount", tier.maxCount());
			array.add(obj);
		}
		return array;
	}

	private static void applyBuffs(JsonObject json) {
		JsonObject buffs = json.has("villagerBuffs") && json.get("villagerBuffs").isJsonObject()
				? json.getAsJsonObject("villagerBuffs")
				: null;
		idleBuffs = readBuffList(buffs, "idle", DEFAULT_IDLE_BUFFS);
		rangedBuffs = readBuffList(buffs, "ranged", DEFAULT_RANGED_BUFFS);
		meleeBuffs = readBuffList(buffs, "melee", DEFAULT_MELEE_BUFFS);
		builderBuffs = readBuffList(buffs, "builder", DEFAULT_BUILDER_BUFFS);
	}

	private static List<BuffSpec> readBuffList(JsonObject buffs, String key, List<BuffSpec> fallback) {
		if (buffs == null || !buffs.has(key) || !buffs.get(key).isJsonArray()) {
			return fallback;
		}
		List<BuffSpec> result = new ArrayList<>();
		for (JsonElement element : buffs.getAsJsonArray(key)) {
			if (!element.isJsonObject()) {
				Refugee.LOGGER.warn("villagerBuffs.{} skipped non-object entry", key);
				continue;
			}
			JsonObject obj = element.getAsJsonObject();
			if (!obj.has("effect") || !obj.get("effect").isJsonPrimitive() || !obj.get("effect").getAsJsonPrimitive().isString()) {
				Refugee.LOGGER.warn("villagerBuffs.{} skipped entry without effect id", key);
				continue;
			}
			String effect = obj.get("effect").getAsString();
			if (effect == null || effect.isBlank()) {
				Refugee.LOGGER.warn("villagerBuffs.{} skipped blank effect id", key);
				continue;
			}
			int amplifier = 0;
			if (obj.has("amplifier") && obj.get("amplifier").isJsonPrimitive() && obj.get("amplifier").getAsJsonPrimitive().isNumber()) {
				amplifier = Math.max(0, obj.get("amplifier").getAsInt());
			}
			result.add(new BuffSpec(effect, amplifier));
		}
		return List.copyOf(result);
	}

	private static JsonArray writeBuffList(List<BuffSpec> specs) {
		JsonArray array = new JsonArray();
		for (BuffSpec spec : specs) {
			JsonObject obj = new JsonObject();
			obj.addProperty("effect", spec.effect());
			obj.addProperty("amplifier", spec.amplifier());
			array.add(obj);
		}
		return array;
	}

	private static void resolveBuffs() {
		resolvedBuffs.put(RefugeeBuffState.IDLE, resolve(idleBuffs, "idle"));
		resolvedBuffs.put(RefugeeBuffState.RANGED, resolve(rangedBuffs, "ranged"));
		resolvedBuffs.put(RefugeeBuffState.MELEE, resolve(meleeBuffs, "melee"));
		resolvedBuffs.put(RefugeeBuffState.BUILDER, resolve(builderBuffs, "builder"));
		Set<Holder<MobEffect>> managed = new HashSet<>();
		for (List<ResolvedBuff> list : resolvedBuffs.values()) {
			for (ResolvedBuff buff : list) {
				managed.add(buff.effect());
			}
		}
		managedEffects = Set.copyOf(managed);
	}

	private static List<ResolvedBuff> resolve(List<BuffSpec> specs, String typeKey) {
		LinkedHashMap<Holder<MobEffect>, Integer> map = new LinkedHashMap<>();
		for (BuffSpec spec : specs) {
			ResourceLocation id = ResourceLocation.tryParse(spec.effect());
			if (id == null) {
				Refugee.LOGGER.warn("villagerBuffs.{} skipped invalid effect id {}", typeKey, spec.effect());
				continue;
			}
			ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, id);
			Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(key);
			if (holder.isEmpty()) {
				Refugee.LOGGER.warn("villagerBuffs.{} skipped unknown effect {}", typeKey, id);
				continue;
			}
			map.put(holder.get(), spec.amplifier());
		}
		List<ResolvedBuff> result = new ArrayList<>(map.size());
		map.forEach((effect, amplifier) -> result.add(new ResolvedBuff(effect, amplifier)));
		return List.copyOf(result);
	}

	/**
	 * 持有区块上限（含）、日选中率 P日、入境人数闭区间。缺 {@code maxOwned} 表示无上限（最后一档）。
	 */
	public record ImmigrationTier(int maxOwned, double dayChance, int minCount, int maxCount) {
	}

	public record BuffSpec(String effect, int amplifier) {
	}

	public record ResolvedBuff(Holder<MobEffect> effect, int amplifier) {
	}
}
