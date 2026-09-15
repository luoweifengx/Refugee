package luowei.refugee.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import luowei.refugee.Refugee;

/**
 * 客户端 → 服务端：确认或取消把蓝图分享给选中的玩家/组织。
 */
public record BlueprintSharePayload(
		boolean confirm,
		List<ResourceLocation> ids,
		List<UUID> targets
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BlueprintSharePayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("blueprint_share"));
	public static final StreamCodec<FriendlyByteBuf, BlueprintSharePayload> STREAM_CODEC =
			StreamCodec.ofMember(BlueprintSharePayload::write, BlueprintSharePayload::new);

	public BlueprintSharePayload(FriendlyByteBuf buf) {
		this(buf.readBoolean(), readIds(buf), readTargets(buf));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeBoolean(confirm);
		buf.writeCollection(ids == null ? List.of() : ids, (out, id) -> out.writeResourceLocation(id));
		buf.writeCollection(targets == null ? List.of() : targets, (out, id) -> out.writeUUID(id));
	}

	private static List<ResourceLocation> readIds(FriendlyByteBuf buf) {
		return new ArrayList<>(buf.readCollection(ArrayList::new, in -> in.readResourceLocation()));
	}

	private static List<UUID> readTargets(FriendlyByteBuf buf) {
		return new ArrayList<>(buf.readCollection(ArrayList::new, in -> in.readUUID()));
	}

	@Override
	public CustomPacketPayload.Type<BlueprintSharePayload> type() {
		return TYPE;
	}
}
