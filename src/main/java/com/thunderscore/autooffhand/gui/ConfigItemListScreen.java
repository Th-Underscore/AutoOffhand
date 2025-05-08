package com.thunderscore.autooffhand.gui;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.thunderscore.autooffhand.config.ConfigItemUtils;
import com.thunderscore.autooffhand.inventory.ConfigItemListContainer;
import com.thunderscore.autooffhand.network.NetworkHandler;
import com.thunderscore.autooffhand.network.UpdateConfigPacket;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

public class ConfigItemListScreen extends AbstractContainerScreen<ConfigItemListContainer> {

    private static final Logger LOGGER = LogManager.getLogger();
    // Standard 176x166 chest texture
    private static final ResourceLocation CONTAINER_BACKGROUND = new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    private List<String> currentConfigEntries;

    // Pagination
    private int currentPage = 0;
    private int totalPages = 0;
    private Button nextPageButton;
    private Button prevPageButton;
    private EditBox searchBox;
    @Nullable private final List<String> initialConfigEntries;
    private final boolean isServerConfig;

    // Constructor for keybind/default opening (assumes player config)
    public ConfigItemListScreen(ConfigItemListContainer container, Inventory playerInv, Component title) { // Updated types
        this(container, playerInv, title, null, false);
        // Note: The actual player config list will be requested via RequestConfigPacket by the keybind handler
    }

    // Main constructor accepting the list and the flag
    public ConfigItemListScreen(ConfigItemListContainer container, Inventory playerInv, Component title, @Nullable List<String> initialConfigEntries, boolean isServerConfig) { // Updated types
        super(container, playerInv, title);
        this.initialConfigEntries = initialConfigEntries;
        this.isServerConfig = isServerConfig;
        // Use dimensions for generic_54.png (6 rows + player inv)
        this.imageWidth = 176;
        this.imageHeight = 222;
        // Adjust player inventory label position based on new height
        this.inventoryLabelY = this.imageHeight - 94;

        // DO NOT load config here, wait for init() after buttons are created
    }

