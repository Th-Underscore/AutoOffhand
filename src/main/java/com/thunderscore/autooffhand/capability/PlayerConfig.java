package com.thunderscore.autooffhand.capability;

import java.util.ArrayList;
import java.util.List;

import com.thunderscore.autooffhand.AutoOffhand;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Default implementation of the IPlayerConfig capability.
 */
public class PlayerConfig implements IPlayerConfig {

    private List<String> configList = new ArrayList<>();
    // Default to using player config if available
    private boolean useServerConfig = false;

    private static final String NBT_KEY_CONFIG_LIST = "AutoOffhandConfigList";
    private static final String NBT_KEY_USE_SERVER_CONFIG = "AutoOffhandUseServerConfig";

    @Override
    public List<String> getConfigList() {
        // Return a defensive copy to prevent external modification of the internal list
        return new ArrayList<>(this.configList);
    }

    @Override
    public void setConfigList(List<String> configList) {
        // Store a defensive copy
        this.configList = new ArrayList<>(configList != null ? configList : new ArrayList<>());
        AutoOffhand.LOGGER.debug("PlayerConfig capability list set to: {}", this.configList);
    }

    @Override
    public boolean isUsingServerConfig() {
        return this.useServerConfig;
    }

    @Override
    public void setUseServerConfig(boolean useServerConfig) {
        this.useServerConfig = useServerConfig;
        AutoOffhand.LOGGER.debug("PlayerConfig capability useServerConfig set to: {}", this.useServerConfig);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        ListTag listNBT = new ListTag();
        for (String entry : this.configList) {
            listNBT.add(StringTag.valueOf(entry));
        }
        nbt.put(NBT_KEY_CONFIG_LIST, listNBT);
        nbt.putBoolean(NBT_KEY_USE_SERVER_CONFIG, this.useServerConfig);
        AutoOffhand.LOGGER.debug("Serializing PlayerConfig capability: {}", nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        List<String> loadedList = new ArrayList<>();
        if (nbt.contains(NBT_KEY_CONFIG_LIST, Tag.TAG_LIST)) {
            ListTag listNBT = nbt.getList(NBT_KEY_CONFIG_LIST, Tag.TAG_STRING);
            for (int i = 0; i < listNBT.size(); i++) {
                loadedList.add(listNBT.getString(i));
            }
        }
        this.configList = loadedList; // Directly assign the loaded list

        // Load the boolean flag, defaulting to false if not present
        this.useServerConfig = nbt.getBoolean(NBT_KEY_USE_SERVER_CONFIG);

        AutoOffhand.LOGGER.debug("Deserialized PlayerConfig capability. Loaded list: {}, useServerConfig: {}", this.configList, this.useServerConfig);
    }
}
