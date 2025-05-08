package com.thunderscore.autooffhand;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.thunderscore.autooffhand.capability.IPlayerConfig;
import com.thunderscore.autooffhand.capability.PlayerConfigCapability;
import com.thunderscore.autooffhand.capability.PlayerConfigProvider;
import com.thunderscore.autooffhand.config.ConfigItemUtils;
import com.thunderscore.autooffhand.config.ModConfig;
import com.thunderscore.autooffhand.network.NetworkHandler;
import com.thunderscore.autooffhand.network.SyncConfigPacket;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityLeaveWorldEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

// Dedicated top-level class for Forge event handlers
public class ForgeEventHandler {

    // Identifier for our capability provider
    private static final ResourceLocation PLAYER_CONFIG_CAP_ID = new ResourceLocation(AutoOffhand.MOD_ID, "player_config");

    // Set to store EntityTypes that should trigger the offhand check on return
    private static final Set<EntityType<?>> RETURNABLE_PROJECTILE_TYPES = new HashSet<>();
    private static final ResourceLocation TETRA_THROWN_MODULAR_ITEM_ID = new ResourceLocation("tetra", "thrown_modular_item");

    /**
     * Populates the set of returnable projectile types during common setup.
     * Includes Trident by default and adds Tetra's item if the mod is loaded.
     */
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> { // Use enqueueWork for registry access safety
            RETURNABLE_PROJECTILE_TYPES.add(EntityType.TRIDENT);
            AutoOffhand.LOGGER.debug("Added TRIDENT to returnable projectile types.");

            // Check if Tetra mod is loaded
            if (ModList.get().isLoaded("tetra")) {
                EntityType<?> tetraItemType = ForgeRegistries.ENTITIES.getValue(TETRA_THROWN_MODULAR_ITEM_ID); // Use ENTITIES registry
                if (tetraItemType != null && tetraItemType != EntityType.PIG) { // Check it's not the default fallback
                    RETURNABLE_PROJECTILE_TYPES.add(tetraItemType);
                    AutoOffhand.LOGGER.info("Tetra mod detected. Added {} to returnable projectile types.", TETRA_THROWN_MODULAR_ITEM_ID);
                } else {
                    AutoOffhand.LOGGER.warn("Tetra mod is loaded, but its entity type '{}' could not be found in the registry.", TETRA_THROWN_MODULAR_ITEM_ID);
                }
            } else {
                AutoOffhand.LOGGER.debug("Tetra mod not detected. Skipping its returnable projectile type.");
            }
        });
    }


    // --- Capability Event Handlers ---

    /**
     * Attaches the IPlayerConfig capability to Player entities.
     */
    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            PlayerConfigProvider provider = new PlayerConfigProvider();
            event.addCapability(PLAYER_CONFIG_CAP_ID, provider);
        }
    }

    /**
     * Copies capability data from the old player instance to the new one when the player respawns.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // Only copy data if the player is actually respawning (not first join)
        if (event.isWasDeath()) {
            Player originalPlayer = event.getOriginal();
            Player newPlayer = event.getPlayer();

            // Get capability from original and new player, then copy data
            originalPlayer.getCapability(PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY).ifPresent(oldCap -> {
                newPlayer.getCapability(PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY).ifPresent(newCap -> {
                    // Copy the data using NBT serialization/deserialization
                    newCap.deserializeNBT(oldCap.serializeNBT());
                    AutoOffhand.LOGGER.debug("Cloned PlayerConfig capability data for player: {}", newPlayer.getName().getString());
                });
            });
        }
    }

    /**
     * Sends the player's capability data to the client when they log in.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getPlayer();
        if (player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            LazyOptional<IPlayerConfig> capOpt = serverPlayer.getCapability(PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY);

            // Get the Connection (previously NetworkManager)
            Connection connection = serverPlayer.connection.connection;

            // Check if the client has the mod's channel registered using SimpleChannel#isRemotePresent
            boolean clientHasMod = NetworkHandler.INSTANCE.isRemotePresent(connection); // Pass Connection object

            if (clientHasMod) {
                AutoOffhand.LOGGER.debug("Player {} logged in with AutoOffhand mod installed.", serverPlayer.getName().getString());
                // Client has the mod, sync their *current* config (respecting their toggle state)
                capOpt.ifPresent(cap -> {
                    List<String> playerList = cap.getConfigList();
                    // Sync player-specific list (isServerConfig = false)
                    AutoOffhand.LOGGER.debug("Syncing player-specific config ({} entries) to client {} on login.", playerList.size(), serverPlayer.getName().getString());
                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new SyncConfigPacket(playerList, false));
                    // We don't change their useServerConfig flag here, respecting their choice
                });
            } else {
                AutoOffhand.LOGGER.debug("Player {} logged in without AutoOffhand mod installed. Forcing useServerConfig=true.", serverPlayer.getName().getString());
                // Client does NOT have the mod, force them to use server config
                capOpt.ifPresent(cap -> {
                    cap.setUseServerConfig(true);
                    // No need to sync config to a client without the mod
                });
            }
        }
    }

     /**
     * Sends the player's capability data to the client when they change dimensions.
     * This is important because capabilities might not persist automatically across dimension changes.
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getPlayer();
        if (player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            // Get capability data and send it via packet
            serverPlayer.getCapability(PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY).ifPresent(cap -> {
                List<String> playerList = cap.getConfigList(); // Get the actual list
                AutoOffhand.LOGGER.debug("Syncing PlayerConfig capability data to client {} on dimension change ({} entries).", serverPlayer.getName().getString(), playerList.size());
                // Indicate this sync is for the player config (isServerConfig = false)
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new SyncConfigPacket(playerList, false));
            });
        }
    }

    // --- Item Pickup Logic ---

    // Add HIGHEST priority to the event subscription
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerPickupItem(EntityItemPickupEvent event) {
        Player player = event.getPlayer();
        // --- Ensure this only runs on the logical server (integrated or dedicated) ---
        if (player.getLevel().isClientSide()) {
            return;
        }
        AutoOffhand.LOGGER.debug("Server-Side EntityItemPickupEvent fired for player: {}, item: {}", player.getName().getString(), event.getItem().getItem());
        ItemEntity itemEntity = event.getItem();
        ItemStack pickedUpStack = itemEntity.getItem();

        if (shouldMoveToOffhand(player, pickedUpStack)) {
            ItemStack offhandStack = player.getItemInHand(InteractionHand.OFF_HAND);

            if (offhandStack.isEmpty()) {
                player.setItemInHand(InteractionHand.OFF_HAND, pickedUpStack.copy());
                pickedUpStack.setCount(0); // Remove the item from the pickup event stack

                event.setCanceled(true);
                if (!itemEntity.isRemoved()) {
                    itemEntity.discard();
                }
                AutoOffhand.LOGGER.debug("Moved item to offhand for player: {}", player.getName().getString());
            }
            // TODO: Add logic here for if the offhand is not empty but you want to replace it (i.e., prioritize certain items)
        }
    }

    // Helper method to check if an item matches configured criteria for a specific player
    // Now accepts Player to check capabilities
    private static boolean shouldMoveToOffhand(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // --- Get Item Registry Name ---
        ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (registryName == null) {
                AutoOffhand.LOGGER.warn("Item picked up without a registry name: {}", stack);
                return false;
        }
        // ---

        // --- DEBUG LOGGING ---
        AutoOffhand.LOGGER.debug("Checking if item should move to offhand: {}", stack);
        AutoOffhand.LOGGER.debug("  Item Registry Name: {}", registryName);
        AutoOffhand.LOGGER.debug("  Item Display Name: {}", stack.getDisplayName().getString());
        AutoOffhand.LOGGER.debug("  Item NBT: {}", stack.getTag());

        // --- Determine which config list to use ---
        List<String> effectiveConfigEntries = null;
        boolean useServerList = true; // Default to server list

        LazyOptional<IPlayerConfig> playerCapOpt = player.getCapability(PlayerConfigCapability.PLAYER_CONFIG_CAPABILITY);

        if (playerCapOpt.isPresent()) {
            IPlayerConfig playerConfig = playerCapOpt.orElseThrow(() -> new IllegalStateException("Capability present but could not be retrieved"));
            if (playerConfig.isUsingServerConfig()) {
                useServerList = true;
                AutoOffhand.LOGGER.debug("  Player capability found. Player prefers server config.");
            } else {
                // Player prefers their own config, get it
                effectiveConfigEntries = playerConfig.getConfigList();
                useServerList = false;
                AutoOffhand.LOGGER.debug("  Player capability found. Using player-specific list ({} entries).", effectiveConfigEntries.size());
            }
        } else {
            // Capability not present (shouldn't happen ideally), force server config
            AutoOffhand.LOGGER.warn("  Player capability NOT found for player {}. Forcing use of global server config list.", player.getName().getString());
            useServerList = true;
        }

        if (useServerList) {
            AutoOffhand.LOGGER.debug("  Using global server config list.");
            List<? extends String> rawGlobalList = ModConfig.SERVER.globalAutoOffhandItems.get();
            effectiveConfigEntries = new ArrayList<>(rawGlobalList); // Assign server list
            AutoOffhand.LOGGER.debug("  Global Config List has {} entries.", effectiveConfigEntries.size());
        }

        // If after all checks, the effective list is null or empty, nothing can match
        if (effectiveConfigEntries == null || effectiveConfigEntries.isEmpty()) {
            AutoOffhand.LOGGER.debug("  Effective config list is empty. No match possible.");
            return false;
        }

        // --- END Config List Determination ---

        // 1. Check the determined item list (Registry Names and NBT data)
        AutoOffhand.LOGGER.debug("  Checking item against effective list...");
        for (String entry : effectiveConfigEntries) { // Use the determined list
            Object parsedEntry = ConfigItemUtils.parseConfigEntry(entry);
            AutoOffhand.LOGGER.debug("    Parsing entry '{}': Result={}", entry, parsedEntry);

            if (parsedEntry instanceof ResourceLocation) {
                // Match against registry name
                boolean match = registryName.equals(parsedEntry);
                AutoOffhand.LOGGER.debug("      Comparing registry name {} == {}: {}", registryName, parsedEntry, match);
                if (match) {
                    AutoOffhand.LOGGER.debug("  MATCH FOUND (Registry Name): {} in effective list", parsedEntry);
                    return true;
                }
            } else if (parsedEntry instanceof ItemStack) {
                // Match against specific item stack (ignoring damage and count)
                // Note: stacksMatchIgnoreDamageAndCount might need internal updates too
                boolean match = ConfigItemUtils.stacksMatchIgnoreDamageAndCount((ItemStack) parsedEntry, stack);
                AutoOffhand.LOGGER.debug("      Comparing item stack (ignore damage/count) {} == {}: {}", parsedEntry, stack, match);
                if (match) {
                    AutoOffhand.LOGGER.debug("  MATCH FOUND (ItemStack NBT): {} in effective list", parsedEntry);
                    return true;
                }
            } else {
                AutoOffhand.LOGGER.debug("      Ignoring invalid/unparsed entry: {}", entry);
            }
            // Ignore null parsedEntry (invalid config lines)
        }

        // Removed substring checks as they were removed from config

        AutoOffhand.LOGGER.debug("  No match found in effective list for item {}. Returning false.", stack);
        return false; // No criteria matched in the effective list
    }


    // --- Returning Projectile Handling ---

    /**
     * Detects when specific projectile entities (like Loyalty Tridents or Tetra items)
     * are leaving the world, potentially returning to the player.
     * Schedules a check at the end of the tick to move the item if it lands in the main inventory.
     */
    @SubscribeEvent
    public static void onProjectileReturn(EntityLeaveWorldEvent event) {
        // Only run on server
        if (event.getWorld().isClientSide() || RETURNABLE_PROJECTILE_TYPES.isEmpty()) {
            return;
        }

        Entity entity = event.getEntity();
        EntityType<?> entityType = entity.getType();

        // Check if the entity type is one we're tracking
        if (!RETURNABLE_PROJECTILE_TYPES.contains(entityType)) {
            return;
        }

        // --- Attempt to get the ItemStack ---
        ItemStack representativeStack = ItemStack.EMPTY;
        if (entity instanceof ThrownTrident) {
            // Basic handling for Trident
            ThrownTrident tridentEntity = (ThrownTrident) entity;
            representativeStack = new ItemStack(Items.TRIDENT); // Start with base
            CompoundTag tridentNBT = tridentEntity.saveWithoutId(new CompoundTag());
            if (tridentNBT.contains("Trident", 10)) { // 10 = CompoundTag Type ID
                ItemStack nbtStack = ItemStack.of(tridentNBT.getCompound("Trident"));
                if (!nbtStack.isEmpty()) {
                    representativeStack = nbtStack; // Use stack from NBT if available
                }
            }
            // Optional: Check for Loyalty enchantment if needed for specific logic, but the primary goal is just to get the item back
            // int loyaltyLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.LOYALTY, representativeStack);
            // if (loyaltyLevel <= 0) return; // Example: Only handle Loyalty tridents

        } else if (ForgeRegistries.ENTITIES.getKey(entityType) != null && ForgeRegistries.ENTITIES.getKey(entityType).equals(TETRA_THROWN_MODULAR_ITEM_ID)) {
            // Handling for Tetra's thrown_modular_item
            // We need to figure out how Tetra stores the item data.
            // Approach 1: Check if it's a Projectile and try getPickupItem() via reflection (less ideal)
            // Approach 2: Inspect NBT data
            CompoundTag entityNBT = entity.saveWithoutId(new CompoundTag());
            // AutoOffhand.LOGGER.info("NBT for Tetra entity {}: {}", entity.getStringUUID(), entityNBT.toString()); // Keep for debugging if needed, may change in the future
            // *** NBT structure confirmed from logs ***
            if (entityNBT.contains("stack", 10)) {
                ItemStack nbtStack = ItemStack.of(entityNBT.getCompound("stack"));
                if (!nbtStack.isEmpty()) {
                    representativeStack = nbtStack;
                    AutoOffhand.LOGGER.debug("Extracted ItemStack {} from Tetra entity NBT using 'stack' key.", representativeStack);
                } else {
                    AutoOffhand.LOGGER.warn("Found 'stack' NBT tag for Tetra entity, but failed to create ItemStack.");
                }
            } else {
                AutoOffhand.LOGGER.warn("Could not find expected 'stack' NBT tag for Tetra entity {}. Cannot determine item to return.", entity.getStringUUID());
                // Fallback or alternative checks could go here if needed
                // For now, we can't proceed without the item stack.
                return; // Exit if we can't get the stack
            }
        }
        // Add more 'else if' blocks here for other supported entity types if necessary

        if (representativeStack.isEmpty()) {
            AutoOffhand.LOGGER.debug("Could not determine representative ItemStack for returning entity {}. Aborting offhand check.", entity.getStringUUID());
            return;
        }

        // --- Get Owner and Schedule Check ---
        Entity owner = null;
        // Tridents store owner directly
        if (entity instanceof ThrownTrident) {
            owner = ((ThrownTrident) entity).getOwner();
        }
        // Other projectiles might use getOwner() if they extend Projectile
        else if (entity instanceof Projectile) {
            owner = ((Projectile) entity).getOwner();
        }
        // Add other owner-retrieval logic if needed for specific entities

        if (owner instanceof Player) {
            Player player = (Player) owner;
            MinecraftServer server = player.getServer();
            if (server != null && !player.isRemoved()) {
                AutoOffhand.LOGGER.debug("Projectile returning to player {}. Scheduling inventory check for item {}.", player.getName().getString(), representativeStack);
                final ItemStack finalRepresentativeStack = representativeStack.copy(); // Final copy for lambda
                // Schedule task for end of tick
                server.tell(new TickTask(server.getTickCount(), () -> {
                    // Re-fetch player instance in case something changed
                    Player taskPlayer = server.getPlayerList().getPlayer(player.getUUID());
                    if (taskPlayer != null) {
                        checkAndMoveSpecificInventoryItem(taskPlayer, finalRepresentativeStack);
                    }
                }));
            }
        }
    }

    /**
     * Checks the player's main inventory for a specific item that should be moved to an empty offhand.
     * Called by the scheduled task after a potential Loyalty trident return.
     * @param player The player whose inventory to check.
     * @param specificItemToMove The specific ItemStack (type and NBT match, count ignored) to look for.
     */
    private static void checkAndMoveSpecificInventoryItem(Player player, ItemStack specificItemToMove) {
        // Double-check player is still valid and on server
        if (player == null || player.getLevel().isClientSide() || specificItemToMove.isEmpty()) {
            return;
        }

        // Check if offhand is empty
        if (player.getItemInHand(InteractionHand.OFF_HAND).isEmpty()) {
            AutoOffhand.LOGGER.debug("Player {} offhand is empty. Scanning inventory for items to move.", player.getName().getString());
            AutoOffhand.LOGGER.debug("Player {} offhand is empty. Scanning inventory for specific item {} to move.", player.getName().getString(), specificItemToMove);
            // Iterate through main inventory slots (0-35)
            for (int i = 0; i < player.getInventory().items.size(); ++i) {
                ItemStack stackInSlot = player.getInventory().getItem(i);
                // Check if the slot is not empty, if it matches the specific item we're looking for,
                // AND if it's configured to be moved to the offhand.
                if (!stackInSlot.isEmpty() && ItemStack.isSameItemSameTags(stackInSlot, specificItemToMove) && shouldMoveToOffhand(player, stackInSlot)) {
                    AutoOffhand.LOGGER.debug("Found specific item in inventory slot {} for player {}. Moving to offhand.", i, player.getName().getString());
                    player.setItemInHand(InteractionHand.OFF_HAND, stackInSlot.copy()); // Move a copy
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                    return;
                }
            }
            AutoOffhand.LOGGER.debug("Finished scanning inventory for player {}. No suitable items found to move.", player.getName().getString());
        } else {
            AutoOffhand.LOGGER.debug("Player {} offhand is not empty. Skipping inventory scan.", player.getName().getString());
        }
    }
}