    @Override
    protected void init() {
        super.init();
        // Ensure keyboard focus repeats are enabled for the text box
        this.minecraft.keyboardHandler.setSendRepeatsToGui(true);

        // --- Title is at this.titleLabelX (8), this.titleLabelY (6) ---

        // --- Search Box and Add Button ---
        // Position horizontally next to the title
        int searchBoxX = this.leftPos + this.titleLabelX + this.font.width(this.title) + 10; // X pos after title + padding // title is Component now
        int searchBoxY = this.topPos + this.titleLabelY - 1; // Align Y with title baseline
        int searchBoxWidth = 80; // Adjust width as needed

        this.searchBox = new EditBox(this.font, searchBoxX, searchBoxY, searchBoxWidth, this.font.lineHeight + 3, new TranslatableComponent("gui.autooffhand.search_box_narrate"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setBordered(true);
        this.searchBox.setVisible(true);
        this.searchBox.setTextColor(16777215);
        this.addRenderableWidget(this.searchBox);

        int addButtonWidth = 30;
        int addButtonX = searchBoxX + searchBoxWidth + 4;
        this.addRenderableWidget(new Button(addButtonX, searchBoxY, addButtonWidth, this.font.lineHeight + 3, new TranslatableComponent("gui.autooffhand.add_button"), (button) -> {
            LOGGER.debug("Add Text button pressed!");
            addTextEntry(this.searchBox.getValue());
        }));


        // --- Pagination Buttons ---
        int pageButtonY = this.topPos + this.titleLabelY - 1;
        int pageButtonWidth = 20;
        int pageButtonXOffset = this.leftPos + this.imageWidth - pageButtonWidth - 5; // Right align buttons

        // Ensure Add button doesn't overlap pagination buttons
        if (addButtonX + addButtonWidth >= pageButtonXOffset - pageButtonWidth - 4) {
            // Maybe reduce search box width or move pagination down slightly?
            // Adjust widths above if needed.
            LOGGER.warn("Add/Search widgets might overlap pagination buttons. Adjust layout if necessary.");
        }

        this.prevPageButton = this.addRenderableWidget(new Button(pageButtonXOffset - pageButtonWidth - 2, pageButtonY, pageButtonWidth, this.font.lineHeight + 3, new TextComponent("<"), (button) -> {
            if (currentPage > 0) {
                currentPage--;
                updateDisplayInventory();
            }
        }));

        this.nextPageButton = this.addRenderableWidget(new Button(pageButtonXOffset, pageButtonY, pageButtonWidth, this.font.lineHeight + 3, new TextComponent(">"), (button) -> {
            if (currentPage < totalPages - 1) {
                currentPage++;
                updateDisplayInventory();
            }
        }));

        loadConfigEntries();
    }

    /**
     * Refreshes the screen's displayed items based on a list received from the server.
     * Called by the SyncConfigPacket handler.
     * @param syncedEntries The list of entries received from the server.
     */
    public void refreshEntriesFromList(List<String> syncedEntries) {
        LOGGER.debug("Refreshing GUI from synced config list ({} entries).", syncedEntries.size());
        this.currentConfigEntries = new ArrayList<>(syncedEntries); // Update local cache
        this.calculatePagination(); // Recalculate pages
        this.updateDisplayInventory(); // Refresh the displayed slots
    }

    // Method to handle adding entries based on search box text
    private void addTextEntry(String textToAdd) {
        if (textToAdd == null || textToAdd.trim().isEmpty()) {
            LOGGER.debug("Cannot add empty text entry.");
            // Optionally provide feedback to the player?
            return;
        }
        String trimmedText = textToAdd.trim();
        String entryToAdd = null;
        boolean entryFound = false;

        if (trimmedText.startsWith("n:")) {
            entryToAdd = trimmedText.substring(2).trim(); // Get text after "n:"
            if (entryToAdd.isEmpty()) {
                LOGGER.debug("Cannot add empty name substring entry.");
                return; // Don't add if only "n:" was entered
            }
            LOGGER.debug("Attempting to add name substring entry: {}", entryToAdd);
            // Note: Name substring logic is handled by ConfigItemUtils.parseConfigEntry,
            // we just need to add the raw string here. Validation happens on use.

        } else if (trimmedText.startsWith("r:")) {
            entryToAdd = trimmedText.substring(2).trim(); // Get text after "r:"
            if (entryToAdd.isEmpty()) {
                LOGGER.debug("Cannot add empty resource location entry.");
                return; // Don't add if only "r:" was entered
            }
            // Basic validation: check if it looks like a ResourceLocation string
            if (!ResourceLocation.isValidResourceLocation(entryToAdd)) {
                LOGGER.warn("Invalid ResourceLocation format provided: {}", entryToAdd);
                // Optionally provide feedback to player
                return;
            }
            LOGGER.debug("Attempting to add resource location entry: {}", entryToAdd);

        } else {
            // Search registry IDs for substring match
            LOGGER.debug("Searching item registry for substring: {}", trimmedText);
            for (ResourceLocation key : ForgeRegistries.ITEMS.getKeys()) {
                String keyString = key.toString();
                if (keyString.contains(trimmedText)) {
                    entryToAdd = keyString;
                    entryFound = true;
                    LOGGER.debug("Found matching registry ID: {}", entryToAdd);
                    break;
                }
            }
            if (!entryFound) {
                LOGGER.debug("No matching item registry ID found for substring: {}", trimmedText);
                // Optionally provide feedback to player
                return;
            }
        }

        // Add the determined entry if it's valid and not already present
        if (entryToAdd != null && !entryToAdd.isEmpty()) {
            if (!this.currentConfigEntries.contains(entryToAdd)) {
                LOGGER.debug("Adding entry to config: {}", entryToAdd);
                this.currentConfigEntries.add(entryToAdd);
                saveConfigEntries();
                this.searchBox.setValue(""); // Clear search box on success
            } else {
                LOGGER.debug("Entry '{}' is already in the config.", entryToAdd);
                // Optionally provide feedback
            }
        } else {
            LOGGER.warn("addTextEntry reached end without a valid entryToAdd for input: {}", textToAdd);
        }
    }


    private void loadConfigEntries() {
        LOGGER.debug("Attempting to load config entries (isServerConfig={})...", this.isServerConfig);
        List<String> configListToUse;

        if (this.initialConfigEntries != null) {
            LOGGER.debug("Using initial entries provided by packet ({} entries).", this.initialConfigEntries.size());
            configListToUse = new ArrayList<>(this.initialConfigEntries); // Use a mutable copy
        } else {
            LOGGER.debug("No initial entries provided (likely opened via keybind, waiting for server response). Starting with empty list.");
            configListToUse = new ArrayList<>();
        }

        // Use the determined list (either from packet or temporary empty list)
        this.currentConfigEntries = configListToUse;
        calculatePagination();
        updateDisplayInventory();
    }

    private void saveConfigEntries() {
        // --- MODIFIED: Send packet to server with the isServerConfig flag ---
        LOGGER.debug("Sending updated config list to server (isServerConfig={}, {} entries)...", this.isServerConfig, this.currentConfigEntries.size());
        // Pass the flag to the UpdateConfigPacket constructor
        NetworkHandler.INSTANCE.sendToServer(new UpdateConfigPacket(this.currentConfigEntries, this.isServerConfig));

        calculatePagination(); // Recalculate pagination based on local changes
        updateDisplayInventory(); // Update display based on local changes
    }

    private void calculatePagination() {
        this.totalPages = (int) Math.ceil((double) currentConfigEntries.size() / ConfigItemListContainer.CONFIG_SLOT_COUNT);
        if (this.totalPages == 0) this.totalPages = 1;
        // Clamp current page
        this.currentPage = Math.max(0, Math.min(this.currentPage, this.totalPages - 1));
    }

    private void updatePaginationButtons() {
        this.prevPageButton.active = this.currentPage > 0;
        this.nextPageButton.active = this.currentPage < this.totalPages - 1;
    }

    // Updates the visual inventory (linked to container slots) based on the current page and config entries
    private void updateDisplayInventory() {
        // Get the inventory linked to the container's display slots
        Container containerDisplayInv = this.getMenu().getConfigDisplayInventory();
        containerDisplayInv.clearContent();
        LOGGER.debug("Updating display inventory for page {}. Total entries: {}. Entries on this page:", currentPage, currentConfigEntries.size());

        int startIndex = currentPage * ConfigItemListContainer.CONFIG_SLOT_COUNT;
        for (int i = 0; i < ConfigItemListContainer.CONFIG_SLOT_COUNT; ++i) {
            int configIndex = startIndex + i;
            ItemStack finalStackToSet = ItemStack.EMPTY;

            if (configIndex < currentConfigEntries.size()) {
                String entry = currentConfigEntries.get(configIndex);
                LOGGER.debug("  Slot {}: Processing entry '{}'", i, entry);
                Object parsed = ConfigItemUtils.parseConfigEntry(entry);
                ItemStack displayStack = ItemStack.EMPTY;

                if (parsed instanceof ItemStack) {
                    displayStack = ((ItemStack) parsed).copy(); // Display the specific item
                    // Ensure count is 1 for display purposes if it wasn't saved (it should be)
                    if (!displayStack.hasTag() || !displayStack.getTag().contains("Count", 99)) {
                        displayStack.setCount(1);
                    }
                    LOGGER.debug("    Parsed as ItemStack: {}", displayStack); // Log parsed stack (DEBUG level)
                } else if (parsed instanceof ResourceLocation) {
                    // Display the default item for the registry name
                    ResourceLocation rl = (ResourceLocation) parsed; // Cast for clarity
                    ItemStack defaultStack = new ItemStack(ForgeRegistries.ITEMS.getValue(rl));
                    if (!defaultStack.isEmpty()) {
                        displayStack = defaultStack;
                        // Add tooltip to indicate it matches any variant
                        displayStack.setHoverName(new TranslatableComponent("gui.autooffhand.any_variant_tooltip", defaultStack.getHoverName())
                                .withStyle(ChatFormatting.AQUA));
                        LOGGER.debug("    Parsed as ResourceLocation: {}, Displaying: {}", rl, displayStack); // Log parsed RL (DEBUG level)
                    } else {
                        // Should not happen if config validation works, but handle gracefully
                        displayStack = new ItemStack(Items.BARRIER).setHoverName(new TextComponent("Invalid ID: " + entry).withStyle(ChatFormatting.RED));
                        LOGGER.warn("    Parsed as ResourceLocation but item not found: {}", rl); // Log warning
                    }
                } else {
                    // Invalid entry, display barrier
                    displayStack = new ItemStack(Items.BARRIER).setHoverName(new TextComponent("Invalid Entry: " + entry).withStyle(ChatFormatting.RED));
                    LOGGER.warn("    Failed to parse entry: {}", entry); // Log warning
                }
                finalStackToSet = displayStack; // Assign the determined stack
            }
            // Set item in the CONTAINER'S inventory
            containerDisplayInv.setItem(i, finalStackToSet);
            // Log even if setting empty, to confirm loop runs
            LOGGER.debug("    Set slot {} to {}", i, finalStackToSet.isEmpty() ? "EMPTY" : finalStackToSet); // Log setting item (DEBUG level)
        }
        LOGGER.debug("Finished updating display inventory."); // Log end (DEBUG level)
        // Update container slots visually (important!)
        // This might require sending packets if server-side interaction was needed,
        // but for client-side display update, modifying the inventory linked to slots is often enough.
        // We might need to manually update the container's internal representation if needed.
        updatePaginationButtons(); // Update button states
    }


    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        // Tick the search box here as tick() is final
        if (this.searchBox != null) {
            this.searchBox.tick();
        }
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks); // Renders widgets added with addRenderableWidget
        // Explicitly render the search box (already handled by super.render if added via addRenderableWidget)
        // if (this.searchBox != null) {
        //     this.searchBox.render(poseStack, mouseX, mouseY, partialTicks);
        // }
        this.renderTooltip(poseStack, mouseX, mouseY); // Render tooltips for items (like items in slots)
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, CONTAINER_BACKGROUND);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        this.blit(poseStack, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        // Draw title using the provided title component
        this.font.draw(poseStack, this.title, (float)this.titleLabelX, (float)this.titleLabelY, 4210752);
        // Draw player inventory title (use field set by superclass)
        this.font.draw(poseStack, this.playerInventoryTitle, (float)this.inventoryLabelX, (float)this.inventoryLabelY, 4210752); // Fixed syntax error
        // Draw search box label (optional, could use placeholder text)
        // this.font.draw(poseStack, "Search ID:", this.leftPos + 7 , this.topPos + 8, 4210752);
        // Draw page number if multiple pages exist
        if (totalPages > 1) {
            String pageText = String.format("%d / %d", currentPage + 1, totalPages);
            int pageTextWidth = this.font.width(pageText);
            this.font.draw(poseStack, pageText, this.imageWidth - pageTextWidth - 8, (float)this.titleLabelY, 4210752);
        }
    }

