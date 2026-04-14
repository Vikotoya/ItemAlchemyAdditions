package pl.viko.itemalchemyaddon.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.pitan76.mcpitanlib.api.client.registry.CompatRegistryClient;
import pl.viko.itemalchemyaddon.mixin.HandledScreenAccessor;
import pl.viko.itemalchemyaddon.screen.AlchemicalTableMk2Screen;
import pl.viko.itemalchemyaddon.screen.EmcEditScreen;
import pl.viko.itemalchemyaddon.screen.ModScreenHandlers;
import pl.viko.itemalchemyaddon.util.ModKeyBindings;

/**
 * Client-side entry point for the ItemAlchemyAddon mod.
 *
 * <p>Registers the Alchemical Table Mk2 screen, key bindings, and the
 * global key-release listener that opens the EMC editing overlay on any
 * {@link HandledScreen}.</p>
 */
public class ItemAlchemyAddonClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CompatRegistryClient.registerScreen(ModScreenHandlers.ALCHEMICAL_TABLE_MK2_SCREEN_HANDLER.getOrNull(), AlchemicalTableMk2Screen::new);

        ModKeyBindings.registerKeyInputs();
        registerKeyEvents();
    }

    /**
     * Registers a global key-release listener on every {@link HandledScreen}.
     *
     * <p>When the configured "Edit EMC" key is released while hovering over an
     * item, the {@link EmcEditScreen} is opened for that item.  Inside the
     * Alchemical Table Mk2 screen the hovered item is first looked up from the
     * virtual transmutation list; in all other screens it falls back to the
     * standard slot-based lookup via the {@link HandledScreenAccessor} mixin.</p>
     */
    private void registerKeyEvents() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen<?>) {
                ScreenKeyboardEvents.beforeKeyRelease(screen).register((theScreen, key, scancode, mods) -> {
                    if (ModKeyBindings.editEmcKey.toMinecraft().matchesKey(key, scancode)) {
                        HandledScreen<?> handledScreen = (HandledScreen<?>) theScreen;
                        double mouseX = client.mouse.getX() / client.getWindow().getScaleFactor();
                        double mouseY = client.mouse.getY() / client.getWindow().getScaleFactor();

                        ItemStack stackToEdit = null;

                        if (handledScreen instanceof AlchemicalTableMk2Screen mk2Screen) {
                            // Try the virtual item list first
                            stackToEdit = mk2Screen.getHoveredStackFromList(mouseX, mouseY);
                            if (stackToEdit == null) {
                                Slot hoveredSlot = ((HandledScreenAccessor) handledScreen).invokeGetSlotAt(mouseX, mouseY);
                                if (hoveredSlot != null && hoveredSlot.hasStack()) {
                                    stackToEdit = hoveredSlot.getStack();
                                }
                            }
                        } else {
                            // Standard slot-based lookup for any other screen
                            Slot hoveredSlot = ((HandledScreenAccessor) handledScreen).invokeGetSlotAt(mouseX, mouseY);
                            if (hoveredSlot != null && hoveredSlot.hasStack()) {
                                stackToEdit = hoveredSlot.getStack();
                            }
                        }

                        if (stackToEdit != null) {
                            client.setScreen(new EmcEditScreen(screen, stackToEdit));
                        }
                    }
                });
            }
        });
    }
}
