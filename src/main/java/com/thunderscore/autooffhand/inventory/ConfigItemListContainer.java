package com.thunderscore.autooffhand.inventory;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;

// Placeholder for a ContainerType if we register one later
// import com.thunderscore.autooffhand.AutoOffhand;

public class ConfigItemListContainer extends Container {

    // Define slot layout constants
    private static final int CONFIG_SLOTS_X = 8;
    private static final int CONFIG_SLOTS_Y = 18;
    private static final int CONFIG_ROWS = 3;
    private static final int CONFIG_COLS = 9;
    public static final int CONFIG_SLOT_COUNT = CONFIG_ROWS * CONFIG_COLS; // 27 slots

    // Standard Y coordinates for player inventory with generic_54.png (imageHeight = 222)
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 140; // Correct Y for player inventory (3 rows)
    private static final int PLAYER_HOTBAR_Y = 198; // Correct Y for hotbar

    // This inventory will be managed by the Screen to display config items
    // It doesn't store real items, just visual representations.
    private final IInventory configDisplayInventory;
    private final PlayerInventory playerInventory;

    // Client-side constructor
    public ConfigItemListContainer(int windowId, PlayerInventory playerInv) {
        // Create a dummy inventory for the config display slots
        this(windowId, playerInv, new Inventory(CONFIG_SLOT_COUNT));
    }

    // Server-side constructor (if needed, though this GUI is client-only focused)
    // Or common constructor used by client
    public ConfigItemListContainer(int windowId, PlayerInventory playerInv, IInventory configDisplayInv) {
        // Placeholder for ContainerType registration if needed
        // super(AutoOffhand.CONFIG_ITEM_LIST_CONTAINER_TYPE, windowId);
        super(null, windowId); // Using null ContainerType for now

        checkContainerSize(configDisplayInv, CONFIG_SLOT_COUNT);
        this.configDisplayInventory = configDisplayInv;
        this.playerInventory = playerInv;
        configDisplayInv.startOpen(playerInv.player);

        // Add Config Display Slots (Read-only visually)
        for (int row = 0; row < CONFIG_ROWS; ++row) {
            for (int col = 0; col < CONFIG_COLS; ++col) {
                this.addSlot(new Slot(configDisplayInv, col + row * CONFIG_COLS, CONFIG_SLOTS_X + col * 18, CONFIG_SLOTS_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        // Prevent placing items into config display slots
                        return false;
                    }
                    // Optional: Override mayPickup to return false if needed,
                    // but removal is handled by screen click, not pickup.
                });
            }
        }

        // Add Player Inventory Slots
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }

        // Add Player Hotbar Slots
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, PLAYER_INV_X + col * 18, PLAYER_HOTBAR_Y));
        }
    }

    // Called when the container is closed
    @Override
    public void removed(PlayerEntity playerIn) {
        super.removed(playerIn);
        this.configDisplayInventory.stopOpen(playerIn);
    }

    @Override
    public boolean stillValid(PlayerEntity playerIn) {
        // Basic validity check, can be expanded if needed
        return this.configDisplayInventory.stillValid(playerIn);
    }

    @Override
    public ItemStack quickMoveStack(PlayerEntity playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            // Index ranges:
            // Config Display: 0 to CONFIG_SLOT_COUNT - 1 (0-26)
            // Player Inventory: CONFIG_SLOT_COUNT to CONFIG_SLOT_COUNT + 26 (27-53)
            // Player Hotbar: CONFIG_SLOT_COUNT + 27 to CONFIG_SLOT_COUNT + 35 (54-62)

            if (index < CONFIG_SLOT_COUNT) {
                // Trying to shift-click *from* config display slot. Prevent merge.
                return ItemStack.EMPTY; // Prevent moving items out via shift-click
            } else {
                // Trying to shift-click *from* player inventory/hotbar. Prevent merge into config slots.
                // Try merging into player inventory (excluding hotbar)
                if (index >= CONFIG_SLOT_COUNT && index < CONFIG_SLOT_COUNT + 27) { // From main inventory
                    if (!this.moveItemStackTo(slotStack, CONFIG_SLOT_COUNT + 27, CONFIG_SLOT_COUNT + 36, false)) { // To hotbar
                        return ItemStack.EMPTY;
                    }
                }
                // Try merging into hotbar
                else if (index >= CONFIG_SLOT_COUNT + 27 && index < CONFIG_SLOT_COUNT + 36) { // From hotbar
                    if (!this.moveItemStackTo(slotStack, CONFIG_SLOT_COUNT, CONFIG_SLOT_COUNT + 27, false)) { // To main inventory
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (slotStack.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY; // No change happened
            }

            slot.onTake(playerIn, slotStack);
        }

        return itemstack;
    }

    // Getter for the screen to access player inventory
    public PlayerInventory getPlayerInventory() {
        return playerInventory;
    }

    // Getter for the screen to access the config display inventory
    public IInventory getConfigDisplayInventory() {
        return configDisplayInventory;
    }
}
