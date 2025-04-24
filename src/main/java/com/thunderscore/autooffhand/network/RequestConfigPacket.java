package com.thunderscore.autooffhand.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.thunderscore.autooffhand.AutoOffhand;
import com.thunderscore.autooffhand.capability.IPlayerConfig;
import com.thunderscore.autooffhand.capability.PlayerConfigCapability;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;

/**
 * Packet sent from Client -> Server when the config GUI keybind is pressed,
 * requesting the server send back the player's current config list.
 */
public class RequestConfigPacket {

    // No data needed for this packet

    public RequestConfigPacket() {
        // No fields to initialize
    }

    // Encoder: No data to write
    public static void encode(RequestConfigPacket msg, PacketBuffer buf) {
        // Empty
    }

    // Decoder: No data to read
    public static RequestConfigPacket decode(PacketBuffer buf) {
        return new RequestConfigPacket();
    }

    // Handler: Process the packet on the receiving side (Server)
    public static void handle(RequestConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity sender = ctx.get().getSender();
            if (sender == null) {
                AutoOffhand.LOGGER.warn("Received RequestConfigPacket from null sender!");
                return;
            }

            AutoOffhand.LOGGER.debug("Received RequestConfigPacket from player {}. Sending their config data back...", sender.getName().getString());

            // Get the player's capability data
            List<String> playerConfigList = sender.getCapability(PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY)
                .map(IPlayerConfig::getConfigList) // Get the list (returns a copy)
                .orElseGet(() -> {
                    // Should not happen if capability is attached correctly, but provide default
                    AutoOffhand.LOGGER.warn("Could not retrieve capability data for player {} when handling RequestConfigPacket. Sending empty list.", sender.getName().getString());
                    return new ArrayList<String>(); // Return empty list on error
                });

            // Send the OpenConfigGuiPacket back to the requesting client, indicating it's for player config (false)
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender), new OpenConfigGuiPacket(playerConfigList, false));
        });
        ctx.get().setPacketHandled(true);
    }
}
