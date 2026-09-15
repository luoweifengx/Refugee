package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

import luowei.refugee.Refugee;
import luowei.refugee.settle.BannerStyle;

/**
 * 服务端 → 客户端：打开安顿旗样式编辑。
 */
public record OpenBannerStylePayload(InteractionHand hand, String text) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenBannerStylePayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("open_banner_style"));
	public static final StreamCodec<FriendlyByteBuf, OpenBannerStylePayload> STREAM_CODEC =
			StreamCodec.ofMember(OpenBannerStylePayload::write, OpenBannerStylePayload::new);

	public OpenBannerStylePayload(FriendlyByteBuf buf) {
		this(buf.readEnum(InteractionHand.class), buf.readUtf(BannerStyle.MAX_INPUT));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeEnum(hand == null ? InteractionHand.MAIN_HAND : hand);
		buf.writeUtf(text == null ? "" : text, BannerStyle.MAX_INPUT);
	}

	@Override
	public CustomPacketPayload.Type<OpenBannerStylePayload> type() {
		return TYPE;
	}
}
