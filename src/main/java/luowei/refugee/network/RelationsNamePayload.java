package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;
import luowei.refugee.staff.RelationsNameKind;

/**
 * 客户端 → 服务端：确认或取消组织名 / 领地名。
 */
public record RelationsNamePayload(RelationsNameKind kind, boolean confirm, String name) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RelationsNamePayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("relations_name"));
	public static final StreamCodec<FriendlyByteBuf, RelationsNamePayload> STREAM_CODEC =
			StreamCodec.ofMember(RelationsNamePayload::write, RelationsNamePayload::new);

	public RelationsNamePayload(FriendlyByteBuf buf) {
		this(RelationsNameKind.byOrdinal(buf.readVarInt()), buf.readBoolean(), buf.readUtf(32));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(kind == null ? 0 : kind.ordinal());
		buf.writeBoolean(confirm);
		buf.writeUtf(name == null ? "" : name, 32);
	}

	@Override
	public CustomPacketPayload.Type<RelationsNamePayload> type() {
		return TYPE;
	}
}
