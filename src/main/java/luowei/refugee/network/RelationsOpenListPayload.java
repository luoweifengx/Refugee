package luowei.refugee.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;
import luowei.refugee.staff.RelationsListKind;

/**
 * 服务端 → 客户端：打开人员关系的玩家列表。
 */
public record RelationsOpenListPayload(RelationsListKind kind, List<RelationsPlayerRow> rows) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RelationsOpenListPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("relations_open_list"));
	public static final StreamCodec<FriendlyByteBuf, RelationsOpenListPayload> STREAM_CODEC =
			StreamCodec.ofMember(RelationsOpenListPayload::write, RelationsOpenListPayload::new);

	public RelationsOpenListPayload(FriendlyByteBuf buf) {
		this(
				RelationsListKind.byOrdinal(buf.readVarInt()),
				buf.readCollection(ArrayList::new, in -> new RelationsPlayerRow(
						in.readUUID(),
						in.readUtf(64),
						in.readUtf(64)
				))
		);
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(kind == null ? 0 : kind.ordinal());
		buf.writeCollection(rows == null ? List.of() : rows, (out, row) -> {
			out.writeUUID(row.id());
			out.writeUtf(row.name() == null ? "" : row.name(), 64);
			out.writeUtf(row.detail() == null ? "" : row.detail(), 64);
		});
	}

	@Override
	public CustomPacketPayload.Type<RelationsOpenListPayload> type() {
		return TYPE;
	}
}
