package luowei.refugee.blueprint;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
 * 玩家划入/上传的结构：世界存档 {@code refugee/blueprints/<UUID>/}，默认仅所有者可见，显式分享后他人可读。
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
		if (id == null || playerId == null) {
			return false;
		}
		UUID owner = OWNERS.get(id);
		if (owner == null) {
			return false;
		}
		if (playerId.equals(owner)) {
			return true;
		}
		PlayerCatalog catalog = BY_PLAYER.get(owner);
		if (catalog == null) {
			return false;
		}
		Set<UUID> grants = catalog.shares.get(id);
		if (grants == null || grants.isEmpty()) {
			return false;
		}
		if (grants.contains(playerId)) {
			return true;
		}
		UUID orgId = PbsAdapter.organizationOf(server, playerId).orElse(null);
		return orgId != null && grants.contains(orgId);
	}

	public static List<BlueprintCatalogEntry> catalog(UUID playerId) {
		PlayerCatalog catalog = playerId == null ? null : BY_PLAYER.get(playerId);
		return catalog == null ? List.of() : List.copyOf(catalog.entries);
	}

	public static List<BlueprintCatalogEntry> catalog(MinecraftServer server, UUID playerId) {
		Set<ResourceLocation> seen = new LinkedHashSet<>();
		List<BlueprintCatalogEntry> entries = new ArrayList<>();
		for (BlueprintCatalogEntry entry : catalog(playerId)) {
			if (seen.add(entry.id())) {
				entries.add(entry.withOwned(true));
			}
		}
		if (server == null || playerId == null) {
			return entries;
		}
		for (Map.Entry<UUID, PlayerCatalog> owner : BY_PLAYER.entrySet()) {
			if (playerId.equals(owner.getKey())) {
				continue;
			}
			for (BlueprintCatalogEntry entry : owner.getValue().entries) {
				if (visibleTo(server, playerId, entry.id()) && seen.add(entry.id())) {
					entries.add(entry.withOwned(false));
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
		Map<ResourceLocation, CompoundTag> nbts = new LinkedHashMap<>(templateNbts(playerId));
		if (server == null || playerId == null) {
			return nbts;
		}
		for (UUID ownerId : BY_PLAYER.keySet()) {
			if (playerId.equals(ownerId)) {
				continue;
			}
			for (Map.Entry<ResourceLocation, CompoundTag> entry : templateNbts(ownerId).entrySet()) {
				if (visibleTo(server, playerId, entry.getKey())) {
					nbts.put(entry.getKey(), entry.getValue());
				}
			}
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
		for (BlueprintCatalogEntry entry : catalog(playerId)) {
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
			HolderGetter<Block> blocks = player.getServer().registryAccess().lookupOrThrow(Registries.BLOCK);
			ResourceLocation id = idFor(playerId, stem);
			if (!loadFile(playerId, id, name, file, blocks)) {
				return ImportResult.of(ImportStatus.FAILED);
			}
			saveCatalog(playerId, dir);
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
		return checkSize(box.sizeX(), box.sizeY(), box.sizeZ());
	}

	public static ImportStatus checkSize(int sizeX, int sizeY, int sizeZ) {
		if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
			return ImportStatus.EMPTY;
		}
		int maxAxis = Math.max(sizeX, Math.max(sizeY, sizeZ));
		if (maxAxis > RefugeeConfig.importMaxAxis) {
			return ImportStatus.TOO_LARGE_AXIS;
		}
		long volume = (long) sizeX * sizeY * sizeZ;
		if (volume > RefugeeConfig.importMaxVolume) {
			return ImportStatus.TOO_LARGE_VOLUME;
		}
		return ImportStatus.OK;
	}

	public static ImportStatus checkNbt(CompoundTag nbt, HolderGetter<Block> blocks) {
		if (nbt == null || blocks == null) {
			return ImportStatus.FAILED;
		}
		try {
			StructureTemplate template = new StructureTemplate();
			template.load(blocks, nbt);
			Vec3i size = template.getSize();
			if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
				return ImportStatus.EMPTY;
			}
			return checkSize(size.getX(), size.getY(), size.getZ());
		} catch (Exception exception) {
			return ImportStatus.FAILED;
		}
	}

	public static ImportResult importBytes(ServerPlayer player, String rawName, byte[] bytes) {
		if (player == null || bytes == null || bytes.length == 0) {
			return ImportResult.of(ImportStatus.FAILED);
		}
		try {
			return importNbt(player, rawName, readNbt(bytes));
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to parse uploaded blueprint for {}", player.getUUID(), exception);
			return ImportResult.of(ImportStatus.FAILED);
		}
	}

	public static ImportResult importNbt(ServerPlayer player, String rawName, CompoundTag rawNbt) {
		if (player == null || rawNbt == null) {
			return ImportResult.of(ImportStatus.FAILED);
		}
		String name = normalizeName(rawName);
		if (name.isEmpty()) {
			return ImportResult.of(ImportStatus.EMPTY_NAME);
		}
		if (nameTaken(player.getServer(), player.getUUID(), name)) {
			return ImportResult.of(ImportStatus.DUPLICATE_NAME);
		}
		HolderGetter<Block> blocks = player.getServer().registryAccess().lookupOrThrow(Registries.BLOCK);
		ImportStatus size = checkNbt(rawNbt, blocks);
		if (size != ImportStatus.OK) {
			return ImportResult.of(size);
		}
		CompoundTag nbt = rawNbt.copy();
		nbt.put("entities", new ListTag());
		UUID playerId = player.getUUID();
		Path dir = playerDir(player.getServer(), playerId);
		try {
			Files.createDirectories(dir);
			String stem = uniqueStem(dir, name);
			Path file = dir.resolve(stem + ".nbt");
			NbtIo.writeCompressed(nbt, file);
			ResourceLocation id = idFor(playerId, stem);
			if (!loadFile(playerId, id, name, file, blocks)) {
				return ImportResult.of(ImportStatus.FAILED);
			}
			saveCatalog(playerId, dir);
			return new ImportResult(ImportStatus.OK, id);
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to store uploaded blueprint for {}", playerId, exception);
			return ImportResult.of(ImportStatus.FAILED);
		}
	}

	public static boolean share(
			MinecraftServer server,
			UUID ownerId,
			Collection<ResourceLocation> ids,
			Collection<UUID> targets
	) {
		if (server == null || ownerId == null || ids == null || targets == null || ids.isEmpty() || targets.isEmpty()) {
			return false;
		}
		PlayerCatalog catalog = BY_PLAYER.get(ownerId);
		if (catalog == null) {
			return false;
		}
		boolean changed = false;
		for (ResourceLocation id : ids) {
			if (!owns(ownerId, id)) {
				continue;
			}
			Set<UUID> grants = catalog.shares.computeIfAbsent(id, ignored -> new LinkedHashSet<>());
			for (UUID target : targets) {
				if (target == null || ownerId.equals(target) || !PbsAdapter.isKnownShareTarget(server, target)) {
					continue;
				}
				changed |= grants.add(target);
			}
		}
		if (changed) {
			saveCatalog(ownerId, playerDir(server, ownerId));
		}
		return changed;
	}

	public static boolean delete(MinecraftServer server, UUID actorId, ResourceLocation id) {
		if (server == null || actorId == null || id == null) {
			return false;
		}
		if (!owns(actorId, id)) {
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
			catalog.shares.remove(id);
			if (catalog.entries.isEmpty()) {
				BY_PLAYER.remove(owner);
			}
		}
		saveCatalog(owner, dir);
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
		CatalogDisk disk = readCatalogDisk(dir);
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.nbt")) {
			for (Path file : stream) {
				String stem = stem(file.getFileName().toString());
				ResourceLocation id = idFor(playerId, stem);
				String display = disk.names.getOrDefault(stem, stem.replace('_', ' '));
				loadFile(playerId, id, display, file, blocks);
			}
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to load player blueprints {}", dir, exception);
		}
		PlayerCatalog catalog = BY_PLAYER.get(playerId);
		if (catalog == null) {
			return;
		}
		for (Map.Entry<String, Set<UUID>> entry : disk.shares.entrySet()) {
			ResourceLocation id = idFor(playerId, entry.getKey());
			if (OWNERS.containsKey(id) && entry.getValue() != null && !entry.getValue().isEmpty()) {
				catalog.shares.put(id, new LinkedHashSet<>(entry.getValue()));
			}
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

	public static CompoundTag readNbt(byte[] bytes) throws IOException {
		if (bytes == null || bytes.length == 0) {
			throw new IOException("empty blueprint bytes");
		}
		try {
			return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
		} catch (IOException compressedFailed) {
			return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)));
		}
	}

	private static CatalogDisk readCatalogDisk(Path dir) {
		Path catalog = dir.resolve(CATALOG_FILE);
		if (!Files.isRegularFile(catalog)) {
			return CatalogDisk.empty();
		}
		try (Reader reader = Files.newBufferedReader(catalog, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (parsed == null || !parsed.isJsonObject()) {
				return CatalogDisk.empty();
			}
			JsonObject json = parsed.getAsJsonObject();
			JsonObject namesJson = json.has("names") && json.get("names").isJsonObject()
					? json.getAsJsonObject("names")
					: json;
			Map<String, String> names = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> entry : namesJson.entrySet()) {
				if (entry.getKey().startsWith("_") || "shares".equals(entry.getKey())) {
					continue;
				}
				if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
					names.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
			Map<String, Set<UUID>> shares = new LinkedHashMap<>();
			if (json.has("shares") && json.get("shares").isJsonObject()) {
				for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("shares").entrySet()) {
					if (!entry.getValue().isJsonArray()) {
						continue;
					}
					Set<UUID> ids = new LinkedHashSet<>();
					for (JsonElement element : entry.getValue().getAsJsonArray()) {
						if (element == null || !element.isJsonPrimitive()) {
							continue;
						}
						UUID parsedId = parseUuid(element.getAsString());
						if (parsedId != null) {
							ids.add(parsedId);
						}
					}
					if (!ids.isEmpty()) {
						shares.put(entry.getKey(), ids);
					}
				}
			}
			return new CatalogDisk(names, shares);
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to read {}", catalog, exception);
			return CatalogDisk.empty();
		}
	}

	private static void saveCatalog(UUID playerId, Path dir) {
		if (playerId == null || dir == null) {
			return;
		}
		try {
			Files.createDirectories(dir);
			JsonObject json = new JsonObject();
			json.addProperty("_comment", "stem → display name");
			JsonObject names = new JsonObject();
			JsonObject shares = new JsonObject();
			PlayerCatalog catalog = BY_PLAYER.get(playerId);
			if (catalog != null) {
				for (BlueprintCatalogEntry entry : catalog.entries) {
					String stem = stemOf(entry.id());
					if (stem == null || stem.isEmpty()) {
						continue;
					}
					names.addProperty(stem, entry.displayName());
					Set<UUID> grants = catalog.shares.get(entry.id());
					if (grants == null || grants.isEmpty()) {
						continue;
					}
					JsonArray array = new JsonArray();
					for (UUID grant : grants) {
						array.add(grant.toString());
					}
					shares.add(stem, array);
				}
			}
			json.add("names", names);
			if (!shares.entrySet().isEmpty()) {
				json.add("shares", shares);
			}
			Path catalogFile = dir.resolve(CATALOG_FILE);
			try (Writer writer = Files.newBufferedWriter(catalogFile, StandardCharsets.UTF_8)) {
				GSON.toJson(json, writer);
				writer.write(System.lineSeparator());
			}
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to write catalog for {}", playerId, exception);
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

	public static String suggestedName(String filename) {
		if (filename == null || filename.isBlank()) {
			return "";
		}
		String stem = stem(filename);
		return stem.replace('_', ' ').trim();
	}

	private static final class CatalogDisk {
		private final Map<String, String> names;
		private final Map<String, Set<UUID>> shares;

		private CatalogDisk(Map<String, String> names, Map<String, Set<UUID>> shares) {
			this.names = names;
			this.shares = shares;
		}

		private static CatalogDisk empty() {
			return new CatalogDisk(Map.of(), Map.of());
		}
	}

	private static final class PlayerCatalog {
		private final List<BlueprintCatalogEntry> entries = new ArrayList<>();
		private final Map<ResourceLocation, StructureTemplate> templates = new LinkedHashMap<>();
		private final Map<ResourceLocation, CompoundTag> nbts = new LinkedHashMap<>();
		private final Map<ResourceLocation, Set<UUID>> shares = new LinkedHashMap<>();
	}
}
