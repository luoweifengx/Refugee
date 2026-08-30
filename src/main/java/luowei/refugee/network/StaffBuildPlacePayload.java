package luowei.refugee.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.Rotation;

import luowei.refugee.Refugee;

/**
 * 客户端 → 服务端：建造预览右键确认，带原点、偏移与旋转。
 */
public record StaffBuildPlacePayload(
		BlockPos origin,
		int offsetX,
		int offsetY,
		int offsetZ,
		Rotation rotation
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StaffBuildPlacePayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("staff_build_place"));
	public static final StreamCodec<FriendlyByteBuf, StaffBuildPlacePayload> STREAM_CODEC =
			StreamCodec.ofMember(StaffBuildPlacePayload::write, StaffBuildPlacePayload::new);

	public StaffBuildPlacePayload(FriendlyByteBuf buf) {
		this(
				buf.readBlockPos(),
				buf.readVarInt(),
				buf.readVarInt(),
				buf.readVarInt(),
				buf.readEnum(Rotation.class)
		);
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeBlockPos(origin == null ? BlockPos.ZERO : origin);
		buf.writeVarInt(offsetX);
		buf.writeVarInt(offsetY);
		buf.writeVarInt(offsetZ);
		buf.writeEnum(rotation == null ? Rotation.NONE : rotation);
	}

	@Override
	public CustomPacketPayload.Type<StaffBuildPlacePayload> type() {
		return TYPE;
	}
}
