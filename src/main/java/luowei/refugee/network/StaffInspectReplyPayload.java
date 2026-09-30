package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;

/**
 * 服务端 → 客户端：这名居民能否检视，以及饱食、体力、舒适、忠诚。
 */
public record StaffInspectReplyPayload(
		int entityId,
		boolean allowed,
		float satiety,
		float stamina,
		float comfort,
		float loyalty,
		float statMax
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StaffInspectReplyPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("staff_inspect_reply"));
	public static final StreamCodec<FriendlyByteBuf, StaffInspectReplyPayload> STREAM_CODEC =
			StreamCodec.ofMember(StaffInspectReplyPayload::write, StaffInspectReplyPayload::new);

	public StaffInspectReplyPayload(FriendlyByteBuf buf) {
		this(
				buf.readVarInt(),
				buf.readBoolean(),
				buf.readFloat(),
				buf.readFloat(),
				buf.readFloat(),
				buf.readFloat(),
				buf.readFloat()
		);
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(entityId);
		buf.writeBoolean(allowed);
		buf.writeFloat(satiety);
		buf.writeFloat(stamina);
		buf.writeFloat(comfort);
		buf.writeFloat(loyalty);
		buf.writeFloat(statMax);
	}

	@Override
	public CustomPacketPayload.Type<StaffInspectReplyPayload> type() {
		return TYPE;
	}
}
