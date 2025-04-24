package com.thunderscore.autooffhand.network;

import com.thunderscore.autooffhand.AutoOffhand;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";
    // Store the channel name ResourceLocation for easy access
    public static final ResourceLocation CHANNEL_NAME = new ResourceLocation(AutoOffhand.MOD_ID, "main");

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            CHANNEL_NAME, // Use the stored ResourceLocation
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals, // Client requires server version to match if present
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION::equals) // Server accepts clients missing the channel or matching the version
    );

    private static int packetId = 0;
    private static int nextId() {
        return packetId++;
    }

    public static void register() {
        AutoOffhand.LOGGER.debug("Registering network packets...");

        // Register the packet for updating config from client to server
        INSTANCE.registerMessage(nextId(),
                UpdateConfigPacket.class,         // Packet class
                UpdateConfigPacket::encode,       // Encoder
                UpdateConfigPacket::decode,       // Decoder
                UpdateConfigPacket::handle        // Handler
        );

        // Register the packet for syncing config from server to client
        INSTANCE.registerMessage(nextId(),
                SyncConfigPacket.class,           // Packet class
                SyncConfigPacket::encode,         // Encoder
                SyncConfigPacket::decode,         // Decoder
                SyncConfigPacket::handle          // Handler
                // Optional: Specify network direction if needed, though SimpleChannel often infers
        );

        // Register the packet for opening GUI from server to client
        INSTANCE.registerMessage(nextId(),
                OpenConfigGuiPacket.class,        // Packet class
                OpenConfigGuiPacket::encode,      // Encoder
                OpenConfigGuiPacket::decode,      // Decoder
                OpenConfigGuiPacket::handle       // Handler
        );

        // Register the packet for requesting config from client to server
        INSTANCE.registerMessage(nextId(),
                RequestConfigPacket.class,        // Packet class
                RequestConfigPacket::encode,      // Encoder
                RequestConfigPacket::decode,      // Decoder
                RequestConfigPacket::handle       // Handler
        );

        AutoOffhand.LOGGER.debug("Network packets registered.");
    }
}
