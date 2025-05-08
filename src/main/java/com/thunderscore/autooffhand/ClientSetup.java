package com.thunderscore.autooffhand;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.thunderscore.autooffhand.gui.ConfigItemListScreen;
import com.thunderscore.autooffhand.inventory.ConfigItemListContainer;
import com.thunderscore.autooffhand.network.NetworkHandler;
import com.thunderscore.autooffhand.network.RequestConfigPacket;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = AutoOffhand.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final DeferredRegister<MenuType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.CONTAINERS, AutoOffhand.MOD_ID);

    public static final RegistryObject<MenuType<ConfigItemListContainer>> CONFIG_ITEM_LIST_CONTAINER = CONTAINERS.register(
            "config_item_list_container", // Registry name must match the one expected by packets/screens
            () -> IForgeMenuType.create((windowId, inv, data) -> new ConfigItemListContainer(windowId, inv))
    );

    public static final KeyMapping OPEN_CONFIG_GUI_KEY = new KeyMapping(
            "key." + AutoOffhand.MOD_ID + ".open_config_gui", // Translation key
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O, // Default key (O)
            "key.categories." + AutoOffhand.MOD_ID // Translation key for category
    );

    public static void init(final FMLClientSetupEvent event) {
        // Register an INSTANCE of this class to the Forge event bus for non-static methods like onKeyInput
        MinecraftForge.EVENT_BUS.register(new ClientSetup());

        // Use enqueueWork for thread-safe operations like screen registration
        event.enqueueWork(() -> {
            // Register Menu Screen
            MenuScreens.register(
                CONFIG_ITEM_LIST_CONTAINER.get(),
                // Use a lambda to explicitly call the constructor matching (container, inv, title)
                (ConfigItemListContainer container, Inventory inv, Component title) -> new ConfigItemListScreen(container, inv, title)
            );
            LOGGER.debug("Registered menu screens.");
        });

        LOGGER.debug("Client setup init phase complete.");
    }

    /**
     * Opens the Config Item List screen.
     * This is called from the OpenConfigGuiPacket handler or the keybind.
     * Needs to run on the client thread.
     * @param initialConfigEntries The list of entries sent by the server.
     * @param isServerConfig True if the entries are for the server config, false for player config.
      */
    public static void openConfigScreen(List<String> initialConfigEntries, boolean isServerConfig) {
        // Ensure we have a player and are on the client
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            // Open the GUI
            mc.setScreen(
                new ConfigItemListScreen(
                    // Create a new container instance on the client
                    // Note: The windowId (0) might be okay for client-opened screens,
                    // but needs verification if server interaction causes issues.
                    new ConfigItemListContainer(0, mc.player.getInventory()),
                    mc.player.getInventory(),
                    // Dynamically change title based on config type
                    new TranslatableComponent(isServerConfig ? "gui." + AutoOffhand.MOD_ID + ".config_item_list.title_server" : "gui." + AutoOffhand.MOD_ID + ".config_item_list.title_player"), // Reverted to new TranslatableComponent
                    initialConfigEntries,
                    isServerConfig
                )
            );
            LOGGER.debug("Opened config screen (isServerConfig={}). Initial entries provided: {}", isServerConfig, initialConfigEntries != null);
        } else {
            LOGGER.warn("Attempted to open config screen, but player was null!");
        }
    }

    // Listen for keyboard input events (non-static method)
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        // Check if the key was pressed, the player exists, and no other screen is open
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null && OPEN_CONFIG_GUI_KEY.consumeClick()) {
            // Send a packet to the server requesting the player's config data
            LOGGER.debug("Config keybind pressed. Sending RequestConfigPacket to server...");
            NetworkHandler.INSTANCE.sendToServer(new RequestConfigPacket());
            // The server will respond with an OpenConfigGuiPacket containing the player's data
        }
    }
}
