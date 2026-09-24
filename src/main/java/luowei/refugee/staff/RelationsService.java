package luowei.refugee.staff;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import luowei.player_block_status.lib.org.OrganizationData;
import luowei.player_block_status.lib.org.OrganizationRecord;
import luowei.player_block_status.lib.event.TerritoryEnterMessagePrefs;
import luowei.player_block_status.lib.org.OrganizationService;
import luowei.player_block_status.lib.org.OrganizationService.OrganizationException;
import luowei.refugee.attachment.PlayerSelectionData;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.block.AltarBlock;
import luowei.refugee.interact.RosterService;
import luowei.refugee.network.RefugeeNetworking;
import luowei.refugee.network.RelationsPlayerRow;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.settle.StandableFinder;
import luowei.refugee.special.SpecialStoryData;
import luowei.refugee.special.SpecialStoryService;

/**
 * 指挥杖人员关系：组织创建、邀请、邀请处理、组织管理，以及献祭村民救助亡魂。
 */
public final class RelationsService {
	private RelationsService() {
	}

	public static void openCreate(ServerPlayer player) {
		if (player == null) {
			return;
		}
		if (organization(player).isPresent()) {
			fail(player, "message.refugee.staff.relations.already_in_org");
			return;
		}
		StaffService.showRelations(player, StaffPage.RELATIONS_PIE, StaffPage.ORG_CREATE);
		RefugeeNetworking.openRelationsName(player, RelationsNameKind.CREATE_ORG, "");
	}

	public static void openInvite(ServerPlayer player) {
		if (player == null) {
			return;
		}
		OrganizationRecord record = ownedOrganization(player);
		if (record == null) {
			return;
		}
		List<RelationsPlayerRow> rows = new ArrayList<>();
		MinecraftServer server = player.getServer();
		OrganizationData data = OrganizationData.get(server);
		for (ServerPlayer target : server.getPlayerList().getPlayers()) {
			if (target.getUUID().equals(player.getUUID())) {
				continue;
			}
			if (record.isMember(target.getUUID()) || data.getPlayerOrganization(target.getUUID()).isPresent()) {
				continue;
			}
			rows.add(new RelationsPlayerRow(
					target.getUUID(),
					target.getGameProfile().getName(),
					record.hasInvite(target.getUUID())
							? Component.translatable("screen.refugee.relations.invited").getString()
							: ""
			));
		}
		StaffService.showRelations(player, StaffPage.RELATIONS_PIE, StaffPage.ORG_INVITE);
		RefugeeNetworking.openRelationsList(player, RelationsListKind.INVITE, rows);
	}

	public static void openInviteManage(ServerPlayer player) {
		if (player == null) {
			return;
		}
		MinecraftServer server = player.getServer();
		Optional<OrganizationRecord> pending = OrganizationData.get(server)
				.findInviteFor(player.getUUID())
				.flatMap(orgId -> OrganizationData.get(server).getOrganization(orgId));
		StaffService.showRelations(player, StaffPage.RELATIONS_PIE, StaffPage.ORG_INVITES);
		if (pending.isEmpty()) {
			RefugeeNetworking.openRelationsInvites(player, false, "", "");
			return;
		}
		OrganizationRecord record = pending.get();
		RefugeeNetworking.openRelationsInvites(
				player,
				true,
				record.name(),
				PbsAdapter.territoryName(server, record.id())
		);
	}

	public static void openManage(ServerPlayer player) {
		if (player == null) {
			return;
		}
		if (organization(player).isEmpty()) {
			fail(player, "message.refugee.staff.relations.not_in_org");
			return;
		}
		StaffService.showRelations(player, StaffPage.ORG_MANAGE_PIE);
		RefugeeNetworking.openStaffPie(player, StaffPage.ORG_MANAGE_PIE);
	}

	public static void showInfo(ServerPlayer player) {
		OrganizationRecord record = requireMember(player);
		if (record == null) {
			return;
		}
		MinecraftServer server = player.getServer();
		boolean owner = player.getUUID().equals(record.owner());
		StringBuilder members = new StringBuilder();
		for (UUID memberId : record.members()) {
			if (!members.isEmpty()) {
				members.append("、");
			}
			members.append(PbsAdapter.displayName(server, memberId));
			if (memberId.equals(record.owner())) {
				members.append(Component.translatable("message.refugee.staff.relations.owner_mark").getString());
			}
		}
		player.sendSystemMessage(Component.translatable(
				"message.refugee.staff.relations.info",
				record.name(),
				PbsAdapter.territoryName(server, record.id()),
				record.members().size(),
				PbsAdapter.onlinePlayerCount(server, record.id()),
				Component.translatable(owner
						? "message.refugee.staff.relations.role.owner"
						: "message.refugee.staff.relations.role.member"),
				members.toString()
		));
		succeed(player);
	}

