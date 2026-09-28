package luowei.refugee.ai;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTarget;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromLookTarget;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.entity.ai.behavior.VillageBoundRandomStroll;
import net.minecraft.world.entity.ai.behavior.WakeUp;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;

import luowei.refugee.Refugee;
import luowei.refugee.interact.RefugeeRoles;

/**
 * 难民空闲时只跑走动、看向和睡觉。不认工作方块，不改职业，不捡东西。
 */
public final class RefugeeIdleBrain {
	private static final float SPEED = 0.5F;
	private static final Schedule SCHEDULE = new ScheduleBuilder(new Schedule())
			.changeActivityAt(10, Activity.IDLE)
			.changeActivityAt(WorkerSleep.REST_START, Activity.REST)
			.build();
	private static final Set<UUID> IDLE = ConcurrentHashMap.newKeySet();
	private static final ConcurrentHashMap<UUID, IdleTrace> TRACES = new ConcurrentHashMap<>();

	private RefugeeIdleBrain() {
	}

	/** 重新去干活时清掉空闲标记，下次再空闲才会打日志。 */
	public static void leave(Villager villager) {
		if (villager != null) {
			IDLE.remove(villager.getUUID());
		}
	}

	/**
	 * 工人这一拍刚进入空闲时记下手里的东西和正在跑的行为。
	 * 返回 true 表示要在换日程和本拍 Brain 之后再补两行。
	 */
	public static boolean beginIdle(Villager villager) {
		if (villager == null || !RefugeeRoles.isBuilder(villager) || !IDLE.add(villager.getUUID())) {
			return false;
		}
		TRACES.put(villager.getUUID(), IdleTrace.before(villager));
		return true;
	}

	public static void noteStopped(Villager villager, boolean replacedSchedule) {
		IdleTrace trace = villager == null ? null : TRACES.get(villager.getUUID());
		if (trace == null) {
			return;
		}
		trace.replacedSchedule = replacedSchedule;
		trace.afterStopMain = stack(villager.getMainHandItem());
		trace.afterStopOff = stack(villager.getOffhandItem());
	}

	public static void finishIdle(Villager villager) {
		if (villager == null) {
			return;
		}
		IdleTrace trace = TRACES.remove(villager.getUUID());
		if (trace == null) {
			return;
		}
		String afterTickMain = stack(villager.getMainHandItem());
		String afterTickOff = stack(villager.getOffhandItem());
		Refugee.LOGGER.info(
				"[refugee idle] 进入空闲 pos={} 职业={} 原因={} 更换日程={} 运行中行为={} 主手 前={} stopAll后={} tick后={} 副手 前={} stopAll后={} tick后={}",
				trace.pos,
				trace.profession,
				trace.reason,
				trace.replacedSchedule,
				trace.running,
				trace.beforeMain,
				trace.afterStopMain,
				afterTickMain,
				trace.beforeOff,
				trace.afterStopOff,
				afterTickOff
		);
	}

	/** @return 这一拍是否把原版日程换成了空闲日程 */
	public static boolean ensure(Villager villager) {
		Brain<Villager> brain = villager.getBrain();
		if (brain.getSchedule() == SCHEDULE) {
			return false;
		}
		if (!(villager.level() instanceof ServerLevel level)) {
			return false;
		}
		brain.stopAll(level, villager);
		brain.removeAllBehaviors();
		brain.setSchedule(SCHEDULE);
		brain.setCoreActivities(Set.of(Activity.CORE));
		brain.setDefaultActivity(Activity.IDLE);
		brain.addActivity(Activity.CORE, core());
		brain.addActivity(Activity.IDLE, idle());
		brain.addActivity(Activity.REST, rest());
		brain.eraseMemory(MemoryModuleType.JOB_SITE);
		brain.eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		brain.eraseMemory(MemoryModuleType.MEETING_POINT);
		brain.setActiveActivityIfPossible(Activity.IDLE);
		villager.setCanPickUpLoot(false);
		return true;
	}

	private static String stack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "empty";
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount();
	}

	private static String running(Villager villager) {
		StringBuilder names = new StringBuilder();
		for (BehaviorControl<?> behavior : villager.getBrain().getRunningBehaviors()) {
			if (names.length() > 0) {
				names.append(',');
			}
			names.append(behavior.getClass().getSimpleName());
		}
		return names.length() == 0 ? "none" : names.toString();
	}

	private static final class IdleTrace {
		private final String pos;
		private final String profession;
		private final String reason;
		private final String running;
		private final String beforeMain;
		private final String beforeOff;
		private boolean replacedSchedule;
		private String afterStopMain = "empty";
		private String afterStopOff = "empty";

		private IdleTrace(
				String pos,
				String profession,
				String reason,
				String running,
				String beforeMain,
				String beforeOff
		) {
			this.pos = pos;
			this.profession = profession;
			this.reason = reason;
			this.running = running;
			this.beforeMain = beforeMain;
			this.beforeOff = beforeOff;
		}

		private static IdleTrace before(Villager villager) {
			String profession = villager.getVillagerData().profession().unwrapKey()
					.map(key -> key.location().toString())
					.orElse("unknown");
			String reason = WorkerSleep.yields(villager) ? "去睡觉" : "没在干活";
			return new IdleTrace(
					villager.blockPosition().toShortString(),
					profession,
					reason,
					running(villager),
					stack(villager.getMainHandItem()),
					stack(villager.getOffhandItem())
			);
		}
	}

	private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> core() {
		return ImmutableList.of(
				Pair.of(0, new LookAtTargetSink(45, 90)),
				Pair.of(0, new MoveToTargetSink()),
				Pair.of(0, InteractWithDoor.create()),
				Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> idle() {
		return ImmutableList.of(
				Pair.of(1, homePoi()),
				Pair.of(2, SetEntityLookTarget.create(8.0F)),
				Pair.of(3, new RunOne<>(List.of(
						Pair.of(VillageBoundRandomStroll.create(SPEED), 2),
						Pair.of(SetWalkTargetFromLookTarget.create(SPEED, 2), 2),
						Pair.of(new DoNothing(30, 60), 1)
				))),
				Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	private static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> rest() {
		return ImmutableList.of(
				Pair.of(2, SetWalkTargetFromBlockMemory.create(MemoryModuleType.HOME, SPEED, 1, 150, 1200)),
				Pair.of(3, ValidateNearbyPoi.create(poi -> poi.is(PoiTypes.HOME), MemoryModuleType.HOME)),
				Pair.of(3, new SleepInBed()),
				Pair.of(5, WakeUp.create()),
				Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	private static BehaviorControl<PathfinderMob> homePoi() {
		return AcquirePoi.create(poi -> poi.is(PoiTypes.HOME), MemoryModuleType.HOME, false, Optional.empty());
	}
}
