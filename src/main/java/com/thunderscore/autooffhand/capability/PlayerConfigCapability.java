package com.thunderscore.autooffhand.capability;

import net.minecraftforge.common.capabilities.Capability;

/**
 * Holds the static reference to the PlayerConfig capability instance.
 * NOTE: Capability registration now happens via RegisterCapabilitiesEvent.
 * The Capability instance is typically initialized using CapabilityManager.get(new CapabilityToken<>(){}).
 */
public class PlayerConfigCapability {
    public static Capability<IPlayerConfig> PLAYER_CONFIG_CAPABILITY = null;

    // Private constructor to prevent instantiation
    private PlayerConfigCapability() {}

    // We will register the capability itself elsewhere (e.g., in common setup)
}
