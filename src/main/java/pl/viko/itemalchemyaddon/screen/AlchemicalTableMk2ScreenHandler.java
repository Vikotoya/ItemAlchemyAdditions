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

public class AlchemicalTableMk2ScreenHandler extends ScreenHandler {

    // Nasz niestandardowy ekwipunek: 20 slotów bufora + 1 slot przetwarzania = 19
    private final Inventory inventory;
    private final PlayerEntity player;

    // --- POPRAWKA: Przywracamy prosty konstruktor dla rejestracji ---
    public AlchemicalTableMk2ScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(19));
    }

    public AlchemicalTableMk2ScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(ModScreenHandlers.ALCHEMICAL_TABLE_MK2_SCREEN_HANDLER, syncId);
        checkSize(inventory, 19);
        this.inventory = inventory;
        this.player = playerInventory.player;
        inventory.onOpen(playerInventory.player);

        int i;
        int j;

        // --- NASZE NIESTANDARDOWE SLOTY ---
        // Sloty bufora (lewa strona, 3x6)
        for (i = 0; i < 6; ++i) {
            for (j = 0; j < 3; ++j) {
                // Współrzędne (x=12, y=20) dopasowane do Twojego projektu
                this.addSlot(new Slot(inventory, j + i * 3, 8 + j * 18, 36 + i * 18));
            }
        }

        // Slot przetwarzania (z ogniem)
        // Współrzędne (x=12, y=116) dopasowane do Twojego projektu
        this.addSlot(new Slot(inventory, 18, 26, 144));


        // --- EKWIPUNEK GRACZA ---
        // Główny ekwipunek (3x9)
        // Współrzędne (x=62, y=196) dopasowane do Twojego projektu
        for (i = 0; i < 3; ++i) {
            for (j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 62 + j * 18, 194 + i * 18));
            }
        }
        // Pasek szybkiego dostępu (1x9)
        // Współrzędne (x=62, y=254) dopasowane do Twojego projektu
        for (i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 62 + i * 18, 252));
        }

    }

    // --- NOWA METODA Z LOGIKĄ SPALANIA ---
    public void tick() {
        if (player.getWorld().isClient()) return; // Działamy tylko na serwerze

        Player mcpPlayer = new Player(this.player);
        Slot burningSlot = this.slots.get(18);

        // 1. Logika spalania
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
                burningSlot.markDirty(); // Zaznaczamy slot jako "brudny", aby zsynchronizować zmiany
                EMCManager.syncS2C(mcpPlayer);
            }
        }
        // 2. Logika przenoszenia
        else {
            int minEmcSlotIndex = -1;
            long minEmcValue = Long.MAX_VALUE;

            for (int i = 0; i < 19; i++) {
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

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack itemStack2 = slot.getStack();
            itemStack = itemStack2.copy();
            if (index < 19) {
                if (!this.insertItem(itemStack2, 19, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.insertItem(itemStack2, 0, 18, false)) {
                return ItemStack.EMPTY;
            }

            if (itemStack2.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }

        return itemStack;
    }
}