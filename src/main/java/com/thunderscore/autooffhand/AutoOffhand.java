package com.thunderscore.autooffhand;

import com.thunderscore.autooffhand.capability.IPlayerConfig;
import com.thunderscore.autooffhand.command.ModCommands;
import com.thunderscore.autooffhand.config.ModConfig;
import com.thunderscore.autooffhand.network.NetworkHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(AutoOffhand.MOD_ID)
public class AutoOffhand {
    // Make logger public so other classes can access it
    public static final Logger LOGGER = LogManager.getLogger();
    public static final String MOD_ID = "autooffhand";

    public AutoOffhand() {
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(Type.SERVER, ModConfig.SERVER_SPEC);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.CONTAINERS.register(modEventBus);
            LOGGER.debug("Registered ClientSetup.CONTAINERS DeferredRegister on CLIENT.");
        }

        // Register setup methods
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        // Register capability registration event handler
        modEventBus.addListener(this::registerCapabilities);
        // Register the static onCommonSetup method from ForgeEventHandler for the FMLCommonSetupEvent
        modEventBus.addListener(ForgeEventHandler::onCommonSetup);

        // Register Forge event bus listeners
        MinecraftForge.EVENT_BUS.register(this); // For onRegisterCommands
        MinecraftForge.EVENT_BUS.register(ForgeEventHandler.class); // For other game events

        LOGGER.debug("AutoOffhand Mod Initialized");
    }

    // Common setup (client and server)
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.debug("AutoOffhand common setup starting.");

        // Old capability registration removed. It's handled by registerCapabilities event now.

        // Register network packets
        event.enqueueWork(NetworkHandler::register); // Network registration often needs enqueueWork
        LOGGER.debug("Scheduled network packet registration.");
    }

    // Client setup ONLY
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.debug("AutoOffhand client setup starting.");
        // Call client setup directly
        event.enqueueWork(() -> ClientSetup.init(event)); // ClientSetup might need enqueueWork too
        LOGGER.debug("Scheduled client setup initialization.");
    }

    // Event handler for RegisterCapabilitiesEvent (called on the MOD bus)
    public void registerCapabilities(final RegisterCapabilitiesEvent event) {
        event.register(IPlayerConfig.class);
        LOGGER.debug("Registered PlayerConfig capability via RegisterCapabilitiesEvent.");
    }

    // Event handler for RegisterCommandsEvent (called on the FORGE bus)
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.debug("Registering AutoOffhand commands via RegisterCommandsEvent...");
        ModCommands.register(event.getDispatcher()); // Pass the dispatcher directly
        LOGGER.debug("AutoOffhand commands registered.");
    }
}
