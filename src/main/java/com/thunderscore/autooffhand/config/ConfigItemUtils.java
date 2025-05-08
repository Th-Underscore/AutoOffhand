package com.thunderscore.autooffhand.config;

import java.util.Objects;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

public final class ConfigItemUtils {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DAMAGE_NBT_KEY = "Damage";

    private ConfigItemUtils() {} // Prevent instantiation

    /**
     * Compares two ItemStacks, ignoring their count and the "Damage" NBT tag.
     * Checks if items are the same and if their NBT tags (excluding Damage) are equal.
     *
     * @param stack1 The first ItemStack.
     * @param stack2 The second ItemStack.
     * @return True if the items and their relevant NBT data match, false otherwise.
     */
    public static boolean stacksMatchIgnoreDamageAndCount(ItemStack stack1, ItemStack stack2) {
        if (stack1.isEmpty() && stack2.isEmpty()) {
            return true;
        }
        if (stack1.isEmpty() || stack2.isEmpty()) {
            return false;
        }
        if (stack1.getItem() != stack2.getItem()) {
            return false;
        }

        CompoundTag nbt1 = stack1.hasTag() ? stack1.getTag().copy() : null;
        CompoundTag nbt2 = stack2.hasTag() ? stack2.getTag().copy() : null;

        if (nbt1 != null) {
            nbt1.remove(DAMAGE_NBT_KEY);
            if (nbt1.isEmpty()) {
                nbt1 = null;
            }
        }
        if (nbt2 != null) {
            nbt2.remove(DAMAGE_NBT_KEY);
            if (nbt2.isEmpty()) {
                nbt2 = null;
            }
        }

        return Objects.equals(nbt1, nbt2);
    }

    /**
     * Serializes an ItemStack to its NBT representation as a String, removing the "Damage" tag.
     * Returns null if the stack is empty.
     *
     * @param stack The ItemStack to serialize.
     * @return The NBT string representation, or null if the stack is empty.
     */
    @Nullable
    public static String serializeItemStack(ItemStack stack) { // Updated ItemStack import
        if (stack.isEmpty()) {
            return null;
        }
        CompoundTag nbt = stack.save(new CompoundTag());
        nbt.remove(DAMAGE_NBT_KEY); // Remove damage/durability
        return nbt.toString();
    }

    /**
     * Parses a string config entry, attempting to interpret it as either an NBT ItemStack
     * or an item ResourceLocation.
     *
     * @param entry The string entry from the config.
     * @return An Object representing the parsed data (either ItemStack or ResourceLocation),
     *         or null if parsing fails or the entry is invalid.
     */
    @Nullable
    public static Object parseConfigEntry(String entry) {
        if (entry == null || entry.trim().isEmpty()) {
            return null;
        }

        if (entry.startsWith("{")) {
            try {
                CompoundTag nbt = TagParser.parseTag(entry);
                // Ensure the NBT represents a valid item stack structure
                if (nbt.contains("id", 8) && nbt.contains("Count", 99)) { // 8=String, 99=Number
                    ItemStack stack = ItemStack.of(nbt);
                    if (!stack.isEmpty()) {
                        // Remove damage tag for consistent matching if present in config string
                        if (stack.hasTag()) {
                            stack.getTag().remove(DAMAGE_NBT_KEY);
                        }
                        return stack;
                    } else {
                        LOGGER.warn("Failed to create valid ItemStack from NBT config entry: {}", entry);
                    }
                } else {
                    LOGGER.warn("NBT config entry is missing 'id' or 'Count': {}", entry);
                }
            } catch (CommandSyntaxException e) {
                LOGGER.warn("Failed to parse NBT string config entry: {}", entry, e);
            }
        } else {
            ResourceLocation rl = ResourceLocation.tryParse(entry);
            if (rl != null && ForgeRegistries.ITEMS.containsKey(rl)) {
                return rl;
            } else {
                LOGGER.warn("Invalid item ResourceLocation string in config: {}", entry);
            }
        }
        return null; // Return null if parsing failed or entry is invalid
    }
}
