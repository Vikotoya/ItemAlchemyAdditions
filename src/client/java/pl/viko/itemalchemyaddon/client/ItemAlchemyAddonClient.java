package pl.viko.itemalchemyaddon.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import pl.viko.itemalchemyaddon.mixin.HandledScreenAccessor;
import pl.viko.itemalchemyaddon.screen.AlchemicalTableMk2Screen;
import pl.viko.itemalchemyaddon.screen.EmcEditScreen;
import pl.viko.itemalchemyaddon.screen.ModScreenHandlers;
import pl.viko.itemalchemyaddon.util.ModKeyBindings;

public class ItemAlchemyAddonClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(ModScreenHandlers.ALCHEMICAL_TABLE_MK2_SCREEN_HANDLER, AlchemicalTableMk2Screen::new);

        ModKeyBindings.registerKeyInputs();
        registerKeyEvents();
    }

    private void registerKeyEvents() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen) {
                ScreenKeyboardEvents.beforeKeyRelease(screen).register((theScreen, key, scancode, mods) -> {
                    if (ModKeyBindings.editEmcKey.matchesKey(key, scancode)) {
                        HandledScreen<?> handledScreen = (HandledScreen<?>) theScreen;
                        double mouseX = client.mouse.getX() / client.getWindow().getScaleFactor();
                        double mouseY = client.mouse.getY() / client.getWindow().getScaleFactor();

                        ItemStack stackToEdit = null;

                        // --- NOWA LOGIKA ---
                        // Jeśli to nasze specjalne GUI...
                        if (handledScreen instanceof AlchemicalTableMk2Screen) {
                            // ...używamy naszej nowej metody "szpiegującej"
                            stackToEdit = ((AlchemicalTableMk2Screen) handledScreen).getHoveredStackFromList(mouseX, mouseY);
                            if (stackToEdit == null) {
                                Slot hoveredSlot = ((HandledScreenAccessor) handledScreen).invokeGetSlotAt(mouseX, mouseY);
                                if (hoveredSlot != null && hoveredSlot.hasStack()) {
                                    stackToEdit = hoveredSlot.getStack();
                                }
                            }
                        }
                        // W każdym innym przypadku (skrzynia, ekwipunek gracza, etc.)...
                        else {
                            // ...używamy starej metody opartej na slotach
                            Slot hoveredSlot = ((HandledScreenAccessor) handledScreen).invokeGetSlotAt(mouseX, mouseY);
                            if (hoveredSlot != null && hoveredSlot.hasStack()) {
                                stackToEdit = hoveredSlot.getStack();
                            }
                        }

                        // Jeśli znaleziono przedmiot w jakikolwiek sposób, otwieramy ekran edycji
                        if (stackToEdit != null) {
                            client.setScreen(new EmcEditScreen(screen, stackToEdit));
                        }
                    }
                });
            }
        });
    }
}
