package com.thunderscore.autooffhand.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.thunderscore.autooffhand.ClientSetup;

public class ConfigItemListContainer extends AbstractContainerMenu {

    // Define slot layout constants
    private static final int CONFIG_SLOTS_X = 8;
    private static final int CONFIG_SLOTS_Y = 18;
    private static final int CONFIG_ROWS = 6;
    private static final int CONFIG_COLS = 9;
    public static final int CONFIG_SLOT_COUNT = CONFIG_ROWS * CONFIG_COLS;

    // Standard Y coordinates for player inventory with generic_54.png (imageHeight = 222)
    private static final int PLAYER_INV_X = 8;
    private static final int PLAYER_INV_Y = 140; // Correct Y for player inventory (3 rows)
    private static final int PLAYER_HOTBAR_Y = 198; // Correct Y for hotbar

    // This inventory will be managed by the Screen to display config items
    private final Container configDisplayInventory;
    private final Inventory playerInventory;

    // Client-side constructor
    public ConfigItemListContainer(int windowId, Inventory playerInv) {
        // Create a dummy inventory for the config display slots
        this(windowId, playerInv, new SimpleContainer(CONFIG_SLOT_COUNT));
    }

    // Common constructor used by client and potentially server if needed later
    public ConfigItemListContainer(int windowId, Inventory playerInv, Container configDisplayInv) {
        // Reference the MenuType RegistryObject from ClientSetup
        super(ClientSetup.CONFIG_ITEM_LIST_CONTAINER.get(), windowId);

        checkContainerSize(configDisplayInv, CONFIG_SLOT_COUNT); // Check custom inventory size
        this.configDisplayInventory = configDisplayInv;
        this.playerInventory = playerInv;
        configDisplayInv.startOpen(playerInv.player); // Use Container's method

        // Add Config Display Slots (Read-only visually)
        for (int row = 0; row < CONFIG_ROWS; ++row) {
            for (int col = 0; col < CONFIG_COLS; ++col) {
                this.addSlot(new Slot(configDisplayInv, col + row * CONFIG_COLS, CONFIG_SLOTS_X + col * 18, CONFIG_SLOTS_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        // Prevent placing items into config display slots
                        return false;
                    }
                    // Prevent taking items out directly
                    @Override
                    public boolean mayPickup(Player pPlayer) {
                        return false;
                    }
                    // Allow screen interaction (like deletion)
                    @Override
                    public boolean isActive() {
                        return true; // Or based on screen logic if needed
                    }
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
    public void removed(Player playerIn) {
        super.removed(playerIn);
        this.configDisplayInventory.stopOpen(playerIn);
    }

    @Override
    public boolean stillValid(Player playerIn) {
        // Basic validity check
        return this.configDisplayInventory.stillValid(playerIn);
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            // Index ranges:
            // Config Display: 0 to CONFIG_SLOT_COUNT - 1 (0-53)
            // Player Inventory: CONFIG_SLOT_COUNT to CONFIG_SLOT_COUNT + 26 (54-80)
            // Player Hotbar: CONFIG_SLOT_COUNT + 27 to CONFIG_SLOT_COUNT + 35 (81-89)
            // Total slots = 54 + 27 + 9 = 90

            boolean merged = false;

            if (index < CONFIG_SLOT_COUNT) { // Index is 0-53 (Config Display)
                // Trying to shift-click *from* config display slot. Prevent merge.
                return ItemStack.EMPTY;

            } else if (index < CONFIG_SLOT_COUNT + 27) { // Index is 54-80 (Player Main Inventory)
                // Try merging into hotbar first.
                merged = this.moveItemStackTo(slotStack, CONFIG_SLOT_COUNT + 27, CONFIG_SLOT_COUNT + 36, false); // To hotbar (81-89)

            } else if (index < CONFIG_SLOT_COUNT + 36) { // Index is 81-89 (Player Hotbar)
                // Try merging into main inventory first.
                merged = this.moveItemStackTo(slotStack, CONFIG_SLOT_COUNT, CONFIG_SLOT_COUNT + 27, false); // To main inventory (54-80)
            }

            if (!merged) {
                 return ItemStack.EMPTY;
            }

            // Successful merge attempt
            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (slotStack.getCount() == itemstack.getCount()) {
                // If counts match, nothing effectively moved for this return value.
                return ItemStack.EMPTY;
            }

            slot.onTake(playerIn, slotStack); // slotStack here is the original stack *before* merging
        }

        return itemstack; // Original copied stack
    }

    public Inventory getPlayerInventory() {
        return playerInventory;
    }

    public Container getConfigDisplayInventory() {
        return configDisplayInventory;
    }
}
