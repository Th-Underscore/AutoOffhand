package com.thunderscore.autooffhand.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.thunderscore.autooffhand.ClientSetup;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

/**
 * Packet sent from Server -> Client to request opening the config GUI.
 * This is triggered by the /autooffhand command.
 */
public class OpenConfigGuiPacket {

    private static final Logger LOGGER = LogManager.getLogger();
    private final List<String> configEntries;
    private final boolean isServerConfig;

    // Constructor used by the server when sending
    public OpenConfigGuiPacket(List<String> configEntries, boolean isServerConfig) {
        this.configEntries = new ArrayList<>(configEntries); // Defensive copy
        this.isServerConfig = isServerConfig;
    }

    // Constructor used by Forge for decoding
    private OpenConfigGuiPacket(PacketBuffer buf) {
        this.isServerConfig = buf.readBoolean();
        int size = buf.readVarInt();
        this.configEntries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.configEntries.add(buf.readUtf(32767));
        }
    }


    // --- Encoding and Decoding ---

    public static void encode(OpenConfigGuiPacket msg, PacketBuffer buf) {
        buf.writeBoolean(msg.isServerConfig);
        buf.writeVarInt(msg.configEntries.size());
        for (String entry : msg.configEntries) {
            buf.writeUtf(entry); // Write each string
        }
    }

    public static OpenConfigGuiPacket decode(PacketBuffer buf) {
        // Decode using the PacketBuffer constructor
        return new OpenConfigGuiPacket(buf);
    }

    // --- Handling ---

    public static void handle(OpenConfigGuiPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            LOGGER.debug("Received OpenConfigGuiPacket on client (isServerConfig={}) with {} entries: {}",
                        msg.isServerConfig,
                        msg.configEntries != null ? msg.configEntries.size() : "null",
                        msg.configEntries);
            // Pass the received list and flag to the screen opening method
            ClientSetup.openConfigScreen(msg.configEntries, msg.isServerConfig);
        });
        ctx.get().setPacketHandled(true);
    }
}
