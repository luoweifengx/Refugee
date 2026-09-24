package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;

/**
 * 服务端 → 客户端：打开自己未处理的组织邀请。PBS 同一时间只保留一条。
 */
public record RelationsOpenInvitesPayload(boolean pending, String orgName, String territoryName) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RelationsOpenInvitesPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("relations_open_invites"));
	public static final StreamCodec<FriendlyByteBuf, RelationsOpenInvitesPayload> STREAM_CODEC =
			StreamCodec.ofMember(RelationsOpenInvitesPayload::write, RelationsOpenInvitesPayload::new);

	public RelationsOpenInvitesPayload(FriendlyByteBuf buf) {
		this(buf.readBoolean(), buf.readUtf(64), buf.readUtf(64));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeBoolean(pending);
		buf.writeUtf(orgName == null ? "" : orgName, 64);
		buf.writeUtf(territoryName == null ? "" : territoryName, 64);
	}

	@Override
	public CustomPacketPayload.Type<RelationsOpenInvitesPayload> type() {
		return TYPE;
	}
}
