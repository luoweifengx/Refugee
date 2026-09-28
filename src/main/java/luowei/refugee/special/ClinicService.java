package luowei.refugee.special;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.phys.AABB;

import luowei.refugee.ai.RefugeeCombat;
import luowei.refugee.ai.WorkerSleep;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;
import luowei.refugee.interact.RefugeeRoles;
import luowei.refugee.logistics.OrgLogisticsData;
import luowei.refugee.warehouse.WarehouseService;

/**
 * 医师和铁匠每隔一会儿扫自己 32 格内的空闲工人和士兵。
 * 居民被点到之后才走过去，不是每个人自己找。
 */
public final class ClinicService {
	public static final double RANGE = 32.0;
	private static final int SCAN_INTERVAL = 40;
	private static final int ANVIL_RADIUS = 8;
	private static final int LOOK_TICKS = 10;
	private static final double ARRIVE = 2.5;
	private static final float DURABILITY = 0.25f;
	private static final float HEALTH = 0.5f;

	private static final ConcurrentHashMap<UUID, Order> ORDERS = new ConcurrentHashMap<>();

	private ClinicService() {
	}

	public enum Kind {
		HEAL,
		REPAIR
	}

	public static final class Order {
		public final UUID npcId;
		public final Kind kind;
		public int lookTicks;

