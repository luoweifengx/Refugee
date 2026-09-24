package luowei.refugee.special;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 按领土主体存的特殊 NPC 剧情进度、外出标记与祭坛登记。
 */
public final class SpecialStoryData extends SavedData {
	private static final String DATA_ID = "refugee_special_story";

	public static final Codec<SpecialStoryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, SubjectStory.CODEC).optionalFieldOf("subjects", Map.of())
					.forGetter(data -> Map.copyOf(data.subjects)),
			Codec.LONG.optionalFieldOf("last_leave_day", -1L).forGetter(data -> data.lastLeaveDay)
	).apply(instance, SpecialStoryData::fromCodec));

	public static final SavedDataType<SpecialStoryData> TYPE = new SavedDataType<>(
			DATA_ID,
			context -> new SpecialStoryData(),
			context -> SpecialStoryData.CODEC,
			null
	);

	private final Map<UUID, SubjectStory> subjects = new LinkedHashMap<>();
	private long lastLeaveDay = -1L;

	public SpecialStoryData() {
	}

	private static SpecialStoryData fromCodec(Map<UUID, SubjectStory> subjects, long lastLeaveDay) {
		SpecialStoryData data = new SpecialStoryData();
		if (subjects != null) {
			data.subjects.putAll(subjects);
		}
		data.lastLeaveDay = lastLeaveDay;
		return data;
	}

	public static SpecialStoryData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public SubjectStory of(UUID subjectId) {
		if (subjectId == null) {
			return new SubjectStory();
		}
		return subjects.computeIfAbsent(subjectId, ignored -> {
			setDirty();
			return new SubjectStory();
		});
	}

	public Map<UUID, SubjectStory> subjects() {
		return subjects;
	}

	public long lastLeaveDay() {
		return lastLeaveDay;
	}

	public void setLastLeaveDay(long lastLeaveDay) {
		this.lastLeaveDay = lastLeaveDay;
		setDirty();
	}

	public static final class SubjectStory {
		public static final Codec<SubjectStory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				GuideFlags.CODEC.optionalFieldOf("guide", GuideFlags.EMPTY).forGetter(GuideFlags::from),
				NurseFlags.CODEC.optionalFieldOf("nurse", NurseFlags.EMPTY).forGetter(NurseFlags::from),
				CartoFlags.CODEC.optionalFieldOf("cartographer", CartoFlags.EMPTY).forGetter(CartoFlags::from),
				EndFlags.CODEC.optionalFieldOf("end", EndFlags.EMPTY).forGetter(EndFlags::from),
				InterruptFlags.CODEC.optionalFieldOf("interrupt", InterruptFlags.EMPTY).forGetter(InterruptFlags::from),
				Codec.STRING.listOf().optionalFieldOf("pending", List.of()).forGetter(s -> List.copyOf(s.pending)),
				AltarRecord.CODEC.listOf().optionalFieldOf("altars", List.of()).forGetter(s -> List.copyOf(s.altars))
		).apply(instance, SubjectStory::fromCodec));

		boolean woodTalked;
		boolean copperTalked;
		boolean ironTalked;
		boolean nurseInjuryTalked;
		boolean nurseDeathTalked;
		boolean pendingNurseHeal;
		boolean pendingNurseDeath;
		boolean cartographerAppeared;
		boolean cartographerAway;
		boolean cartographerFirstVisit;
		boolean cartographerFirstReturn;
		boolean cartographerAncientMap;
		long cartographerSpawnDay = -1L;
		boolean enchanterArrivalTalked;
		boolean enchanterBookTalked;
		boolean pendingDivine;
		boolean dragonVanillaDone;
		boolean dragonGloryDone;
		boolean banished;
		boolean finaleDone;
		int vanillaDragonMask;
		final List<String> pending = new ArrayList<>();
		final List<AltarRecord> altars = new ArrayList<>();
		final Map<String, Integer> interruptCounts = new LinkedHashMap<>();
		final Map<String, Integer> resumeSteps = new LinkedHashMap<>();
		final List<String> nurseSkip = new ArrayList<>();
		boolean nurseApology;
		boolean cartoPaused;
		boolean cartoLine;
		long enchanterSilentUntil;
		boolean enchanterEllipsis;
		long guideSilentUntil;

		public SubjectStory() {
		}

		private static SubjectStory fromCodec(
				GuideFlags guide,
				NurseFlags nurse,
				CartoFlags carto,
				EndFlags end,
				InterruptFlags interrupt,
				List<String> pending,
				List<AltarRecord> altars
		) {
			SubjectStory story = new SubjectStory();
			if (guide != null) {
				guide.apply(story);
			}
			if (nurse != null) {
				nurse.apply(story);
			}
			if (carto != null) {
				carto.apply(story);
			}
			if (end != null) {
				end.apply(story);
			}
			if (interrupt != null) {
				interrupt.apply(story);
			}
			if (pending != null) {
				story.pending.addAll(pending);
			}
			if (altars != null) {
				story.altars.addAll(altars);
			}
			return story;
		}

		public boolean isTalked(SpecialStoryKind kind) {
			if (kind == null) {
				return false;
			}
			return switch (kind) {
				case GUIDE_WOOD -> woodTalked;
				case GUIDE_COPPER -> copperTalked;
				case GUIDE_IRON -> ironTalked;
				case NURSE_INJURY -> nurseInjuryTalked;
				case NURSE_DEATH -> nurseDeathTalked;
				case NURSE_HEAL -> false;
				case CARTO_FIRST -> cartographerFirstVisit;
				case CARTO_RETURN -> cartographerFirstReturn;
				case CARTO_ANCIENT -> cartographerAncientMap;
				case ENCHANTER_ARRIVAL -> enchanterArrivalTalked;
				case ENCHANTER_BOOK -> enchanterBookTalked;
				case DRAGON_VANILLA -> dragonVanillaDone;
				case DRAGON_GLORY -> dragonGloryDone;
			};
		}

		public void markTalked(SpecialStoryKind kind) {
			if (kind == null) {
				return;
			}
			switch (kind) {
				case GUIDE_WOOD -> woodTalked = true;
				case GUIDE_COPPER -> copperTalked = true;
				case GUIDE_IRON -> ironTalked = true;
				case NURSE_INJURY -> nurseInjuryTalked = true;
				case NURSE_DEATH -> nurseDeathTalked = true;
				case NURSE_HEAL -> {
				}
				case CARTO_FIRST -> cartographerFirstVisit = true;
				case CARTO_RETURN -> cartographerFirstReturn = true;
				case CARTO_ANCIENT -> cartographerAncientMap = true;
				case ENCHANTER_ARRIVAL -> enchanterArrivalTalked = true;
				case ENCHANTER_BOOK -> enchanterBookTalked = true;
				case DRAGON_VANILLA -> dragonVanillaDone = true;
				case DRAGON_GLORY -> dragonGloryDone = true;
			}
		}

		public void queue(SpecialStoryKind kind) {
			if (kind == null || skipProactive(kind)) {
				return;
			}
			String id = kind.id();
			if (!pending.contains(id)) {
				pending.add(id);
			}
		}

		public void dequeue(SpecialStoryKind kind) {
			if (kind != null) {
				pending.remove(kind.id());
			}
		}

		public SpecialStoryKind nextPending() {
			for (String id : pending) {
				SpecialStoryKind kind = SpecialStoryKind.byId(id);
				if (kind == null || skipProactive(kind)) {
					continue;
				}
				if (kind.role() == RefugeeSpecialRole.CARTOGRAPHER && cartoPaused) {
					continue;
				}
				return kind;
			}
			return null;
		}

		public boolean hasPending(SpecialStoryKind kind) {
			return kind != null && pending.contains(kind.id());
		}

		public int interruptCount(SpecialStoryKind kind) {
			if (kind == null) {
				return 0;
			}
			return interruptCounts.getOrDefault(kind.id(), 0);
		}

		public int bumpInterrupt(SpecialStoryKind kind) {
			if (kind == null) {
				return 0;
			}
			int next = interruptCount(kind) + 1;
			interruptCounts.put(kind.id(), next);
			return next;
		}

		public int resumeStep(SpecialStoryKind kind) {
			if (kind == null) {
				return 0;
			}
			return Math.max(0, resumeSteps.getOrDefault(kind.id(), 0));
		}

		public void setResumeStep(SpecialStoryKind kind, int step) {
			if (kind == null) {
				return;
			}
			resumeSteps.put(kind.id(), Math.max(0, step));
		}

		public void clearInterrupt(SpecialStoryKind kind) {
			if (kind == null) {
				return;
			}
			interruptCounts.remove(kind.id());
			resumeSteps.remove(kind.id());
			nurseSkip.remove(kind.id());
		}

		public boolean skipProactive(SpecialStoryKind kind) {
			return kind != null && nurseSkip.contains(kind.id());
		}

		public void skipNurseProactive(SpecialStoryKind kind) {
			if (kind != null && !nurseSkip.contains(kind.id())) {
				nurseSkip.add(kind.id());
			}
		}

		private record GuideFlags(
				boolean wood,
				boolean copper,
				boolean iron,
				boolean enchArrival,
				boolean enchBook,
				boolean pendingDivine
		) {
			static final GuideFlags EMPTY = new GuideFlags(false, false, false, false, false, false);
			static final Codec<GuideFlags> CODEC = RecordCodecBuilder.create(instance -> instance.group(
					Codec.BOOL.optionalFieldOf("wood", false).forGetter(GuideFlags::wood),
					Codec.BOOL.optionalFieldOf("copper", false).forGetter(GuideFlags::copper),
					Codec.BOOL.optionalFieldOf("iron", false).forGetter(GuideFlags::iron),
					Codec.BOOL.optionalFieldOf("ench_arrival", false).forGetter(GuideFlags::enchArrival),
					Codec.BOOL.optionalFieldOf("ench_book", false).forGetter(GuideFlags::enchBook),
					Codec.BOOL.optionalFieldOf("pending_divine", false).forGetter(GuideFlags::pendingDivine)
			).apply(instance, GuideFlags::new));

			static GuideFlags from(SubjectStory story) {
				return new GuideFlags(
						story.woodTalked,
						story.copperTalked,
						story.ironTalked,
						story.enchanterArrivalTalked,
						story.enchanterBookTalked,
						story.pendingDivine
				);
			}

			void apply(SubjectStory story) {
				story.woodTalked = wood;
				story.copperTalked = copper;
				story.ironTalked = iron;
				story.enchanterArrivalTalked = enchArrival;
				story.enchanterBookTalked = enchBook;
				story.pendingDivine = pendingDivine;
			}
		}

		private record NurseFlags(
				boolean injury,
				boolean death,
				boolean healSeek,
				boolean deathSeek
		) {
			static final NurseFlags EMPTY = new NurseFlags(false, false, false, false);
			static final Codec<NurseFlags> CODEC = RecordCodecBuilder.create(instance -> instance.group(
					Codec.BOOL.optionalFieldOf("injury", false).forGetter(NurseFlags::injury),
					Codec.BOOL.optionalFieldOf("death", false).forGetter(NurseFlags::death),
					Codec.BOOL.optionalFieldOf("heal_seek", false).forGetter(NurseFlags::healSeek),
					Codec.BOOL.optionalFieldOf("death_seek", false).forGetter(NurseFlags::deathSeek)
			).apply(instance, NurseFlags::new));

			static NurseFlags from(SubjectStory story) {
				return new NurseFlags(
						story.nurseInjuryTalked,
						story.nurseDeathTalked,
						story.pendingNurseHeal,
						story.pendingNurseDeath
				);
			}

			void apply(SubjectStory story) {
				story.nurseInjuryTalked = injury;
				story.nurseDeathTalked = death;
				story.pendingNurseHeal = healSeek;
				story.pendingNurseDeath = deathSeek;
			}
		}

		private record CartoFlags(
				boolean appeared,
				boolean away,
				boolean first,
				boolean firstReturn,
				boolean ancient,
				long spawnDay
		) {
			static final CartoFlags EMPTY = new CartoFlags(false, false, false, false, false, -1L);
			static final Codec<CartoFlags> CODEC = RecordCodecBuilder.create(instance -> instance.group(
					Codec.BOOL.optionalFieldOf("appeared", false).forGetter(CartoFlags::appeared),
					Codec.BOOL.optionalFieldOf("away", false).forGetter(CartoFlags::away),
					Codec.BOOL.optionalFieldOf("first", false).forGetter(CartoFlags::first),
					Codec.BOOL.optionalFieldOf("return", false).forGetter(CartoFlags::firstReturn),
					Codec.BOOL.optionalFieldOf("ancient", false).forGetter(CartoFlags::ancient),
					Codec.LONG.optionalFieldOf("spawn_day", -1L).forGetter(CartoFlags::spawnDay)
			).apply(instance, CartoFlags::new));

			static CartoFlags from(SubjectStory story) {
				return new CartoFlags(
						story.cartographerAppeared,
						story.cartographerAway,
						story.cartographerFirstVisit,
						story.cartographerFirstReturn,
						story.cartographerAncientMap,
						story.cartographerSpawnDay
				);
			}

			void apply(SubjectStory story) {
				story.cartographerAppeared = appeared;
				story.cartographerAway = away;
				story.cartographerFirstVisit = first;
				story.cartographerFirstReturn = firstReturn;
				story.cartographerAncientMap = ancient;
				story.cartographerSpawnDay = spawnDay;
			}
		}

		private record InterruptFlags(
				Map<String, Integer> counts,
				Map<String, Integer> resume,
				List<String> nurseSkip,
				boolean nurseApology,
				boolean cartoPaused,
				boolean cartoLine,
				long enchanterSilentUntil,
				boolean enchanterEllipsis,
				long guideSilentUntil
		) {
			static final InterruptFlags EMPTY = new InterruptFlags(
					Map.of(), Map.of(), List.of(), false, false, false, 0L, false, 0L
			);
			static final Codec<InterruptFlags> CODEC = RecordCodecBuilder.create(instance -> instance.group(
					Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counts", Map.of())
							.forGetter(InterruptFlags::counts),
					Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("resume", Map.of())
							.forGetter(InterruptFlags::resume),
					Codec.STRING.listOf().optionalFieldOf("nurse_skip", List.of())
							.forGetter(InterruptFlags::nurseSkip),
					Codec.BOOL.optionalFieldOf("nurse_apology", false).forGetter(InterruptFlags::nurseApology),
					Codec.BOOL.optionalFieldOf("carto_paused", false).forGetter(InterruptFlags::cartoPaused),
					Codec.BOOL.optionalFieldOf("carto_line", false).forGetter(InterruptFlags::cartoLine),
					Codec.LONG.optionalFieldOf("ench_silent", 0L).forGetter(InterruptFlags::enchanterSilentUntil),
					Codec.BOOL.optionalFieldOf("ench_ellipsis", false).forGetter(InterruptFlags::enchanterEllipsis),
					Codec.LONG.optionalFieldOf("guide_silent", 0L).forGetter(InterruptFlags::guideSilentUntil)
			).apply(instance, InterruptFlags::new));

			static InterruptFlags from(SubjectStory story) {
				return new InterruptFlags(
						Map.copyOf(story.interruptCounts),
						Map.copyOf(story.resumeSteps),
						List.copyOf(story.nurseSkip),
						story.nurseApology,
						story.cartoPaused,
						story.cartoLine,
						story.enchanterSilentUntil,
						story.enchanterEllipsis,
						story.guideSilentUntil
				);
			}

			void apply(SubjectStory story) {
				story.interruptCounts.clear();
				if (counts != null) {
					story.interruptCounts.putAll(counts);
				}
				story.resumeSteps.clear();
				if (resume != null) {
					story.resumeSteps.putAll(resume);
				}
				story.nurseSkip.clear();
				if (nurseSkip != null) {
					story.nurseSkip.addAll(nurseSkip);
				}
				story.nurseApology = nurseApology;
				story.cartoPaused = cartoPaused;
				story.cartoLine = cartoLine;
				story.enchanterSilentUntil = enchanterSilentUntil;
				story.enchanterEllipsis = enchanterEllipsis;
				story.guideSilentUntil = guideSilentUntil;
			}
		}

		private record EndFlags(
				boolean vanilla,
				boolean glory,
				boolean banished,
				boolean finale,
				int vanillaMask
		) {
			static final EndFlags EMPTY = new EndFlags(false, false, false, false, 0);
			static final Codec<EndFlags> CODEC = RecordCodecBuilder.create(instance -> instance.group(
					Codec.BOOL.optionalFieldOf("vanilla", false).forGetter(EndFlags::vanilla),
					Codec.BOOL.optionalFieldOf("glory", false).forGetter(EndFlags::glory),
					Codec.BOOL.optionalFieldOf("banished", false).forGetter(EndFlags::banished),
					Codec.BOOL.optionalFieldOf("finale", false).forGetter(EndFlags::finale),
					Codec.INT.optionalFieldOf("vanilla_mask", 0).forGetter(EndFlags::vanillaMask)
			).apply(instance, EndFlags::new));

			static EndFlags from(SubjectStory story) {
				return new EndFlags(
						story.dragonVanillaDone,
						story.dragonGloryDone,
						story.banished,
						story.finaleDone,
						story.vanillaDragonMask
				);
			}

			void apply(SubjectStory story) {
				story.dragonVanillaDone = vanilla;
				story.dragonGloryDone = glory;
				story.banished = banished;
				story.finaleDone = finale;
				story.vanillaDragonMask = vanillaMask;
			}
		}
	}

	public record AltarRecord(ResourceLocation dimension, BlockPos pos, long placedAt) {
		public static final Codec<AltarRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ResourceLocation.CODEC.fieldOf("dimension").forGetter(AltarRecord::dimension),
				BlockPos.CODEC.fieldOf("pos").forGetter(AltarRecord::pos),
				Codec.LONG.optionalFieldOf("placed_at", 0L).forGetter(AltarRecord::placedAt)
		).apply(instance, AltarRecord::new));

		public ResourceKey<Level> dimensionKey() {
			return ResourceKey.create(Registries.DIMENSION, dimension);
		}
	}
}
