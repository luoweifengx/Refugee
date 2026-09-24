package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;

/**
 * 客户端 → 服务端：接受或拒绝自己的组织邀请。
 */
public record RelationsInviteReplyPayload(boolean accept) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RelationsInviteReplyPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("relations_invite_reply"));
	public static final StreamCodec<FriendlyByteBuf, RelationsInviteReplyPayload> STREAM_CODEC =
			StreamCodec.ofMember(RelationsInviteReplyPayload::write, RelationsInviteReplyPayload::new);

	public RelationsInviteReplyPayload(FriendlyByteBuf buf) {
		this(buf.readBoolean());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeBoolean(accept);
	}

	@Override
	public CustomPacketPayload.Type<RelationsInviteReplyPayload> type() {
		return TYPE;
	}
}
