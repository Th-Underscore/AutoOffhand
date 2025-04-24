package com.thunderscore.autooffhand.inventory;

import com.thunderscore.autooffhand.AutoOffhand;

import net.minecraft.inventory.container.ContainerType;
import net.minecraftforge.common.extensions.IForgeContainerType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = AutoOffhand.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModContainers {

    // Deferred Register for ContainerTypes
    public static final DeferredRegister<ContainerType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.CONTAINERS, AutoOffhand.MOD_ID);

    // Register the ContainerType for ConfigItemListContainer
    public static final RegistryObject<ContainerType<ConfigItemListContainer>> CONFIG_ITEM_LIST_CONTAINER = CONTAINERS.register(
            "config_item_list_container", // Registry name
            () -> IForgeContainerType.create((windowId, inv, data) -> {
                // Client-side constructor for the container
                // Data buffer is not used here, but could be for server->client info
                return new ConfigItemListContainer(windowId, inv);
            })
    );

    // We don't need a specific @SubscribeEvent for RegistryEvent.Register<ContainerType<?>>
    // because DeferredRegister handles it automatically when its register method is called
    // on the Mod event bus.
}
