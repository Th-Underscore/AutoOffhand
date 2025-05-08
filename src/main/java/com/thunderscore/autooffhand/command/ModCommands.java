package com.thunderscore.autooffhand.command;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.thunderscore.autooffhand.AutoOffhand;
import com.thunderscore.autooffhand.capability.IPlayerConfig;
import com.thunderscore.autooffhand.capability.PlayerConfigCapability;
import com.thunderscore.autooffhand.config.ModConfig;
import com.thunderscore.autooffhand.network.NetworkHandler;
import com.thunderscore.autooffhand.network.OpenConfigGuiPacket;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.PacketDistributor;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> cmdAutoOffhand = Commands.literal("autooffhand");

        // Subcommand for OPs to view/edit SERVER config
        cmdAutoOffhand.then(Commands.literal("serverconfig")
            .requires((source) -> source.hasPermission(2))
            .executes((context) -> {
                CommandSourceStack source = context.getSource();
                if (source.getEntity() instanceof ServerPlayer) {
                    ServerPlayer player = (ServerPlayer) source.getEntity();

                    // --- Get the SERVER config list ---
                    List<String> serverConfigList = new ArrayList<>(ModConfig.SERVER.globalAutoOffhandItems.get());
                    // --- End Server Config Access ---

                    // Send packet with the SERVER config list and set isServerConfig to true
                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new OpenConfigGuiPacket(serverConfigList, true));
                    source.sendSuccess(new TranslatableComponent("commands.autooffhand.serverconfig.success"), true);
                    return 1; // Success
                } else {
                    source.sendFailure(new TranslatableComponent("commands.autooffhand.fail.not_player"));
                    return 0; // Failure
                }
            })
        );

        // Subcommand for players to toggle their preference
        cmdAutoOffhand.then(Commands.literal("toggle")
            .requires((source) -> source.hasPermission(0))
            .executes((context) -> {
                CommandSourceStack source = context.getSource();
                if (source.getEntity() instanceof ServerPlayer) {
                    ServerPlayer player = (ServerPlayer) source.getEntity();
                    LazyOptional<IPlayerConfig> capOpt = player.getCapability(PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY);

                    if (capOpt.isPresent()) {
                        IPlayerConfig cap = capOpt.orElseThrow(() -> new IllegalStateException("Capability present but could not be retrieved"));
                        boolean currentSetting = cap.isUsingServerConfig();
                        cap.setUseServerConfig(!currentSetting); // Toggle the setting

                        // Send feedback message
                        String feedbackKey = !currentSetting ? "commands.autooffhand.toggle.success.server" : "commands.autooffhand.toggle.success.player";
                        source.sendSuccess(new TranslatableComponent(feedbackKey), true);
                        return 1; // Success
                    } else {
                        // Should not happen if capability is attached correctly
                        AutoOffhand.LOGGER.error("Could not retrieve PlayerConfig capability for player {} during toggle command.", player.getName().getString());
                        source.sendFailure(new TextComponent("Internal error: Could not access player configuration."));
                        return 0; // Failure
                    }
                } else {
                    source.sendFailure(new TranslatableComponent("commands.autooffhand.fail.not_player"));
                    return 0; // Failure
                }
            })
        );

        dispatcher.register(cmdAutoOffhand);
    }
}
