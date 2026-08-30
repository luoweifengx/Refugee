package luowei.refugee.staff;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import luowei.refugee.zone.AreaBox;

/**
 * 玩家当前指挥杖会话（不落盘）：页面栈 + 框选/导入临时状态。
 */
public final class StaffSession {
	private final List<StaffPage> stack = new ArrayList<>();
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
			case WAREHOUSE -> setPages(StaffPage.WAREHOUSE);
			case ZONE -> setPages(StaffPage.ZONE);
			case BUILD -> setPages(StaffPage.BUILD_CATALOG, StaffPage.BUILD_PREVIEW);
			case IMPORT -> setPages(StaffPage.IMPORT);
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
	}

	private void onPageChanged() {
		StaffPage page = page();
		if (page != StaffPage.ZONE && page != StaffPage.IMPORT && page != StaffPage.IMPORT_NAME) {
			clearZoneCorner();
		}
		if (page != StaffPage.IMPORT && page != StaffPage.IMPORT_NAME) {
			clearImportBox();
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
}
