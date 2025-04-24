package com.thunderscore.autooffhand.gui;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thunderscore.autooffhand.config.ConfigItemUtils;
// Removed ModConfig import, as we no longer read config values directly here
import com.thunderscore.autooffhand.inventory.ConfigItemListContainer;
import com.thunderscore.autooffhand.network.NetworkHandler;
import com.thunderscore.autooffhand.network.UpdateConfigPacket;

import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

public class ConfigItemListScreen extends ContainerScreen<ConfigItemListContainer> {

    private static final Logger LOGGER = LogManager.getLogger();
    // Standard 176x166 chest texture
    private static final ResourceLocation CONTAINER_BACKGROUND = new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    private final PlayerInventory playerInventory;
    private List<String> currentConfigEntries; // Local cache of config list
    // Removed screen's local displayInventory, will use container's

    // Pagination
    private int currentPage = 0;
    private int totalPages = 0;
    private Button nextPageButton;
    private Button prevPageButton;
    private TextFieldWidget searchBox; // Add field for search box
    private Button addItemButton; // Add field for the add button
    @Nullable private final List<String> initialConfigEntries;
    private final boolean isServerConfig;

    // Constructor for keybind/default opening (assumes player config)
    public ConfigItemListScreen(ConfigItemListContainer container, PlayerInventory playerInv, ITextComponent title) {
        // Chain to the main constructor, passing null for entries and false for isServerConfig
        this(container, playerInv, title, null, false);
        // Note: The actual player config list will be requested via RequestConfigPacket by the keybind handler
    }

    // Main constructor accepting the list and the flag
    public ConfigItemListScreen(ConfigItemListContainer container, PlayerInventory playerInv, ITextComponent title, @Nullable List<String> initialConfigEntries, boolean isServerConfig) {
        super(container, playerInv, title);
        this.initialConfigEntries = initialConfigEntries; // Store the list
        this.isServerConfig = isServerConfig; // Store the flag
        this.playerInventory = playerInv;
        // Use dimensions for generic_54.png (6 rows + player inv)
        this.imageWidth = 176;
        this.imageHeight = 222; // Correct height for the texture
        // Adjust player inventory label position based on new height
        this.inventoryLabelY = this.imageHeight - 94;

        // No need to initialize screen's local inventory anymore
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
        int searchBoxX = this.leftPos + this.titleLabelX + this.font.width(this.title) + 10; // X pos after title + padding
        int searchBoxY = this.topPos + this.titleLabelY - 1; // Align Y with title baseline
        int searchBoxWidth = 80; // Adjust width as needed

        this.searchBox = new TextFieldWidget(this.font, searchBoxX, searchBoxY, searchBoxWidth, this.font.lineHeight + 3, new TranslationTextComponent("gui.autooffhand.search_box_narrate"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setBordered(true);
        this.searchBox.setVisible(true);
        this.searchBox.setTextColor(16777215);
        this.addWidget(this.searchBox);

        int addButtonWidth = 30; // Smaller button?
        int addButtonX = searchBoxX + searchBoxWidth + 4;
        this.addItemButton = this.addButton(new Button(addButtonX, searchBoxY, addButtonWidth, this.font.lineHeight + 3, new TranslationTextComponent("gui.autooffhand.add_button"), (button) -> { // Match height with search box
            LOGGER.debug("Add Text button pressed!");
            addTextEntry(this.searchBox.getValue());
        }));


        // --- Pagination Buttons ---
        // Keep pagination buttons at the top right, ensure they don't overlap Add button
        int pageButtonY = this.topPos + this.titleLabelY - 1; // Align Y with title/search
        int pageButtonWidth = 20;
        int pageButtonXOffset = this.leftPos + this.imageWidth - pageButtonWidth - 5; // Right align buttons

        // Ensure Add button doesn't overlap pagination buttons
        if (addButtonX + addButtonWidth >= pageButtonXOffset - pageButtonWidth - 4) {
            // If overlap, maybe reduce search box width or move pagination down slightly?
            // For now, let's assume it fits or slightly overlaps. Adjust widths above if needed.
            LOGGER.warn("Add/Search widgets might overlap pagination buttons. Adjust layout if necessary.");
        }

        this.prevPageButton = this.addButton(new Button(pageButtonXOffset - pageButtonWidth - 2, pageButtonY, pageButtonWidth, this.font.lineHeight + 3, new StringTextComponent("<"), (button) -> { // Match height
            if (currentPage > 0) {
                currentPage--;
                updateDisplayInventory();
            }
        }));

        this.nextPageButton = this.addButton(new Button(pageButtonXOffset, pageButtonY, pageButtonWidth, this.font.lineHeight + 3, new StringTextComponent(">"), (button) -> { // Match height
            if (currentPage < totalPages - 1) {
                currentPage++;
                updateDisplayInventory();
            }
        }));

        // Load config entries and update display AFTER buttons are initialized
        loadConfigEntries();
        // updatePaginationButtons() is called by loadConfigEntries -> updateDisplayInventory

        // Set initial focus to the search box maybe?
        this.setInitialFocus(this.searchBox);
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
                    break; // Use the first match
                }
            }
            if (!entryFound) {
                LOGGER.debug("No matching item registry ID found for substring: {}", trimmedText);
                // Optionally provide feedback to player
                return; // Nothing to add
            }
        }

