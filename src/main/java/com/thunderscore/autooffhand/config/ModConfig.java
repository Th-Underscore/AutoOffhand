package com.thunderscore.autooffhand.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

public class ModConfig {
    // Add a logger for this class
    private static final Logger LOGGER = LogManager.getLogger();

   
    public static final ServerConfig SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static {
       
        final Pair<ServerConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    // Renamed from CommonConfig to ServerConfig
    public static class ServerConfig {
        // This list now serves as the GLOBAL fallback
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> globalAutoOffhandItems;
        // Removing substring lists for now to simplify the capability implementation
        // public final ForgeConfigSpec.ConfigValue<List<? extends String>> autoOffhandNameSubstrings;
        // public final ForgeConfigSpec.ConfigValue<List<? extends String>> autoOffhandIdSubstrings;

        ServerConfig(ForgeConfigSpec.Builder builder) { // Renamed constructor
            builder.comment("Server-only configuration settings for Auto Offhand (Global Fallbacks)") // Updated comment
                   .push("general");

            // Renamed variable and updated comment
            globalAutoOffhandItems = builder
                    .comment("GLOBAL FALLBACK list: Item registry names (e.g., 'minecraft:totem_of_undying') or NBT strings. Used for players without the mod OR if per-player config doesn't match.")
                    .translation("config.autooffhand.globalAutoOffhandItems") // Updated translation key
                    .defineList("globalAutoOffhandItems", // Updated name
                            Arrays.asList("minecraft:totem_of_undying", "minecraft:shield"), // Default values remain
                            // Validator: Accepts registry names or NBT strings
                            (obj) -> { // Validator remains the same
                                if (!(obj instanceof String)) return false;
                                String str = (String) obj;
                                if (str.startsWith("{")) {
                                    // Check if it's potentially valid NBT
                                    try {
                                        TagParser.parseTag(str);
                                        return true;
                                    } catch (CommandSyntaxException e) {
                                        // Invalid NBT syntax
                                        // Use the ModConfig logger
                                        LOGGER.warn("Invalid NBT string in config: {}", str, e);
                                        return false;
                                    }
                                } else {
                                    // Check if it's a valid item ResourceLocation
                                    ResourceLocation rl = ResourceLocation.tryParse(str);
                                    return rl != null && ForgeRegistries.ITEMS.containsKey(rl);
                                }
                            }
                    );

            // Removing substring lists for now to simplify capability implementation
            /*
            autoOffhandNameSubstrings = builder
                    .comment("A list of substrings. If an item's display name contains any of these (case-insensitive), it will be moved to the offhand.")
                    .translation("config.autooffhand.autoOffhandNameSubstrings")
                    .defineList("autoOffhandNameSubstrings",
                            Arrays.asList(), // Default empty list
                            (obj) -> obj instanceof String
                    );

            autoOffhandIdSubstrings = builder
                    .comment("A list of substrings. If an item's registry ID (e.g., 'minecraft:iron_sword') contains any of these (case-insensitive), it will be moved to the offhand.")
                    .translation("config.autooffhand.autoOffhandIdSubstrings")
                    .defineList("autoOffhandIdSubstrings",
                            Arrays.asList(), // Default empty list
                            (obj) -> obj instanceof String
                    );
            */

            builder.pop();
        }

        // Removed helper methods for substrings as the config values are removed for now
    }
}
