package luowei.refugee.staff;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import luowei.refugee.zone.AreaBox;

/**
 * 玩家当前指挥杖会话（不落盘）：页面栈 + 框选/导入/巡逻临时状态。
 */
public final class StaffSession {
	private final List<StaffPage> stack = new ArrayList<>();
	private final List<BlockPos> patrolPoints = new ArrayList<>();
	private static final int IMPORT_VERTEX_FIRST = 0;
	private static final int IMPORT_VERTEX_SECOND = 1;
	private static final int IMPORT_VERTEX_BOTH = 2;
	private static final int IMPORT_VERTEX_NONE = 3;

	private BlockPos zoneCorner;
	private ResourceLocation zoneDimension;
	private BlockPos importCorner1;
	private BlockPos importCorner2;
	/** 未按 Tab 前为 -1。0 第一个，1 第二个，2 两个，3 零个。 */
	private int importVertexMode = -1;
	private AreaBox pendingImport;
	private Direction.Axis advanceAxis = Direction.Axis.Y;
	private boolean advancePositive = true;

	public StaffPage page() {
		return stack.isEmpty() ? StaffPage.ROOT : stack.getLast();
	}

	public StaffMode mode() {
		return page().worldMode();
	}

	public boolean isRoot() {
		return stack.isEmpty();
	}

	public void setMode(StaffMode mode) {
		StaffMode next = mode == null ? StaffMode.NONE : mode;
		switch (next) {
			case NONE -> resetToRoot();
			case WAREHOUSE -> setPages(StaffPage.WAREHOUSE_PIE, StaffPage.WAREHOUSE);
			case FOOD_WAREHOUSE -> setPages(StaffPage.WAREHOUSE_PIE, StaffPage.FOOD_WAREHOUSE);
			case FARM_WAREHOUSE -> setPages(StaffPage.WAREHOUSE_PIE, StaffPage.FARM_WAREHOUSE);
			case GEAR_WAREHOUSE -> setPages(StaffPage.WAREHOUSE_PIE, StaffPage.GEAR_WAREHOUSE);
			case SMELT_RESULT -> setPages(StaffPage.WAREHOUSE_PIE, StaffPage.SMELT_RESULT);
			case SMELTER -> setPages(StaffPage.WAREHOUSE_PIE, StaffPage.SMELTER);
			case ZONE -> setPages(StaffPage.ZONE_PIE, StaffPage.ZONE);
			case ADVANCE -> setPages(StaffPage.ZONE_PIE, StaffPage.ZONE_ADVANCE);
			case BUILD -> setPages(StaffPage.BUILD_PIE, StaffPage.BUILD_CATALOG, StaffPage.BUILD_PREVIEW);
			case IMPORT -> setPages(StaffPage.BUILD_PIE, StaffPage.IMPORT);
			case FOLLOW_ENTITY -> setPages(StaffPage.COMBAT_PIE, StaffPage.COMBAT_FOLLOW);
			case PATROL -> setPages(StaffPage.COMBAT_PIE, StaffPage.COMBAT_PATROL);
		}
	}

	public void setPages(StaffPage... pages) {
		stack.clear();
		if (pages != null) {
			for (StaffPage page : pages) {
				if (page != null && page != StaffPage.ROOT) {
					stack.add(page);
				}
			}
		}
		onPageChanged();
	}

	public void resetToRoot() {
		stack.clear();
		clearZoneCorner();
		clearImportBox();
		clearImportCorners();
		clearPatrolPoints();
		resetAdvanceTune();
	}

	private void onPageChanged() {
		StaffPage page = page();
		if (page != StaffPage.ZONE && page != StaffPage.ZONE_ADVANCE && page != StaffPage.IMPORT && page != StaffPage.IMPORT_NAME) {
			clearZoneCorner();
		}
		if (page != StaffPage.IMPORT && page != StaffPage.IMPORT_NAME) {
			clearImportBox();
			clearImportCorners();
		}
		if (page != StaffPage.COMBAT_PATROL) {
			clearPatrolPoints();
		}
		if (page != StaffPage.ZONE_ADVANCE) {
			resetAdvanceTune();
		}
	}

