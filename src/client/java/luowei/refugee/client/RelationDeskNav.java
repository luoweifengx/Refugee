package luowei.refugee.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

import luowei.refugee.network.StaffNavPayload;
import luowei.refugee.staff.RelationDeskPage;
import luowei.refugee.staff.StaffNavAction;
import luowei.refugee.staff.StaffPage;

/**
 * 从名单、命名这些表单回到关系管理台时，记住当时的那一页。
 */
public final class RelationDeskNav {
	private static RelationDeskPage section = RelationDeskPage.HOME;
	private static boolean using;

	private RelationDeskNav() {
	}

	public static RelationDeskPage section() {
		return section;
	}

	public static boolean using() {
		return using;
	}

	public static void remember(RelationDeskPage page) {
		section = page == null ? RelationDeskPage.HOME : page;
		using = true;
	}

	public static void show(Minecraft client, RelationDeskPage page) {
		remember(page);
		if (client != null) {
			client.setScreen(new RelationDeskScreen(section));
		}
	}

	public static void closed() {
		using = false;
	}

	/** 取消表单后回到打开它的那一页，并清掉指挥杖那边的页面。 */
	public static void returnHere() {
		ClientPlayNetworking.send(new StaffNavPayload(StaffNavAction.RESET));
		ClientStaffState.setPage(StaffPage.ROOT);
		show(Minecraft.getInstance(), section);
	}
}
