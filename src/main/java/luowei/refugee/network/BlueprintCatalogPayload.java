package luowei.refugee.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import luowei.refugee.Refugee;
import luowei.refugee.blueprint.BlueprintCatalogEntry;

/**
 * 服务端 → 客户端：蓝图目录 + 结构 NBT（幽灵预览缓存）；{@code open} 为真时打开选择界面。
 */
public record BlueprintCatalogPayload(
		List<BlueprintCatalogEntry> entries,
		Map<ResourceLocation, CompoundTag> templates,
		boolean open,
		InteractionHand hand,
		Optional<ResourceLocation> selected
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BlueprintCatalogPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("blueprint_catalog"));
	public static final StreamCodec<FriendlyByteBuf, BlueprintCatalogPayload> STREAM_CODEC =
			StreamCodec.ofMember(BlueprintCatalogPayload::write, BlueprintCatalogPayload::new);

	public BlueprintCatalogPayload(FriendlyByteBuf buf) {
		this(
				readEntries(buf),
				readTemplates(buf),
				buf.readBoolean(),
				buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND,
				buf.readBoolean() ? Optional.of(buf.readResourceLocation()) : Optional.empty()
		);
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeCollection(entries, (out, entry) -> {
			out.writeResourceLocation(entry.id());
			out.writeUtf(entry.displayName());
			out.writeBoolean(entry.imported());
		});
		buf.writeVarInt(templates.size());
		for (Map.Entry<ResourceLocation, CompoundTag> entry : templates.entrySet()) {
			buf.writeResourceLocation(entry.getKey());
			buf.writeNbt(entry.getValue());
		}
		buf.writeBoolean(open);
		buf.writeBoolean(hand == InteractionHand.MAIN_HAND);
		boolean hasSelected = selected != null && selected.isPresent();
		buf.writeBoolean(hasSelected);
		if (hasSelected) {
			buf.writeResourceLocation(selected.get());
		}
	}

	private static List<BlueprintCatalogEntry> readEntries(FriendlyByteBuf buf) {
		return new ArrayList<>(buf.readCollection(
				ArrayList::new,
				in -> new BlueprintCatalogEntry(in.readResourceLocation(), in.readUtf(), in.readBoolean())
		));
	}

	private static Map<ResourceLocation, CompoundTag> readTemplates(FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		Map<ResourceLocation, CompoundTag> map = new LinkedHashMap<>(Math.max(size, 1));
		for (int i = 0; i < size; i++) {
			ResourceLocation id = buf.readResourceLocation();
			CompoundTag nbt = buf.readNbt();
			if (nbt != null) {
				map.put(id, nbt);
			}
		}
		return map;
	}

	@Override
	public CustomPacketPayload.Type<BlueprintCatalogPayload> type() {
		return TYPE;
	}
}
