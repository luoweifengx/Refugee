package luowei.refugee.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;

/**
 * 服务端 → 客户端：打开特殊居民开屏对话。
 */
public record SpecialSplashPayload(
		int entityId,
		String roleId,
		List<String> talkLines,
		String initialTalkKey,
		byte screenMode,
		int introIndex,
		String interruptKey,
		boolean foodSecret
) implements CustomPacketPayload {
	public static final byte MODE_NORMAL = 0;
	public static final byte MODE_INTRO = 1;
	public static final byte MODE_ABANDON = 2;

	public static final CustomPacketPayload.Type<SpecialSplashPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("special_splash"));
	public static final StreamCodec<FriendlyByteBuf, SpecialSplashPayload> STREAM_CODEC =
			StreamCodec.ofMember(SpecialSplashPayload::write, SpecialSplashPayload::new);

	public SpecialSplashPayload(int entityId, String roleId, List<String> talkLines) {
		this(entityId, roleId, talkLines, null, MODE_NORMAL, 0, "", false);
	}

	public SpecialSplashPayload(int entityId, String roleId, List<String> talkLines, String initialTalkKey) {
		this(entityId, roleId, talkLines, initialTalkKey, MODE_NORMAL, 0, "", false);
	}

	public SpecialSplashPayload(FriendlyByteBuf buf) {
		this(
				buf.readVarInt(),
				buf.readUtf(),
				readLines(buf),
				readOptionalUtf(buf),
				buf.readByte(),
				buf.readVarInt(),
				readOptionalUtf(buf),
				buf.readBoolean()
		);
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(entityId);
		buf.writeUtf(roleId == null ? "" : roleId);
		List<String> lines = talkLines == null ? List.of() : talkLines;
		buf.writeVarInt(lines.size());
		for (String line : lines) {
			buf.writeUtf(line == null ? "" : line);
		}
		buf.writeUtf(initialTalkKey == null ? "" : initialTalkKey);
		buf.writeByte(screenMode);
		buf.writeVarInt(introIndex);
		buf.writeUtf(interruptKey == null ? "" : interruptKey);
		buf.writeBoolean(foodSecret);
	}

	private static String readOptionalUtf(FriendlyByteBuf buf) {
		String value = buf.readUtf();
		return value == null || value.isBlank() ? null : value;
	}

	private static List<String> readLines(FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		List<String> lines = new ArrayList<>(Math.max(size, 0));
		for (int i = 0; i < size; i++) {
			lines.add(buf.readUtf());
		}
		return List.copyOf(lines);
	}

	@Override
	public CustomPacketPayload.Type<SpecialSplashPayload> type() {
		return TYPE;
	}
}
