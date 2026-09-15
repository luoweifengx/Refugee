package luowei.refugee.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import luowei.refugee.Refugee;
import luowei.refugee.blueprint.BlueprintShareTarget;

/**
 * 服务端 → 客户端：打开与选择建筑同一套列表的分享界面。
 */
public record BlueprintShareTargetsPayload(
		List<ResourceLocation> ids,
		List<BlueprintShareTarget> targets
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BlueprintShareTargetsPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("blueprint_share_targets"));
	public static final StreamCodec<FriendlyByteBuf, BlueprintShareTargetsPayload> STREAM_CODEC =
			StreamCodec.ofMember(BlueprintShareTargetsPayload::write, BlueprintShareTargetsPayload::new);

	public BlueprintShareTargetsPayload(FriendlyByteBuf buf) {
		this(
				buf.readCollection(ArrayList::new, FriendlyByteBuf::readResourceLocation),
				buf.readCollection(ArrayList::new, in -> new BlueprintShareTarget(
						in.readUUID(),
						in.readUtf(64),
						in.readUtf(64),
						in.readBoolean()
				))
		);
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeCollection(ids == null ? List.of() : ids, FriendlyByteBuf::writeResourceLocation);
		buf.writeCollection(targets == null ? List.of() : targets, (out, target) -> {
			out.writeUUID(target.id());
			out.writeUtf(target.name() == null ? "" : target.name(), 64);
			out.writeUtf(target.territoryName() == null ? "" : target.territoryName(), 64);
			out.writeBoolean(target.organization());
		});
	}

	@Override
	public CustomPacketPayload.Type<BlueprintShareTargetsPayload> type() {
		return TYPE;
	}
}
