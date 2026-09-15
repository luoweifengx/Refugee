package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;
import luowei.refugee.blueprint.BlueprintUpload;

/**
 * 客户端 → 服务端：蓝图文件的一片字节。
 */
public record BlueprintUploadChunkPayload(int index, byte[] data) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BlueprintUploadChunkPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("blueprint_upload_chunk"));
	public static final StreamCodec<FriendlyByteBuf, BlueprintUploadChunkPayload> STREAM_CODEC =
			StreamCodec.ofMember(BlueprintUploadChunkPayload::write, BlueprintUploadChunkPayload::new);

	public BlueprintUploadChunkPayload(FriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readByteArray(BlueprintUpload.CHUNK_SIZE));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(index);
		buf.writeByteArray(data == null ? new byte[0] : data);
	}

	@Override
	public CustomPacketPayload.Type<BlueprintUploadChunkPayload> type() {
		return TYPE;
	}
}
