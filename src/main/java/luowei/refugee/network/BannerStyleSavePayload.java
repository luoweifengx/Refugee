package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

import luowei.refugee.Refugee;
import luowei.refugee.settle.BannerStyle;

/**
 * 客户端 → 服务端：保存安顿旗样式。
 */
public record BannerStyleSavePayload(InteractionHand hand, String text, boolean copyOffhand) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BannerStyleSavePayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("banner_style_save"));
	public static final StreamCodec<FriendlyByteBuf, BannerStyleSavePayload> STREAM_CODEC =
			StreamCodec.ofMember(BannerStyleSavePayload::write, BannerStyleSavePayload::new);

	public BannerStyleSavePayload(FriendlyByteBuf buf) {
		this(buf.readEnum(InteractionHand.class), buf.readUtf(BannerStyle.MAX_INPUT), buf.readBoolean());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeEnum(hand == null ? InteractionHand.MAIN_HAND : hand);
		buf.writeUtf(text == null ? "" : text, BannerStyle.MAX_INPUT);
		buf.writeBoolean(copyOffhand);
	}

	@Override
	public CustomPacketPayload.Type<BannerStyleSavePayload> type() {
		return TYPE;
	}
}