	public static void leave(ServerPlayer player) {
		if (requireMember(player) == null) {
			return;
		}
		try {
			boolean dissolved = OrganizationService.leaveOrganization(player.getServer(), player);
			player.sendSystemMessage(Component.translatable(dissolved
					? "message.refugee.staff.relations.left_dissolved"
					: "message.refugee.staff.relations.left"));
			succeed(player);
		} catch (OrganizationException exception) {
			failRaw(player, exception);
		}
	}

	public static void openKick(ServerPlayer player) {
		openMemberList(player, RelationsListKind.KICK, StaffPage.ORG_KICK, false);
	}

	public static void openTransfer(ServerPlayer player) {
		openMemberList(player, RelationsListKind.TRANSFER, StaffPage.ORG_TRANSFER, false);
	}

	public static void openTerritoryTexts(ServerPlayer player) {
		if (player == null) {
			return;
		}
		MinecraftServer server = player.getServer();
		Optional<OrganizationRecord> org = organization(player);
		boolean othersEditable = org.isEmpty() || player.getUUID().equals(org.get().owner());
		String others = org.isPresent()
				? PbsAdapter.territoryName(server, org.get().id())
				: PbsAdapter.territoryName(server, player.getUUID());
		String self = TerritoryEnterMessagePrefs.get(player.getUUID()).ownEnterMessage();
		StaffService.showRelations(player, StaffPage.RELATIONS_PIE, StaffPage.TERRITORY_MINE);
		RefugeeNetworking.openRelationsTexts(player, self, others, othersEditable);
	}

	public static void handleTexts(ServerPlayer player, boolean confirm, String selfText, String othersText) {
		if (player == null || StaffService.page(player) != StaffPage.TERRITORY_MINE) {
			return;
		}
		if (!confirm) {
			StaffService.resetToRoot(player);
			return;
		}
		try {
			TerritoryEnterMessagePrefs.Settings settings = TerritoryEnterMessagePrefs.withOwnMessage(player.getUUID(), selfText);
			TerritoryEnterMessagePrefs.syncToClient(player, settings);
			Optional<OrganizationRecord> org = organization(player);
			boolean othersEditable = org.isEmpty() || player.getUUID().equals(org.get().owner());
			if (othersEditable) {
				if (org.isPresent()) {
					OrganizationService.setOrganizationTerritoryName(player.getServer(), org.get().id(), othersText);
				} else {
					OrganizationService.setPlayerTerritoryName(player.getServer(), player.getUUID(), othersText);
				}
			}
			player.sendSystemMessage(Component.translatable("message.refugee.staff.relations.texts.saved"));
			succeed(player);
		} catch (OrganizationException exception) {
			failRaw(player, exception);
		}
	}

	public static void openRescue(ServerPlayer player) {
		if (player == null) {
			return;
		}
		if (RefugeeAttachments.get(player).isRosterEmpty() || RefugeeAttachments.get(player).isDefeated()) {
			fail(player, "message.refugee.staff.relations.rescue.no_villager");
			return;
		}
		if (findAltar(player).isEmpty()) {
			fail(player, "message.refugee.staff.relations.rescue.no_altar");
			return;
		}
		List<RelationsPlayerRow> rows = new ArrayList<>();
		for (ServerPlayer target : player.getServer().getPlayerList().getPlayers()) {
			if (target.getUUID().equals(player.getUUID()) || !isGhost(target)) {
				continue;
			}
			rows.add(new RelationsPlayerRow(target.getUUID(), target.getGameProfile().getName(), ""));
		}
		StaffService.showRelations(player, StaffPage.RELATIONS_PIE, StaffPage.ORG_RESCUE);
		RefugeeNetworking.openRelationsList(player, RelationsListKind.RESCUE, rows);
	}

