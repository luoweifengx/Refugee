package luowei.refugee.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;

/**
 * 客户端 → 服务端：这张床的职业标记。空字符串表示无。
 */
public record BedMarkPayload(BlockPos pos, String role) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BedMarkPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("bed_mark"));
	public static final StreamCodec<FriendlyByteBuf, BedMarkPayload> STREAM_CODEC =
			StreamCodec.ofMember(BedMarkPayload::write, BedMarkPayload::new);

	public BedMarkPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos(), buf.readUtf());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeBlockPos(pos);
		buf.writeUtf(role == null ? "" : role);
	}

	@Override
	public CustomPacketPayload.Type<BedMarkPayload> type() {
		return TYPE;
	}
}
