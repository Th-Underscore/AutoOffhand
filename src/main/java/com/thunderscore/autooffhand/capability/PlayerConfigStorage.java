package com.thunderscore.autooffhand.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;

/**
 * Handles saving and loading the IPlayerConfig capability data to/from NBT.
 * NOTE: Capability.IStorage is deprecated/removed in 1.18+. This class might be removed later.
 */
public class PlayerConfigStorage {

    @Nullable
    public Tag writeNBT(Capability<IPlayerConfig> capability, IPlayerConfig instance, @Nullable Direction side) {
        // We expect the instance to handle its own serialization via INBTSerializable
        if (instance instanceof PlayerConfig) {
            return instance.serializeNBT();
        }
        // Log error or return empty NBT if the instance type is unexpected
        // AutoOffhand.LOGGER.error("Attempted to write NBT for unexpected IPlayerConfig instance type: {}", instance.getClass().getName());
        return new CompoundTag(); // Return empty compound to avoid crashes
    }

    // @Override // No longer overrides an interface method
    public void readNBT(Capability<IPlayerConfig> capability, IPlayerConfig instance, @Nullable Direction side, Tag nbt) {
        // We expect the instance to handle its own deserialization via INBTSerializable
        if (instance instanceof PlayerConfig && nbt instanceof CompoundTag) {
            instance.deserializeNBT((CompoundTag) nbt);
        } else {
            // Log error if the instance type or NBT type is unexpected
            // AutoOffhand.LOGGER.error("Attempted to read NBT for unexpected IPlayerConfig instance type ({}) or NBT type ({})", instance.getClass().getName(), nbt.getClass().getName());
        }
    }
}
