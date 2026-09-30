package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;
import luowei.refugee.staff.RelationDeskPage;

/**
 * 服务端 → 客户端：打开关系管理台的某一页。
 */
public record RelationDeskOpenPayload(RelationDeskPage page) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RelationDeskOpenPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("relation_desk_open"));
	public static final StreamCodec<FriendlyByteBuf, RelationDeskOpenPayload> STREAM_CODEC =
			StreamCodec.ofMember(RelationDeskOpenPayload::write, RelationDeskOpenPayload::new);

	public RelationDeskOpenPayload(FriendlyByteBuf buf) {
		this(RelationDeskPage.byOrdinal(buf.readVarInt()));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(page == null ? 0 : page.ordinal());
	}

	@Override
	public CustomPacketPayload.Type<RelationDeskOpenPayload> type() {
		return TYPE;
	}
}
