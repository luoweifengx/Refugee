package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;

/**
 * 客户端 → 服务端：确认或取消两段领地文字。
 */
public record RelationsTextsPayload(boolean confirm, String selfText, String othersText) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RelationsTextsPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("relations_texts"));
	public static final StreamCodec<FriendlyByteBuf, RelationsTextsPayload> STREAM_CODEC =
			StreamCodec.ofMember(RelationsTextsPayload::write, RelationsTextsPayload::new);

	public RelationsTextsPayload(FriendlyByteBuf buf) {
		this(buf.readBoolean(), buf.readUtf(32), buf.readUtf(32));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeBoolean(confirm);
		buf.writeUtf(selfText == null ? "" : selfText, 32);
		buf.writeUtf(othersText == null ? "" : othersText, 32);
	}

	@Override
	public CustomPacketPayload.Type<RelationsTextsPayload> type() {
		return TYPE;
	}
}
