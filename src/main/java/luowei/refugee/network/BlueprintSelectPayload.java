package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

import luowei.refugee.Refugee;

/**
 * 客户端 → 服务端：把选定结构 id 写到指定手里的蓝图。
 */
public record BlueprintSelectPayload(ResourceLocation id, InteractionHand hand) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BlueprintSelectPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("blueprint_select"));
	public static final StreamCodec<FriendlyByteBuf, BlueprintSelectPayload> STREAM_CODEC =
			StreamCodec.ofMember(BlueprintSelectPayload::write, BlueprintSelectPayload::new);

	public BlueprintSelectPayload(FriendlyByteBuf buf) {
		this(buf.readResourceLocation(), buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeResourceLocation(id);
		buf.writeBoolean(hand == InteractionHand.MAIN_HAND);
	}

	@Override
	public CustomPacketPayload.Type<BlueprintSelectPayload> type() {
		return TYPE;
	}
}