    // REMOVED mouseClicked override

    // Override slotClicked to handle interactions directly
    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        // Special handling for Offhand Swap (F key) which uses SWAP type and button 40
        if (type == ClickType.SWAP && mouseButton == 40) {
             LOGGER.debug("slotClicked intercepted Offhand Swap (F key). Blocking action while config GUI is open. slot={}, button={}, type={}", slotId, mouseButton, type);
             // Do nothing, effectively blocking the swap keybind. Do NOT call super.
             return;
        }

        // We only care about simple clicks (PICKUP type) for adding/removing items via click.
        // Let other types (QUICK_MOVE, THROW, etc.) pass through to super, as they seem to work.
        if (slot == null || type != ClickType.PICKUP) {
            LOGGER.debug("slotClicked ignored or passed to super: slot={}, button={}, type={}.", slotId, mouseButton, type);
            super.slotClicked(slot, slotId, mouseButton, type);
            return;
        }

        // --- Handle PICKUP clicks for adding/removing ---
        LOGGER.debug("slotClicked handling PICKUP: slotIndex={}, mouseButton={}, container={}",
            slot.index, mouseButton, slot.container.getClass().getSimpleName());

        ItemStack clickedStack = slot.getItem(); // Use slot.getItem() which should be correct

        // Check if click is within player inventory (including hotbar)
        if (slot.container instanceof Inventory) {
            LOGGER.debug("  Click in player inventory.");
            if (!clickedStack.isEmpty()) {
                LOGGER.debug("  Clicked stack is not empty: {}", clickedStack);
                if (mouseButton == 0) { // Left Click - Add specific item NBT
                    LOGGER.debug("  Left click detected.");
                    ItemStack stackToSerialize = clickedStack.copy();
                    stackToSerialize.setCount(1);
                    if (stackToSerialize.isDamageableItem() && stackToSerialize.hasTag() && stackToSerialize.getTag().contains("Damage")) {
                        stackToSerialize.getTag().remove("Damage"); // Remove damage for matching
                    }
                    String serialized = ConfigItemUtils.serializeItemStack(stackToSerialize);
                    LOGGER.debug("  Serialized item: {}", serialized);
                    if (serialized != null && !this.currentConfigEntries.contains(serialized)) {
                        LOGGER.debug("  Adding serialized item to config: {}", serialized);
                        this.currentConfigEntries.add(serialized);
                        saveConfigEntries();
                    } else {
                        LOGGER.debug("  Serialized item is null or already in config.");
                    }
                    // DO NOT call super.slotClicked - we handled it.
                } else if (mouseButton == 1) { // Right Click - Add registry name only
                    LOGGER.debug("  Right click detected.");
                    ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(clickedStack.getItem());
                    LOGGER.debug("  Registry name: {}", registryName);
                    if (registryName != null) {
                        String nameStr = registryName.toString();
                        if (!this.currentConfigEntries.contains(nameStr)) {
                            LOGGER.debug("  Adding registry name to config: {}", nameStr);
                            this.currentConfigEntries.add(nameStr);
                            saveConfigEntries();
                        } else {
                            LOGGER.debug("  Registry name already in config.");
                        }
                    } else {
                        LOGGER.warn("  Could not get registry name for item: {}", clickedStack);
                    }
                    // DO NOT call super.slotClicked - we handled it.
                } else {
                    // Unhandled button in player inventory, maybe let default handle? Or block? Let's block for now.
                    LOGGER.debug("  Unhandled mouse button {} in player inventory slot.", mouseButton);
                }
            } else {
                 LOGGER.debug("  Clicked empty player inventory slot.");
                 // Clicking empty slot, do nothing, don't call super.
            }
        }
        // Check if click is within the config display area
        else if (slot.container == this.getMenu().getConfigDisplayInventory()) {
            LOGGER.debug("  Click in config display area.");
            int displaySlotIndex = slot.getSlotIndex();
            int configIndex = (currentPage * ConfigItemListContainer.CONFIG_SLOT_COUNT) + displaySlotIndex;
            LOGGER.debug("  Display slot index: {}, Calculated config index: {}", displaySlotIndex, configIndex);

            // Only handle left-click (mouseButton 0) for removal
            if (mouseButton == 0 && configIndex >= 0 && configIndex < this.currentConfigEntries.size()) {
                String removedEntry = this.currentConfigEntries.remove(configIndex);
                LOGGER.debug("  Removing entry at config index {}: {}", configIndex, removedEntry);
                saveConfigEntries();
            } else if (mouseButton != 0) {
                 LOGGER.debug("  Ignoring non-left click in config display area.");
            } else {
                LOGGER.debug("  Calculated config index is out of bounds or list is empty.");
            }
            // DO NOT call super.slotClicked - we handled it (or ignored it intentionally).
        }
        // Click was on some other slot type (shouldn't happen) or was PICKUP but we didn't handle the button
        else {
             LOGGER.warn("  Click in unknown slot type or unhandled button/type: container={}, slotIndex={}, button={}, type={}",
                 slot.container.getClass().getName(), slot.index, mouseButton, type);
             // Let's call super in this unexpected case, maybe it's needed for something else.
             super.slotClicked(slot, slotId, mouseButton, type);
        }
        // IMPORTANT: If we handled the PICKUP click (added/removed/ignored intentionally), we DO NOT call super.slotClicked().
        // This prevents the problematic vanilla logic from executing with the wrong container context.
    }

    // REMOVED tick() method override as it's final in superclass

    // Make sure text box gets events when screen closes
    @Override
    public void removed() {
        super.removed();
        this.minecraft.keyboardHandler.setSendRepeatsToGui(false);
    }

    // Allow text box to handle keyboard input, but ensure Escape always closes
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Prioritize Escape key (256) to close the screen
        if (keyCode == 256) { // GLFW.GLFW_KEY_ESCAPE
            // Let the default screen handling close the GUI
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        // If not Escape, let the search box try to handle other keys
        if (this.searchBox.keyPressed(keyCode, scanCode, modifiers) || this.searchBox.canConsumeInput()) {
            // Check again if it was Escape, in case the search box consumed it but shouldn't prevent closing
            if (keyCode == 256) {
                 return super.keyPressed(keyCode, scanCode, modifiers); // Still ensure close
            }
            return true; // Input handled by search box (and wasn't Escape)
        }

        // Otherwise, let the default handling occur for other keys
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
