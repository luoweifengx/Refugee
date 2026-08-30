package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;

/**
 * 服务端 → 客户端：只更新当前开屏对话框台词，不重建界面。
 */
public record SpecialSplashTalkPayload(int entityId, String talkKey) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpecialSplashTalkPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("special_splash_talk"));
	public static final StreamCodec<FriendlyByteBuf, SpecialSplashTalkPayload> STREAM_CODEC =
			StreamCodec.ofMember(SpecialSplashTalkPayload::write, SpecialSplashTalkPayload::new);

	public SpecialSplashTalkPayload(FriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readUtf());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(entityId);
		buf.writeUtf(talkKey == null ? "" : talkKey);
	}

	@Override
	public CustomPacketPayload.Type<SpecialSplashTalkPayload> type() {
		return TYPE;
	}
}
