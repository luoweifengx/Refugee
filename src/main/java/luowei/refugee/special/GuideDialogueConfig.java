package luowei.refugee.special;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.server.level.ServerPlayer;

import luowei.refugee.Refugee;
import luowei.refugee.attachment.PlayerSelectionData;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.compat.FoodCompat;

/**
 * {@code config/refugee/guide.json}：向导对话条目，可改文本不必重编译。
 * <p>
 * 条目可带 {@code mods}（需已加载）、{@code unless_mods}（未加载才显示）、
 * {@code require}（{@code enchanter} = 附魔师已出现）。
 */
public final class GuideDialogueConfig {
	public static final String DIRECTORY_NAME = "refugee";
	public static final String FILE_NAME = "guide.json";
	public static final int SCHEMA_VERSION = 2;

	public static final String MOD_PBS = "player-block-status";
	public static final String MOD_TRAVEL = "travel-business-team";
	public static final String REQUIRE_ENCHANTER = "enchanter";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static List<RawEntry> entries = List.of();

	private GuideDialogueConfig() {
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(DIRECTORY_NAME).resolve(FILE_NAME);
		try {
			Files.createDirectories(path.getParent());
			if (!Files.isRegularFile(path)) {
				entries = defaultEntries();
				write(path, defaultJson());
				Refugee.LOGGER.debug("Wrote default guide dialogue to {}", path);
				return;
			}
			JsonObject loaded = null;
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonElement parsed = JsonParser.parseReader(reader);
				if (parsed != null && parsed.isJsonObject()) {
					loaded = parsed.getAsJsonObject();
				}
			}
			int version = loaded == null ? 0 : readVersion(loaded);
			if (version < SCHEMA_VERSION) {
				entries = defaultEntries();
				write(path, defaultJson());
				Refugee.LOGGER.debug("Upgraded {} from version {} to {}", FILE_NAME, version, SCHEMA_VERSION);
				return;
			}
			entries = loaded == null ? List.of() : parse(loaded);
			if (entries.isEmpty()) {
				entries = defaultEntries();
			}
			Refugee.LOGGER.debug("Loaded {} guide dialogue entries from {}", entries.size(), path);
		} catch (Exception exception) {
			Refugee.LOGGER.warn("Failed to load {}; using defaults", FILE_NAME, exception);
			entries = defaultEntries();
		}
	}

	public static List<LocalizedEntry> localized(ServerPlayer player) {
		String lang = player == null || player.clientInformation() == null
				? "en_us"
				: player.clientInformation().language();
		final String locale = (lang == null || lang.isBlank() ? "en_us" : lang).toLowerCase(Locale.ROOT);
		List<LocalizedEntry> result = new ArrayList<>();
		for (RawEntry entry : entries) {
			if (!visible(entry, player)) {
				continue;
			}
			result.add(new LocalizedEntry(
					entry.id,
					entry.title.pick(locale),
					entry.lines.stream().map(text -> text.pick(locale)).toList()
			));
		}
		return result;
	}

	/**
	 * 开屏「交谈」台词池：按玩家语言摊平 guide.json 各条说明。
	 */
	public static List<String> talkLines(ServerPlayer player) {
		List<String> lines = new ArrayList<>();
		for (LocalizedEntry entry : localized(player)) {
			for (String line : entry.lines()) {
				if (line != null && !line.isBlank()) {
					lines.add(line);
				}
			}
		}
		return lines;
	}

	private static boolean visible(RawEntry entry, ServerPlayer player) {
		for (String modId : entry.mods) {
			if (modId == null || modId.isBlank()) {
				continue;
			}
			if (!FabricLoader.getInstance().isModLoaded(modId)) {
				return false;
			}
		}
		for (String modId : entry.unlessMods) {
			if (modId == null || modId.isBlank()) {
				continue;
			}
			if (FabricLoader.getInstance().isModLoaded(modId)) {
				return false;
			}
		}
		if (REQUIRE_ENCHANTER.equals(entry.require)) {
			if (player == null) {
				return false;
			}
			PlayerSelectionData data = RefugeeAttachments.get(player);
			return data.isEnchanterGranted() || data.enchanterId() != null;
		}
		return true;
	}

	private static int readVersion(JsonObject json) {
		if (json == null || !json.has("version") || !json.get("version").isJsonPrimitive()) {
			return 0;
		}
		try {
			return json.get("version").getAsInt();
		} catch (RuntimeException ignored) {
			return 0;
		}
	}

	private static List<RawEntry> parse(JsonObject json) {
		List<RawEntry> parsed = new ArrayList<>();
		JsonArray array = json.getAsJsonArray("entries");
		if (array == null) {
			return parsed;
		}
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject object = element.getAsJsonObject();
			String id = object.has("id") ? object.get("id").getAsString() : "";
			if (id.isBlank()) {
				continue;
			}
			LocalizedText title = LocalizedText.from(object.get("title"));
			List<LocalizedText> lines = new ArrayList<>();
			if (object.has("lines") && object.get("lines").isJsonArray()) {
				for (JsonElement line : object.getAsJsonArray("lines")) {
					lines.add(LocalizedText.from(line));
				}
			}
			parsed.add(new RawEntry(
					id,
					stringList(object, "mods"),
					stringList(object, "unless_mods"),
					object.has("require") && object.get("require").isJsonPrimitive()
							? object.get("require").getAsString()
							: "",
					title,
					List.copyOf(lines)
			));
		}
		return parsed;
	}

	private static List<String> stringList(JsonObject object, String key) {
		if (object == null || !object.has(key)) {
			return List.of();
		}
		JsonElement element = object.get(key);
		if (element.isJsonPrimitive()) {
			String value = element.getAsString();
			return value == null || value.isBlank() ? List.of() : List.of(value);
		}
		if (!element.isJsonArray()) {
			return List.of();
		}
		List<String> values = new ArrayList<>();
		for (JsonElement item : element.getAsJsonArray()) {
			if (!item.isJsonPrimitive()) {
				continue;
			}
			String value = item.getAsString();
			if (value != null && !value.isBlank()) {
				values.add(value);
			}
		}
		return List.copyOf(values);
	}

	private static void write(Path path, JsonObject json) throws IOException {
		try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
			GSON.toJson(json, writer);
			writer.write(System.lineSeparator());
		}
	}

	private static List<RawEntry> defaultEntries() {
		return parse(defaultJson());
	}

	private static JsonObject defaultJson() {
		JsonObject root = new JsonObject();
		root.addProperty("_comment", "version 低于内置时会重写。entries 可设 mods、unless_mods、require（enchanter=附魔师已出现）。");
		root.addProperty("version", SCHEMA_VERSION);
		JsonArray entriesJson = new JsonArray();
		entriesJson.add(entry(
				"follow",
				"跟随与安顿",
				"Follow and settle",
				List.of(
						"空手右键普通居民可选中并让其跟随；再次空手右键取消跟随，守卫中心会重置到当前位置。",
						"对特殊居民（向导、护士、绘图师、附魔师）请潜行空手右键来选中/取消跟随。",
						"手持安顿旗帜对准地面放置，会把当前选中的居民传送到旗帜附近并取消选中。"
				),
				List.of(
						"Empty-hand right-click a generic resident to select them and make them follow. Do it again to stop following; their guard center resets here.",
						"For special residents (guide, nurse, cartographer, enchanter), sneak and empty-hand right-click to toggle follow.",
						"Place a settlement banner on the ground to teleport currently selected residents next to it and clear the selection."
				)
		));
		entriesJson.add(entry(
				"horn_bell",
				"号角与钟",
				"Horn and bell",
				List.of(
						"吹响山羊号角，或敲钟，会把附近半径内的居民一并选中并跟随。",
						"选中表从空变为有人时会发一面安顿旗；选中清空时会收回旗帜。"
				),
				List.of(
						"Blow a goat horn or ring a bell to select nearby residents and make them follow.",
						"You receive a settlement banner when the selection becomes non-empty, and banners are taken back when it is empty."
				)
		));
		entriesJson.add(entry(
				"guard_build",
				"守卫与工人",
				"Guard and workers",
				List.of(
						"潜行右键把剑、弓或弩交给居民，他们会成为守卫并在守卫中心附近作战。斧不再是武器。",
						"潜行右键把镐、斧、锄或铲交给居民，他们会成为工人：可建筑，也会对仓库存取。",
						"特殊居民不会接受武器或工具，以免变成守卫或工人。"
				),
				List.of(
						"Sneak-right-click to give a sword, bow, or crossbow; they become guards and fight near their guard center. Axes are no longer weapons.",
						"Sneak-right-click to give a pickaxe, axe, hoe, or shovel to make workers: they can build and use the warehouse.",
						"Special residents will not take weapons or tools, so they cannot become guards or workers."
				)
		));
		entriesJson.add(entry(
				"command_staff",
				"指挥杖",
				"Command staff",
				List.of(
						"一根木棍即可合成指挥杖。对空气右键打开扇形菜单：右上=仓库，右下=工作区，左下=选择建筑，左上=导入。",
						"仓库模式右键箱子加入或踢出，加入后按木头/木板/石头/泥沙/种子/杂项整理。E 或 Esc 退出。建筑从仓库远程取料，不靠近箱子。",
						"工作区用两点划出范围。当前跟随的镐工人采石，斧只砍原木，锄会犁地、收熟作物并补种，铲只挖泥沙。潜行右键正在建造的村民可停止建造。"
				),
				List.of(
						"Craft a command staff with one stick. Right-click air for a pie menu: top-right=warehouse, bottom-right=work zone, bottom-left=select build, top-left=import.",
						"In warehouse mode, right-click a chest to add or remove it; adding sorts logs/planks/stone/soil/seeds/misc. E or Esc exits. Builders pull from the warehouse remotely.",
						"A work zone is two corners. Following pickaxe workers mine stone, axes chop logs only, hoes till/harvest/replant, and shovels dig soil and sand. Sneak-right-click a building villager to stop the job."
				)
		));
		entriesJson.add(entry(
				"immigration",
				"入境与领地",
				"Immigration and territory",
				List.of(
						"在 PBS 领地上，占领区块足够多时会定期有难民入境，加入你的队伍。",
						"护士会在你的第一批入境难民到来时加入；绘图师会在占领区块超过 20 时加入。",
						"空手右键绘图师可查看领地区块图。玩家死亡时会献祭一名普通居民；特殊居民会尽量留到最后。"
				),
				List.of(
						"On PBS territory, extra refugees periodically immigrate once you own enough chunks, and join your roster.",
						"A nurse joins with your first immigration; a cartographer joins after you own more than 20 chunks.",
						"Empty-hand the cartographer to open a territory map. On player death a generic resident is sacrificed first; special residents are kept until last."
				)
		));
		entriesJson.add(entry(
				"immigration_lapis",
				List.of(),
				List.of(FoodCompat.MOD_ID),
				"",
				"附魔师",
				"Enchanter",
				List.of("未装 Food 时，附魔师会在你首次获得青金石时加入。"),
				List.of("Without Food, an enchanter joins when you first obtain lapis lazuli.")
		));
		entriesJson.add(entry(
				"immigration_food",
				List.of(FoodCompat.MOD_ID),
				List.of(),
				"",
				"法师塔",
				"Mage tower",
				List.of("地脉仪式完成后，会在出生点 10–20 区块外的地表升起法师塔，附魔师出现在塔内。"),
				List.of("After the leyline ritual, a mage tower rises 10–20 chunks from world spawn, and the enchanter appears inside.")
		));
		entriesJson.add(entry(
				"flavor_self",
				"居民与向导",
				"Residents and the guide",
				List.of(
						"周围的这些村民或许不单单是拿着武器打架那么简单",
						"我吗？我与他们不同，绿色的衣服就是我不同寻常的象征！",
						"注意点别死啦！我可不想变成倒霉蛋",
						"只有叫住周围的这群家伙，他们才会听你的"
				),
				List.of(
						"The villagers around you aren't just swinging weapons at each other.",
						"Me? I'm different from them. The green clothes are the sign!",
						"Try not to die! I'd rather not be the unlucky one.",
						"Only if you call out to the folks nearby will they actually listen."
				)
		));
		entriesJson.add(entry(
				"flavor_enchanter",
				List.of(),
				List.of(),
				REQUIRE_ENCHANTER,
				"地底的动静",
				"Noise underground",
				List.of("有人被地底的动静吵醒了！你或许应该去问问他们是否在因此抱怨"),
				List.of("Someone was woken by the noise underground! You might want to ask if they're complaining about it.")
		));
		entriesJson.add(entry(
				"flavor_pbs",
				List.of(MOD_PBS),
				List.of(),
				"",
				"地盘与地狱门",
				"Territory and portals",
				List.of(
						"方块多了，有些地盘就是你的了，但是不见得都是好事",
						"地狱门会带来很多变化，与很多的危险，一定要做好准备"
				),
				List.of(
						"More blocks, and some of that ground is yours. That isn't always a blessing.",
						"A nether portal brings a lot of change, and a lot of danger. Be ready."
				)
		));
		entriesJson.add(entry(
				"flavor_food",
				List.of(FoodCompat.MOD_ID),
				List.of(),
				"",
				"生存与地脉",
				"Survival and leyline",
				List.of(
						"很惊讶吗？你变得比以前更加强壮了。",
						"越深越危险，听说每个人肚子里有条虫子吞了个深度计，你呆在太深的地方它或许会因此不愉快",
						"古城带上钟，敲几下，叫醒那些懒虫，他们总在偷吃你的东西"
				),
				List.of(
						"Surprised? You've grown a lot stronger than before.",
						"The deeper you go, the worse it gets. They say everyone has a worm in their belly that swallowed a depth meter—stay too deep and it may get unhappy.",
						"Take a bell to the ancient city and ring it a few times. Wake those lazy bugs; they keep stealing your food."
				)
		));
		entriesJson.add(entry(
				"flavor_travel",
				List.of(MOD_TRAVEL),
				List.of(),
				"",
				"旅商",
				"Traveling merchants",
				List.of(
						"有种诡异的红石，他被镀上一层绿宝石，或许能让一些人驻足观看",
						"试着拿几张纸拼起来，有人愿意看上面的内容，当然得要有点小钱"
				),
				List.of(
						"There's a strange redstone, plated in emerald. It might make some people stop and stare.",
						"Try piecing a few sheets of paper together. Someone will read what's on them—though it'll cost a little coin."
				)
		));
		root.add("entries", entriesJson);
		return root;
	}

	private static JsonObject entry(String id, String zhTitle, String enTitle, List<String> zhLines, List<String> enLines) {
		return entry(id, List.of(), List.of(), "", zhTitle, enTitle, zhLines, enLines);
	}

	private static JsonObject entry(
			String id,
			List<String> mods,
			List<String> unlessMods,
			String require,
			String zhTitle,
			String enTitle,
			List<String> zhLines,
			List<String> enLines
	) {
		JsonObject object = new JsonObject();
		object.addProperty("id", id);
		if (mods != null && !mods.isEmpty()) {
			object.add("mods", stringArray(mods));
		}
		if (unlessMods != null && !unlessMods.isEmpty()) {
			object.add("unless_mods", stringArray(unlessMods));
		}
		if (require != null && !require.isBlank()) {
			object.addProperty("require", require);
		}
		object.add("title", localized(zhTitle, enTitle));
		JsonArray lines = new JsonArray();
		for (int i = 0; i < zhLines.size(); i++) {
			lines.add(localized(zhLines.get(i), i < enLines.size() ? enLines.get(i) : zhLines.get(i)));
		}
		object.add("lines", lines);
		return object;
	}

	private static JsonArray stringArray(List<String> values) {
		JsonArray array = new JsonArray();
		for (String value : values) {
			array.add(value);
		}
		return array;
	}

	private static JsonObject localized(String zh, String en) {
		JsonObject object = new JsonObject();
		object.addProperty("zh_cn", zh);
		object.addProperty("en_us", en);
		return object;
	}

	public record LocalizedEntry(String id, String title, List<String> lines) {
	}

	private record RawEntry(
			String id,
			List<String> mods,
			List<String> unlessMods,
			String require,
			LocalizedText title,
			List<LocalizedText> lines
	) {
	}

	private record LocalizedText(String zhCn, String enUs) {
		private String pick(String lang) {
			if (lang.startsWith("zh")) {
				return zhCn == null || zhCn.isBlank() ? enUs : zhCn;
			}
			return enUs == null || enUs.isBlank() ? zhCn : enUs;
		}

		private static LocalizedText from(JsonElement element) {
			if (element == null || element.isJsonNull()) {
				return new LocalizedText("", "");
			}
			if (element.isJsonPrimitive()) {
				String text = element.getAsString();
				return new LocalizedText(text, text);
			}
			if (!element.isJsonObject()) {
				return new LocalizedText("", "");
			}
			JsonObject object = element.getAsJsonObject();
			String zh = object.has("zh_cn") ? object.get("zh_cn").getAsString() : "";
			String en = object.has("en_us") ? object.get("en_us").getAsString() : "";
			return new LocalizedText(zh, en);
		}
	}
}
