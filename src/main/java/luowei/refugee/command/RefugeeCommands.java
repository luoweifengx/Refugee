package luowei.refugee.command;

import java.util.UUID;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import luowei.refugee.attachment.PlayerSelectionData;
import luowei.refugee.attachment.PlayerSelectionData.RosterEntry;
import luowei.refugee.attachment.RefugeeAttachments;
import luowei.refugee.blueprint.BlueprintRegistry;
import luowei.refugee.interact.RosterService;
import luowei.refugee.network.RefugeeNetworking;
import luowei.refugee.pbs.PbsAdapter;
import luowei.refugee.spawn.RefugeeImmigration;
import luowei.refugee.spawn.RefugeeImmigration.ImmigrationResult;
import luowei.refugee.special.RefugeeSpecialRole;
import luowei.refugee.special.SpecialRefugeeService;
import luowei.refugee.special.SpecialRefugeeService.CommandSpawnResult;

public final class RefugeeCommands {
	private RefugeeCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				Commands.literal("refugee")
						.then(Commands.literal("roster")
								.executes(RefugeeCommands::rosterSelf)
								.then(Commands.argument("player", EntityArgument.player())
										.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
										.executes(RefugeeCommands::rosterForArgument)))
						.then(Commands.literal("spawn")
								.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.executes(RefugeeCommands::spawnSelf)
								.then(Commands.argument("player", EntityArgument.player())
										.executes(RefugeeCommands::spawnForArgument))
								.then(Commands.literal("special")
										.then(specialRole("guide", RefugeeSpecialRole.GUIDE))
										.then(specialRole("nurse", RefugeeSpecialRole.NURSE))
										.then(specialRole("cartographer", RefugeeSpecialRole.CARTOGRAPHER))
										.then(specialRole("enchanter", RefugeeSpecialRole.ENCHANTER))))
						.then(Commands.literal("blueprint")
								.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.literal("reload")
										.executes(RefugeeCommands::reloadBlueprints)))
		);
	}

	private static int rosterSelf(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.translatable("message.refugee.roster.no_player"));
			return 0;
		}
		return listRoster(context.getSource(), player);
	}

	private static int rosterForArgument(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		return listRoster(context.getSource(), EntityArgument.getPlayer(context, "player"));
	}

	private static int listRoster(CommandSourceStack source, ServerPlayer owner) {
		PlayerSelectionData data = RefugeeAttachments.get(owner);
		if (data.isRosterEmpty()) {
			source.sendFailure(Component.translatable("message.refugee.roster.empty", owner.getGameProfile().getName()));
			return 0;
		}
		MinecraftServer server = source.getServer();
		source.sendSuccess(
				() -> Component.translatable("message.refugee.roster.header", owner.getGameProfile().getName(), data.rosterSize()),
				false
		);
		int index = 1;
		for (RosterEntry entry : data.rosterEntries()) {
			int line = index++;
			boolean selected = data.isSelected(entry.villagerId());
			boolean loaded = findLoaded(server, entry.villagerId()) != null;
			Component selectedText = Component.translatable(
					selected ? "message.refugee.roster.selected" : "message.refugee.roster.unselected"
			);
			Component loadedText = Component.translatable(
					loaded ? "message.refugee.roster.loaded" : "message.refugee.roster.unloaded"
			);
			source.sendSuccess(
					() -> Component.translatable(
							"message.refugee.roster.entry",
							line,
							selectedText,
							dimensionText(entry.dimension()),
							posText(entry.pos()),
							loadedText
					),
					false
			);
		}
		return data.rosterSize();
	}

	private static Component dimensionText(ResourceLocation dimension) {
		if (dimension == null) {
			return Component.translatable("message.refugee.roster.unknown_dimension");
		}
		return Component.literal(dimension.toString());
	}

	private static Component posText(BlockPos pos) {
		if (pos == null) {
			return Component.translatable("message.refugee.roster.unknown_pos");
		}
		return Component.translatable("message.refugee.roster.pos", pos.getX(), pos.getY(), pos.getZ());
	}

	private static Entity findLoaded(MinecraftServer server, UUID id) {
		if (server == null || id == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(id);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}

	private static int reloadBlueprints(CommandContext<CommandSourceStack> context) {
		MinecraftServer server = context.getSource().getServer();
		int loaded = BlueprintRegistry.reload(server);
		RefugeeNetworking.syncCatalogToAll(server);
		context.getSource().sendSuccess(
				() -> Component.translatable("message.refugee.blueprint.reload.success", loaded),
				true
		);
		return Command.SINGLE_SUCCESS;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> specialRole(
			String name,
			RefugeeSpecialRole role
	) {
		return Commands.literal(name)
				.executes(context -> spawnSpecialSelf(context, role))
				.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> spawnSpecialForArgument(context, role)));
	}

	private static int spawnSpecialSelf(CommandContext<CommandSourceStack> context, RefugeeSpecialRole role) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.translatable("message.refugee.spawn.no_player"));
			return 0;
		}
		return spawnSpecialFor(context.getSource(), player, role);
	}

	private static int spawnSpecialForArgument(
			CommandContext<CommandSourceStack> context,
			RefugeeSpecialRole role
	) throws CommandSyntaxException {
		return spawnSpecialFor(context.getSource(), EntityArgument.getPlayer(context, "player"), role);
	}

	private static int spawnSpecialFor(CommandSourceStack source, ServerPlayer player, RefugeeSpecialRole role) {
		Component roleName = Component.translatable("role.refugee." + role.id());
		CommandSpawnResult result = SpecialRefugeeService.spawnForCommand(player, role);
		return switch (result.status()) {
			case SUCCESS -> {
				source.sendSuccess(
						() -> Component.translatable(
								"message.refugee.spawn.special.success",
								player.getGameProfile().getName(),
								roleName,
								result.pos().getX(),
								result.pos().getY(),
								result.pos().getZ()
						),
						true
				);
				yield Command.SINGLE_SUCCESS;
			}
			case ALREADY_HAS -> {
				source.sendFailure(Component.translatable(
						"message.refugee.spawn.special.exists",
						player.getGameProfile().getName(),
						roleName
				));
				yield 0;
			}
			case NO_STANDABLE -> {
				source.sendFailure(Component.translatable("message.refugee.spawn.no_standable"));
				yield 0;
			}
			case FAILED -> {
				source.sendFailure(Component.translatable("message.refugee.spawn.failed"));
				yield 0;
			}
		};
	}

	private static int spawnSelf(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.translatable("message.refugee.spawn.no_player"));
			return 0;
		}
		return spawnFor(context.getSource(), player);
	}

	private static int spawnForArgument(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		return spawnFor(context.getSource(), EntityArgument.getPlayer(context, "player"));
	}

	private static int spawnFor(CommandSourceStack source, ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			source.sendFailure(Component.translatable("message.refugee.spawn.chunk_unloaded"));
			return 0;
		}
		UUID subjectId = PbsAdapter.resolveSubject(player);
		String subjectName = PbsAdapter.displayName(level.getServer(), subjectId);
		ImmigrationResult result = RefugeeImmigration.tryImmigrate(level, subjectId, true);
		return switch (result.status()) {
			case SUCCESS -> {
				RosterService.registerOwnedIfPlayer(level.getServer(), subjectId, result.villager());
				source.sendSuccess(
						() -> Component.translatable(
								"message.refugee.spawn.success",
								subjectName,
								result.pos().getX(),
								result.pos().getY(),
								result.pos().getZ()
						),
						true
				);
				yield Command.SINGLE_SUCCESS;
			}
			case NO_TERRITORY -> {
				source.sendFailure(Component.translatable("message.refugee.spawn.no_territory", subjectName));
				yield 0;
			}
			case CHUNK_UNLOADED -> {
				source.sendFailure(Component.translatable("message.refugee.spawn.chunk_unloaded"));
				yield 0;
			}
			case NO_STANDABLE -> {
				source.sendFailure(Component.translatable("message.refugee.spawn.no_standable"));
				yield 0;
			}
			case SKIPPED_DIMENSION -> {
				source.sendFailure(Component.translatable("message.refugee.spawn.dimension_not_allowed"));
				yield 0;
			}
			case CREATE_FAILED, SKIPPED_CHANCE -> {
				source.sendFailure(Component.translatable("message.refugee.spawn.failed"));
				yield 0;
			}
		};
	}
}
