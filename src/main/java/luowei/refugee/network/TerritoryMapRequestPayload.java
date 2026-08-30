package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;

/**
 * 客户端 → 服务端：绘图师地图仅切换观察半径。
 */
public record TerritoryMapRequestPayload(int radius, int villagerEntityId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TerritoryMapRequestPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("territory_map_request"));
	public static final StreamCodec<FriendlyByteBuf, TerritoryMapRequestPayload> STREAM_CODEC =
			StreamCodec.ofMember(TerritoryMapRequestPayload::write, TerritoryMapRequestPayload::new);

	public TerritoryMapRequestPayload(FriendlyByteBuf buf) {
		this(buf.readVarInt(), buf.readVarInt());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(radius);
		buf.writeVarInt(villagerEntityId);
	}

	@Override
	public CustomPacketPayload.Type<TerritoryMapRequestPayload> type() {
		return TYPE;
	}
}
