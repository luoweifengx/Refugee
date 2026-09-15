package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;
import luowei.refugee.blueprint.BlueprintUpload;

/**
 * 客户端 → 服务端：开始上传一份蓝图文件。
 */
public record BlueprintUploadStartPayload(String name, int totalBytes, int chunks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BlueprintUploadStartPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("blueprint_upload_start"));
	public static final StreamCodec<FriendlyByteBuf, BlueprintUploadStartPayload> STREAM_CODEC =
			StreamCodec.ofMember(BlueprintUploadStartPayload::write, BlueprintUploadStartPayload::new);

	public BlueprintUploadStartPayload(FriendlyByteBuf buf) {
		this(buf.readUtf(32), buf.readVarInt(), buf.readVarInt());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeUtf(name == null ? "" : name, 32);
		buf.writeVarInt(totalBytes);
		buf.writeVarInt(chunks);
	}

	public boolean valid() {
		return totalBytes > 0
				&& totalBytes <= BlueprintUpload.MAX_BYTES
				&& chunks > 0
				&& chunks <= BlueprintUpload.MAX_CHUNKS
				&& chunks == BlueprintUpload.chunkCount(totalBytes);
	}

	@Override
	public CustomPacketPayload.Type<BlueprintUploadStartPayload> type() {
		return TYPE;
	}
}
