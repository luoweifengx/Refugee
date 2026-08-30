package luowei.refugee.client;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;

import luowei.refugee.staff.StaffMode;
import luowei.refugee.staff.StaffPage;
import luowei.refugee.zone.AreaBox;

/**
 * 客户端指挥杖页面、仓库箱、工作区、建筑任务与导入框选（由 S2C 同步）。
 */
public final class ClientStaffState {
	private static StaffMode mode = StaffMode.NONE;
	private static StaffPage page = StaffPage.ROOT;
	private static List<BlockPos> chests = List.of();
	private static List<AreaBox> zones = List.of();
	private static List<AreaBox> builds = List.of();
	private static BlockPos pendingCorner;
	private static AreaBox importBox;

	private ClientStaffState() {
	}

	public static void apply(
			StaffMode nextMode,
			StaffPage nextPage,
			List<BlockPos> nextChests,
			List<AreaBox> nextZones,
			List<AreaBox> nextBuilds,
			Optional<BlockPos> nextCorner,
			Optional<AreaBox> nextImport
	) {
		StaffPage previous = page;
		mode = nextMode == null ? StaffMode.NONE : nextMode;
		page = nextPage == null ? StaffPage.ROOT : nextPage;
		chests = nextChests == null ? List.of() : List.copyOf(nextChests);
		zones = nextZones == null ? List.of() : List.copyOf(nextZones);
		builds = nextBuilds == null ? List.of() : List.copyOf(nextBuilds);
		pendingCorner = nextCorner == null ? null : nextCorner.orElse(null);
		importBox = nextImport == null ? null : nextImport.orElse(null);
		if (page != StaffPage.BUILD_PREVIEW || previous != StaffPage.BUILD_PREVIEW) {
			ClientBlueprintSelection.clearPreview();
		}
	}

	public static void setPage(StaffPage nextPage) {
		page = nextPage == null ? StaffPage.ROOT : nextPage;
		mode = page.worldMode();
		if (page != StaffPage.BUILD_PREVIEW) {
			ClientBlueprintSelection.clearPreview();
		}
	}

	public static StaffMode mode() {
		return mode;
	}

	public static StaffPage page() {
		return page;
	}

	public static List<BlockPos> chests() {
		return chests;
	}

	public static List<AreaBox> zones() {
		return zones;
	}

	public static List<AreaBox> builds() {
		return builds;
	}

	public static BlockPos pendingCorner() {
		return pendingCorner;
	}

	public static AreaBox importBox() {
		return importBox;
	}

	public static void clear() {
		mode = StaffMode.NONE;
		page = StaffPage.ROOT;
		chests = List.of();
		zones = List.of();
		builds = List.of();
		pendingCorner = null;
		importBox = null;
		ClientBlueprintSelection.clearPreview();
	}
}
