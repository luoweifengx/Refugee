package luowei.refugee.attachment;

import com.mojang.serialization.Codec;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

import luowei.refugee.Refugee;
import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 村民所属主体与玩家选定信息，随实体 NBT 存盘（与旅商模组 Attachment 写法一致）。
 */
public final class RefugeeAttachments {
	public static final AttachmentType<RefugeeVillagerData> VILLAGER = AttachmentRegistry.create(
			Refugee.id("villager_data"),
			builder -> builder
					.initializer(RefugeeVillagerData::new)
					.persistent(RefugeeVillagerData.CODEC)
	);

	public static final AttachmentType<PlayerSelectionData> PLAYER = AttachmentRegistry.create(
			Refugee.id("player_selection"),
			builder -> builder
					.initializer(PlayerSelectionData::new)
					.persistent(PlayerSelectionData.CODEC)
					.copyOnDeath()
	);

	public static final AttachmentType<Byte> BUBBLE_ICON = AttachmentRegistry.create(
			Refugee.id("bubble_icon"),
			builder -> builder
					.initializer(() -> (byte) 0)
					.syncWith(ByteBufCodecs.BYTE, AttachmentSyncPredicate.all())
	);

	public static final AttachmentType<String> SPECIAL_ROLE = AttachmentRegistry.create(
			Refugee.id("special_role"),
			builder -> builder
					.initializer(() -> "")
					.persistent(Codec.STRING)
					.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all())
	);

	private RefugeeAttachments() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!(entity instanceof Villager villager)) {
				return;
			}
			RefugeeVillagerData data = villager.getAttached(VILLAGER);
			if (data == null) {
				return;
			}
			RefugeeSpecialRole role = data.specialRole();
			if (role != null) {
				RefugeeSpecialRole.apply(villager, role);
			}
		});
	}

	public static RefugeeVillagerData get(Villager villager) {
		return villager.getAttachedOrCreate(VILLAGER);
	}

	public static void markDirty(Villager villager, RefugeeVillagerData data) {
		villager.setAttached(VILLAGER, data);
	}

	public static boolean isRefugee(Villager villager) {
		RefugeeVillagerData data = villager.getAttached(VILLAGER);
		return data != null && data.subjectId() != null;
	}

	public static PlayerSelectionData get(ServerPlayer player) {
		return player.getAttachedOrCreate(PLAYER);
	}

	public static void markDirty(ServerPlayer player, PlayerSelectionData data) {
		player.setAttached(PLAYER, data);
	}
}
