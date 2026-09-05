package luowei.refugee.interact;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import luowei.refugee.Refugee;

public final class VillagerKitMenus {
	public record OpenData(int entityId) {
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenData> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT,
				OpenData::entityId,
				OpenData::new
		);
	}

	public static MenuType<VillagerKitMenu> KIT;

	private VillagerKitMenus() {
	}

	public static void register() {
		KIT = Registry.register(
				BuiltInRegistries.MENU,
				Refugee.id("kit"),
				new ExtendedScreenHandlerType<>(VillagerKitMenu::new, OpenData.STREAM_CODEC)
		);
	}

	public static boolean open(ServerPlayer player, Villager villager) {
		if (player == null || villager == null || !villager.isAlive()) {
			return false;
		}
		player.openMenu(new ExtendedScreenHandlerFactory<OpenData>() {
			@Override
			public OpenData getScreenOpeningData(ServerPlayer opener) {
				return new OpenData(villager.getId());
			}

			@Override
			public Component getDisplayName() {
				return Component.translatable("screen.refugee.kit.title", villager.getDisplayName());
			}

			@Override
			public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player opener) {
				return new VillagerKitMenu(syncId, inventory, villager);
			}
		});
		return true;
	}

	static Villager findVillager(Player player, int entityId) {
		if (player == null || player.level() == null) {
			return null;
		}
		Entity entity = player.level().getEntity(entityId);
		return entity instanceof Villager villager ? villager : null;
	}
}
