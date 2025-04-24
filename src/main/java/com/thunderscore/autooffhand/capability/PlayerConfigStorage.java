package com.thunderscore.autooffhand.capability;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;

/**
 * Handles saving and loading the IPlayerConfig capability data to/from NBT.
 */
public class PlayerConfigStorage implements Capability.IStorage<IPlayerConfig> {

    @Nullable
    @Override
    public INBT writeNBT(Capability<IPlayerConfig> capability, IPlayerConfig instance, Direction side) {
        // We expect the instance to handle its own serialization via INBTSerializable
        if (instance instanceof PlayerConfig) {
            return instance.serializeNBT();
        }
        // Log error or return empty NBT if the instance type is unexpected
        // AutoOffhand.LOGGER.error("Attempted to write NBT for unexpected IPlayerConfig instance type: {}", instance.getClass().getName());
        return new CompoundNBT(); // Return empty compound to avoid crashes
    }

    @Override
    public void readNBT(Capability<IPlayerConfig> capability, IPlayerConfig instance, Direction side, INBT nbt) {
        // We expect the instance to handle its own deserialization via INBTSerializable
        if (instance instanceof PlayerConfig && nbt instanceof CompoundNBT) {
            instance.deserializeNBT((CompoundNBT) nbt);
        } else {
            // Log error if the instance type or NBT type is unexpected
            // AutoOffhand.LOGGER.error("Attempted to read NBT for unexpected IPlayerConfig instance type ({}) or NBT type ({})", instance.getClass().getName(), nbt.getClass().getName());
        }
    }
}
