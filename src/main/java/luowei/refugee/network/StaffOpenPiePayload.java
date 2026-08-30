package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;

/**
 * 服务端 → 客户端：打开指挥杖扇形菜单。
 */
public record StaffOpenPiePayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StaffOpenPiePayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("staff_open_pie"));
	public static final StreamCodec<FriendlyByteBuf, StaffOpenPiePayload> STREAM_CODEC =
			StreamCodec.ofMember(StaffOpenPiePayload::write, StaffOpenPiePayload::new);

	public StaffOpenPiePayload(FriendlyByteBuf buf) {
		this();
	}

	public void write(FriendlyByteBuf buf) {
	}

	@Override
	public CustomPacketPayload.Type<StaffOpenPiePayload> type() {
		return TYPE;
	}
}
