package pl.viko.itemalchemyaddon.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
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
 * <p>There are <b>no custom inventory slots</b> — the burn zone is a virtual
 * icon handled entirely through {@link #onButtonClick} and
 * {@link #onSlotClick}.  Only the standard 36 player-inventory slots are
 * registered.</p>
 *
 * <p>Supports two modes synchronised via a {@link PropertyDelegate}:</p>
 * <ul>
 *   <li><b>BURNING</b> — items can be burned (from cursor on the burn zone,
 *       or shift-clicked from the player inventory) and purchased from the
 *       transmutation list.</li>
 *   <li><b>UNLEARNING</b> — all inventory interactions are blocked.
 *       The client selects items to unlearn and sends an
 *       {@link pl.viko.itemalchemyaddon.networking.packet.UnlearnItemsC2SPacket}.</li>
 * </ul>
 */
public class AlchemicalTableMk2ScreenHandler extends ScreenHandler {

    /** The two operational modes of the GUI. */
    public enum GuiMode { BURNING, UNLEARNING }

    // ── Property delegate indices ────────────────────────────────────────

    private static final int PROPERTY_MODE = 0;
    private static final int PROPERTY_LEARN_ENABLED = 1;
    private static final int PROPERTY_COUNT = 2;

    // ── Button IDs for onButtonClick ─────────────────────────────────────

    /** Toggle the learn-on-burn flag. */
    public static final int BUTTON_TOGGLE_LEARN = 0;
    /** Toggle between BURNING and UNLEARNING mode. */
    public static final int BUTTON_TOGGLE_MODE = 1;
    /** Exit UNLEARNING mode without applying changes. */
    public static final int BUTTON_DENY_UNLEARN = 2;
    /** Burn the entire cursor stack (virtual burn zone, left click). */
    public static final int BUTTON_BURN_ALL = 3;
    /** Burn a single item from the cursor stack (virtual burn zone, right click). */
    public static final int BUTTON_BURN_ONE = 4;

    // ── Fields ───────────────────────────────────────────────────────────

    private final PlayerEntity player;
    private final PropertyDelegate propertyDelegate;

    // ── Constructor ──────────────────────────────────────────────────────

    /**
     * Single constructor used by both client (factory) and server (createMenu).
     * No custom inventory is needed — the burn zone is purely virtual.
     */
    public AlchemicalTableMk2ScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ModScreenHandlers.ALCHEMICAL_TABLE_MK2_SCREEN_HANDLER, syncId);
        this.player = playerInventory.player;
        this.propertyDelegate = new ArrayPropertyDelegate(PROPERTY_COUNT);
        this.addProperties(this.propertyDelegate);

        // Learn is ON by default
        propertyDelegate.set(PROPERTY_LEARN_ENABLED, 1);

        // Player main inventory (3×9) — positions match the 216×252 texture
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 30 + col * 18, 170 + row * 18));
            }
        }

        // Player hotbar (1×9)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 30 + col * 18, 228));
        }
    }

    // ── Property accessors ───────────────────────────────────────────────

    /** Returns the current GUI mode, synchronised via PropertyDelegate. */
    public GuiMode getMode() {
        return propertyDelegate.get(PROPERTY_MODE) == 0 ? GuiMode.BURNING : GuiMode.UNLEARNING;
    }

    /** Returns whether the learn-on-burn toggle is enabled. */
    public boolean isLearnEnabled() {
        return propertyDelegate.get(PROPERTY_LEARN_ENABLED) != 0;
    }

    /** Sets the GUI mode (server-side). Propagates to the client automatically. */
    public void setMode(GuiMode mode) {
        propertyDelegate.set(PROPERTY_MODE, mode == GuiMode.BURNING ? 0 : 1);
    }

    // ── Slot interaction ─────────────────────────────────────────────────

    /**
     * Intercepts slot clicks to implement burning and mode-gating.
     *
     * <ul>
     *   <li>In UNLEARNING mode, <em>all</em> slot interactions are blocked.</li>
     *   <li>In BURNING mode, shift + left click burns the <b>entire stack</b>;
     *       shift + right click burns <b>one item</b>.  Items without EMC
     *       are ignored entirely.</li>
     * </ul>
     */
    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (getMode() == GuiMode.UNLEARNING) {
            return;
        }

        if (actionType == SlotActionType.QUICK_MOVE && slotIndex >= 0 && slotIndex < this.slots.size()) {
            Slot slot = this.slots.get(slotIndex);
            if (slot != null && slot.hasStack()) {
                ItemStack stack = slot.getStack();
                long emcValue = EMCManager.get(stack.getItem());
                if (emcValue > 0) {
                    int burnCount = (button == 0) ? stack.getCount() : 1;
                    burnItems(stack.getItem(), burnCount, emcValue);
                    stack.decrement(burnCount);
                    if (stack.isEmpty()) {
                        slot.setStack(ItemStack.EMPTY);
                    }
                    slot.markDirty();
                }
            }
            return;
        }

        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        switch (id) {
            case BUTTON_TOGGLE_LEARN -> {
                int current = propertyDelegate.get(PROPERTY_LEARN_ENABLED);
                propertyDelegate.set(PROPERTY_LEARN_ENABLED, current == 0 ? 1 : 0);
            }
            case BUTTON_TOGGLE_MODE -> {
                if (getMode() == GuiMode.BURNING) {
                    setMode(GuiMode.UNLEARNING);
                } else {
                    setMode(GuiMode.BURNING);
                }
            }
            case BUTTON_DENY_UNLEARN -> setMode(GuiMode.BURNING);
            case BUTTON_BURN_ALL -> burnCursorStack(false);
            case BUTTON_BURN_ONE -> burnCursorStack(true);
            default -> { return false; }
        }
        return true;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    /**
     * All shift-click actions are handled by {@link #onSlotClick}.
     * This method returns EMPTY to satisfy the contract and prevent loops.
     */
    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        return ItemStack.EMPTY;
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /**
     * Burns the item currently held on the player's cursor.
     *
     * @param singleOnly if {@code true}, only one item is burned;
     *                   otherwise the entire stack is consumed
     */
    private void burnCursorStack(boolean singleOnly) {
        if (getMode() != GuiMode.BURNING) return;

        ItemStack cursor = getCursorStack();
        if (cursor.isEmpty()) return;

        long emcValue = EMCManager.get(cursor.getItem());
        if (emcValue <= 0) return;

        int burnCount = singleOnly ? 1 : cursor.getCount();
        burnItems(cursor.getItem(), burnCount, emcValue);

        cursor.decrement(burnCount);
        if (cursor.isEmpty()) {
            setCursorStack(ItemStack.EMPTY);
        }
    }

    /**
     * Converts {@code count} of the given item to EMC, optionally registering
     * the item to the player's team when the learn toggle is enabled.
     */
    private void burnItems(Item item, int count, long emcPerItem) {
        Player mcpPlayer = new Player(this.player);
        EMCManager.incrementEmc(mcpPlayer, emcPerItem * count);

        if (isLearnEnabled()) {
            Optional<TeamState> teamState = ModState.getModState(player.getServer())
                    .getTeamByPlayer(player.getUuid());
            if (teamState.isPresent()) {
                String itemId = ItemUtil.toId(item).toString();
                if (!teamState.get().registeredItems.contains(itemId)) {
                    teamState.get().registeredItems.add(itemId);
                    ServerState.of(player.getServer()).callMarkDirty();
                }
            }
        }

        EMCManager.syncS2C(mcpPlayer);
    }
}