	public static void handleName(ServerPlayer player, RelationsNameKind kind, boolean confirm, String name) {
		if (player == null || kind == null) {
			return;
		}
		StaffPage page = StaffService.page(player);
		if (kind == RelationsNameKind.CREATE_ORG && page != StaffPage.ORG_CREATE) {
			return;
		}
		if (kind == RelationsNameKind.RENAME_TERRITORY && page != StaffPage.ORG_RENAME) {
			return;
		}
		if (kind == RelationsNameKind.RENAME_PERSONAL && page != StaffPage.TERRITORY_MINE) {
			return;
		}
		if (!confirm) {
			StaffService.resetToRoot(player);
			return;
		}
		switch (kind) {
			case CREATE_ORG -> create(player, name);
			case RENAME_TERRITORY -> renameTerritory(player, name);
			case RENAME_PERSONAL -> renamePersonal(player, name);
		}
	}

	public static void handlePick(ServerPlayer player, RelationsListKind kind, boolean confirm, Collection<UUID> ids) {
		if (player == null || kind == null) {
			return;
		}
		if (kind == RelationsListKind.RELATIONS || kind == RelationsListKind.WAR
				|| kind == RelationsListKind.PEACE || kind == RelationsListKind.ALLY
				|| kind == RelationsListKind.PEACE_INBOX) {
			DiplomacyService.handle(player, kind, confirm, ids);
			return;
		}
		if (!confirm) {
			StaffService.resetToRoot(player);
			return;
		}
		if (ids == null || ids.isEmpty()) {
			fail(player, "message.refugee.staff.relations.none_selected");
			return;
		}
		switch (kind) {
			case INVITE -> invite(player, ids);
			case KICK -> actOnOne(player, ids, true);
			case TRANSFER -> actOnOne(player, ids, false);
			case RESCUE -> rescue(player, ids.iterator().next());
		}
	}

	public static void handleInviteReply(ServerPlayer player, boolean accept) {
		if (player == null || StaffService.page(player) != StaffPage.ORG_INVITES) {
			return;
		}
		try {
			if (accept) {
				OrganizationRecord record = OrganizationService.acceptInvite(player.getServer(), player);
				player.sendSystemMessage(Component.translatable("message.refugee.staff.relations.accepted", record.name()));
			} else {
				OrganizationRecord record = OrganizationService.denyInvite(player.getServer(), player);
				player.sendSystemMessage(Component.translatable("message.refugee.staff.relations.denied", record.name()));
			}
			succeed(player);
		} catch (OrganizationException exception) {
			failRaw(player, exception);
		}
	}

	private static void create(ServerPlayer player, String name) {
		try {
			OrganizationRecord record = OrganizationService.createOrganization(player.getServer(), player, name);
			player.sendSystemMessage(Component.translatable("message.refugee.staff.relations.created", record.name()));
			succeed(player);
		} catch (OrganizationException exception) {
			failRaw(player, exception);
		}
	}

	private static void renamePersonal(ServerPlayer player, String name) {
		try {
			String renamed = OrganizationService.setPlayerTerritoryName(player.getServer(), player.getUUID(), name);
			player.sendSystemMessage(Component.translatable("message.refugee.staff.relations.renamed_personal", renamed));
			if (organization(player).isPresent()) {
				player.sendSystemMessage(Component.translatable("message.refugee.staff.relations.renamed_personal.org"));
			}
			succeed(player);
		} catch (OrganizationException exception) {
			failRaw(player, exception);
		}
	}

	private static void renameTerritory(ServerPlayer player, String name) {
		OrganizationRecord record = ownedOrganization(player);
		if (record == null) {
			return;
		}
		try {
			String renamed = OrganizationService.setOrganizationTerritoryName(player.getServer(), record.id(), name);
			player.sendSystemMessage(Component.translatable("message.refugee.staff.relations.renamed", renamed));
			succeed(player);
		} catch (OrganizationException exception) {
			failRaw(player, exception);
		}
	}

	private static void invite(ServerPlayer player, Collection<UUID> ids) {
		OrganizationRecord owned = ownedOrganization(player);
		if (owned == null) {
			return;
		}
		MinecraftServer server = player.getServer();
		List<String> invited = new ArrayList<>();
		List<String> failed = new ArrayList<>();
		Set<UUID> unique = new LinkedHashSet<>(ids);
		for (UUID id : unique) {
			ServerPlayer target = server.getPlayerList().getPlayer(id);
			if (target == null) {
				failed.add(id.toString());
				continue;
			}
			try {
				OrganizationService.invitePlayer(server, player, target);
				invited.add(target.getGameProfile().getName());
				target.sendSystemMessage(Component.translatable(
						"message.refugee.staff.relations.invite_received",
						owned.name()
				));
			} catch (OrganizationException exception) {
				failed.add(target.getGameProfile().getName());
			}
		}
		if (invited.isEmpty()) {
			fail(player, "message.refugee.staff.relations.invite_none");
			return;
		}
		player.sendSystemMessage(Component.translatable(
				"message.refugee.staff.relations.invited",
				String.join("、", invited),
				invited.size()
		));
		if (!failed.isEmpty()) {
			player.sendSystemMessage(Component.translatable(
					"message.refugee.staff.relations.invite_failed",
					String.join("、", failed)
			));
		}
		succeed(player);
	}

