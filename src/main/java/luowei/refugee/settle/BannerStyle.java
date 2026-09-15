package luowei.refugee.settle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * 安顿旗底色 + 图案层：编码/解析玩家输入。
 */
public record BannerStyle(DyeColor base, BannerPatternLayers patterns) {
	public static final int MAX_LAYERS = 16;
	public static final int MAX_INPUT = 2048;

	public BannerStyle {
		base = base == null ? DyeColor.WHITE : base;
		patterns = patterns == null ? BannerPatternLayers.EMPTY : patterns;
	}

	public static BannerStyle plain() {
		return new BannerStyle(DyeColor.WHITE, BannerPatternLayers.EMPTY);
	}

	public static BannerStyle fromBanner(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BannerItem banner)) {
			return null;
		}
		return new BannerStyle(
				banner.getColor(),
				stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)
		);
	}

	public static String encode(DyeColor base, BannerPatternLayers patterns) {
		DyeColor color = base == null ? DyeColor.WHITE : base;
		StringBuilder text = new StringBuilder(color.getName());
		if (patterns == null) {
			return text.toString();
		}
		for (BannerPatternLayers.Layer layer : patterns.layers()) {
			Optional<ResourceKey<BannerPattern>> key = layer.pattern().unwrapKey();
			if (key.isEmpty()) {
				continue;
			}
			ResourceLocation id = key.get().location();
			text.append(';');
			if ("minecraft".equals(id.getNamespace())) {
				text.append(id.getPath());
			} else {
				text.append(id);
			}
			text.append(':').append(layer.color().getName());
		}
		return text.toString();
	}

	public String encode() {
		return encode(base, patterns);
	}

	public static BannerStyle parse(Player player, String raw, DyeColor fallbackBase) {
		if (player == null) {
			return null;
		}
		DyeColor fallback = fallbackBase == null ? DyeColor.WHITE : fallbackBase;
		String text = raw == null ? "" : raw.trim();
		if (text.length() > MAX_INPUT) {
			text = text.substring(0, MAX_INPUT);
		}
		if (text.isEmpty()) {
			return new BannerStyle(fallback, BannerPatternLayers.EMPTY);
		}
		BannerStyle fromGive = parseGiveCommand(player, text, fallback);
		if (fromGive != null) {
			return fromGive;
		}
		char first = text.charAt(0);
		if (first == '[' || first == '{') {
			BannerStyle json = parseJsonOrSnbt(player, text, fallback);
			if (json != null) {
				return json;
			}
		}
		return parseSimple(player, text, fallback);
	}

	private static BannerStyle parseGiveCommand(Player player, String text, DyeColor fallback) {
		String lower = text.toLowerCase(Locale.ROOT);
		if (!lower.contains("banner_patterns") && !lower.contains("_banner")) {
			return null;
		}
		if (!lower.contains("banner_patterns=")) {
			return null;
		}
		int start = lower.indexOf("banner_patterns=");
		int bracket = text.indexOf('[', start);
		if (bracket < 0) {
			return null;
		}
		String array = extractBracket(text, bracket);
		if (array == null) {
			return null;
		}
		BannerPatternLayers layers = parseLayersJson(player, array);
		if (layers == null) {
			return null;
		}
		DyeColor base = fallback;
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([a-z_]+)_banner").matcher(lower);
		if (matcher.find()) {
			DyeColor parsed = DyeColor.byName(matcher.group(1), null);
			if (parsed != null) {
				base = parsed;
			}
		}
		return capped(base, layers);
	}

	private static BannerStyle parseJsonOrSnbt(Player player, String text, DyeColor fallback) {
		try {
			JsonElement json = JsonParser.parseString(text);
			BannerStyle fromJson = parseJson(player, json, fallback);
			if (fromJson != null) {
				return fromJson;
			}
		} catch (RuntimeException ignored) {
			// SNBT 不是合法 JSON
		}
		try {
			CompoundTag tag = TagParser.parseCompoundFully(text);
			Optional<ItemStack> parsed = ItemStack.parse(player.registryAccess(), tag);
			if (parsed.isPresent()) {
				return fromBanner(parsed.get());
			}
		} catch (CommandSyntaxException ignored) {
			return null;
		}
		return null;
	}

	private static BannerStyle parseJson(Player player, JsonElement json, DyeColor fallback) {
		if (json == null || json.isJsonNull()) {
			return null;
		}
		if (json.isJsonArray()) {
			BannerPatternLayers layers = parseLayersJson(player, json);
			return layers == null ? null : capped(fallback, layers);
		}
		if (!json.isJsonObject()) {
			return null;
		}
		JsonObject object = json.getAsJsonObject();
		if (object.has("id")) {
			return ItemStack.CODEC.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), json)
					.result()
					.map(BannerStyle::fromBanner)
					.orElse(null);
		}
		DyeColor base = fallback;
		if (object.has("base")) {
			if (!object.get("base").isJsonPrimitive()) {
				return null;
			}
			DyeColor parsed = DyeColor.byName(object.get("base").getAsString(), null);
			if (parsed == null) {
				return null;
			}
			base = parsed;
		}
		JsonElement layersJson = object.has("patterns") ? object.get("patterns") : object.get("banner_patterns");
		BannerPatternLayers layers = BannerPatternLayers.EMPTY;
		if (layersJson != null) {
			layers = parseLayersJson(player, layersJson);
			if (layers == null) {
				return null;
			}
		}
		return capped(base, layers);
	}

	private static BannerPatternLayers parseLayersJson(Player player, JsonElement json) {
		return BannerPatternLayers.CODEC
				.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), json)
				.result()
				.orElse(null);
	}

	private static BannerPatternLayers parseLayersJson(Player player, String json) {
		try {
			return parseLayersJson(player, JsonParser.parseString(json));
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static BannerStyle parseSimple(Player player, String text, DyeColor fallback) {
		String normalized = text.replace('\n', ';').replace(',', ';');
		String[] parts = normalized.split(";");
		DyeColor base = fallback;
		boolean sawBase = false;
		HolderLookup.RegistryLookup<BannerPattern> lookup = player.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		List<BannerPatternLayers.Layer> layers = new ArrayList<>();
		for (String part : parts) {
			String token = part.trim();
			if (token.isEmpty()) {
				continue;
			}
			int split = token.lastIndexOf(':');
			if (split < 0) {
				DyeColor color = DyeColor.byName(token.toLowerCase(Locale.ROOT), null);
				if (color == null || sawBase) {
					return null;
				}
				base = color;
				sawBase = true;
				continue;
			}
			String patternToken = token.substring(0, split).trim();
			String colorToken = token.substring(split + 1).trim();
			DyeColor color = DyeColor.byName(colorToken.toLowerCase(Locale.ROOT), null);
			Holder.Reference<BannerPattern> pattern = resolvePattern(lookup, patternToken);
			if (color == null || pattern == null) {
				return null;
			}
			layers.add(new BannerPatternLayers.Layer(pattern, color));
		}
		return capped(base, new BannerPatternLayers(List.copyOf(layers)));
	}

	private static Holder.Reference<BannerPattern> resolvePattern(
			HolderLookup.RegistryLookup<BannerPattern> lookup,
			String token
	) {
		String raw = token.toLowerCase(Locale.ROOT);
		ResourceLocation id = ResourceLocation.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
		if (id == null) {
			return null;
		}
		return lookup.get(ResourceKey.create(Registries.BANNER_PATTERN, id)).orElse(null);
	}

	private static BannerStyle capped(DyeColor base, BannerPatternLayers layers) {
		if (layers.layers().size() > MAX_LAYERS) {
			return new BannerStyle(base, new BannerPatternLayers(List.copyOf(layers.layers().subList(0, MAX_LAYERS))));
		}
		return new BannerStyle(base, layers);
	}

	private static String extractBracket(String text, int start) {
		int depth = 0;
		for (int i = start; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '[') {
				depth++;
			} else if (c == ']') {
				depth--;
				if (depth == 0) {
					return text.substring(start, i + 1);
				}
			}
		}
		return null;
	}
}
