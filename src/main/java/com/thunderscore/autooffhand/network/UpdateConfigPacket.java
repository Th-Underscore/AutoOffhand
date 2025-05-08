package com.thunderscore.autooffhand.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.thunderscore.autooffhand.AutoOffhand;
import com.thunderscore.autooffhand.capability.PlayerConfigCapability;
import com.thunderscore.autooffhand.config.ModConfig;

import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

public class UpdateConfigPacket {

    private final List<String> configEntries;
    private final boolean isServerConfig;

    public UpdateConfigPacket(List<String> entries, boolean isServerConfig) {
        this.configEntries = new ArrayList<>(entries);
        this.isServerConfig = isServerConfig;
    }

    // Encoder: Write data to the buffer
    public static void encode(UpdateConfigPacket msg, FriendlyByteBuf buf) {
        AutoOffhand.LOGGER.info("Encoding UpdateConfigPacket (isServerConfig={}) with {} entries.", msg.isServerConfig, msg.configEntries.size());
        buf.writeBoolean(msg.isServerConfig);
        buf.writeVarInt(msg.configEntries.size());
        for (String entry : msg.configEntries) {
            buf.writeUtf(entry);
        }
    }

    // Decoder: Read data from the buffer
    public static UpdateConfigPacket decode(FriendlyByteBuf buf) {
        boolean isServer = buf.readBoolean();
        int size = buf.readVarInt();
        AutoOffhand.LOGGER.info("Decoding UpdateConfigPacket (isServerConfig={}) with {} entries.", isServer, size);
        List<String> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(buf.readUtf());
        }
        return new UpdateConfigPacket(entries, isServer);
    }

    // Handler: Process the packet on the receiving side (server)
    public static void handle(UpdateConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                AutoOffhand.LOGGER.warn("Received UpdateConfigPacket from null sender!");
                return;
            }

            AutoOffhand.LOGGER.info("Received UpdateConfigPacket on server from player {} (isServerConfig={}) with {} entries.", sender.getName().getString(), msg.isServerConfig, msg.configEntries.size());

            try {
                if (msg.isServerConfig) {
                    // --- Update Server Config ---
                    if (sender.hasPermissions(Commands.LEVEL_GAMEMASTERS)) {
                        AutoOffhand.LOGGER.info("Applying update to server config...");
                        // Set the new list in the config object
                        ModConfig.SERVER.globalAutoOffhandItems.set(msg.configEntries);
                        // ModConfig.SERVER_SPEC.save(); // Explicit save is generally not needed here
                        AutoOffhand.LOGGER.info("Updated server config value in memory. List size: {}", msg.configEntries.size());
                        sender.displayClientMessage(new TranslatableComponent("commands.autooffhand.success.server_updated"), false); // TODO: Show message on GUI close

                        // Send the updated SERVER list back to the *sender* for confirmation/sync
                        List<String> currentServerList = new ArrayList<>(ModConfig.SERVER.globalAutoOffhandItems.get());
                        AutoOffhand.LOGGER.info("Sending SyncConfigPacket back to client {} with {} server entries.", sender.getName().getString(), currentServerList.size());
                        // Indicate this sync is for the server config
                        SyncConfigPacket syncPacket = new SyncConfigPacket(currentServerList, true);
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender), syncPacket);

                    } else {
                        AutoOffhand.LOGGER.warn("Player {} attempted to update server config without permission!", sender.getName().getString());
                        sender.displayClientMessage(new TranslatableComponent("commands.autooffhand.fail.permission"), false);
                    }
                } else {
                    // --- Update Player Config ---
                    AutoOffhand.LOGGER.info("Applying update to player {}'s capability...", sender.getName().getString());
                    sender.getCapability(PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY).ifPresent(cap -> {
                        // Set the list in the capability
                        cap.setConfigList(msg.configEntries);
                        AutoOffhand.LOGGER.info("Updated player {}'s config capability.", sender.getName().getString());
                        // sender.displayClientMessage(new TranslatableComponent("commands.autooffhand.success.player_updated"), false); // TODO: Show message on GUI close

                        // Send the updated PLAYER list back to the client for confirmation/sync
                        List<String> currentPlayersList = cap.getConfigList(); // getConfigList returns a defensive copy
                        AutoOffhand.LOGGER.info("Sending SyncConfigPacket back to client {} with {} player-specific entries.", sender.getName().getString(), currentPlayersList.size());
                        // Indicate this sync is for the player config
                        SyncConfigPacket syncPacket = new SyncConfigPacket(currentPlayersList, false);
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender), syncPacket);
                    });
                }
            } catch (Exception e) {
                AutoOffhand.LOGGER.error("Failed to process UpdateConfigPacket for player {} (isServerConfig={})!", sender.getName().getString(), msg.isServerConfig, e);
            }
        });
        context.setPacketHandled(true);
    }
}