	private static void actOnOne(ServerPlayer player, Collection<UUID> ids, boolean kick) {
		OrganizationRecord owned = ownedOrganization(player);
		if (owned == null) {
			return;
		}
		UUID id = ids.iterator().next();
		ServerPlayer target = player.getServer().getPlayerList().getPlayer(id);
		if (target == null) {
			fail(player, "message.refugee.staff.relations.player_offline");
			return;
		}
		try {
			if (kick) {
				OrganizationService.kickMember(player.getServer(), player, target);
				player.sendSystemMessage(Component.translatable(
						"message.refugee.staff.relations.kicked",
						target.getGameProfile().getName()
				));
				target.sendSystemMessage(Component.translatable("message.refugee.staff.relations.kicked_you"));
			} else {
				OrganizationService.transferOwnership(player.getServer(), player, target);
				player.sendSystemMessage(Component.translatable(
						"message.refugee.staff.relations.transferred",
						target.getGameProfile().getName()
				));
				target.sendSystemMessage(Component.translatable("message.refugee.staff.relations.transferred_you"));
			}
			succeed(player);
		} catch (OrganizationException exception) {
			failRaw(player, exception);
		}
	}

	private static void rescue(ServerPlayer player, UUID targetId) {
		if (targetId == null || targetId.equals(player.getUUID())) {
			fail(player, "message.refugee.staff.relations.rescue.invalid");
			return;
		}
		ServerPlayer target = player.getServer().getPlayerList().getPlayer(targetId);
		if (target == null || !isGhost(target)) {
			fail(player, "message.refugee.staff.relations.rescue.not_ghost");
			return;
		}
		Optional<AltarSpot> altar = findAltar(player);
		if (altar.isEmpty()) {
			fail(player, "message.refugee.staff.relations.rescue.no_altar");
			return;
		}
		int remaining = RosterService.sacrificeOne(player);
		if (remaining < 0) {
			fail(player, "message.refugee.staff.relations.rescue.no_villager");
			return;
		}
		reviveAtAltar(target, altar.get());
		player.sendSystemMessage(Component.translatable(
				"message.refugee.staff.relations.rescue.done",
				target.getGameProfile().getName(),
				remaining
		));
		target.sendSystemMessage(Component.translatable(
				"message.refugee.staff.relations.rescue.received",
				player.getGameProfile().getName()
		));
		target.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
		target.connection.send(new ClientboundSetTitleTextPacket(
				Component.translatable("title.refugee.rescued")
		));
		target.connection.send(new ClientboundSetSubtitleTextPacket(
				Component.translatable("subtitle.refugee.rescued", player.getGameProfile().getName())
		));
		succeed(player);
	}

	private static void reviveAtAltar(ServerPlayer target, AltarSpot altar) {
		PlayerSelectionData data = RefugeeAttachments.get(target);
		data.setDefeated(false);
		RefugeeAttachments.markDirty(target, data);
		BlockPos stand = StandableFinder.findStandable(altar.level(), altar.pos().above(), Set.of(), 1)
				.stream()
				.findFirst()
				.orElse(altar.pos().above());
		target.setGameMode(GameType.SURVIVAL);
		if (target.getHealth() <= 0.0F) {
			target.setHealth(target.getMaxHealth());
		}
		target.teleportTo(
				altar.level(),
				stand.getX() + 0.5,
				stand.getY(),
				stand.getZ() + 0.5,
				Set.of(),
				target.getYRot(),
				target.getXRot(),
				true
		);
	}

	private static boolean isGhost(ServerPlayer player) {
		return player != null && RefugeeAttachments.get(player).isDefeated();
	}

	private static Optional<AltarSpot> findAltar(ServerPlayer player) {
		MinecraftServer server = player.getServer();
		UUID subject = PbsAdapter.resolveSubject(player);
		Optional<AltarSpot> found = firstLivingAltar(server, SpecialStoryService.altars(server, subject), player);
		if (found.isPresent() || subject.equals(player.getUUID())) {
			return found;
		}
		return firstLivingAltar(server, SpecialStoryService.altars(server, player.getUUID()), player);
	}

