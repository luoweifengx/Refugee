package luowei.refugee.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

import luowei.refugee.network.RefugeeNetworking;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.special.RefugeeSpecialRole;
import luowei.refugee.special.SpecialRefugeeService;

/**
 * 床头上的职业标记。没有标记时不写入。
 */
public final class BedMarks extends SavedData {
	private static final String DATA_ID = "refugee_bed_marks";

	private static final Codec<Mark> MARK_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("dimension").forGetter(Mark::dimension),
			Codec.INT.fieldOf("x").forGetter(Mark::x),
			Codec.INT.fieldOf("y").forGetter(Mark::y),
			Codec.INT.fieldOf("z").forGetter(Mark::z),
			Codec.STRING.fieldOf("role").forGetter(Mark::role),
			Codec.STRING.optionalFieldOf("subject", "").forGetter(Mark::subject)
	).apply(instance, Mark::new));

	public static final Codec<BedMarks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			MARK_CODEC.listOf().optionalFieldOf("marks", List.of()).forGetter(BedMarks::marks)
	).apply(instance, BedMarks::fromCodec));

	public static final SavedDataType<BedMarks> TYPE = new SavedDataType<>(
			DATA_ID,
			context -> new BedMarks(),
			context -> BedMarks.CODEC,
			null
	);

	private static final double ASSIGN_REACH_SQR = 64.0;
	private final Map<String, Stored> roles = new HashMap<>();

	public BedMarks() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerLevel level && state.getBlock() instanceof BedBlock) {
				clear(level, BedClaim.head(pos, state));
			}
		});
	}

	public static BedMarks get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** 放下床后打开标记页。名单只含这个组织里还在的特殊居民。 */
	public static void openChooser(ServerPlayer player, BlockPos pos) {
		if (player == null || pos == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos head = BedClaim.head(level, pos);
		if (!(level.getBlockState(head).getBlock() instanceof BedBlock)) {
			return;
		}
		RefugeeNetworking.openBedMark(player, head, SpecialRefugeeService.presentRoleIds(player));
	}

	/**
	 * 玩家选定标记。空字符串是无标记。
	 * 特殊居民每种人在同一组织里只留一张床，新标会清掉原来那张。
	 */
	public static void assign(ServerPlayer player, BlockPos pos, String role) {
		if (player == null || pos == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos head = BedClaim.head(level, pos);
		if (!(level.getBlockState(head).getBlock() instanceof BedBlock)) {
			return;
		}
		if (player.distanceToSqr(Vec3.atCenterOf(head)) > ASSIGN_REACH_SQR) {
			return;
		}
		String stored = role == null ? "" : role.trim();
		if (!stored.isEmpty() && !allowed(player, stored)) {
			return;
		}
		UUID subject = PbsAdapter.resolveSubject(player);
		if (RefugeeSpecialRole.byId(stored) != null && subject != null) {
			clearOtherSpecial(level.getServer(), subject, stored, level.dimension(), head);
		}
		set(level, head, stored, subject);
	}

	/** 没有标记时返回 null。 */
	public static String roleAt(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return null;
		}
		BlockPos head = BedClaim.head(level, pos);
		Stored stored = get(level.getServer()).roles.get(key(level.dimension(), head));
		return stored == null ? null : stored.role;
	}

	public static void set(ServerLevel level, BlockPos pos, String role) {
		set(level, pos, role, null);
	}

	private static void set(ServerLevel level, BlockPos pos, String role, UUID subject) {
		if (level == null || pos == null) {
			return;
		}
		BlockPos head = BedClaim.head(level, pos);
		BedMarks data = get(level.getServer());
		String stored = role == null ? "" : role.trim();
		String mapKey = key(level.dimension(), head);
		if (stored.isEmpty()) {
			if (data.roles.remove(mapKey) != null) {
				data.setDirty();
			}
		} else {
			Stored next = new Stored(stored, subject == null ? "" : subject.toString());
			if (!next.equals(data.roles.put(mapKey, next))) {
				data.setDirty();
			}
		}
		BedClaim.evict(level, head);
	}

	public static void clear(ServerLevel level, BlockPos head) {
		set(level, head, null);
	}

	private static boolean allowed(ServerPlayer player, String role) {
		if ("worker".equals(role) || "guard".equals(role) || "civilian".equals(role)) {
			return true;
		}
		RefugeeSpecialRole special = RefugeeSpecialRole.byId(role);
		return special != null && SpecialRefugeeService.presentRoleIds(player).contains(role);
	}

	private static void clearOtherSpecial(
			MinecraftServer server,
			UUID subject,
			String role,
			ResourceKey<Level> keepDimension,
			BlockPos keep
	) {
		BedMarks data = get(server);
		String subjectId = subject.toString();
		List<String> drop = new ArrayList<>();
		for (Map.Entry<String, Stored> entry : data.roles.entrySet()) {
			Stored stored = entry.getValue();
			if (stored == null || !role.equals(stored.role) || !subjectId.equals(stored.subject)) {
				continue;
			}
			if (entry.getKey().equals(key(keepDimension, keep))) {
				continue;
			}
			drop.add(entry.getKey());
		}
		for (String mapKey : drop) {
			String[] parts = mapKey.split("\\|");
			ResourceKey<Level> dimension = ResourceKey.create(
					net.minecraft.core.registries.Registries.DIMENSION,
					net.minecraft.resources.ResourceLocation.parse(parts[0])
			);
			BlockPos pos = new BlockPos(
					Integer.parseInt(parts[1]),
					Integer.parseInt(parts[2]),
					Integer.parseInt(parts[3])
			);
			ServerLevel world = server.getLevel(dimension);
			if (world != null) {
				clear(world, pos);
			} else if (data.roles.remove(mapKey) != null) {
				data.setDirty();
			}
		}
	}

	private static BedMarks fromCodec(List<Mark> marks) {
		BedMarks data = new BedMarks();
		if (marks != null) {
			for (Mark mark : marks) {
				if (mark == null || mark.role == null || mark.role.isBlank()) {
					continue;
				}
				data.roles.put(
						mark.dimension + "|" + mark.x + "|" + mark.y + "|" + mark.z,
						new Stored(mark.role, mark.subject == null ? "" : mark.subject)
				);
			}
		}
		return data;
	}

	private List<Mark> marks() {
		return roles.entrySet().stream().map(entry -> {
			String[] parts = entry.getKey().split("\\|");
			Stored stored = entry.getValue();
			return new Mark(
					parts[0],
					Integer.parseInt(parts[1]),
					Integer.parseInt(parts[2]),
					Integer.parseInt(parts[3]),
					stored.role,
					stored.subject == null ? "" : stored.subject
			);
		}).toList();
	}

	private static String key(ResourceKey<Level> dimension, BlockPos pos) {
		return dimension.location() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
	}

	private record Stored(String role, String subject) {
	}

	private record Mark(String dimension, int x, int y, int z, String role, String subject) {
	}
}
