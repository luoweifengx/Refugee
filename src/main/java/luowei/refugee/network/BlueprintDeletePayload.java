package luowei.refugee.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import luowei.refugee.Refugee;

/**
 * 客户端 → 服务端：删除自己拥有的导入/划入/上传蓝图，可多选。
 */
public record BlueprintDeletePayload(List<ResourceLocation> ids) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BlueprintDeletePayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("blueprint_delete"));
	public static final StreamCodec<FriendlyByteBuf, BlueprintDeletePayload> STREAM_CODEC =
			StreamCodec.ofMember(BlueprintDeletePayload::write, BlueprintDeletePayload::new);

	public BlueprintDeletePayload(ResourceLocation id) {
		this(id == null ? List.of() : List.of(id));
	}

	public BlueprintDeletePayload(FriendlyByteBuf buf) {
		this(readIds(buf));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeCollection(ids == null ? List.of() : ids, (out, id) -> out.writeResourceLocation(id));
	}

	private static List<ResourceLocation> readIds(FriendlyByteBuf buf) {
		return new ArrayList<>(buf.readCollection(ArrayList::new, in -> in.readResourceLocation()));
	}

	@Override
	public CustomPacketPayload.Type<BlueprintDeletePayload> type() {
		return TYPE;
	}
}