	private static Optional<AltarSpot> firstLivingAltar(
			MinecraftServer server,
			List<SpecialStoryData.AltarRecord> records,
			ServerPlayer preferNear
	) {
		AltarSpot fallback = null;
		ResourceLocation here = preferNear.level().dimension().location();
		for (SpecialStoryData.AltarRecord record : records) {
			ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, record.dimension());
			ServerLevel level = server.getLevel(key);
			if (level == null) {
				continue;
			}
			level.getChunk(record.pos());
			if (!(level.getBlockState(record.pos()).getBlock() instanceof AltarBlock)) {
				continue;
			}
			AltarSpot spot = new AltarSpot(level, record.pos());
			if (here.equals(record.dimension())) {
				return Optional.of(spot);
			}
			if (fallback == null) {
				fallback = spot;
			}
		}
		return Optional.ofNullable(fallback);
	}

	private static void openMemberList(ServerPlayer player, RelationsListKind kind, StaffPage page, boolean includeSelf) {
		OrganizationRecord record = ownedOrganization(player);
		if (record == null) {
			return;
		}
		List<RelationsPlayerRow> rows = new ArrayList<>();
		MinecraftServer server = player.getServer();
		for (UUID memberId : record.members()) {
			if (!includeSelf && memberId.equals(player.getUUID())) {
				continue;
			}
			ServerPlayer online = server.getPlayerList().getPlayer(memberId);
			if (online == null) {
				continue;
			}
			rows.add(new RelationsPlayerRow(memberId, online.getGameProfile().getName(), ""));
		}
		StaffService.showRelations(player, StaffPage.ORG_MANAGE_PIE, page);
		RefugeeNetworking.openRelationsList(player, kind, rows);
	}

	private static Optional<OrganizationRecord> organization(ServerPlayer player) {
		MinecraftServer server = player.getServer();
		return OrganizationData.get(server)
				.getPlayerOrganization(player.getUUID())
				.flatMap(orgId -> OrganizationData.get(server).getOrganization(orgId));
	}

	private static OrganizationRecord requireMember(ServerPlayer player) {
		if (player == null) {
			return null;
		}
		Optional<OrganizationRecord> record = organization(player);
		if (record.isEmpty()) {
			fail(player, "message.refugee.staff.relations.not_in_org");
			return null;
		}
		return record.get();
	}

	private static OrganizationRecord ownedOrganization(ServerPlayer player) {
		OrganizationRecord record = requireMember(player);
		if (record == null) {
			return null;
		}
		if (!player.getUUID().equals(record.owner())) {
			fail(player, "message.refugee.staff.relations.owner_only");
			return null;
		}
		return record;
	}

	private static void succeed(ServerPlayer player) {
		player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
		StaffService.resetToRoot(player);
	}

	private static void fail(ServerPlayer player, String key) {
		player.displayClientMessage(Component.translatable(key), true);
		player.playNotifySound(SoundEvents.GENERIC_BURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		StaffService.resetToRoot(player);
	}

	private static void failRaw(ServerPlayer player, OrganizationException exception) {
		String message = exception.getMessage() == null ? "" : exception.getMessage();
		String key = switch (message) {
			case String text when text.startsWith("Already in an organization") ->
					"message.refugee.staff.relations.already_in_org";
			case String text when text.startsWith("Organization already exists") ->
					"message.refugee.staff.relations.duplicate_name";
			case String text when text.contains("cannot be empty") ->
					"message.refugee.staff.relations.empty_name";
			case String text when text.contains("too long") ->
					"message.refugee.staff.relations.name_too_long";
			case String text when text.contains("control characters") ->
					"message.refugee.staff.relations.bad_name";
			case String text when text.startsWith("Owner must transfer") ->
					"message.refugee.staff.relations.leave_owner";
			case String text when text.startsWith("You do not have a pending") ->
					"message.refugee.staff.relations.no_invite";
			case String text when text.startsWith("Only the organization owner") ->
					"message.refugee.staff.relations.owner_only";
			default -> null;
		};
		if (key == null) {
			player.displayClientMessage(Component.literal(message), true);
		} else {
			player.displayClientMessage(Component.translatable(key), true);
		}
		player.playNotifySound(SoundEvents.GENERIC_BURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		StaffService.resetToRoot(player);
	}

	private record AltarSpot(ServerLevel level, BlockPos pos) {
	}
}
