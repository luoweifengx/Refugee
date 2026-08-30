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
 * 蓝图目录：{@code config/refugee/blueprints} 下的原版结构模板 {@code .nbt}。
 */
public final class BlueprintRegistry {
	public static final String DIRECTORY_NAME = "blueprints";
	public static final String CATALOG_FILE = "catalog.json";
	public static final String SAMPLE_FILE = "cobble_pad.nbt";
	public static final String OAK_CHECKER_FILE = "oak_checker.nbt";

	private static final Map<ResourceLocation, StructureTemplate> TEMPLATES = new LinkedHashMap<>();
	private static final Map<ResourceLocation, CompoundTag> TEMPLATE_NBTS = new LinkedHashMap<>();
	private static final List<BlueprintCatalogEntry> CATALOG = new ArrayList<>();

	private BlueprintRegistry() {
	}

	public static void register() {
		ensureDirectory();
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
				Refugee.LOGGER.info("Created blueprint directory {}", dir);
			}
			// Always rewrite known-generated samples so pos/size stay vanilla List-of-Int format.
			writeSampleNbt(dir.resolve(SAMPLE_FILE));
			writeOakCheckerNbt(dir.resolve(OAK_CHECKER_FILE));
			BuiltinBlueprints.writeAll(dir);
			ensureCatalogEntry(dir, "cobble_pad", "圆石垫（示例）");
			ensureCatalogEntry(dir, "oak_checker", "橡木棋盘 3x3");
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
		Refugee.LOGGER.info("Loaded {} blueprint(s) from {}", loaded, dir);
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
		PlayerBlueprints.clear();
	}

	private static boolean loadFile(Path file, HolderGetter<Block> blocks, Map<String, String> names) {
		String filename = file.getFileName().toString();
		String stem = stem(filename);
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
				目录会自带一批基础城墙/房屋/道路/哨塔。
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
		json.addProperty("cobble_pad", "圆石垫（示例）");
		json.addProperty("oak_checker", "橡木棋盘 3x3");
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

	private static void writeSampleNbt(Path path) throws IOException {
		BlueprintNbtWriter writer = new BlueprintNbtWriter(2, 1, 2);
		writer.fill(0, 0, 0, 1, 0, 1, BuiltinBlueprints.COBBLE);
		writer.write(path);
	}

	/**
	 * 3×3 地面：橡木原木与橡木木板交错（俯视 101 / 010 / 101，1=原木，0=木板）。
	 */
	private static void writeOakCheckerNbt(Path path) throws IOException {
		BlueprintNbtWriter writer = new BlueprintNbtWriter(3, 1, 3);
		for (int z = 0; z < 3; z++) {
			for (int x = 0; x < 3; x++) {
				if ((x + z) % 2 == 0) {
					writer.set(x, 0, z, BuiltinBlueprints.LOG, BlueprintNbtWriter.axisY());
				} else {
					writer.set(x, 0, z, BuiltinBlueprints.PLANKS);
				}
			}
		}
		writer.write(path);
	}
}
