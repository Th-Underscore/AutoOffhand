package com.thunderscore.autooffhand;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import com.thunderscore.autooffhand.gui.ConfigItemListScreen;
import com.thunderscore.autooffhand.inventory.ConfigItemListContainer;
import com.thunderscore.autooffhand.inventory.ModContainers;
import com.thunderscore.autooffhand.network.NetworkHandler;
import com.thunderscore.autooffhand.network.RequestConfigPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = AutoOffhand.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    // Add logger for this class
    private static final Logger LOGGER = LogManager.getLogger();

    // Define the keybinding
    public static final KeyBinding OPEN_CONFIG_GUI_KEY = new KeyBinding(
            "key." + AutoOffhand.MOD_ID + ".open_config_gui", // Translation key
            InputMappings.Type.KEYSYM, // Type of input (keyboard)
            GLFW.GLFW_KEY_O, // Default key (O) - Choose an appropriate default
            "key.categories." + AutoOffhand.MOD_ID // Translation key for category
    );

    // Called during FMLClientSetupEvent
    public static void init(final FMLClientSetupEvent event) {
        // ContainerType Deferred Register is registered in the main mod constructor

        // Register the keybinding
        ClientRegistry.registerKeyBinding(OPEN_CONFIG_GUI_KEY);

        // Register the ScreenFactory for our ContainerType
        // Assumes ModContainers.CONFIG_ITEM_LIST_CONTAINER is registered elsewhere
        ScreenManager.register(
            ModContainers.CONFIG_ITEM_LIST_CONTAINER.get(), // Get the registered ContainerType
            // Explicit lambda matching the IScreenFactory signature
            (ConfigItemListContainer container, PlayerInventory inv, ITextComponent title) -> new ConfigItemListScreen(container, inv, title)
        );

        // Register an INSTANCE of this class to the Forge event bus for non-static methods
        MinecraftForge.EVENT_BUS.register(new ClientSetup());

        LOGGER.debug("Client setup complete.");
    }

    /**
     * Opens the Config Item List screen.
     * This is called from the OpenConfigGuiPacket handler or the keybind.
     * Needs to run on the client thread.
     * @param initialConfigEntries The list of entries sent by the server.
     * @param isServerConfig True if the entries are for the server config, false for player config.
      */
    public static void openConfigScreen(List<String> initialConfigEntries, boolean isServerConfig) { // Add isServerConfig parameter
        // Ensure we have a player and are on the client
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            // Open the GUI
            mc.setScreen(
                new ConfigItemListScreen(
                    // Create a new container instance on the client
                    // Note: The windowId (0) might be okay for client-opened screens,
                    // but needs verification if server interaction causes issues.
                    new ConfigItemListContainer(0, mc.player.inventory),
                    mc.player.inventory,
                    // Dynamically change title based on config type (Explicit cast added)
                    (ITextComponent) new TranslationTextComponent(isServerConfig ? "gui." + AutoOffhand.MOD_ID + ".config_item_list.title_server" : "gui." + AutoOffhand.MOD_ID + ".config_item_list.title_player"),
                    initialConfigEntries, // Pass the list
                    isServerConfig // Pass the flag to the screen constructor
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
