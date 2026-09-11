package luowei.refugee.blueprint;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import luowei.refugee.Refugee;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.zone.AreaBox;

/**
 * 玩家导入的结构：世界存档 {@code refugee/blueprints/<UUID>/}，与基础目录、世界生成目录分开。
 */
public final class PlayerBlueprints {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String CATALOG_FILE = "catalog.json";
	private static final Map<UUID, PlayerCatalog> BY_PLAYER = new HashMap<>();
	private static final Map<ResourceLocation, StructureTemplate> TEMPLATES = new LinkedHashMap<>();
	private static final Map<ResourceLocation, CompoundTag> NBTS = new LinkedHashMap<>();
	private static final Map<ResourceLocation, UUID> OWNERS = new HashMap<>();

	private PlayerBlueprints() {
	}

	public static void reload(MinecraftServer server) {
		TEMPLATES.clear();
		NBTS.clear();
		OWNERS.clear();
		BY_PLAYER.clear();
		if (server == null) {
			return;
		}
		Path root = root(server);
		if (!Files.isDirectory(root)) {
			return;
		}
		HolderGetter<Block> blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
			for (Path dir : stream) {
				if (!Files.isDirectory(dir)) {
					continue;
				}
				UUID playerId = parseUuid(dir.getFileName().toString());
				if (playerId == null) {
					continue;
				}
				loadPlayer(playerId, dir, blocks);
			}
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to scan player blueprints {}", root, exception);
		}
	}

	public static void clear() {
		TEMPLATES.clear();
		NBTS.clear();
		OWNERS.clear();
		BY_PLAYER.clear();
	}

	public static StructureTemplate get(ResourceLocation id) {
		return id == null ? null : TEMPLATES.get(id);
	}

	public static boolean owns(UUID playerId, ResourceLocation id) {
		if (playerId == null || id == null) {
			return false;
		}
		return playerId.equals(OWNERS.get(id));
	}

	public static boolean visibleTo(MinecraftServer server, UUID playerId, ResourceLocation id) {
		if (id == null) {
			return false;
		}
		UUID owner = OWNERS.get(id);
		if (owner == null) {
			return false;
		}
		return PbsAdapter.shareGroup(server, playerId).contains(owner);
	}

	public static List<BlueprintCatalogEntry> catalog(UUID playerId) {
		PlayerCatalog catalog = playerId == null ? null : BY_PLAYER.get(playerId);
		return catalog == null ? List.of() : List.copyOf(catalog.entries);
	}

	public static List<BlueprintCatalogEntry> catalog(MinecraftServer server, UUID playerId) {
		Set<ResourceLocation> seen = new LinkedHashSet<>();
		List<BlueprintCatalogEntry> entries = new ArrayList<>();
		for (UUID memberId : PbsAdapter.shareGroup(server, playerId)) {
			for (BlueprintCatalogEntry entry : catalog(memberId)) {
				if (seen.add(entry.id())) {
					entries.add(entry);
				}
			}
		}
		return entries;
	}

	public static Map<ResourceLocation, CompoundTag> templateNbts(UUID playerId) {
		PlayerCatalog catalog = playerId == null ? null : BY_PLAYER.get(playerId);
		if (catalog == null || catalog.nbts.isEmpty()) {
			return Map.of();
		}
		return Map.copyOf(catalog.nbts);
	}

	public static Map<ResourceLocation, CompoundTag> templateNbts(MinecraftServer server, UUID playerId) {
		Map<ResourceLocation, CompoundTag> nbts = new LinkedHashMap<>();
		for (UUID memberId : PbsAdapter.shareGroup(server, playerId)) {
			nbts.putAll(templateNbts(memberId));
		}
		return nbts;
	}

	public static boolean nameTaken(MinecraftServer server, UUID playerId, String displayName) {
		String needle = normalizeName(displayName);
		if (needle.isEmpty()) {
			return false;
		}
		for (BlueprintCatalogEntry entry : BlueprintRegistry.builtinCatalog()) {
			if (needle.equalsIgnoreCase(entry.displayName())) {
				return true;
			}
		}
		for (BlueprintCatalogEntry entry : catalog(server, playerId)) {
			if (needle.equalsIgnoreCase(entry.displayName())) {
				return true;
			}
		}
		return false;
	}

	public static boolean nameTaken(UUID playerId, String displayName) {
		return nameTaken(null, playerId, displayName);
	}

	public record ImportResult(ImportStatus status, ResourceLocation id) {
		private static ImportResult of(ImportStatus status) {
			return new ImportResult(status, null);
		}
	}

	public static ImportResult capture(ServerPlayer player, AreaBox box, String rawName) {
		if (player == null || box == null || !(player.level() instanceof ServerLevel level)) {
			return ImportResult.of(ImportStatus.FAILED);
		}
		String name = normalizeName(rawName);
		if (name.isEmpty()) {
			return ImportResult.of(ImportStatus.EMPTY_NAME);
		}
		if (nameTaken(player.getServer(), player.getUUID(), name)) {
			return ImportResult.of(ImportStatus.DUPLICATE_NAME);
		}
		ImportStatus size = checkSize(box);
		if (size != ImportStatus.OK) {
			return ImportResult.of(size);
		}
		if (!areaLoaded(level, box)) {
			return ImportResult.of(ImportStatus.UNLOADED);
		}
		int sx = box.sizeX();
		int sy = box.sizeY();
		int sz = box.sizeZ();
		BlueprintNbtWriter writer = new BlueprintNbtWriter(sx, sy, sz);
		BlockPos min = box.min();
		for (int y = 0; y < sy; y++) {
			for (int z = 0; z < sz; z++) {
				for (int x = 0; x < sx; x++) {
					BlockPos world = min.offset(x, y, z);
					BlockState state = level.getBlockState(world);
					if (BlueprintBlocks.shouldSkip(state)) {
						continue;
					}
					ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
					if (blockId == null) {
						continue;
					}
					writer.set(x, y, z, blockId.toString(), propertiesOf(state));
				}
			}
		}
		if (writer.isEmpty()) {
			return ImportResult.of(ImportStatus.EMPTY);
		}
		UUID playerId = player.getUUID();
		Path dir = playerDir(player.getServer(), playerId);
		try {
			Files.createDirectories(dir);
			String stem = uniqueStem(dir, name);
			Path file = dir.resolve(stem + ".nbt");
			writer.write(file, false);
			writeCatalogName(dir, stem, name);
			HolderGetter<Block> blocks = player.getServer().registryAccess().lookupOrThrow(Registries.BLOCK);
			ResourceLocation id = idFor(playerId, stem);
			if (!loadFile(playerId, id, name, file, blocks)) {
				return ImportResult.of(ImportStatus.FAILED);
			}
			return new ImportResult(ImportStatus.OK, id);
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to import blueprint for {}", playerId, exception);
			return ImportResult.of(ImportStatus.FAILED);
		}
	}

	public static ImportStatus checkSize(AreaBox box) {
		if (box == null) {
			return ImportStatus.FAILED;
		}
		if (box.maxAxis() > RefugeeConfig.importMaxAxis) {
			return ImportStatus.TOO_LARGE_AXIS;
		}
		if (box.volume() > RefugeeConfig.importMaxVolume) {
			return ImportStatus.TOO_LARGE_VOLUME;
		}
		return ImportStatus.OK;
	}

	public static boolean delete(MinecraftServer server, UUID actorId, ResourceLocation id) {
		if (server == null || actorId == null || id == null) {
			return false;
		}
		if (!visibleTo(server, actorId, id)) {
			return false;
		}
		UUID owner = OWNERS.get(id);
		if (owner == null) {
			return false;
		}
		String stem = stemOf(id);
		if (stem == null || stem.isEmpty()) {
			return false;
		}
		Path dir = playerDir(server, owner);
		Path file = dir.resolve(stem + ".nbt");
		try {
			Files.deleteIfExists(file);
			removeCatalogName(dir, stem);
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to delete blueprint {} for {}", id, owner, exception);
			return false;
		}
		TEMPLATES.remove(id);
		NBTS.remove(id);
		OWNERS.remove(id);
		PlayerCatalog catalog = BY_PLAYER.get(owner);
		if (catalog != null) {
			catalog.entries.removeIf(entry -> id.equals(entry.id()));
			catalog.templates.remove(id);
			catalog.nbts.remove(id);
			if (catalog.entries.isEmpty()) {
				BY_PLAYER.remove(owner);
			}
		}
		return true;
	}

	private static String stemOf(ResourceLocation id) {
		if (id == null) {
			return null;
		}
		String path = id.getPath();
		int slash = path.lastIndexOf('/');
		return slash < 0 ? path : path.substring(slash + 1);
	}

	public static boolean areaLoaded(ServerLevel level, AreaBox box) {
		if (level == null || box == null) {
			return false;
		}
		int minCx = box.min().getX() >> 4;
		int maxCx = box.max().getX() >> 4;
		int minCz = box.min().getZ() >> 4;
		int maxCz = box.max().getZ() >> 4;
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				if (!level.hasChunk(cx, cz)) {
					return false;
				}
			}
		}
		return true;
	}

	public enum ImportStatus {
		OK,
		EMPTY_NAME,
		DUPLICATE_NAME,
		TOO_LARGE_AXIS,
		TOO_LARGE_VOLUME,
		UNLOADED,
		EMPTY,
		FAILED
	}

	private static Path root(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(Refugee.MOD_ID).resolve("blueprints");
	}

	private static Path playerDir(MinecraftServer server, UUID playerId) {
		return root(server).resolve(playerId.toString());
	}

	private static ResourceLocation idFor(UUID playerId, String stem) {
		return Refugee.id("p/" + playerId.toString().replace("-", "") + "/" + stem);
	}

	private static UUID parseUuid(String folder) {
		try {
			return UUID.fromString(folder);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static void loadPlayer(UUID playerId, Path dir, HolderGetter<Block> blocks) {
		Map<String, String> names = readCatalogNames(dir);
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.nbt")) {
			for (Path file : stream) {
				String stem = stem(file.getFileName().toString());
				ResourceLocation id = idFor(playerId, stem);
				String display = names.getOrDefault(stem, stem.replace('_', ' '));
				loadFile(playerId, id, display, file, blocks);
			}
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to load player blueprints {}", dir, exception);
		}
	}

	private static boolean loadFile(
			UUID playerId,
			ResourceLocation id,
			String displayName,
			Path file,
			HolderGetter<Block> blocks
	) {
		try {
			CompoundTag nbt = readNbt(file);
			StructureTemplate template = new StructureTemplate();
			template.load(blocks, nbt);
			TEMPLATES.put(id, template);
			NBTS.put(id, nbt.copy());
			OWNERS.put(id, playerId);
			PlayerCatalog catalog = BY_PLAYER.computeIfAbsent(playerId, ignored -> new PlayerCatalog());
			catalog.nbts.put(id, nbt.copy());
			catalog.templates.put(id, template);
			catalog.entries.removeIf(entry -> entry.id().equals(id));
			catalog.entries.add(new BlueprintCatalogEntry(id, displayName, true));
			return true;
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to load player blueprint {}", file, exception);
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

	private static Map<String, String> readCatalogNames(Path dir) {
		Path catalog = dir.resolve(CATALOG_FILE);
		if (!Files.isRegularFile(catalog)) {
			return Map.of();
		}
		try (Reader reader = Files.newBufferedReader(catalog, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (parsed == null || !parsed.isJsonObject()) {
				return Map.of();
			}
			JsonObject json = parsed.getAsJsonObject();
			JsonObject names = json.has("names") && json.get("names").isJsonObject()
					? json.getAsJsonObject("names")
					: json;
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

	private static void writeCatalogName(Path dir, String stem, String displayName) throws IOException {
		Path catalog = dir.resolve(CATALOG_FILE);
		JsonObject json;
		if (Files.isRegularFile(catalog)) {
			try (Reader reader = Files.newBufferedReader(catalog, StandardCharsets.UTF_8)) {
				JsonElement parsed = JsonParser.parseReader(reader);
				json = parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
			}
		} else {
			json = new JsonObject();
			json.addProperty("_comment", "stem → display name");
		}
		JsonObject names;
		if (json.has("names") && json.get("names").isJsonObject()) {
			names = json.getAsJsonObject("names");
		} else {
			names = new JsonObject();
			for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
				if (!entry.getKey().startsWith("_") && entry.getValue().isJsonPrimitive()) {
					names.add(entry.getKey(), entry.getValue());
				}
			}
			json = new JsonObject();
			json.addProperty("_comment", "stem → display name");
			json.add("names", names);
		}
		names.addProperty(stem, displayName);
		if (!json.has("names")) {
			json.add("names", names);
		}
		try (Writer writer = Files.newBufferedWriter(catalog, StandardCharsets.UTF_8)) {
			GSON.toJson(json, writer);
			writer.write(System.lineSeparator());
		}
	}

	private static void removeCatalogName(Path dir, String stem) throws IOException {
		Path catalog = dir.resolve(CATALOG_FILE);
		if (!Files.isRegularFile(catalog) || stem == null || stem.isEmpty()) {
			return;
		}
		JsonObject json;
		try (Reader reader = Files.newBufferedReader(catalog, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			json = parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
		}
		JsonObject names = json.has("names") && json.get("names").isJsonObject()
				? json.getAsJsonObject("names")
				: json;
		names.remove(stem);
		try (Writer writer = Files.newBufferedWriter(catalog, StandardCharsets.UTF_8)) {
			GSON.toJson(json, writer);
			writer.write(System.lineSeparator());
		}
	}

	private static String uniqueStem(Path dir, String displayName) throws IOException {
		String base = stemFromName(displayName);
		String stem = base;
		int suffix = 2;
		while (Files.exists(dir.resolve(stem + ".nbt"))) {
			stem = base + "_" + suffix;
			suffix++;
		}
		return stem;
	}

	private static String stemFromName(String displayName) {
		StringBuilder path = new StringBuilder();
		for (char c : displayName.toLowerCase(Locale.ROOT).toCharArray()) {
			if (ResourceLocation.validPathChar(c)) {
				path.append(c);
			} else if (c == ' ' || c == '\\' || c == '/') {
				path.append('_');
			}
		}
		if (path.isEmpty()) {
			return "imp";
		}
		if (path.length() > 32) {
			path.setLength(32);
		}
		return path.toString();
	}

	private static String stem(String filename) {
		int dot = filename.lastIndexOf('.');
		return dot <= 0 ? filename : filename.substring(0, dot);
	}

	public static String normalizeName(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.trim();
	}

	private static CompoundTag propertiesOf(BlockState state) {
		CompoundTag tag = new CompoundTag();
		for (Property<?> property : state.getProperties()) {
			tag.putString(property.getName(), nameOf(state, property));
		}
		return tag.isEmpty() ? null : tag;
	}

	private static <T extends Comparable<T>> String nameOf(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	private static final class PlayerCatalog {
		private final List<BlueprintCatalogEntry> entries = new ArrayList<>();
		private final Map<ResourceLocation, StructureTemplate> templates = new LinkedHashMap<>();
		private final Map<ResourceLocation, CompoundTag> nbts = new LinkedHashMap<>();
	}
}
