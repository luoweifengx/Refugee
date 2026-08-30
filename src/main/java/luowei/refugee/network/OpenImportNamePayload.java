package luowei.refugee.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;

/**
 * 服务端 → 客户端：导入已框选，打开命名界面。
 */
public record OpenImportNamePayload(BlockPos min, BlockPos max, int maxAxis, int maxVolume) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenImportNamePayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("open_import_name"));
	public static final StreamCodec<FriendlyByteBuf, OpenImportNamePayload> STREAM_CODEC =
			StreamCodec.ofMember(OpenImportNamePayload::write, OpenImportNamePayload::new);

	public OpenImportNamePayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeBlockPos(min);
		buf.writeBlockPos(max);
		buf.writeVarInt(maxAxis);
		buf.writeVarInt(maxVolume);
	}

	@Override
	public CustomPacketPayload.Type<OpenImportNamePayload> type() {
		return TYPE;
	}
}
