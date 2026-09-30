package luowei.refugee.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;

/**
 * 服务端 → 客户端：床已放下，打开职业标记页。
 */
public record OpenBedMarkPayload(BlockPos pos, List<String> specials) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OpenBedMarkPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("open_bed_mark"));
	public static final StreamCodec<FriendlyByteBuf, OpenBedMarkPayload> STREAM_CODEC =
			StreamCodec.ofMember(OpenBedMarkPayload::write, OpenBedMarkPayload::new);

	public OpenBedMarkPayload(FriendlyByteBuf buf) {
		this(buf.readBlockPos(), buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeBlockPos(pos);
		buf.writeCollection(specials == null ? List.of() : specials, FriendlyByteBuf::writeUtf);
	}

	@Override
	public CustomPacketPayload.Type<OpenBedMarkPayload> type() {
		return TYPE;
	}
}