	public AreaBox pendingImport() {
		return pendingImport;
	}

	public void setPendingImport(AreaBox box) {
		this.pendingImport = box;
	}

	public void clearImportBox() {
		this.pendingImport = null;
	}

	public void beginImportCorner(BlockPos pos) {
		this.importCorner1 = pos == null ? null : pos.immutable();
		this.importCorner2 = null;
	}

	public void completeImportCorners(BlockPos first, BlockPos second) {
		this.importCorner1 = first == null ? null : first.immutable();
		this.importCorner2 = second == null ? null : second.immutable();
	}

	public int cycleImportVertexMode(boolean forward) {
		if (importVertexMode < 0) {
			importVertexMode = forward ? IMPORT_VERTEX_FIRST : IMPORT_VERTEX_NONE;
		} else {
			importVertexMode = forward
					? (importVertexMode + 1) % 4
					: (importVertexMode + 3) % 4;
		}
		return importVertexMode;
	}

	/** 写入结构时要丢掉的框选角方块。0 个顶点或未选档时为空。 */
	public List<BlockPos> omittedImportBlocks() {
		return switch (importVertexMode) {
			case IMPORT_VERTEX_FIRST -> copyCorner(importCorner1);
			case IMPORT_VERTEX_SECOND -> copyCorner(importCorner2);
			case IMPORT_VERTEX_BOTH -> {
				List<BlockPos> both = new ArrayList<>(2);
				if (importCorner1 != null) {
					both.add(importCorner1);
				}
				if (importCorner2 != null) {
					both.add(importCorner2);
				}
				yield List.copyOf(both);
			}
			default -> List.of();
		};
	}

	private static List<BlockPos> copyCorner(BlockPos corner) {
		return corner == null ? List.of() : List.of(corner);
	}

	public void clearImportCorners() {
		importCorner1 = null;
		importCorner2 = null;
		importVertexMode = -1;
	}

	public static String importVertexMessage(int mode) {
		return switch (mode) {
			case IMPORT_VERTEX_SECOND -> "message.refugee.staff.import.vertex.second";
			case IMPORT_VERTEX_BOTH -> "message.refugee.staff.import.vertex.both";
			case IMPORT_VERTEX_NONE -> "message.refugee.staff.import.vertex.none";
			default -> "message.refugee.staff.import.vertex.first";
		};
	}

	public BlockPos zoneCorner() {
		return zoneCorner;
	}

	public ResourceLocation zoneDimension() {
		return zoneDimension;
	}

	public void setZoneCorner(ResourceLocation dimension, BlockPos pos) {
		this.zoneDimension = dimension;
		this.zoneCorner = pos == null ? null : pos.immutable();
	}

	public void clearZoneCorner() {
		this.zoneCorner = null;
		this.zoneDimension = null;
	}

	public Direction.Axis advanceAxis() {
		return advanceAxis == null ? Direction.Axis.Y : advanceAxis;
	}

	public boolean advancePositive() {
		return advancePositive;
	}

	public void cycleAdvanceAxis(boolean forward) {
		Direction.Axis[] values = Direction.Axis.values();
		int index = advanceAxis().ordinal();
		index = forward ? (index + 1) % values.length : (index + values.length - 1) % values.length;
		advanceAxis = values[index];
	}

	public void setAdvancePositive(boolean positive) {
		this.advancePositive = positive;
	}

	public void resetAdvanceTune() {
		this.advanceAxis = Direction.Axis.Y;
		this.advancePositive = true;
	}

	public List<BlockPos> patrolPoints() {
		return List.copyOf(patrolPoints);
	}

	public boolean addPatrolPoint(BlockPos pos) {
		if (pos == null) {
			return false;
		}
		BlockPos immutable = pos.immutable();
		if (!patrolPoints.isEmpty() && patrolPoints.getLast().equals(immutable)) {
			return false;
		}
		patrolPoints.add(immutable);
		return true;
	}

	public void clearPatrolPoints() {
		patrolPoints.clear();
	}
}
