package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;
import luowei.refugee.staff.StaffPieAction;

/**
 * 客户端 → 服务端：饼图扇区左键。
 */
public record StaffPiePayload(StaffPieAction action) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StaffPiePayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("staff_pie"));
	public static final StreamCodec<FriendlyByteBuf, StaffPiePayload> STREAM_CODEC =
			StreamCodec.ofMember(StaffPiePayload::write, StaffPiePayload::new);

	public StaffPiePayload(FriendlyByteBuf buf) {
		this(StaffPieAction.byOrdinal(buf.readVarInt()));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(action == null ? -1 : action.ordinal());
	}

	@Override
	public CustomPacketPayload.Type<StaffPiePayload> type() {
		return TYPE;
	}
}
