package luowei.refugee.blueprint;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import luowei.refugee.Refugee;

/**
 * 基础可造蓝图：{@code config/refugee/blueprints} 下的原版结构模板 {@code .nbt}。
 * 世界生成建筑在 {@link WorldgenBlueprints}，玩家导入在 {@link PlayerBlueprints}。
 */
public final class BlueprintRegistry {
	public static final String DIRECTORY_NAME = "blueprints";
	public static final String CATALOG_FILE = "catalog.json";
	private static final List<String> DEBUG_STEMS = List.of("cobble_pad", "oak_checker");

	private static final Map<ResourceLocation, StructureTemplate> TEMPLATES = new LinkedHashMap<>();
	private static final Map<ResourceLocation, CompoundTag> TEMPLATE_NBTS = new LinkedHashMap<>();
	private static final List<BlueprintCatalogEntry> CATALOG = new ArrayList<>();

	private BlueprintRegistry() {
	}

	public static void register() {
		ensureDirectory();
		WorldgenBlueprints.ensureDirectory();
		ServerLifecycleEvents.SERVER_STARTED.register(BlueprintRegistry::reload);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	public static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve(Refugee.MOD_ID).resolve(DIRECTORY_NAME);
	}

	public static void ensureDirectory() {
		Path dir = directory();
		boolean created = Files.notExists(dir);
		try {
			Files.createDirectories(dir);
			if (created) {
				writeReadme(dir);
				writeDefaultCatalog(dir);
				Refugee.LOGGER.debug("Created blueprint directory {}", dir);
			}
			BuiltinBlueprints.writeAll(dir);
			removeExcludedBlueprints(dir);
			for (Map.Entry<String, String> entry : BuiltinBlueprints.displayNames().entrySet()) {
				ensureCatalogEntry(dir, entry.getKey(), entry.getValue());
			}
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to prepare blueprint directory {}", dir, exception);
		}
	}

