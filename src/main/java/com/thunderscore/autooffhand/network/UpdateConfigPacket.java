package com.thunderscore.autooffhand.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.thunderscore.autooffhand.AutoOffhand;
import com.thunderscore.autooffhand.capability.PlayerConfigCapability;
import com.thunderscore.autooffhand.config.ModConfig;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;

public class UpdateConfigPacket {

    private final List<String> configEntries;
    private final boolean isServerConfig; // Flag to indicate target

    public UpdateConfigPacket(List<String> entries, boolean isServerConfig) {
        this.configEntries = new ArrayList<>(entries); // Copy list
        this.isServerConfig = isServerConfig;
    }

    // Encoder: Write data to the buffer
    public static void encode(UpdateConfigPacket msg, PacketBuffer buf) {
        AutoOffhand.LOGGER.debug("Encoding UpdateConfigPacket (isServerConfig={}) with {} entries.", msg.isServerConfig, msg.configEntries.size());
        buf.writeBoolean(msg.isServerConfig);
        // Write the number of entries
        buf.writeVarInt(msg.configEntries.size());
        // Then write each entry
        for (String entry : msg.configEntries) {
            buf.writeUtf(entry);
        }
    }

    // Decoder: Read data from the buffer
    public static UpdateConfigPacket decode(PacketBuffer buf) {
        boolean isServer = buf.readBoolean();
        // Read the number of entries
        int size = buf.readVarInt();
        AutoOffhand.LOGGER.debug("Decoding UpdateConfigPacket (isServerConfig={}) with {} entries.", isServer, size);
        List<String> entries = new ArrayList<>(size);
        // Read each entry
        for (int i = 0; i < size; i++) {
            entries.add(buf.readUtf());
        }
        return new UpdateConfigPacket(entries, isServer);
    }

    // Handler: Process the packet on the receiving side (server)
    public static void handle(UpdateConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity sender = ctx.get().getSender();
            if (sender == null) {
                AutoOffhand.LOGGER.warn("Received UpdateConfigPacket from null sender!");
                return;
            }

            AutoOffhand.LOGGER.debug("Received UpdateConfigPacket on server from player {} (isServerConfig={}) with {} entries.", sender.getName().getString(), msg.isServerConfig, msg.configEntries.size());

            try {
                if (msg.isServerConfig) {
                    // --- Update Server Config ---
                    // Check permission (same level as command)
                    if (sender.hasPermissions(2)) {
                        AutoOffhand.LOGGER.debug("Applying update to server config...");
                        // Set the new list in the config object
                        // Note: Forge handles saving the config file, usually on server stop or via commands.
                        // The .set() method marks it as dirty.
                        ModConfig.SERVER.globalAutoOffhandItems.set(msg.configEntries);
                        // ModConfig.SERVER_SPEC.save(); // Explicit save might be needed depending on Forge version/setup, but often isn't required here. Let's omit for now.
                        AutoOffhand.LOGGER.debug("Updated server config value in memory. List size: {}", msg.configEntries.size());
                        sender.sendMessage(new TranslationTextComponent("commands.autooffhand.success.server_updated"), sender.getUUID()); // Use sendMessage

                        // Send the updated SERVER list back to the *sender* for confirmation/sync
                        // Retrieve the list *from the config* after setting it
                        List<String> currentServerList = new ArrayList<>(ModConfig.SERVER.globalAutoOffhandItems.get());
                        AutoOffhand.LOGGER.debug("Sending SyncConfigPacket back to client {} with {} server entries.", sender.getName().getString(), currentServerList.size());
                        // Indicate this sync is for the server config
                        SyncConfigPacket syncPacket = new SyncConfigPacket(currentServerList, true);
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender), syncPacket);

                    } else {
                        AutoOffhand.LOGGER.warn("Player {} attempted to update server config without permission!", sender.getName().getString());
                        sender.sendMessage(new TranslationTextComponent("commands.autooffhand.fail.permission"), sender.getUUID()); // Use sendMessage
                    }
                } else {
                    // --- Update Player Config ---
                    AutoOffhand.LOGGER.debug("Applying update to player {}'s capability...", sender.getName().getString());
                    sender.getCapability(PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY).ifPresent(cap -> {
                        // Set the list in the capability
                        cap.setConfigList(msg.configEntries);
                        AutoOffhand.LOGGER.debug("Updated player {}'s config capability.", sender.getName().getString());
                        sender.sendMessage(new TranslationTextComponent("commands.autooffhand.success.player_updated"), sender.getUUID()); // Use sendMessage

                        // Send the updated PLAYER list back to the client for confirmation/sync
                        // Retrieve the list *from the capability* after setting it
                        List<String> currentPlayersList = cap.getConfigList(); // getConfigList returns a defensive copy
                        AutoOffhand.LOGGER.debug("Sending SyncConfigPacket back to client {} with {} player-specific entries.", sender.getName().getString(), currentPlayersList.size());
                        // Indicate this sync is for the player config
                        SyncConfigPacket syncPacket = new SyncConfigPacket(currentPlayersList, false);
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender), syncPacket);
                    });
                }
            } catch (Exception e) {
                AutoOffhand.LOGGER.error("Failed to process UpdateConfigPacket for player {} (isServerConfig={})!", sender.getName().getString(), msg.isServerConfig, e);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
