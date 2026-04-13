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
 * <p>The player's current EMC balance is synchronised to the client every tick
 * via a {@link PropertyDelegate} (split into two {@code int} properties to
 * represent a full {@code long}).</p>
 */
public class AlchemicalTableMk2ScreenHandler extends ScreenHandler {

    /** The two operational modes of the GUI. */
    public enum GuiMode { BURNING, UNLEARNING }

    // ── Property delegate indices ────────────────────────────────────────

    private static final int PROPERTY_MODE = 0;
    private static final int PROPERTY_LEARN_ENABLED = 1;
    private static final int PROPERTY_EMC_LOW = 2;
    private static final int PROPERTY_EMC_HIGH = 3;
    private static final int PROPERTY_COUNT = 4;

    // ── Button IDs for onButtonClick ─────────────────────────────────────

    public static final int BUTTON_TOGGLE_LEARN = 0;
    public static final int BUTTON_TOGGLE_MODE = 1;
    public static final int BUTTON_DENY_UNLEARN = 2;
    public static final int BUTTON_BURN_ALL = 3;
    public static final int BUTTON_BURN_ONE = 4;

    // ── Fields ───────────────────────────────────────────────────────────

    private final PlayerEntity player;
    private final PropertyDelegate propertyDelegate;

    // ── Constructor ──────────────────────────────────────────────────────

    public AlchemicalTableMk2ScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ModScreenHandlers.ALCHEMICAL_TABLE_MK2_SCREEN_HANDLER, syncId);
        this.player = playerInventory.player;
        this.propertyDelegate = new ArrayPropertyDelegate(PROPERTY_COUNT);
        this.addProperties(this.propertyDelegate);

        propertyDelegate.set(PROPERTY_LEARN_ENABLED, 1);

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 30 + col * 18, 170 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 30 + col * 18, 228));
        }
    }

    // ── Property accessors ───────────────────────────────────────────────

    public GuiMode getMode() {
        return propertyDelegate.get(PROPERTY_MODE) == 0 ? GuiMode.BURNING : GuiMode.UNLEARNING;
    }

    public boolean isLearnEnabled() {
        return propertyDelegate.get(PROPERTY_LEARN_ENABLED) != 0;
    }

    public void setMode(GuiMode mode) {
        propertyDelegate.set(PROPERTY_MODE, mode == GuiMode.BURNING ? 0 : 1);
    }

    /**
     * Returns the player's EMC balance as synced via PropertyDelegate.
     * Reconstructs a {@code long} from two {@code int} properties.
     */
    public long getClientEmc() {
        int low = propertyDelegate.get(PROPERTY_EMC_LOW);
        int high = propertyDelegate.get(PROPERTY_EMC_HIGH);
        return ((long) high << 32) | (low & 0xFFFFFFFFL);
    }

    // ── Tick-level EMC sync ──────────────────────────────────────────────

    /**
     * Called every server tick by the vanilla screen-handler infrastructure.
     * Updates the EMC property delegate so the client always has a fresh value.
     */
    @Override
    public void sendContentUpdates() {
        if (!player.getWorld().isClient()) {
            long emc = EMCManager.getEmcFromPlayer(new Player(player));
            propertyDelegate.set(PROPERTY_EMC_LOW, (int) emc);
            propertyDelegate.set(PROPERTY_EMC_HIGH, (int) (emc >>> 32));
        }
        super.sendContentUpdates();
    }

    // ── Slot interaction ─────────────────────────────────────────────────

    /**
     * Intercepts slot clicks to implement burning and mode-gating.
     * <p>Burn logic (EMC increment, learn registration) runs only on the
     * server; the client side still decrements the stack for prediction.</p>
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
                    if (!player.getWorld().isClient()) {
                        burnItems(stack.getItem(), burnCount, emcValue);
                    }
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

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        return ItemStack.EMPTY;
    }

    // ── Private helpers ──────────────────────────────────────────────────

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
     * Converts items to EMC.  Runs server-side only (called from
     * {@link #onButtonClick} which is server-only, and guarded in
     * {@link #onSlotClick}).
     */
    private void burnItems(Item item, int count, long emcPerItem) {
        if (player.getWorld().isClient()) return;

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
