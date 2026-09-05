package luowei.refugee.blueprint;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * 世界生成建筑：{@code config/refugee/worldgen} 下的废墟与地标模板。
 * 不进入指挥杖目录。
 */
public final class WorldgenBlueprints {
	public static final String DIRECTORY_NAME = "worldgen";

	private static final Map<ResourceLocation, CompoundTag> NBTS = new LinkedHashMap<>();
	private static final Map<ResourceLocation, StructureTemplate> TEMPLATES = new LinkedHashMap<>();
	private static final List<String> STEMS = List.copyOf(buildStems());
	private static final Set<String> STEM_SET = Set.copyOf(STEMS);

	private WorldgenBlueprints() {
	}

	public static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve(Refugee.MOD_ID).resolve(DIRECTORY_NAME);
	}

	public static List<String> stems() {
		return STEMS;
	}

	public static boolean isStem(String stem) {
		return stem != null && STEM_SET.contains(stem);
	}

	private static List<String> buildStems() {
		List<String> stems = new ArrayList<>();
		for (RuinBlueprints.Spec spec : RuinBlueprints.spawnables()) {
			stems.add(spec.id());
		}
		stems.addAll(LandmarkBlueprints.displayNames().keySet());
		return stems;
	}

	public static void ensureDirectory() {
		Path dir = directory();
		boolean created = Files.notExists(dir);
		try {
			Files.createDirectories(dir);
			if (created) {
				writeReadme(dir);
				Refugee.LOGGER.debug("Created worldgen blueprint directory {}", dir);
			}
			RuinBlueprints.writeAll(dir);
			LandmarkBlueprints.writeAll(dir);
			loadNbts();
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to prepare worldgen blueprint directory {}", dir, exception);
			loadNbts();
		}
	}

	public static void reload(MinecraftServer server) {
		ensureDirectory();
		TEMPLATES.clear();
		if (server == null) {
			return;
		}
		HolderGetter<Block> blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
		for (Map.Entry<ResourceLocation, CompoundTag> entry : NBTS.entrySet()) {
			try {
				StructureTemplate template = new StructureTemplate();
				template.load(blocks, entry.getValue());
				TEMPLATES.put(entry.getKey(), template);
			} catch (Exception exception) {
				Refugee.LOGGER.warn("Failed to load worldgen blueprint {}", entry.getKey(), exception);
			}
		}
		Refugee.LOGGER.debug("Loaded {} worldgen blueprint(s) from {}", TEMPLATES.size(), directory());
	}

	public static void clear() {
		TEMPLATES.clear();
		NBTS.clear();
	}

	public static StructureTemplate get(ResourceLocation id) {
		return id == null ? null : TEMPLATES.get(id);
	}

	public static StructureTemplate get(ResourceLocation id, HolderGetter<Block> blocks) {
		StructureTemplate cached = get(id);
		if (cached != null || id == null || blocks == null) {
			return cached;
		}
		CompoundTag nbt = NBTS.get(id);
		if (nbt == null) {
			return null;
		}
		StructureTemplate template = new StructureTemplate();
		template.load(blocks, nbt);
		TEMPLATES.put(id, template);
		return template;
	}

	public static CompoundTag nbt(String stem) {
		if (stem == null || stem.isBlank()) {
			return null;
		}
		CompoundTag stored = NBTS.get(Refugee.id(stem));
		if (stored != null) {
			return stored.copy();
		}
		if (RuinBlueprints.spec(stem) != null) {
			return RuinBlueprints.templateNbt(stem);
		}
		return null;
	}

	private static void loadNbts() {
		NBTS.clear();
		Path dir = directory();
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.nbt")) {
			for (Path file : stream) {
				String filename = file.getFileName().toString();
				int dot = filename.lastIndexOf('.');
				String stem = dot <= 0 ? filename : filename.substring(0, dot);
				ResourceLocation id = Refugee.id(stem);
				try {
					NBTS.put(id, readNbt(file));
				} catch (Exception exception) {
					Refugee.LOGGER.warn("Failed to read worldgen blueprint {}", file, exception);
				}
			}
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to scan worldgen blueprint directory {}", dir, exception);
		}
	}

	private static CompoundTag readNbt(Path path) throws IOException {
		try {
			return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
		} catch (IOException compressedFailed) {
			return NbtIo.read(path);
		}
	}

	private static void writeReadme(Path dir) throws IOException {
		Path readme = dir.resolve("README.txt");
		String text = """
				世界生成建筑（废墟、法师塔、炼狱炉）放在本目录，不出现在指挥杖蓝图列表。
				基础可造建筑在 config/refugee/blueprints/。
				玩家导入在世界存档 refugee/blueprints/<玩家UUID>/。
				Worldgen structures live here and are not shown in the staff catalog.
				""";
		Files.writeString(readme, text, java.nio.charset.StandardCharsets.UTF_8);
	}
}
