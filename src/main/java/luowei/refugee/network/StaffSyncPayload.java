package luowei.refugee.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import luowei.refugee.Refugee;
import luowei.refugee.staff.StaffMode;
import luowei.refugee.staff.StaffPage;
import luowei.refugee.zone.AreaBox;

/**
 * 服务端 → 客户端：指挥杖页面、各类仓库箱、熔炼处、工作区、建筑任务描边、导入框选预览、巡逻点。
 */
public record StaffSyncPayload(
		StaffMode mode,
		StaffPage page,
		List<BlockPos> chests,
		List<BlockPos> foodChests,
		List<BlockPos> furnaces,
		List<AreaBox> zones,
		List<AreaBox> builds,
		Optional<BlockPos> pendingCorner,
		Optional<AreaBox> importBox,
		List<BlockPos> patrolPoints,
		List<BlockPos> farmChests,
		List<BlockPos> gearChests,
		List<BlockPos> resultChests
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StaffSyncPayload> TYPE =
			new CustomPacketPayload.Type<>(Refugee.id("staff_sync"));
	public static final StreamCodec<FriendlyByteBuf, StaffSyncPayload> STREAM_CODEC =
			StreamCodec.ofMember(StaffSyncPayload::write, StaffSyncPayload::new);

	public StaffSyncPayload(FriendlyByteBuf buf) {
		this(
				StaffMode.byOrdinal(buf.readVarInt()),
				StaffPage.byOrdinal(buf.readVarInt()),
				readPosList(buf),
				readPosList(buf),
				readPosList(buf),
				readBoxes(buf),
				readBoxes(buf),
				buf.readBoolean() ? Optional.of(buf.readBlockPos()) : Optional.empty(),
				buf.readBoolean() ? Optional.of(new AreaBox(buf.readBlockPos(), buf.readBlockPos())) : Optional.empty(),
				readPosList(buf),
				readPosList(buf),
				readPosList(buf),
				readPosList(buf)
		);
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(mode == null ? 0 : mode.ordinal());
		buf.writeVarInt(page == null ? 0 : page.ordinal());
		writePosList(buf, chests);
		writePosList(buf, foodChests);
		writePosList(buf, furnaces);
		writeBoxes(buf, zones);
		writeBoxes(buf, builds);
		buf.writeBoolean(pendingCorner != null && pendingCorner.isPresent());
		if (pendingCorner != null) {
			pendingCorner.ifPresent(buf::writeBlockPos);
		}
		buf.writeBoolean(importBox != null && importBox.isPresent());
		if (importBox != null && importBox.isPresent()) {
			buf.writeBlockPos(importBox.get().min());
			buf.writeBlockPos(importBox.get().max());
		}
		writePosList(buf, patrolPoints);
		writePosList(buf, farmChests);
		writePosList(buf, gearChests);
		writePosList(buf, resultChests);
	}

	private static void writePosList(FriendlyByteBuf buf, List<BlockPos> list) {
		buf.writeVarInt(list == null ? 0 : list.size());
		if (list == null) {
			return;
		}
		for (BlockPos pos : list) {
			buf.writeBlockPos(pos);
		}
	}

	private static void writeBoxes(FriendlyByteBuf buf, List<AreaBox> boxes) {
		buf.writeVarInt(boxes == null ? 0 : boxes.size());
		if (boxes == null) {
			return;
		}
		for (AreaBox box : boxes) {
			buf.writeBlockPos(box.min());
			buf.writeBlockPos(box.max());
		}
	}

	private static List<BlockPos> readPosList(FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		List<BlockPos> result = new ArrayList<>(Math.max(size, 1));
		for (int i = 0; i < size; i++) {
			result.add(buf.readBlockPos());
		}
		return result;
	}

	private static List<AreaBox> readBoxes(FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		List<AreaBox> result = new ArrayList<>(Math.max(size, 1));
		for (int i = 0; i < size; i++) {
			result.add(new AreaBox(buf.readBlockPos(), buf.readBlockPos()));
		}
		return result;
	}

	@Override
	public CustomPacketPayload.Type<StaffSyncPayload> type() {
		return TYPE;
	}
}
