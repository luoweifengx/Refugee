package luowei.refugee.staff;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import luowei.player_block_status.lib.org.OrganizationData;
import luowei.player_block_status.lib.org.OrganizationRecord;
import luowei.refugee.network.RefugeeNetworking;
import luowei.refugee.network.RelationsPlayerRow;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.staff.DiplomacyData.Bond;

/**
 * 与其他组织、未入组玩家的关系。默认中立。宣战会让对方同时变为敌对；
 * 回到中立需要双方都同意和解。结盟同样要对方同意。
 */
public final class DiplomacyService {
	public static final int STANCE_HOSTILE = 1;
	public static final int STANCE_ALLIED = 2;
	public static final int PENDING_PEACE = 1;
	public static final int PENDING_ALLY = 2;

	private DiplomacyService() {
	}

	public static void openList(ServerPlayer player) {
		open(player, RelationsListKind.RELATIONS, targets(player, false));
	}

	public static void openWar(ServerPlayer player) {
		open(player, RelationsListKind.WAR, targets(player, false));
	}

	public static void openPeace(ServerPlayer player) {
		open(player, RelationsListKind.PEACE, targets(player, true));
	}

	public static void openAlly(ServerPlayer player) {
		open(player, RelationsListKind.ALLY, targets(player, false));
	}

	public static void openInbox(ServerPlayer player) {
		open(player, RelationsListKind.PEACE_INBOX, inbox(player));
	}

	public static void handle(ServerPlayer player, RelationsListKind kind, boolean confirm, Collection<UUID> ids) {
		if (player == null || kind == null || StaffService.page(player) != StaffPage.DIPLOMACY) {
			return;
		}
		UUID self = PbsAdapter.resolveSubject(player);
		if (!confirm) {
			if (kind == RelationsListKind.PEACE_INBOX && ids != null && !ids.isEmpty()) {
				reject(player, self, ids.iterator().next());
				return;
			}
			StaffService.resetToRoot(player);
			return;
		}
		if (ids == null || ids.isEmpty() || self == null) {
			fail(player, "message.refugee.staff.relations.none_selected");
			return;
		}
		UUID other = ids.iterator().next();
		if (other.equals(self)) {
			fail(player, "message.refugee.diplomacy.self");
			return;
		}
		switch (kind) {
			case RELATIONS -> {
				player.sendSystemMessage(Component.translatable(
						"message.refugee.diplomacy.status",
						name(player.getServer(), other),
						Component.translatable(stanceKey(player.getServer(), self, other))
				));
				StaffService.resetToRoot(player);
			}
			case WAR -> declareWar(player, self, other);
			case PEACE -> requestPeace(player, self, other);
			case ALLY -> requestAlly(player, self, other);
			case PEACE_INBOX -> accept(player, self, other);
			default -> StaffService.resetToRoot(player);
		}
	}

	private static void open(ServerPlayer player, RelationsListKind kind, List<RelationsPlayerRow> rows) {
		StaffService.showRelations(player, StaffPage.RELATIONS_PIE, StaffPage.DIPLOMACY);
		RefugeeNetworking.openRelationsList(player, kind, rows);
	}

	private static void declareWar(ServerPlayer player, UUID self, UUID other) {
		DiplomacyData data = DiplomacyData.get(player.getServer());
		data.put(bond(self, other, STANCE_HOSTILE, 0, null));
		Component message = Component.translatable(
				"message.refugee.diplomacy.war",
				name(player.getServer(), self),
				name(player.getServer(), other)
		);
		player.sendSystemMessage(message);
		tell(player.getServer(), other, message);
		succeed(player);
	}