        // Add the determined entry if it's valid and not already present
        if (entryToAdd != null && !entryToAdd.isEmpty()) {
            if (!this.currentConfigEntries.contains(entryToAdd)) {
                LOGGER.debug("Adding entry to config: {}", entryToAdd);
                this.currentConfigEntries.add(entryToAdd);
                saveConfigEntries(); // Save and refresh display
                this.searchBox.setValue(""); // Clear search box on success
            } else {
                LOGGER.debug("Entry '{}' is already in the config.", entryToAdd);
                // Optionally provide feedback
            }
        } else {
            // This case should ideally not be reached if logic above is correct
            LOGGER.warn("addTextEntry reached end without a valid entryToAdd for input: {}", textToAdd);
        }
    }


    private void loadConfigEntries() {
        LOGGER.debug("Attempting to load config entries (isServerConfig={})...", this.isServerConfig);
        List<String> configListToUse;

        if (this.initialConfigEntries != null) {
            // Use the list provided by the OpenConfigGuiPacket
            LOGGER.debug("Using initial entries provided by packet ({} entries).", this.initialConfigEntries.size());
            configListToUse = new ArrayList<>(this.initialConfigEntries); // Use a mutable copy
        } else {
            // This case should now primarily happen briefly when opened via keybind,
            // before the RequestConfigPacket -> OpenConfigGuiPacket round trip completes.
            // Initialize with an empty list temporarily.
            LOGGER.debug("No initial entries provided (likely opened via keybind, waiting for server response). Starting with empty list.");
            configListToUse = new ArrayList<>();
            // We already know isServerConfig is false if initialConfigEntries is null due to constructor chaining.
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

        // We don't save the client-side config file anymore.
        // We also don't immediately reload here, as the server now holds the truth.
        // The display *will* update visually because we modified currentConfigEntries locally
        // before calling save, and updateDisplayInventory uses that local list.
        // For a truly robust solution, the server could send back a confirmation or the updated list,
        // but this is simpler for now.
        calculatePagination(); // Recalculate pagination based on local changes
        updateDisplayInventory(); // Update display based on local changes
    }

    private void calculatePagination() {
        this.totalPages = (int) Math.ceil((double) currentConfigEntries.size() / ConfigItemListContainer.CONFIG_SLOT_COUNT);
        if (this.totalPages == 0) this.totalPages = 1; // Always at least one page
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
        IInventory containerDisplayInv = this.menu.getConfigDisplayInventory();
        containerDisplayInv.clearContent(); // Clear previous items
        // Revert logging to DEBUG level
        LOGGER.debug("Updating display inventory for page {}. Total entries: {}. Entries on this page:", currentPage, currentConfigEntries.size());

        int startIndex = currentPage * ConfigItemListContainer.CONFIG_SLOT_COUNT;
        for (int i = 0; i < ConfigItemListContainer.CONFIG_SLOT_COUNT; ++i) {
            int configIndex = startIndex + i;
            ItemStack finalStackToSet = ItemStack.EMPTY; // Default to empty

            if (configIndex < currentConfigEntries.size()) {
                String entry = currentConfigEntries.get(configIndex);
                LOGGER.debug("  Slot {}: Processing entry '{}'", i, entry); // Log entry (DEBUG level)
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
                        displayStack.setHoverName(new TranslationTextComponent("gui.autooffhand.any_variant_tooltip", defaultStack.getHoverName())
                                .withStyle(TextFormatting.AQUA));
                        LOGGER.debug("    Parsed as ResourceLocation: {}, Displaying: {}", rl, displayStack); // Log parsed RL (DEBUG level)
                    } else {
                        // Should not happen if config validation works, but handle gracefully
                        displayStack = new ItemStack(Items.BARRIER).setHoverName(new StringTextComponent("Invalid ID: " + entry).withStyle(TextFormatting.RED));
                        LOGGER.warn("    Parsed as ResourceLocation but item not found: {}", rl); // Log warning
                    }
                } else {
                    // Invalid entry, display barrier
                    displayStack = new ItemStack(Items.BARRIER).setHoverName(new StringTextComponent("Invalid Entry: " + entry).withStyle(TextFormatting.RED));
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
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        super.render(matrixStack, mouseX, mouseY, partialTicks); // Renders buttons added with addButton
        // Explicitly render the search box
        if (this.searchBox != null) {
            this.searchBox.render(matrixStack, mouseX, mouseY, partialTicks);
        }
        this.renderTooltip(matrixStack, mouseX, mouseY); // Render tooltips for items (like items in slots)
    }

    @Override
    protected void renderBg(MatrixStack matrixStack, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bind(CONTAINER_BACKGROUND);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        this.blit(matrixStack, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(MatrixStack matrixStack, int mouseX, int mouseY) {
        // Draw title using the provided title component
        this.font.draw(matrixStack, this.title.getString(), (float)this.titleLabelX, (float)this.titleLabelY, 4210752);
        // Draw player inventory title
        this.font.draw(matrixStack, this.playerInventory.getDisplayName().getString(), (float)this.inventoryLabelX, (float)this.inventoryLabelY, 4210752);
        // Draw search box label (optional, could use placeholder text)
        // this.font.draw(matrixStack, "Search ID:", this.leftPos + 7 , this.topPos + 8, 4210752);
        // Draw page number if multiple pages exist
        if (totalPages > 1) {
            String pageText = String.format("%d / %d", currentPage + 1, totalPages);
            int pageTextWidth = this.font.width(pageText);
            this.font.draw(matrixStack, pageText, this.imageWidth - pageTextWidth - 8, (float)this.titleLabelY, 4210752);
        }
    }

    // Handle mouse clicks for buttons etc.
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle button clicks FIRST using super method.
        if (super.mouseClicked(mouseX, mouseY, button)) {
            LOGGER.debug("Screen mouseClicked handled by super (button click).");
            return true; // Let super handle button clicks
        }

        // If super didn't handle it (e.g., click wasn't on a button),
        // we check if it was a click *outside* the slots that we might care about.
        // However, slot clicks are now handled in slotClicked, so we generally
        // don't need to do much here unless there are other clickable areas.
        // Returning false here allows other potential handlers (like dragging) to work.
        LOGGER.debug("Screen mouseClicked not handled by super, passing through (maybe handled by slotClicked or drag). mouseX={}, mouseY={}, button={}", mouseX, mouseY, button);
        return false;
    }

    // Override to handle clicks ON SLOTS specifically
    @Override
    protected void slotClicked(Slot slotIn, int slotId, int mouseButton, ClickType type) {
        // We handle clicks directly here for this client-side GUI.
        // Do NOT call super.slotClicked() to avoid sending default inventory packets.

        if (slotIn == null) {
            LOGGER.debug("slotClicked called with null slot.");
            return; // Click wasn't on a slot we know
        }

        // Use INFO level for entry log to ensure visibility
        LOGGER.debug("slotClicked: slotIndex={}, slotId={}, mouseButton={}, type={}, container={}",
            slotIn.index, slotId, mouseButton, type, slotIn.container.getClass().getSimpleName());

        ItemStack clickedStack = slotIn.getItem();

        // Check if click is within player inventory (including hotbar)
        // PlayerInventory slot indices for Container are usually complex, rely on container instance check.
        boolean isPlayerInv = slotIn.container instanceof PlayerInventory; // More reliable check
        LOGGER.debug("  Is click in player inventory? {}", isPlayerInv);

        if (isPlayerInv) {
            LOGGER.debug("  Click in player inventory.");
            if (!clickedStack.isEmpty()) {
                LOGGER.debug("  Clicked stack is not empty: {}", clickedStack);
                if (mouseButton == 0) { // Left Click - Add specific item NBT (excluding damage/count)
                    LOGGER.debug("  Left click detected.");
                    // Create a copy to modify for serialization
                    ItemStack stackToSerialize = clickedStack.copy();
                    // Set count to 1 as required by parsing logic and requirement
                    stackToSerialize.setCount(1);
                    // Remove Damage tag to ignore durability, if it exists
                    if (stackToSerialize.hasTag() && stackToSerialize.getTag().contains("Damage")) { // Check if Damage tag exists before removing
                        stackToSerialize.getTag().remove("Damage");
                    }
                    // Now serialize the modified stack
                    String serialized = ConfigItemUtils.serializeItemStack(stackToSerialize);
                    LOGGER.debug("  Serialized item: {}", serialized);
                    if (serialized != null && !this.currentConfigEntries.contains(serialized)) {
                        LOGGER.debug("  Adding serialized item to config: {}", serialized);
                        this.currentConfigEntries.add(serialized);
                        saveConfigEntries(); // Save and refresh display
                        // We don't return true here as slotClicked is void
                    } else {
                        LOGGER.debug("  Serialized item is null or already in config.");
                    }
                } else if (mouseButton == 1) { // Right Click - Add registry name only
                    LOGGER.debug("  Right click detected.");
                    ResourceLocation registryName = clickedStack.getItem().getRegistryName();
                    LOGGER.debug("  Registry name: {}", registryName);
                    if (registryName != null) {
                        String nameStr = registryName.toString();
                        if (!this.currentConfigEntries.contains(nameStr)) {
                            LOGGER.debug("  Adding registry name to config: {}", nameStr);
                            this.currentConfigEntries.add(nameStr);
                            saveConfigEntries(); // Save and refresh display
                            // We don't return true here as slotClicked is void
                        } else {
                            LOGGER.debug("  Registry name already in config.");
                        }
                    } else {
                        LOGGER.warn("  Could not get registry name for item: {}", clickedStack);
                    }
                }
            } else {
                LOGGER.debug("  Clicked stack is empty.");
            }
            // Even though we handled it, we don't call super.slotClicked()
            return; // Handled player inventory click
        }

        // Check if click is within the config display area (use container's inventory reference)
        // Note: We compare the container instance directly.
        boolean isConfigInv = slotIn.container == this.menu.getConfigDisplayInventory();
        LOGGER.debug("  Is click in config display inventory? {}", isConfigInv);

        if (isConfigInv) {
            LOGGER.debug("  Click in config display area.");
            // slotIn.getSlotIndex() should give the index within *its* container (0-53 for display)
            int displaySlotIndex = slotIn.getSlotIndex();
            int configIndex = (currentPage * ConfigItemListContainer.CONFIG_SLOT_COUNT) + displaySlotIndex;
            LOGGER.debug("  Display slot index: {}, Calculated config index: {}", displaySlotIndex, configIndex);

            if (configIndex >= 0 && configIndex < this.currentConfigEntries.size()) {
                // Any click (left/right) on a config item removes it
                String removedEntry = this.currentConfigEntries.remove(configIndex);
                LOGGER.debug("  Removing entry at config index {}: {}", configIndex, removedEntry);
                saveConfigEntries(); // Save and refresh display
            } else {
                LOGGER.debug("  Calculated config index is out of bounds or list is empty.");
            }
            // Even though we handled it, we don't call super.slotClicked()
            return; // Handled config inventory click
        }

        // If the click was on a slot but not player inv or config inv (shouldn't happen here)
        LOGGER.warn("  Click in unknown slot type: container={}, slotIndex={}", slotIn.container.getClass().getName(), slotIn.index);
        // Do not call super.slotClicked()
    }

    // Ensure config is reloaded if screen is reopened
    @Override
    public void tick() {
        super.tick();
        // Tick the search box (handles cursor blinking etc.)
        if (this.searchBox != null) {
            this.searchBox.tick();
        }
        // Optional: Could add a check here to see if the underlying config file changed
        // and force a reload, but manual refresh on add/remove is usually sufficient.
    }

    // Make sure text box gets events when screen closes
    @Override
    public void removed() {
        super.removed();
        this.minecraft.keyboardHandler.setSendRepeatsToGui(false);
    }

    // Allow text box to handle keyboard input
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox.keyPressed(keyCode, scanCode, modifiers) || this.searchBox.canConsumeInput()) {
            return true; // Input handled by search box
        }
        return super.keyPressed(keyCode, scanCode, modifiers); // Let container handle other keys
    }
}
