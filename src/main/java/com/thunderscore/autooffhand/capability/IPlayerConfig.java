package com.thunderscore.autooffhand.capability;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * Capability interface for storing per-player AutoOffhand configuration.
 */
public interface IPlayerConfig extends INBTSerializable<CompoundTag> {

    /**
     * Gets the player's specific list of items/rules for auto offhand.
     * The returned list should be mutable if modifications are intended directly,
     * or immutable/a copy if modifications should go through setConfigList.
     *
     * @return A list of strings representing the player's config entries.
     */
    List<String> getConfigList();

    /**
     * Sets the player's specific list of items/rules for auto offhand.
     *
     * @param configList The new list of config entries for the player.
     */
    void setConfigList(List<String> configList);

    /**
     * Checks if the player is currently set to use the server's default configuration.
     *
     * @return true if using server config, false if using player-specific config.
     */
    boolean isUsingServerConfig();

    /**
     * Sets whether the player should use the server's default configuration.
     *
     * @param useServerConfig true to use server config, false to use player-specific config.
     */
    void setUseServerConfig(boolean useServerConfig);

    // We extend INBTSerializable<CompoundNBT> so we don't need separate read/write methods here.
    // The implementation will handle serializeNBT and deserializeNBT.
}
