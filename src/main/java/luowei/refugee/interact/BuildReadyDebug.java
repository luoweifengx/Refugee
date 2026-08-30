package luowei.refugee.interact;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

import luowei.refugee.Refugee;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.attachment.RefugeeVillagerData;

/**
 * 临时诊断：给予工具或指派建造后打印 Goal 条件是否满足。
 */
public final class BuildReadyDebug {
	private BuildReadyDebug() {
	}

	public static void report(ServerPlayer player, Villager villager, String when) {
		RefugeeVillagerData data = RefugeeAttachments.get(villager);
		ItemStack hand = villager.getMainHandItem();
		boolean building = data.isBuilding();
		boolean builder = RefugeeRoles.isBuilder(villager);
		boolean ready = building && builder;

		String handName = hand.isEmpty() ? "empty" : hand.getItem().toString();
		String structure = data.structureId() == null ? "null" : data.structureId().toString();
		String origin = data.buildOrigin() == null ? "null" : data.buildOrigin().toShortString();
		String job = data.jobId() == null ? "null" : data.jobId().toString();

		String line = String.format(
				"[build-ready/%s] ready=%s (isBuilding=%s isBuilder=%s) hand=%s structure=%s origin=%s job=%s buildIndex=%d",
				when,
				ready,
				building,
				builder,
				handName,
				structure,
				origin,
				job,
				data.buildIndex()
		);
		Refugee.LOGGER.info(line);
		player.displayClientMessage(Component.literal(line), false);
	}
}
