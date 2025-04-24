package com.thunderscore.autooffhand;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.thunderscore.autooffhand.capability.IPlayerConfig;
import com.thunderscore.autooffhand.capability.PlayerConfig;
import com.thunderscore.autooffhand.capability.PlayerConfigStorage;
import com.thunderscore.autooffhand.command.ModCommands;
import com.thunderscore.autooffhand.config.ModConfig;
import com.thunderscore.autooffhand.inventory.ModContainers;
import com.thunderscore.autooffhand.network.NetworkHandler;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(AutoOffhand.MOD_ID) // Updated class name reference
public class AutoOffhand { // Updated class name

    // Make logger public so other classes can access it
    public static final Logger LOGGER = LogManager.getLogger();
    public static final String MOD_ID = "autooffhand"; // Define mod ID constant

    public AutoOffhand() {
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the config - Changed to SERVER type and SERVER_SPEC
        ModLoadingContext.get().registerConfig(Type.SERVER, ModConfig.SERVER_SPEC);

        // Register ContainerType Deferred Register ONLY on the client side
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModContainers.CONTAINERS.register(modEventBus);
            LOGGER.debug("Registered ModContainers DeferredRegister on CLIENT.");
        } else {
            LOGGER.debug("Skipped registering ModContainers DeferredRegister on SERVER.");
        }

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        // Register the static onCommonSetup method from ForgeEventHandler for the FMLCommonSetupEvent
        modEventBus.addListener(ForgeEventHandler::onCommonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.debug("AutoOffhand Mod Initialized");
    }

    // Runs during common setup (both client and server)
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.debug("AutoOffhand common setup starting.");

        // Register Capability
        event.enqueueWork(() -> { // Capabilities should be registered during enqueueWork
            CapabilityManager.INSTANCE.register(
                IPlayerConfig.class,                // Capability Interface
                new PlayerConfigStorage(),          // Storage Implementation
                PlayerConfig::new                   // Factory for default instance
            );
            LOGGER.debug("Registered PlayerConfig capability.");
        });


        // Register the new top-level event handler class (includes capability events now)
        MinecraftForge.EVENT_BUS.register(ForgeEventHandler.class);
        LOGGER.debug("AutoOffhand ForgeEventHandler class registered on Forge event bus.");

        // Register network packets
        NetworkHandler.register();
        LOGGER.debug("Registered network packets."); // Added log
    }

    // Renamed from 'setup' - Runs during client setup ONLY
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.debug("AutoOffhand client setup starting.");
        // Call client setup directly (removed enqueueWork)
        ClientSetup.init(event);
        LOGGER.debug("AutoOffhand client setup complete."); // Adjusted log message
    }

    // Event handler for FMLServerStartingEvent (called on the Forge bus, needs @SubscribeEvent)
    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        LOGGER.debug("Registering AutoOffhand commands...");
        ModCommands.register(event.getServer().getCommands().getDispatcher());
        LOGGER.debug("AutoOffhand commands registered.");
    }

    // Removed the static inner ForgeEvents class as it's now in ForgeEventHandler.java
}
