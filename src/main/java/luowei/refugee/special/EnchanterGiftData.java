package luowei.refugee.special;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import luowei.refugee.pbs.PbsAdapter;

/**
 * 附魔台赠予标记：按 PBS 组织 UUID（否则玩家 UUID）存档，村民死亡后仍生效。
 */
public final class EnchanterGiftData extends SavedData {
	private static final String DATA_ID = "refugee_enchanter_gifts";

	public static final Codec<EnchanterGiftData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.listOf().optionalFieldOf("gifted_subjects", List.of())
					.forGetter(data -> List.copyOf(data.giftedSubjects))
	).apply(instance, EnchanterGiftData::fromCodec));

	public static final SavedDataType<EnchanterGiftData> TYPE = new SavedDataType<>(
			DATA_ID,
			context -> new EnchanterGiftData(),
			context -> EnchanterGiftData.CODEC,
			null
	);

	private final Set<UUID> giftedSubjects = new LinkedHashSet<>();

	public EnchanterGiftData() {
	}

	private static EnchanterGiftData fromCodec(List<UUID> giftedSubjects) {
		EnchanterGiftData data = new EnchanterGiftData();
		if (giftedSubjects != null) {
			data.giftedSubjects.addAll(giftedSubjects);
		}
		return data;
	}

	public static EnchanterGiftData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean hasGifted(UUID subjectId) {
		return subjectId != null && giftedSubjects.contains(subjectId);
	}

	public void markGifted(UUID subjectId) {
		if (subjectId != null && giftedSubjects.add(subjectId)) {
			setDirty();
		}
	}

	/**
	 * 该主体第一次与附魔师交谈时给予一座附魔台（背包满则掉落）。
	 *
	 * @return 是否实际给予
	 */
	public static boolean tryGiveTable(ServerPlayer player) {
		if (player == null || player.getServer() == null) {
			return false;
		}
		UUID subjectId = PbsAdapter.resolveSubject(player);
		EnchanterGiftData data = get(player.getServer());
		if (data.hasGifted(subjectId)) {
			return false;
		}
		ItemStack table = new ItemStack(Items.ENCHANTING_TABLE);
		if (!player.getInventory().add(table)) {
			player.drop(table, false);
		}
		data.markGifted(subjectId);
		player.containerMenu.broadcastChanges();
		player.displayClientMessage(Component.translatable("message.refugee.enchanter.gift_table"), false);
		return true;
	}
}
