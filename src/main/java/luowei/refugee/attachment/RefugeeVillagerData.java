package luowei.refugee.attachment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import luowei.refugee.ai.RefugeeCombat;
import luowei.refugee.special.RefugeeSpecialRole;

/**
 * 挂在村民实体上的难民数据：所属玩家/组织、跟随、守卫中心、建造进度。
 */
public final class RefugeeVillagerData {
	public static final Codec<RefugeeVillagerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.optionalFieldOf("subject").forGetter(data -> Optional.ofNullable(data.subjectId)),
			UUIDUtil.CODEC.optionalFieldOf("follow_player").forGetter(data -> Optional.ofNullable(data.followPlayerId)),
			Codec.BOOL.optionalFieldOf("following", false).forGetter(data -> data.following),
			BlockPos.CODEC.optionalFieldOf("guard_center").forGetter(data -> Optional.ofNullable(data.guardCenter)),
			BlockPos.CODEC.optionalFieldOf("container").forGetter(data -> Optional.ofNullable(data.containerPos)),
			BlockPos.CODEC.optionalFieldOf("build_origin").forGetter(data -> Optional.ofNullable(data.buildOrigin)),
			Codec.STRING.optionalFieldOf("structure_id", "").forGetter(data -> data.structureId == null ? "" : data.structureId.toString()),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("blueprint", ItemStack.EMPTY).forGetter(data -> ItemStack.EMPTY),
			Codec.INT.optionalFieldOf("build_index", 0).forGetter(data -> data.buildIndex),
			UUIDUtil.CODEC.optionalFieldOf("job_id").forGetter(data -> Optional.ofNullable(data.jobId)),
			Codec.STRING.optionalFieldOf("role", "").forGetter(data -> data.role == null ? "" : data.role),
			Codec.INT.optionalFieldOf("enchant_day", Integer.MIN_VALUE).forGetter(data -> data.enchantDay),
			ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("enchant_books", List.of()).forGetter(data -> List.copyOf(data.enchantBooks)),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("resource").forGetter(data -> {
				ItemStack stack = data.resourceItem;
				return stack == null || stack.isEmpty() ? Optional.empty() : Optional.of(stack);
			}),
			Codec.BOOL.optionalFieldOf("eating", false).forGetter(data -> data.eating),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("eat_stash").forGetter(data -> Optional.empty())
	).apply(instance, RefugeeVillagerData::fromCodec));

	private UUID subjectId;
	private UUID followPlayerId;
	private boolean following;
	private BlockPos guardCenter;
	private BlockPos containerPos;
	private BlockPos buildOrigin;
	private ResourceLocation structureId;
	private int buildIndex;
	private UUID jobId;
	private String role = "";
	private int enchantDay = Integer.MIN_VALUE;
	private final List<ItemStack> enchantBooks = new ArrayList<>();
	private long talkUntilGameTime;
	private UUID lookAtPlayerId;
	private long lookUntilGameTime;
	private long loveUntilGameTime;
	private long sweatUntilGameTime;
	private long fullUntilGameTime;
	private long angryUntilGameTime;
	private long selectUntilGameTime;
	private byte selectIconId;
	private boolean healthWasDamaged;
	private ItemStack resourceItem = ItemStack.EMPTY;
	private boolean eating;
	private int eatWatchCount;
	private FoodProperties eatWatchFood;
	private RefugeeCombat.Mood combatMood = RefugeeCombat.Mood.IDLE;
	private int eatCooldown;
	private int mainAttackCooldown;
	private int offAttackCooldown;

	public RefugeeVillagerData() {
	}

	private static RefugeeVillagerData fromCodec(
			Optional<UUID> subject,
			Optional<UUID> followPlayer,
			boolean following,
			Optional<BlockPos> guardCenter,
			Optional<BlockPos> container,
			Optional<BlockPos> buildOrigin,
			String structureId,
			ItemStack ignoredBlueprint,
			int buildIndex,
			Optional<UUID> jobId,
			String role,
			int enchantDay,
			List<ItemStack> enchantBooks,
			Optional<ItemStack> resource,
			boolean eating,
			Optional<ItemStack> eatStash
	) {
		RefugeeVillagerData data = new RefugeeVillagerData();
		data.subjectId = subject.orElse(null);
		data.followPlayerId = followPlayer.orElse(null);
		data.following = following;
		data.guardCenter = guardCenter.orElse(null);
		data.containerPos = container.orElse(null);
		data.buildOrigin = buildOrigin.orElse(null);
		data.structureId = structureId == null || structureId.isBlank() ? null : ResourceLocation.tryParse(structureId);
		data.buildIndex = Math.max(0, buildIndex);
		data.jobId = jobId.orElse(null);
		data.role = role == null ? "" : role;
		data.enchantDay = enchantDay;
		if (enchantBooks != null) {
			for (ItemStack book : enchantBooks) {
				if (book != null && !book.isEmpty()) {
					data.enchantBooks.add(book.copy());
				}
			}
		}
		if (resource != null && resource.isPresent() && !resource.get().isEmpty()) {
			data.resourceItem = resource.get().copy();
		}
		data.eating = eating;
		if (eating && eatStash != null && eatStash.isPresent() && !eatStash.get().isEmpty()) {
			data.resourceItem = eatStash.get().copy();
		}
		return data;
	}

	public UUID subjectId() {
		return subjectId;
	}

	public void setSubjectId(UUID subjectId) {
		this.subjectId = subjectId;
	}

	public UUID followPlayerId() {
		return followPlayerId;
	}

	public boolean isFollowing() {
		return following && followPlayerId != null;
	}

	public void startFollowing(UUID playerId) {
		this.followPlayerId = playerId;
		this.following = playerId != null;
		this.guardCenter = null;
	}

	/** 清跟随并清空守卫中心（工人取消跟随、派工作区、派建筑）。 */
	public void stopFollowing() {
		this.following = false;
		this.followPlayerId = null;
		this.guardCenter = null;
	}

	/** 清跟随并写入守卫中心（仅剑/弓/弩守卫）。 */
	public void stopFollowing(BlockPos newGuardCenter) {
		this.following = false;
		this.followPlayerId = null;
		this.guardCenter = newGuardCenter == null ? null : newGuardCenter.immutable();
	}

	public BlockPos guardCenter() {
		return guardCenter;
	}

	public void setGuardCenter(BlockPos guardCenter) {
		this.guardCenter = guardCenter == null ? null : guardCenter.immutable();
	}

	public BlockPos containerPos() {
		return containerPos;
	}

	public BlockPos buildOrigin() {
		return buildOrigin;
	}

	public ResourceLocation structureId() {
		return structureId;
	}

	public boolean isBuilding() {
		return jobId != null || (structureId != null && buildOrigin != null);
	}

	public UUID jobId() {
		return jobId;
	}

	public void assignJob(UUID jobId, ResourceLocation structureId, BlockPos origin) {
		this.jobId = jobId;
		this.structureId = structureId;
		this.containerPos = null;
		this.buildOrigin = origin == null ? null : origin.immutable();
		this.buildIndex = 0;
	}

	public void assignBuild(ResourceLocation structureId, BlockPos origin) {
		this.jobId = null;
		this.structureId = structureId;
		this.containerPos = null;
		this.buildOrigin = origin == null ? null : origin.immutable();
		this.buildIndex = 0;
	}

	public void clearBuild() {
		this.jobId = null;
		this.structureId = null;
		this.containerPos = null;
		this.buildOrigin = null;
		this.buildIndex = 0;
	}

	public int buildIndex() {
		return buildIndex;
	}

	public void setBuildIndex(int buildIndex) {
		this.buildIndex = Math.max(0, buildIndex);
	}

	public RefugeeSpecialRole specialRole() {
		return RefugeeSpecialRole.byId(role);
	}

	public void setSpecialRole(RefugeeSpecialRole specialRole) {
		this.role = specialRole == null ? "" : specialRole.id();
	}

	public boolean isSpecial() {
		return specialRole() != null;
	}

	public int enchantDay() {
		return enchantDay;
	}

	public void setEnchantDay(int enchantDay) {
		this.enchantDay = enchantDay;
	}

	public List<ItemStack> enchantBooks() {
		return enchantBooks;
	}

	public void setEnchantBooks(List<ItemStack> books) {
		enchantBooks.clear();
		if (books == null) {
			return;
		}
		for (ItemStack book : books) {
			if (book != null && !book.isEmpty()) {
				enchantBooks.add(book.copy());
			}
		}
	}

	public void startTalk(long untilGameTime) {
		this.talkUntilGameTime = untilGameTime;
	}

	public boolean isTalking(long gameTime) {
		return gameTime < talkUntilGameTime;
	}

	public void startLookAt(UUID playerId, long untilGameTime) {
		this.lookAtPlayerId = playerId;
		this.lookUntilGameTime = untilGameTime;
	}

	public UUID lookAtPlayerId() {
		return lookAtPlayerId;
	}

	public boolean isLookingAtPlayer(long gameTime) {
		return lookAtPlayerId != null && gameTime < lookUntilGameTime;
	}

	public void startLove(long untilGameTime) {
		this.loveUntilGameTime = untilGameTime;
		this.sweatUntilGameTime = 0L;
	}

	public boolean isLoving(long gameTime) {
		return gameTime < loveUntilGameTime;
	}

	public void startSweat(long untilGameTime) {
		this.sweatUntilGameTime = untilGameTime;
		this.loveUntilGameTime = 0L;
	}

	public boolean isSweating(long gameTime) {
		return gameTime < sweatUntilGameTime;
	}

	public void startFull(long untilGameTime) {
		this.fullUntilGameTime = untilGameTime;
	}

	public boolean isFull(long gameTime) {
		return gameTime < fullUntilGameTime;
	}

	public boolean healthWasDamaged() {
		return healthWasDamaged;
	}

	public void setHealthWasDamaged(boolean healthWasDamaged) {
		this.healthWasDamaged = healthWasDamaged;
	}

	public void startAngry(long untilGameTime) {
		this.angryUntilGameTime = untilGameTime;
	}

	public void clearAngry() {
		this.angryUntilGameTime = 0L;
	}

	public boolean isAngry(long gameTime) {
		return gameTime < angryUntilGameTime;
	}

	public void startSelect(byte iconId, long untilGameTime) {
		this.selectIconId = iconId;
		this.selectUntilGameTime = untilGameTime;
	}

	public boolean isSelecting(long gameTime) {
		return gameTime < selectUntilGameTime;
	}

	public byte selectIconId() {
		return selectIconId;
	}

	public ItemStack resourceItem() {
		return resourceItem == null ? ItemStack.EMPTY : resourceItem;
	}

	public void setResourceItem(ItemStack stack) {
		this.resourceItem = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
	}

	public boolean isEating() {
		return eating;
	}

	public void setEating(boolean eating) {
		this.eating = eating;
		if (!eating) {
			clearEatWatch();
		}
	}

	public int eatWatchCount() {
		return eatWatchCount;
	}

	public FoodProperties eatWatchFood() {
		return eatWatchFood;
	}

	public void syncEatWatch(ItemStack eatingStack) {
		if (!eating) {
			return;
		}
		if (eatingStack != null && !eatingStack.isEmpty() && eatingStack.has(DataComponents.FOOD)) {
			eatWatchCount = eatingStack.getCount();
			eatWatchFood = eatingStack.get(DataComponents.FOOD);
		} else {
			clearEatWatch();
		}
	}

	public void clearEatWatch() {
		eatWatchCount = 0;
		eatWatchFood = null;
	}

	public void clearEating() {
		this.eating = false;
		clearEatWatch();
	}

	public RefugeeCombat.Mood combatMood() {
		return combatMood == null ? RefugeeCombat.Mood.IDLE : combatMood;
	}

	public void setCombatMood(RefugeeCombat.Mood mood) {
		this.combatMood = mood == null ? RefugeeCombat.Mood.IDLE : mood;
	}

	public int eatCooldown() {
		return eatCooldown;
	}

	public void setEatCooldown(int eatCooldown) {
		this.eatCooldown = Math.max(0, eatCooldown);
	}

	public int mainAttackCooldown() {
		return mainAttackCooldown;
	}

	public void setMainAttackCooldown(int mainAttackCooldown) {
		this.mainAttackCooldown = Math.max(0, mainAttackCooldown);
	}

	public int offAttackCooldown() {
		return offAttackCooldown;
	}

	public void setOffAttackCooldown(int offAttackCooldown) {
		this.offAttackCooldown = Math.max(0, offAttackCooldown);
	}

	public void tickCombatCooldowns() {
		if (mainAttackCooldown > 0) {
			mainAttackCooldown--;
		}
		if (offAttackCooldown > 0) {
			offAttackCooldown--;
		}
	}
}
