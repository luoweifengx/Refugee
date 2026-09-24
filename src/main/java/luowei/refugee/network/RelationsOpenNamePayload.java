package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;
import luowei.refugee.staff.RelationsNameKind;

/**
 * 服务端 → 客户端：打开创建组织或改领地名的命名框。
 */
public record RelationsOpenNamePayload(RelationsNameKind kind, String suggested) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RelationsOpenNamePayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("relations_open_name"));
	public static final StreamCodec<FriendlyByteBuf, RelationsOpenNamePayload> STREAM_CODEC =
			StreamCodec.ofMember(RelationsOpenNamePayload::write, RelationsOpenNamePayload::new);

	public RelationsOpenNamePayload(FriendlyByteBuf buf) {
		this(RelationsNameKind.byOrdinal(buf.readVarInt()), buf.readUtf(32));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(kind == null ? 0 : kind.ordinal());
		buf.writeUtf(suggested == null ? "" : suggested, 32);
	}

	@Override
	public CustomPacketPayload.Type<RelationsOpenNamePayload> type() {
		return TYPE;
	}
}
