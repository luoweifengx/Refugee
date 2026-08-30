package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;

/**
 * 服务端 → 客户端：绘图师领地区块网格。
 */
public record TerritoryMapPayload(
		int centerX,
		int centerZ,
		int radius,
		byte[] cells,
		boolean open,
		int villagerEntityId
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TerritoryMapPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("territory_map"));
	public static final StreamCodec<FriendlyByteBuf, TerritoryMapPayload> STREAM_CODEC =
			StreamCodec.ofMember(TerritoryMapPayload::write, TerritoryMapPayload::new);

	public TerritoryMapPayload(FriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(), buf.readBoolean(), buf.readVarInt());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(centerX);
		buf.writeVarInt(centerZ);
		buf.writeVarInt(radius);
		buf.writeByteArray(cells);
		buf.writeBoolean(open);
		buf.writeVarInt(villagerEntityId);
	}

	@Override
	public CustomPacketPayload.Type<TerritoryMapPayload> type() {
		return TYPE;
	}
}
