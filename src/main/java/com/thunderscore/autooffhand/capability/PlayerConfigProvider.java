package com.thunderscore.autooffhand.capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Provides the IPlayerConfig capability instance for a player entity.
 * Handles attaching, retrieving, and serializing the capability.
 */
public class PlayerConfigProvider implements ICapabilitySerializable<CompoundTag> {

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
    public CompoundTag serializeNBT() {
        // Delegate serialization directly to the capability instance
        // The null check for the capability itself isn't needed here as the provider wouldn't exist without it
        // The PlayerConfig instance handles its own serialization.
        return playerConfig.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        // Delegate deserialization directly to the capability instance
        // The null check for the capability itself isn't needed here
        playerConfig.deserializeNBT(nbt);
    }

    /**
     * Invalidates the LazyOptional when the capability provider is removed.
     * This is important for preventing memory leaks.
     */
    public void invalidate() {
        optionalData.invalidate();
    }
}