		public Order(UUID npcId, Kind kind) {
			this.npcId = npcId;
			this.kind = kind;
		}
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ClinicService::onServerTick);
	}

	public static Order orderOf(Villager villager) {
		return villager == null ? null : ORDERS.get(villager.getUUID());
	}

	public static boolean hasOrder(Villager villager) {
		return orderOf(villager) != null;
	}

	public static void clear(Villager villager) {
		if (villager != null) {
			ORDERS.remove(villager.getUUID());
		}
	}

	public static void arrive(Villager patient, Villager npc) {
		if (patient == null || npc == null) {
			return;
		}
		patient.getLookControl().setLookAt(npc, 30.0f, patient.getMaxHeadXRot());
		npc.getLookControl().setLookAt(patient, 30.0f, npc.getMaxHeadXRot());
		npc.getNavigation().stop();
		RefugeeVillagerData data = RefugeeAttachments.get(npc);
		data.holdBrain(npc.level().getGameTime() + 40L);
		RefugeeAttachments.markDirty(npc, data);
	}

	/** 看够了就治疗或修好。成功或这次做不成都清掉委托，下一轮扫描再决定。 */
	public static void finish(Villager patient, Villager npc, Order order) {
		if (!(patient.level() instanceof ServerLevel level) || order == null) {
			clear(patient);
			return;
		}
		if (order.kind == Kind.HEAL) {
			heal(level, patient);
		} else if (hasAnvil(level, npc.blockPosition())) {
			repair(level, npc, patient);
		}
		clear(patient);
	}

	private static void onServerTick(MinecraftServer server) {
		long time = server.overworld() == null ? 0L : server.overworld().getGameTime();
		if (time % SCAN_INTERVAL != 0L) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Villager npc : level.getEntities(EntityType.VILLAGER, Entity::isAlive)) {
				RefugeeSpecialRole role = RefugeeSpecialRole.of(npc);
				if (role != RefugeeSpecialRole.NURSE && role != RefugeeSpecialRole.SMITH) {
					continue;
				}
				if (busy(npc.getUUID())) {
					continue;
				}
				if (role == RefugeeSpecialRole.SMITH && !hasAnvil(level, npc.blockPosition())) {
					continue;
				}
				Villager patient = nearest(level, npc, role);
				if (patient == null) {
					continue;
				}
				Kind kind = role == RefugeeSpecialRole.NURSE ? Kind.HEAL : Kind.REPAIR;
				ORDERS.putIfAbsent(patient.getUUID(), new Order(npc.getUUID(), kind));
			}
		}
	}

	private static boolean busy(UUID npcId) {
		for (Order order : ORDERS.values()) {
			if (npcId.equals(order.npcId)) {
				return true;
			}
		}
		return false;
	}

	private static Villager nearest(ServerLevel level, Villager npc, RefugeeSpecialRole role) {
		AABB box = npc.getBoundingBox().inflate(RANGE);
		Villager best = null;
		double bestDist = RANGE * RANGE;
		for (Villager candidate : level.getEntitiesOfClass(Villager.class, box, Villager::isAlive)) {
			if (candidate == npc || ORDERS.containsKey(candidate.getUUID())) {
				continue;
			}
			double dist = candidate.distanceToSqr(npc);
			if (dist > bestDist || !isIdle(candidate)) {
				continue;
			}
			if (role == RefugeeSpecialRole.NURSE) {
				if (!needsHeal(candidate) || !canPay(level, candidate)) {
					continue;
				}
			} else if (!needsRepair(candidate)) {
				continue;
			}
			bestDist = dist;
			best = candidate;
		}
		return best;
	}

	public static boolean isIdle(Villager villager) {
		if (villager == null || villager.isBaby() || villager.isSleeping() || !RefugeeAttachments.isRefugee(villager)) {
			return false;
		}
		if (RefugeeSpecialRole.isSpecial(villager) || RefugeeAttachments.get(villager).isHostileFaction()) {
			return false;
		}
		if (RefugeeCombat.isEating(villager)) {
			return false;
		}
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		if (data.isFollowing() || data.isFollowingEntity() || data.isPatrolling()) {
			return false;
		}
		if (RefugeeRoles.isBuilder(villager)) {
			return !WorkerSleep.yields(villager) && !WorkerSleep.isWorking(villager);
		}
		return RefugeeRoles.isGuard(villager) && data.combatMood() == RefugeeCombat.Mood.IDLE;
	}

	private static boolean needsHeal(Villager villager) {
		float max = villager.getMaxHealth();
		return max > 0.0f && villager.getHealth() / max < HEALTH;
	}

	private static boolean needsRepair(Villager villager) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!isGear(slot)) {
				continue;
			}
			if (wornDown(villager.getItemBySlot(slot))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isGear(EquipmentSlot slot) {
		return slot == EquipmentSlot.MAINHAND
				|| slot == EquipmentSlot.OFFHAND
				|| slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
	}

	private static boolean wornDown(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
			return false;
		}
		float left = (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage();
		return left < DURABILITY;
	}

	private static boolean canPay(ServerLevel level, Villager patient) {
		UUID subjectId = subjectOf(patient);
		if (subjectId == null || level.getServer() == null) {
			return false;
		}
		float missing = healTarget(patient) - patient.getHealth();
		int fee = fee(patient, missing);
		if (fee <= 0) {
			return false;
		}
		return WarehouseService.countIn(
				level.getServer(),
				OrgLogisticsData.get(level.getServer()).smelters(subjectId),
				Items.EMERALD
		) >= fee;
	}

	private static void heal(ServerLevel level, Villager patient) {
		if (!needsHeal(patient)) {
			return;
		}
		UUID subjectId = subjectOf(patient);
		MinecraftServer server = level.getServer();
		if (subjectId == null || server == null) {
			return;
		}
		float target = healTarget(patient);
		int fee = fee(patient, target - patient.getHealth());
		int paid = WarehouseService.consumeIn(
				server,
				subjectId,
				OrgLogisticsData.get(server).smelters(subjectId),
				Items.EMERALD,
				fee
		);
		if (paid < fee) {
			return;
		}
		patient.setHealth(target);
		level.playSound(null, patient.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 0.6f, 1.0f);
	}

	private static float healTarget(Villager villager) {
		float max = villager.getMaxHealth();
		return Math.min(max, max * HEALTH + 0.05f);
	}

	private static int fee(Villager villager, float restored) {
		if (restored <= 0.0f) {
			return 0;
		}
		int armor = Math.max(0, villager.getArmorValue());
		int cost = (int) Math.ceil(restored * Math.sqrt(armor + 1.0) / 20.0);
		return Math.max(1, cost);
	}

	private static void repair(ServerLevel level, Villager npc, Villager patient) {
		boolean repaired = false;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!isGear(slot)) {
				continue;
			}
			ItemStack stack = patient.getItemBySlot(slot);
			if (!wornDown(stack)) {
				continue;
			}
			stack.setDamageValue(0);
			repaired = true;
		}
		if (repaired) {
			level.playSound(null, npc.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.7f, 1.0f);
		}
	}

	private static UUID subjectOf(Villager villager) {
		return RefugeeAttachments.get(villager).subjectId();
	}

	private static boolean hasAnvil(ServerLevel level, BlockPos origin) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -ANVIL_RADIUS; dx <= ANVIL_RADIUS; dx++) {
			for (int dy = -4; dy <= 4; dy++) {
				for (int dz = -ANVIL_RADIUS; dz <= ANVIL_RADIUS; dz++) {
					cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					if (level.isLoaded(cursor) && level.getBlockState(cursor).getBlock() instanceof AnvilBlock) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