	public static int reload(MinecraftServer server) {
		ensureDirectory();
		TEMPLATES.clear();
		TEMPLATE_NBTS.clear();
		CATALOG.clear();
		PlayerBlueprints.clear();
		if (server == null) {
			return 0;
		}
		HolderGetter<Block> blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
		Map<String, String> names = readCatalogNames();
		Path dir = directory();
		int loaded = 0;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.nbt")) {
			for (Path file : stream) {
				if (loadFile(file, blocks, names)) {
					loaded++;
				}
			}
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to scan blueprint directory {}", dir, exception);
		}
		Refugee.LOGGER.debug("Loaded {} blueprint(s) from {}", loaded, dir);
		WorldgenBlueprints.reload(server);
		PlayerBlueprints.reload(server);
		return loaded + PlayerBlueprints.catalog(null).size();
	}

	public static StructureTemplate get(ResourceLocation id) {
		if (id == null) {
			return null;
		}
		StructureTemplate template = TEMPLATES.get(id);
		return template != null ? template : PlayerBlueprints.get(id);
	}

	public static boolean contains(ResourceLocation id) {
		return get(id) != null;
	}

	public static boolean visibleTo(UUID playerId, ResourceLocation id) {
		if (id == null) {
			return false;
		}
		if (TEMPLATES.containsKey(id)) {
			return true;
		}
		return PlayerBlueprints.owns(playerId, id);
	}

	public static List<BlueprintCatalogEntry> builtinCatalog() {
		return List.copyOf(CATALOG);
	}

	public static List<BlueprintCatalogEntry> catalog() {
		return catalog(null);
	}

	public static List<BlueprintCatalogEntry> catalog(UUID playerId) {
		List<BlueprintCatalogEntry> entries = new ArrayList<>();
		if (playerId != null) {
			entries.addAll(PlayerBlueprints.catalog(playerId));
		}
		entries.addAll(CATALOG);
		return entries;
	}

	/** 该玩家可见的结构 NBT（自带 + 自己导入的）。 */
	public static Map<ResourceLocation, CompoundTag> templateNbts(UUID playerId) {
		Map<ResourceLocation, CompoundTag> nbts = new LinkedHashMap<>(TEMPLATE_NBTS);
		if (playerId != null) {
			nbts.putAll(PlayerBlueprints.templateNbts(playerId));
		}
		return nbts;
	}

	/** 原始结构 NBT，供客户端幽灵预览同步。 */
	public static Map<ResourceLocation, CompoundTag> templateNbts() {
		return Map.copyOf(TEMPLATE_NBTS);
	}

	private static void clear() {
		TEMPLATES.clear();
		TEMPLATE_NBTS.clear();
		CATALOG.clear();
		WorldgenBlueprints.clear();
		PlayerBlueprints.clear();
	}

	private static boolean loadFile(Path file, HolderGetter<Block> blocks, Map<String, String> names) {
		String filename = file.getFileName().toString();
		String stem = stem(filename);
		if (isExcludedStem(stem)) {
			return false;
		}
		ResourceLocation id = idForStem(stem);
		if (id == null) {
			Refugee.LOGGER.warn("Skip blueprint with invalid name: {}", filename);
			return false;
		}
		try {
			CompoundTag nbt = readNbt(file);
			StructureTemplate template = new StructureTemplate();
			template.load(blocks, nbt);
			TEMPLATES.put(id, template);
			TEMPLATE_NBTS.put(id, nbt.copy());
			CATALOG.add(new BlueprintCatalogEntry(id, displayName(stem, filename, id, names)));
			return true;
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to load blueprint {}", file, exception);
			return false;
		}
	}

	private static CompoundTag readNbt(Path path) throws IOException {
		try {
			return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
		} catch (IOException compressedFailed) {
			return NbtIo.read(path);
		}
	}

	private static Map<String, String> readCatalogNames() {
		Path catalog = directory().resolve(CATALOG_FILE);
		if (!Files.isRegularFile(catalog)) {
			return Map.of();
		}
		try (Reader reader = Files.newBufferedReader(catalog, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (parsed == null || !parsed.isJsonObject()) {
				return Map.of();
			}
			JsonObject json = parsed.getAsJsonObject();
			JsonObject names = json;
			if (json.has("names") && json.get("names").isJsonObject()) {
				names = json.getAsJsonObject("names");
			}
			Map<String, String> result = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> entry : names.entrySet()) {
				if (entry.getKey().startsWith("_")) {
					continue;
				}
				if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
					result.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
			return result;
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to read {}", catalog, exception);
			return Map.of();
		}
	}

	private static String displayName(String stem, String filename, ResourceLocation id, Map<String, String> names) {
		String named = names.get(id.toString());
		if (named == null) {
			named = names.get(filename);
		}
		if (named == null) {
			named = names.get(stem);
		}
		if (named != null && !named.isBlank()) {
			return named;
		}
		return stem.replace('_', ' ');
	}

	private static String stem(String filename) {
		int dot = filename.lastIndexOf('.');
		return dot <= 0 ? filename : filename.substring(0, dot);
	}

	private static ResourceLocation idForStem(String stem) {
		StringBuilder path = new StringBuilder();
		for (char c : stem.toLowerCase(Locale.ROOT).toCharArray()) {
			if (ResourceLocation.validPathChar(c)) {
				path.append(c);
			} else if (c == ' ' || c == '\\' || c == '/') {
				path.append('_');
			}
		}
		if (path.isEmpty()) {
			return null;
		}
		return Refugee.id(path.toString());
	}

	private static void writeReadme(Path dir) throws IOException {
		Path readme = dir.resolve("README.txt");
		String text = """
				把原版结构方块导出的 .nbt 放到本目录，然后执行 /refugee blueprint reload。
				本目录只放基础可造建筑（城墙/房屋/道路/哨塔/仓库/农田等）。
				世界生成建筑在 config/refugee/worldgen/。
				玩家导入的结构存在世界存档 refugee/blueprints/<玩家UUID>/，互不可见。
				Drop vanilla structure-block .nbt files here, then run /refugee blueprint reload.

				id 规则 / id rule: hut.nbt → refugee:hut
				可选 catalog.json 为文件名提供显示名。
				Optional catalog.json maps file stems to display names.
				""";
		Files.writeString(readme, text, StandardCharsets.UTF_8);
	}

	private static void writeDefaultCatalog(Path dir) throws IOException {
		Path catalog = dir.resolve(CATALOG_FILE);
		JsonObject json = new JsonObject();
		json.addProperty("_comment", "Keys are file names without .nbt (or refugee:id). Values are display names.");
		for (Map.Entry<String, String> entry : BuiltinBlueprints.displayNames().entrySet()) {
			json.addProperty(entry.getKey(), entry.getValue());
		}
		try (Writer writer = Files.newBufferedWriter(catalog, StandardCharsets.UTF_8)) {
			new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json, writer);
			writer.write(System.lineSeparator());
		}
	}

	private static void ensureCatalogEntry(Path dir, String key, String displayName) {
		Path catalog = dir.resolve(CATALOG_FILE);
		try {
			JsonObject json;
			if (Files.isRegularFile(catalog)) {
				try (Reader reader = Files.newBufferedReader(catalog, StandardCharsets.UTF_8)) {
					JsonElement parsed = JsonParser.parseReader(reader);
					json = parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
				}
			} else {
				json = new JsonObject();
				json.addProperty("_comment", "Keys are file names without .nbt (or refugee:id). Values are display names.");
			}
			if (json.has(key)) {
				return;
			}
			json.addProperty(key, displayName);
			try (Writer writer = Files.newBufferedWriter(catalog, StandardCharsets.UTF_8)) {
				new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json, writer);
				writer.write(System.lineSeparator());
			}
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to update catalog entry {} in {}", key, catalog, exception);
		}
	}

	private static boolean isExcludedStem(String stem) {
		return DEBUG_STEMS.contains(stem) || WorldgenBlueprints.isStem(stem);
	}

	private static List<String> excludedStems() {
		List<String> stems = new ArrayList<>(DEBUG_STEMS);
		stems.addAll(WorldgenBlueprints.stems());
		return stems;
	}

	private static void removeExcludedBlueprints(Path dir) {
		for (String stem : excludedStems()) {
			try {
				Files.deleteIfExists(dir.resolve(stem + ".nbt"));
			} catch (IOException exception) {
				Refugee.LOGGER.warn("Failed to delete non-catalog blueprint {}.nbt", stem, exception);
			}
		}
		Path catalog = dir.resolve(CATALOG_FILE);
		if (!Files.isRegularFile(catalog)) {
			return;
		}
		try {
			JsonObject json;
			try (Reader reader = Files.newBufferedReader(catalog, StandardCharsets.UTF_8)) {
				JsonElement parsed = JsonParser.parseReader(reader);
				json = parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
			}
			if (json == null) {
				return;
			}
			boolean changed = false;
			for (String stem : excludedStems()) {
				changed |= json.remove(stem) != null;
				changed |= json.remove(Refugee.id(stem).toString()) != null;
			}
			if (!changed) {
				return;
			}
			try (Writer writer = Files.newBufferedWriter(catalog, StandardCharsets.UTF_8)) {
				new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json, writer);
				writer.write(System.lineSeparator());
			}
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to strip non-catalog entries from {}", catalog, exception);
		}
	}
}
