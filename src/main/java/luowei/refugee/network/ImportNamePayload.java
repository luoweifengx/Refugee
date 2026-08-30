package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;

/**
 * 客户端 → 服务端：导入命名确认或取消。
 */
public record ImportNamePayload(boolean confirm, String name) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ImportNamePayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("import_name"));
	public static final StreamCodec<FriendlyByteBuf, ImportNamePayload> STREAM_CODEC =
			StreamCodec.ofMember(ImportNamePayload::write, ImportNamePayload::new);

	public ImportNamePayload(FriendlyByteBuf buf) {
		this(buf.readBoolean(), buf.readUtf(32));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeBoolean(confirm);
		buf.writeUtf(name == null ? "" : name, 32);
	}

	@Override
	public CustomPacketPayload.Type<ImportNamePayload> type() {
		return TYPE;
	}
}
