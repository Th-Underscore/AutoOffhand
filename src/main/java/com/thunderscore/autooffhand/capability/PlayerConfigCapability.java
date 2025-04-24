package com.thunderscore.autooffhand.capability;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;

/**
 * Holds the static reference to the PlayerConfig capability instance.
 */
public class PlayerConfigCapability {

    // CapabilityInject automatically finds and assigns the capability instance upon registration
    @CapabilityInject(IPlayerConfig.class)
    public static Capability<IPlayerConfig> PLAYER_CONFIG_CAPABILITY = null; // Initialized by Forge

    // Private constructor to prevent instantiation
    private PlayerConfigCapability() {}

    // We will register the capability itself elsewhere (e.g., in common setup)
}
