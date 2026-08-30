package luowei.refugee.network;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import luowei.refugee.Refugee;

/**
 * 服务端 → 客户端：结构选定与建造原点，供幽灵预览。
 */
public record BlueprintSelectionPayload(
		Optional<ResourceLocation> structureId,
		Optional<BlockPos> buildOrigin
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BlueprintSelectionPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("blueprint_selection"));
	public static final StreamCodec<FriendlyByteBuf, BlueprintSelectionPayload> STREAM_CODEC =
			StreamCodec.ofMember(BlueprintSelectionPayload::write, BlueprintSelectionPayload::new);

	public BlueprintSelectionPayload(FriendlyByteBuf buf) {
		this(readOptionalId(buf), readOptionalPos(buf));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeBoolean(structureId.isPresent());
		structureId.ifPresent(buf::writeResourceLocation);
		writeOptionalPos(buf, buildOrigin);
	}

	private static Optional<ResourceLocation> readOptionalId(FriendlyByteBuf buf) {
		return buf.readBoolean() ? Optional.of(buf.readResourceLocation()) : Optional.empty();
	}

	private static Optional<BlockPos> readOptionalPos(FriendlyByteBuf buf) {
		return buf.readBoolean() ? Optional.of(buf.readBlockPos()) : Optional.empty();
	}

	private static void writeOptionalPos(FriendlyByteBuf buf, Optional<BlockPos> pos) {
		buf.writeBoolean(pos.isPresent());
		pos.ifPresent(buf::writeBlockPos);
	}

	@Override
	public CustomPacketPayload.Type<BlueprintSelectionPayload> type() {
		return TYPE;
	}
}
