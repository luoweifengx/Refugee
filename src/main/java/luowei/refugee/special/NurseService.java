package luowei.refugee.special;



import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import net.minecraft.world.item.ItemStack;

import net.minecraft.world.item.Items;



/**

 * 护士给玩家治疗：费用 = ceil(缺口生命 * sqrt(护甲+1) / 20)，至少 1。

 */

public final class NurseService {

	public enum HealResult {

		FULL,

		NOT_ENOUGH,

		HEALED

	}



	private NurseService() {

	}



	public static int computeFee(Player player) {

		if (player == null) {

			return 1;

		}

		float missing = player.getMaxHealth() - player.getHealth();

		if (missing <= 0.0f) {

			return 0;

		}

		int armor = Math.max(0, player.getArmorValue());

		int fee = (int) Math.ceil(missing * Math.sqrt(armor + 1.0) / 20.0);

		return Math.max(1, fee);

	}



	public static HealResult heal(ServerPlayer player, Villager villager) {

		if (player == null || villager == null) {

			return HealResult.FULL;

		}

		float missing = player.getMaxHealth() - player.getHealth();

		if (missing <= 0.0f) {

			return HealResult.FULL;

		}

		int fee = computeFee(player);

		if (countEmeralds(player) < fee) {

			return HealResult.NOT_ENOUGH;

		}

		consumeEmeralds(player, fee);

		player.setHealth(player.getMaxHealth());

		player.containerMenu.broadcastChanges();

		return HealResult.HEALED;

	}



	private static int countEmeralds(ServerPlayer player) {

		int total = 0;

		Inventory inventory = player.getInventory();

		for (int i = 0; i < inventory.getContainerSize(); i++) {

			ItemStack stack = inventory.getItem(i);

			if (stack.is(Items.EMERALD)) {

				total += stack.getCount();

			}

		}

		return total;

	}



	private static void consumeEmeralds(ServerPlayer player, int fee) {

		int remaining = fee;

		Inventory inventory = player.getInventory();

		for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {

			ItemStack stack = inventory.getItem(i);

			if (!stack.is(Items.EMERALD)) {

				continue;

			}

			int take = Math.min(remaining, stack.getCount());

			stack.shrink(take);

			remaining -= take;

		}

	}

}