	private static void requestPeace(ServerPlayer player, UUID self, UUID other) {
		DiplomacyData data = DiplomacyData.get(player.getServer());
		Bond current = data.find(self, other);
		if (current == null || current.stance() != STANCE_HOSTILE) {
			fail(player, "message.refugee.diplomacy.not_hostile");
			return;
		}
		if (current.pending() == PENDING_PEACE && other.equals(current.pendingFrom())) {
			data.put(bond(self, other, 0, 0, null));
			Component message = Component.translatable(
					"message.refugee.diplomacy.peace",
					name(player.getServer(), self),
					name(player.getServer(), other)
			);
			player.sendSystemMessage(message);
			tell(player.getServer(), other, message);
			succeed(player);
			return;
		}
		data.put(bond(self, other, STANCE_HOSTILE, PENDING_PEACE, self));
		player.sendSystemMessage(Component.translatable("message.refugee.diplomacy.peace_sent", name(player.getServer(), other)));
		tell(player.getServer(), other, Component.translatable(
				"message.refugee.diplomacy.peace_incoming",
				name(player.getServer(), self)
		));
		succeed(player);
	}

	private static void requestAlly(ServerPlayer player, UUID self, UUID other) {
		DiplomacyData data = DiplomacyData.get(player.getServer());
		Bond current = data.find(self, other);
		int stance = current == null ? 0 : current.stance();
		if (stance == STANCE_HOSTILE) {
			fail(player, "message.refugee.diplomacy.hostile_blocks_ally");
			return;
		}
		if (stance == STANCE_ALLIED) {
			fail(player, "message.refugee.diplomacy.already_allied");
			return;
		}
		if (current != null && current.pending() == PENDING_ALLY && other.equals(current.pendingFrom())) {
			data.put(bond(self, other, STANCE_ALLIED, 0, null));
			Component message = Component.translatable(
					"message.refugee.diplomacy.allied",
					name(player.getServer(), self),
					name(player.getServer(), other)
			);
			player.sendSystemMessage(message);
			tell(player.getServer(), other, message);
			succeed(player);
			return;
		}
		data.put(bond(self, other, 0, PENDING_ALLY, self));
		player.sendSystemMessage(Component.translatable("message.refugee.diplomacy.ally_sent", name(player.getServer(), other)));
		tell(player.getServer(), other, Component.translatable(
				"message.refugee.diplomacy.ally_incoming",
				name(player.getServer(), self)
		));
		succeed(player);
	}

	private static void accept(ServerPlayer player, UUID self, UUID other) {
		DiplomacyData data = DiplomacyData.get(player.getServer());
		Bond current = data.find(self, other);
		if (current == null || current.pending() == 0 || !other.equals(current.pendingFrom())) {
			fail(player, "message.refugee.diplomacy.no_request");
			return;
		}
		if (current.pending() == PENDING_PEACE) {
			data.put(bond(self, other, 0, 0, null));
			Component message = Component.translatable(
					"message.refugee.diplomacy.peace",
					name(player.getServer(), self),
					name(player.getServer(), other)
			);
			player.sendSystemMessage(message);
			tell(player.getServer(), other, message);
		} else {
			data.put(bond(self, other, STANCE_ALLIED, 0, null));
			Component message = Component.translatable(
					"message.refugee.diplomacy.allied",
					name(player.getServer(), self),
					name(player.getServer(), other)
			);
			player.sendSystemMessage(message);
			tell(player.getServer(), other, message);
		}
		succeed(player);
	}

	private static void reject(ServerPlayer player, UUID self, UUID other) {
		DiplomacyData data = DiplomacyData.get(player.getServer());
		Bond current = data.find(self, other);
		if (current == null || current.pending() == 0 || !other.equals(current.pendingFrom())) {
			StaffService.resetToRoot(player);
			return;
		}
		data.put(bond(self, other, current.stance(), 0, null));
		player.sendSystemMessage(Component.translatable("message.refugee.diplomacy.rejected", name(player.getServer(), other)));
		tell(player.getServer(), other, Component.translatable(
				"message.refugee.diplomacy.rejected_them",
				name(player.getServer(), self)
		));
		succeed(player);
	}

