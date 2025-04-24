package com.thunderscore.autooffhand.capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Provides the IPlayerConfig capability instance for a player entity.
 * Handles attaching, retrieving, and serializing the capability.
 */
public class PlayerConfigProvider implements ICapabilitySerializable<CompoundNBT> {

    // The actual instance of our capability data
    private final IPlayerConfig playerConfig = new PlayerConfig();
    // LazyOptional wrapper for thread safety and capability invalidation
    private final LazyOptional<IPlayerConfig> optionalData = LazyOptional.of(() -> playerConfig);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        // Return our capability instance if it matches the requested capability
        if (cap == PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY) {
            return optionalData.cast();
        }
        // Otherwise, return empty optional
        return LazyOptional.empty();
    }

    @Override
    public CompoundNBT serializeNBT() {
        // Delegate serialization to the capability instance itself
        if (PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY != null) {
            // Use the storage associated with the capability to write NBT
            return (CompoundNBT) PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY.getStorage()
                                    .writeNBT(PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY, playerConfig, null);
        }
        // Return empty NBT if capability isn't registered yet (shouldn't happen in normal use)
        return new CompoundNBT();
    }

    @Override
    public void deserializeNBT(CompoundNBT nbt) {
        // Delegate deserialization to the capability instance itself
        if (PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY != null) {
            // Use the storage associated with the capability to read NBT
            PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY.getStorage()
                                    .readNBT(PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY, playerConfig, null, nbt);
        }
    }

    /**
     * Invalidates the LazyOptional when the capability provider is removed.
     * This is important for preventing memory leaks.
     */
    public void invalidate() {
        optionalData.invalidate();
    }
}
