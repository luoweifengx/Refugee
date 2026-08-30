package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;

/**
 * 客户端 → 服务端：开屏上选择交谈 / 治疗 / 地界 / 交易。
 */
public record SpecialSplashActionPayload(int entityId, SpecialSplashAction action) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpecialSplashActionPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("special_splash_action"));
	public static final StreamCodec<FriendlyByteBuf, SpecialSplashActionPayload> STREAM_CODEC =
			StreamCodec.ofMember(SpecialSplashActionPayload::write, SpecialSplashActionPayload::new);

	public SpecialSplashActionPayload(FriendlyByteBuf buf) {
		this(buf.readVarInt(), SpecialSplashAction.byId(buf.readByte()));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(entityId);
		buf.writeByte(action == null ? -1 : action.ordinal());
	}

	@Override
	public CustomPacketPayload.Type<SpecialSplashActionPayload> type() {
		return TYPE;
	}
}
