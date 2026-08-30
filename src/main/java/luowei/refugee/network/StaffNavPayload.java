package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;
import luowei.refugee.staff.StaffNavAction;

/**
 * 客户端 → 服务端：指挥杖回到根。
 */
public record StaffNavPayload(StaffNavAction action) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StaffNavPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("staff_nav"));
	public static final StreamCodec<FriendlyByteBuf, StaffNavPayload> STREAM_CODEC =
			StreamCodec.ofMember(StaffNavPayload::write, StaffNavPayload::new);

	public StaffNavPayload(FriendlyByteBuf buf) {
		this(StaffNavAction.byOrdinal(buf.readVarInt()));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(action == null ? -1 : action.ordinal());
	}

	@Override
	public CustomPacketPayload.Type<StaffNavPayload> type() {
		return TYPE;
	}
}
