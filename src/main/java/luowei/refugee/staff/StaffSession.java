package luowei.refugee.staff;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import luowei.refugee.zone.AreaBox;

/**
 * 玩家当前指挥杖会话（不落盘）：页面栈 + 框选/导入/巡逻临时状态。
 */
public final class StaffSession {
	private final List<StaffPage> stack = new ArrayList<>();
	private final List<BlockPos> patrolPoints = new ArrayList<>();
	private BlockPos zoneCorner;
	private ResourceLocation zoneDimension;
	private AreaBox pendingImport;

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
			case ZONE -> setPages(StaffPage.ZONE);
			case BUILD -> setPages(StaffPage.BUILD_CATALOG, StaffPage.BUILD_PREVIEW);
			case IMPORT -> setPages(StaffPage.IMPORT);
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
		clearPatrolPoints();
	}

	private void onPageChanged() {
		StaffPage page = page();
		if (page != StaffPage.ZONE && page != StaffPage.IMPORT && page != StaffPage.IMPORT_NAME) {
			clearZoneCorner();
		}
		if (page != StaffPage.IMPORT && page != StaffPage.IMPORT_NAME) {
			clearImportBox();
		}
		if (page != StaffPage.COMBAT_PATROL) {
			clearPatrolPoints();
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
