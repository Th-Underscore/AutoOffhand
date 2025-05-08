package com.thunderscore.autooffhand.inventory;

import com.thunderscore.autooffhand.AutoOffhand;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.fml.common.Mod;

// This class is now primarily a placeholder or for future container types if needed.
// The registration is moved to ClientSetup for client-only registration.
@Mod.EventBusSubscriber(modid = AutoOffhand.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModContainers {
    public static MenuType<ConfigItemListContainer> CONFIG_ITEM_LIST_CONTAINER = null;
}
