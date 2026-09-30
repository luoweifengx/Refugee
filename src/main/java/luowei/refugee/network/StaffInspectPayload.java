package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;

/**
 * 客户端 → 服务端：指挥杖准心上的居民，请求检视数值。
 */
public record StaffInspectPayload(int entityId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StaffInspectPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("staff_inspect"));
	public static final StreamCodec<FriendlyByteBuf, StaffInspectPayload> STREAM_CODEC =
			StreamCodec.ofMember(StaffInspectPayload::write, StaffInspectPayload::new);

	public StaffInspectPayload(FriendlyByteBuf buf) {
		this(buf.readVarInt());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(entityId);
	}

	@Override
	public CustomPacketPayload.Type<StaffInspectPayload> type() {
		return TYPE;
	}
}
