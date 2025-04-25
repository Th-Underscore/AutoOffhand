# AutoOffhand Minecraft Mod

## Description

AutoOffhand is a server-side Minecraft Forge mod for Minecraft 1.16.5, designed to automatically manage your offhand slot. It moves specific items directly into your offhand when you pick them up, provided the slot is empty. It also intelligently handles returning projectiles like Loyalty Tridents, placing them back into your offhand if configured.

## Features

*   **Automatic Offhand Placement:** Configured items are automatically placed into your offhand slot upon pickup if the slot is free.
*   **Returning Projectile Handling:** Automatically moves returning projectiles (e.g., Loyalty Tridents, compatible modded items like Tetra's aerodynamic modular items) back to the offhand if it's empty and the item is configured.
*   **Server & Player Configuration:**
    *   Server administrators with the mod installed on the client can define a global list of items in the server config file (`config/autooffhand-server.toml`) or via `/autooffhand serverconfig`.
    *   Players with the mod installed on the client can manage their own list of items via an in-game configuration screen.
    *   Players can choose whether to use the server's list or their personal list (persistent even after uninstalling).
*   **Configuration GUI:** Access the player-specific configuration screen using a keybind (default: **O**) to add or remove items from your personal list.
*   **Commands:** Provides commands for managing configuration (details may vary, check in-game command help).

## Configuration

1.  **Server-Side:**
    *   Use the `/autooffhand serverconfig` command as an administrator to open the server config GUI.
    *   Edit the `autooffhand-server.toml` file found in your server's `<world>/serverconfig` directory (`saves/<world>/serverconfig` for single-player) to define the global list of items. Use item registry names (e.g., `minecraft:torch`) or item NBT data for specific items.
2.  **Client-Side:**
    *   Press the **O** key (by default) in-game to open the configuration GUI.
    *   Add or remove item registry names (right-click) or specific item NBT strings (left-click) to customize your personal list.
    *   Use the `/autooffhand toggle` command to switch between using the server's global config list and your own.

## Building

This project uses Forge Gradle. Use the following commands:
*   `./gradlew build` to build the mod (into `build/libs`).
*   `./gradlew runClient` to run the Minecraft client with the mod (slow af).
*   `./gradlew runServer` to run the Minecraft server with the mod (slow af).
