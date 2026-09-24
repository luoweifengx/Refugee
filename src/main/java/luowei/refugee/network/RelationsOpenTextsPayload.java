package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;

/**
 * 服务端 → 客户端：打开领地文字，带上自己看到的和别人看到的两段现文。
 */
public record RelationsOpenTextsPayload(String selfText, String othersText, boolean othersEditable) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RelationsOpenTextsPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("relations_open_texts"));
	public static final StreamCodec<FriendlyByteBuf, RelationsOpenTextsPayload> STREAM_CODEC =
			StreamCodec.ofMember(RelationsOpenTextsPayload::write, RelationsOpenTextsPayload::new);

	public RelationsOpenTextsPayload(FriendlyByteBuf buf) {
		this(buf.readUtf(32), buf.readUtf(32), buf.readBoolean());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeUtf(selfText == null ? "" : selfText, 32);
		buf.writeUtf(othersText == null ? "" : othersText, 32);
		buf.writeBoolean(othersEditable);
	}

	@Override
	public CustomPacketPayload.Type<RelationsOpenTextsPayload> type() {
		return TYPE;
	}
}
