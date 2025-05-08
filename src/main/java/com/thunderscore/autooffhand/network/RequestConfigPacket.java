package com.thunderscore.autooffhand.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.thunderscore.autooffhand.AutoOffhand;
import com.thunderscore.autooffhand.capability.IPlayerConfig;
import com.thunderscore.autooffhand.capability.PlayerConfigCapability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Packet sent from Client -> Server when the config GUI keybind is pressed,
 * requesting the server send back the player's current config list.
 */
public class RequestConfigPacket {

    // No data needed for this packet

    public RequestConfigPacket() {
    }

    // Encoder: No data to write
    public static void encode(RequestConfigPacket msg, FriendlyByteBuf buf) {
    }

    // Decoder: No data to read
    public static RequestConfigPacket decode(FriendlyByteBuf buf) {
        return new RequestConfigPacket();
    }

    // Handler: Process the packet on the receiving side (Server)
    public static void handle(RequestConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) {
                AutoOffhand.LOGGER.warn("Received RequestConfigPacket from null sender!");
                return;
            }

            AutoOffhand.LOGGER.info("Received RequestConfigPacket from player {}. Sending their config data back...", sender.getName().getString());

            // Get the player's capability data
            List<String> playerConfigList = sender.getCapability(PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY)
                .map(IPlayerConfig::getConfigList)
                .orElseGet(() -> {
                    // Should not happen if capability is attached correctly, but provide default
                    AutoOffhand.LOGGER.warn("Could not retrieve capability data for player {} when handling RequestConfigPacket. Sending empty list.", sender.getName().getString());
                    return new ArrayList<String>();
                });

            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender), new OpenConfigGuiPacket(playerConfigList, false));
        });
        ctx.get().setPacketHandled(true);
    }
}
