package pl.viko.itemalchemyaddon.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.pitan76.itemalchemy.EMCManager;
import net.pitan76.itemalchemy.data.ModState;
import net.pitan76.itemalchemy.data.ServerState;
import net.pitan76.itemalchemy.data.TeamState;
import net.pitan76.mcpitanlib.api.entity.Player;
import net.pitan76.mcpitanlib.api.util.item.ItemUtil;

import java.util.Optional;

/**
 * Server-side screen handler for the Alchemical Table Mk2.
 *
 * <p>Layout (19 custom slots total):</p>
 * <ul>
 *   <li>Slots 0–17: 3×6 item buffer grid (left side of the GUI)</li>
 *   <li>Slot 18: the burning/processing slot</li>
 * </ul>
 *
 * <p>The {@link #tick()} method is called every server tick (from the main
 * mod initializer) for every player who has this screen open.  It implements
 * the automatic burning loop:</p>
 * <ol>
 *   <li>If the burning slot contains an item with an EMC value, that item is
 *       consumed one at a time: its EMC is added to the player's balance,
 *       and the item is registered to the team if not already known.</li>
 *   <li>When the burning slot is empty, the buffer slot containing the item
 *       with the <em>lowest</em> positive EMC value is moved into the burning
 *       slot.</li>
 * </ol>
 */
public class AlchemicalTableMk2ScreenHandler extends ScreenHandler {

    /** Total number of custom (non-player) inventory slots. */
    private static final int INVENTORY_SIZE = 19;

    /** Number of buffer slots (indices 0 through 17). */
    private static final int BUFFER_SLOT_COUNT = 18;

    /** Index of the burning / processing slot. */
    private static final int BURNING_SLOT_INDEX = 18;

    private final Inventory inventory;
    private final PlayerEntity player;

    /**
     * Client-side constructor used by the screen handler registry.
     * Creates a dummy {@link SimpleInventory} as a placeholder.
     */
    public AlchemicalTableMk2ScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(INVENTORY_SIZE));
    }

    /**
     * Server-side constructor receiving the real (item-backed) inventory.
     *
     * @param syncId          the synchronisation ID assigned by the server
     * @param playerInventory the player's inventory
     * @param inventory       the 19-slot custom inventory (buffer + burning)
     */
    public AlchemicalTableMk2ScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(ModScreenHandlers.ALCHEMICAL_TABLE_MK2_SCREEN_HANDLER, syncId);
        checkSize(inventory, INVENTORY_SIZE);
        this.inventory = inventory;
        this.player = playerInventory.player;
        inventory.onOpen(playerInventory.player);

        // Buffer slots — 3 columns × 6 rows
        for (int row = 0; row < 6; ++row) {
            for (int col = 0; col < 3; ++col) {
                this.addSlot(new Slot(inventory, col + row * 3, 8 + col * 18, 36 + row * 18));
            }
        }

        // Burning / processing slot
        this.addSlot(new Slot(inventory, 18, 26, 144));

        // Player main inventory (3×9)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 62 + col * 18, 194 + row * 18));
            }
        }

        // Player hotbar (1×9)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 62 + col * 18, 252));
        }
    }

    /**
     * Called every server tick to process the burning logic.
     *
     * <p>If the burning slot has a stack with a positive EMC value, one item is
     * consumed per tick: EMC is credited and the item is registered to the
     * player's team.  Otherwise, the buffer slot with the lowest EMC value is
     * moved into the burning slot.</p>
     */
    public void tick() {
        if (player.getWorld().isClient()) return;

        Player mcpPlayer = new Player(this.player);
        Slot burningSlot = this.slots.get(BURNING_SLOT_INDEX);

        if (burningSlot.hasStack()) {
            ItemStack stackToBurn = burningSlot.getStack();
            long emcValue = EMCManager.get(stackToBurn.getItem());

            if (emcValue > 0) {
                EMCManager.incrementEmc(mcpPlayer, emcValue);

                Optional<TeamState> teamState = ModState.getModState(player.getServer()).getTeamByPlayer(player.getUuid());
                if (teamState.isPresent()) {
                    String itemId = ItemUtil.toId(stackToBurn.getItem()).toString();
                    if (!teamState.get().registeredItems.contains(itemId)) {
                        teamState.get().registeredItems.add(itemId);
                        ServerState.of(player.getServer()).callMarkDirty();
                    }
                }

                stackToBurn.decrement(1);
                burningSlot.markDirty();
                EMCManager.syncS2C(mcpPlayer);
            }
        } else {
            // Find the buffer slot with the lowest positive EMC value
            int minEmcSlotIndex = -1;
            long minEmcValue = Long.MAX_VALUE;

            for (int i = 0; i < BUFFER_SLOT_COUNT; i++) {
                Slot bufferSlot = this.slots.get(i);
                if (bufferSlot.hasStack()) {
                    long currentEmc = EMCManager.get(bufferSlot.getStack().getItem());
                    if (currentEmc > 0 && currentEmc < minEmcValue) {
                        minEmcValue = currentEmc;
                        minEmcSlotIndex = i;
                    }
                }
            }

            if (minEmcSlotIndex != -1) {
                Slot fromSlot = this.slots.get(minEmcSlotIndex);
                burningSlot.setStack(fromSlot.getStack());
                fromSlot.setStack(ItemStack.EMPTY);
                fromSlot.markDirty();
                burningSlot.markDirty();
            }
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    /**
     * Handles shift-clicking items between the custom inventory and the player
     * inventory.
     *
     * <p>Items shift-clicked out of the custom slots (0–18) are moved into the
     * player inventory.  Items shift-clicked from the player inventory are
     * moved into the buffer slots only (0–17), never directly into the
     * burning slot.</p>
     */
    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack slotStack = slot.getStack();
            itemStack = slotStack.copy();
            if (index < INVENTORY_SIZE) {
                if (!this.insertItem(slotStack, INVENTORY_SIZE, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.insertItem(slotStack, 0, BUFFER_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }

            if (slotStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }

        return itemStack;
    }
}