	private static List<RelationsPlayerRow> targets(ServerPlayer player, boolean hostileOnly) {
		List<RelationsPlayerRow> rows = new ArrayList<>();
		MinecraftServer server = player.getServer();
		UUID self = PbsAdapter.resolveSubject(player);
		if (server == null || self == null) {
			return rows;
		}
		for (OrganizationRecord record : OrganizationData.get(server).getOrganizations().values()) {
			if (record.id().equals(self)) {
				continue;
			}
			addRow(rows, server, self, record.id(), hostileOnly);
		}
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other.getUUID().equals(player.getUUID())) {
				continue;
			}
			UUID subject = PbsAdapter.resolveSubject(other);
			if (subject == null || subject.equals(self) || !subject.equals(other.getUUID())) {
				continue;
			}
			addRow(rows, server, self, subject, hostileOnly);
		}
		return rows;
	}

	private static void addRow(List<RelationsPlayerRow> rows, MinecraftServer server, UUID self, UUID other, boolean hostileOnly) {
		String key = stanceKey(server, self, other);
		if (hostileOnly && !"message.refugee.diplomacy.stance.hostile".equals(key)) {
			return;
		}
		rows.add(new RelationsPlayerRow(other, name(server, other), Component.translatable(key).getString()));
	}

	private static List<RelationsPlayerRow> inbox(ServerPlayer player) {
		List<RelationsPlayerRow> rows = new ArrayList<>();
		MinecraftServer server = player.getServer();
		UUID self = PbsAdapter.resolveSubject(player);
		if (server == null || self == null) {
			return rows;
		}
		for (Bond bond : DiplomacyData.get(server).bonds()) {
			if (bond.pending() == 0 || bond.pendingFrom() == null || bond.pendingFrom().equals(self)
					|| bond.pendingFrom().equals(new UUID(0L, 0L))) {
				continue;
			}
			if (!bond.low().equals(self) && !bond.high().equals(self)) {
				continue;
			}
			UUID other = bond.low().equals(self) ? bond.high() : bond.low();
			String detail = bond.pending() == PENDING_ALLY
					? "message.refugee.diplomacy.pending.ally"
					: "message.refugee.diplomacy.pending.peace";
			rows.add(new RelationsPlayerRow(other, name(server, other), Component.translatable(detail).getString()));
		}
		return rows;
	}

	private static String stanceKey(MinecraftServer server, UUID self, UUID other) {
		Bond bond = DiplomacyData.get(server).find(self, other);
		if (bond == null || bond.stance() == 0) {
			return "message.refugee.diplomacy.stance.neutral";
		}
		if (bond.stance() == STANCE_ALLIED) {
			return "message.refugee.diplomacy.stance.allied";
		}
		return "message.refugee.diplomacy.stance.hostile";
	}

	private static Bond bond(UUID left, UUID right, int stance, int pending, UUID from) {
		return new Bond(
				DiplomacyData.low(left, right),
				DiplomacyData.high(left, right),
				stance,
				pending,
				from == null ? new UUID(0L, 0L) : from
		);
	}

	private static String name(MinecraftServer server, UUID subjectId) {
		return PbsAdapter.displayName(server, subjectId);
	}

	private static void tell(MinecraftServer server, UUID subjectId, Component message) {
		ServerPlayer direct = server.getPlayerList().getPlayer(subjectId);
		if (direct != null) {
			direct.sendSystemMessage(message);
			return;
		}
		for (UUID member : PbsAdapter.organizationMembers(server, subjectId)) {
			ServerPlayer player = server.getPlayerList().getPlayer(member);
			if (player != null) {
				player.sendSystemMessage(message);
			}
		}
	}

	private static void succeed(ServerPlayer player) {
		player.playNotifySound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
		StaffService.resetToRoot(player);
	}

	private static void fail(ServerPlayer player, String key) {
		player.displayClientMessage(Component.translatable(key), true);
		player.playNotifySound(net.minecraft.sounds.SoundEvents.GENERIC_BURN, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
		StaffService.resetToRoot(player);
	}
}
