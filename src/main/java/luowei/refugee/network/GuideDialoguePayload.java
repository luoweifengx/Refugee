package luowei.refugee.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;
import luowei.refugee.special.GuideDialogueConfig.LocalizedEntry;

/**
 * 服务端 → 客户端：打开向导对话界面。
 */
public record GuideDialoguePayload(List<LocalizedEntry> entries) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GuideDialoguePayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("guide_dialogue"));
	public static final StreamCodec<FriendlyByteBuf, GuideDialoguePayload> STREAM_CODEC =
			StreamCodec.ofMember(GuideDialoguePayload::write, GuideDialoguePayload::new);

	public GuideDialoguePayload(FriendlyByteBuf buf) {
		this(readEntries(buf));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(entries.size());
		for (LocalizedEntry entry : entries) {
			buf.writeUtf(entry.id());
			buf.writeUtf(entry.title());
			buf.writeVarInt(entry.lines().size());
			for (String line : entry.lines()) {
				buf.writeUtf(line);
			}
		}
	}

	private static List<LocalizedEntry> readEntries(FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		List<LocalizedEntry> entries = new ArrayList<>(Math.max(size, 0));
		for (int i = 0; i < size; i++) {
			String id = buf.readUtf();
			String title = buf.readUtf();
			int lineCount = buf.readVarInt();
			List<String> lines = new ArrayList<>(Math.max(lineCount, 0));
			for (int j = 0; j < lineCount; j++) {
				lines.add(buf.readUtf());
			}
			entries.add(new LocalizedEntry(id, title, List.copyOf(lines)));
		}
		return entries;
	}

	@Override
	public CustomPacketPayload.Type<GuideDialoguePayload> type() {
		return TYPE;
	}
}
