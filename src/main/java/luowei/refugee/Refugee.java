package luowei.refugee;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.block.ModBlocks;
import luowei.refugee.blueprint.BlueprintRegistry;
import luowei.refugee.command.RefugeeCommands;
import luowei.refugee.config.RefugeeConfig;
import luowei.refugee.interact.DeathDropService;
import luowei.refugee.interact.RefugeeInteractions;
import luowei.refugee.interact.RosterService;
import luowei.refugee.interact.SelectionService;
import luowei.refugee.interact.VillagerKitMenus;
import luowei.refugee.ai.RefugeeCombat;
import luowei.refugee.item.ModItems;
import luowei.refugee.network.RefugeeNetworking;
import luowei.refugee.spawn.LandmarkSpawnService;
import luowei.refugee.spawn.RefugeeImmigration;
import luowei.refugee.spawn.RefugeeStructures;
import luowei.refugee.special.RefugeeSpecialRole;
import luowei.refugee.special.SpecialRefugeeService;

public class Refugee implements ModInitializer {
	public static final String MOD_ID = "refugee";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			Configurator.setLevel(MOD_ID, Level.DEBUG);
		}
		RefugeeConfig.load();
		RefugeeStructures.register();
		RefugeeAttachments.register();
		ModBlocks.register();
		ModItems.register();
		VillagerKitMenus.register();
		BlueprintRegistry.register();
		RefugeeNetworking.register();
		RefugeeInteractions.register();
		RosterService.register();
		luowei.refugee.staff.StaffService.register();
		luowei.refugee.staff.GuardService.register();
		luowei.refugee.warehouse.WarehouseService.register();
		LandmarkSpawnService.register();
		SpecialRefugeeService.register();
		luowei.refugee.special.GuideTutorialService.register();
		luowei.refugee.pbs.OrgMergeService.register();
		RefugeeImmigration.register();
		luowei.refugee.crusader.CrusaderService.register();
		RefugeeCommands.register();
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof Villager villager) {
				RefugeeCombat.onDamaged(villager, source);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Villager villager && entity.level() instanceof ServerLevel level) {
				if (RefugeeSpecialRole.isSpecial(villager)) {
					SpecialRefugeeService.markSpecialGone(level.getServer(), villager.getUUID());
				}
				DeathDropService.dropOnDeath(villager, level);
				SelectionService.onVillagerRemoved(villager.getUUID(), level);
			}
		});
		LOGGER.debug("Refugee initialized (PBS territory + zombie villages + immigration)");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
