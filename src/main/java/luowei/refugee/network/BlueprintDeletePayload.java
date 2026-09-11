package luowei.refugee.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import luowei.refugee.Refugee;

/**
 * 客户端 → 服务端：删除一条组织共享的导入蓝图。
 */
public record BlueprintDeletePayload(ResourceLocation id) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BlueprintDeletePayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("blueprint_delete"));
	public static final StreamCodec<FriendlyByteBuf, BlueprintDeletePayload> STREAM_CODEC =
			StreamCodec.ofMember(BlueprintDeletePayload::write, BlueprintDeletePayload::new);

	public BlueprintDeletePayload(FriendlyByteBuf buf) {
		this(buf.readResourceLocation());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeResourceLocation(id);
	}

	@Override
	public CustomPacketPayload.Type<BlueprintDeletePayload> type() {
		return TYPE;
	}
}
