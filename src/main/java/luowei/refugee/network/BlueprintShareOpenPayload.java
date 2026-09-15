package luowei.refugee.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import luowei.refugee.Refugee;

/**
 * 客户端 → 服务端：打开分享界面，带上要分享的蓝图。
 */
public record BlueprintShareOpenPayload(List<ResourceLocation> ids) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BlueprintShareOpenPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("blueprint_share_open"));
	public static final StreamCodec<FriendlyByteBuf, BlueprintShareOpenPayload> STREAM_CODEC =
			StreamCodec.ofMember(BlueprintShareOpenPayload::write, BlueprintShareOpenPayload::new);

	public BlueprintShareOpenPayload(FriendlyByteBuf buf) {
		this(readIds(buf));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeCollection(ids == null ? List.of() : ids, (out, id) -> out.writeResourceLocation(id));
	}

	private static List<ResourceLocation> readIds(FriendlyByteBuf buf) {
		return new ArrayList<>(buf.readCollection(ArrayList::new, in -> in.readResourceLocation()));
	}

	@Override
	public CustomPacketPayload.Type<BlueprintShareOpenPayload> type() {
		return TYPE;
	}
}
