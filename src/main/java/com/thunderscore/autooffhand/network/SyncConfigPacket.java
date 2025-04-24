package com.thunderscore.autooffhand.network;

import com.thunderscore.autooffhand.AutoOffhand;
// Removed ModConfig import as we no longer set client-side config values here
import com.thunderscore.autooffhand.gui.ConfigItemListScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncConfigPacket {

    private final List<String> configEntries;
    private final boolean isServerConfig; // Flag to indicate which config this syncs

    public SyncConfigPacket(List<String> entries, boolean isServerConfig) {
        this.configEntries = new ArrayList<>(entries); // Copy list
        this.isServerConfig = isServerConfig;
    }

    // Getters might be useful if accessed elsewhere, but not strictly needed for handler
    // public List<String> getConfigEntries() { return configEntries; }
    // public boolean isServerConfig() { return isServerConfig; }

    // Encoder: Write data to the buffer (Server -> Client)
    public static void encode(SyncConfigPacket msg, PacketBuffer buf) {
        AutoOffhand.LOGGER.debug("Encoding SyncConfigPacket (isServerConfig={}) with {} entries.", msg.isServerConfig, msg.configEntries.size());
        buf.writeBoolean(msg.isServerConfig); // Write flag first
        buf.writeVarInt(msg.configEntries.size());
        for (String entry : msg.configEntries) {
            buf.writeUtf(entry);
        }
    }

    // Decoder: Read data from the buffer (Client side)
    public static SyncConfigPacket decode(PacketBuffer buf) {
        boolean isServer = buf.readBoolean(); // Read flag first
        int size = buf.readVarInt();
        AutoOffhand.LOGGER.debug("Decoding SyncConfigPacket (isServerConfig={}) with {} entries.", isServer, size);
        List<String> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(buf.readUtf());
        }
        return new SyncConfigPacket(entries, isServer);
    }

    // Handler: Process the packet on the receiving side (Client)
    public static void handle(SyncConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // IMPORTANT: Execute on the Client thread
            // Use DistExecutor to ensure this runs only on the client side safely
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                AutoOffhand.LOGGER.debug("Received SyncConfigPacket on client (isServerConfig={}) with {} entries.", msg.isServerConfig, msg.configEntries.size());
                try {
                    // Update the GUI only if it's open AND it matches the type of config being synced.
                    Screen currentScreen = Minecraft.getInstance().screen;
                    if (currentScreen instanceof ConfigItemListScreen) {
                        ConfigItemListScreen configScreen = (ConfigItemListScreen) currentScreen;
                        // Check if the screen's config type matches the packet's config type
                        // We need to expose the isServerConfig flag from the screen or add a getter.
                        // Let's assume we add a public getter `isEditingServerConfig()` to ConfigItemListScreen.
                        // if (configScreen.isEditingServerConfig() == msg.isServerConfig) { // TODO: Add getter to ConfigItemListScreen
                        // For now, let's just refresh regardless, but add a TODO
                        // TODO: Check if configScreen.isServerConfig matches msg.isServerConfig before refreshing
                        AutoOffhand.LOGGER.debug("Config screen is open, refreshing entries from synced config (isServerConfig={}).", msg.isServerConfig);
                        configScreen.refreshEntriesFromList(msg.configEntries);
                        // } else {
                        //    AutoOffhand.LOGGER.debug("Config screen is open but for the wrong config type (screen={}, packet={}). Ignoring sync.", configScreen.isEditingServerConfig(), msg.isServerConfig);
                        // }
                    } else {
                        // If the screen isn't open, we don't need to do anything.
                        AutoOffhand.LOGGER.debug("Config screen not open, no GUI update needed from SyncConfigPacket.");
                    }

                } catch (Exception e) {
                    AutoOffhand.LOGGER.error("Failed to update client GUI from sync packet!", e);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
