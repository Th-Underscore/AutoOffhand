package com.thunderscore.autooffhand.network;

import com.thunderscore.autooffhand.AutoOffhand;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";
    // Store the channel name ResourceLocation for easy access
    public static final ResourceLocation CHANNEL_NAME = new ResourceLocation(AutoOffhand.MOD_ID, "main");

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            CHANNEL_NAME,
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION::equals) // Server accepts clients missing the channel or matching the version
    );

    private static int packetId = 0;
    private static int nextId() {
        return packetId++;
    }

    public static void register() {
        AutoOffhand.LOGGER.info("Registering network packets...");

        // Update config from client to server
        INSTANCE.registerMessage(nextId(),
                UpdateConfigPacket.class,
                UpdateConfigPacket::encode,
                UpdateConfigPacket::decode,
                UpdateConfigPacket::handle
        );

        // Sync config from server to client
        INSTANCE.registerMessage(nextId(),
                SyncConfigPacket.class,
                SyncConfigPacket::encode,
                SyncConfigPacket::decode,
                SyncConfigPacket::handle
        );

        // Open GUI from server to client
        INSTANCE.registerMessage(nextId(),
                OpenConfigGuiPacket.class,
                OpenConfigGuiPacket::encode,
                OpenConfigGuiPacket::decode,
                OpenConfigGuiPacket::handle
        );

        // Request config from client to server
        INSTANCE.registerMessage(nextId(),
                RequestConfigPacket.class,
                RequestConfigPacket::encode,
                RequestConfigPacket::decode,
                RequestConfigPacket::handle
        );

        AutoOffhand.LOGGER.info("Network packets registered.");
    }
}
