package luowei.refugee.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;
import luowei.refugee.staff.RelationsListKind;

/**
 * 客户端 → 服务端：确认或取消人员关系玩家列表。
 */
public record RelationsPickPayload(RelationsListKind kind, boolean confirm, List<UUID> ids) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RelationsPickPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("relations_pick"));
	public static final StreamCodec<FriendlyByteBuf, RelationsPickPayload> STREAM_CODEC =
			StreamCodec.ofMember(RelationsPickPayload::write, RelationsPickPayload::new);

	public RelationsPickPayload(FriendlyByteBuf buf) {
		this(
				RelationsListKind.byOrdinal(buf.readVarInt()),
				buf.readBoolean(),
				buf.readCollection(ArrayList::new, in -> in.readUUID())
		);
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(kind == null ? 0 : kind.ordinal());
		buf.writeBoolean(confirm);
		buf.writeCollection(ids == null ? List.of() : ids, (out, id) -> out.writeUUID(id));
	}

	@Override
	public CustomPacketPayload.Type<RelationsPickPayload> type() {
		return TYPE;
	}
}
