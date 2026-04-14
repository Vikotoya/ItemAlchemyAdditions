package pl.viko.itemalchemyaddon.screen;

import net.minecraft.screen.ScreenHandlerType;
import net.pitan76.mcpitanlib.api.gui.SimpleScreenHandlerTypeBuilder;
import net.pitan76.mcpitanlib.api.registry.result.SupplierResult;

import static pl.viko.itemalchemyaddon.ItemAlchemyAddon._id;
import static pl.viko.itemalchemyaddon.ItemAlchemyAddon.registry;

/**
 * Registers all custom {@link ScreenHandlerType}s for this mod.
 */
public class ModScreenHandlers {

    /** Screen handler type for the Alchemical Table Mk2. */
    public static SupplierResult<ScreenHandlerType<AlchemicalTableMk2ScreenHandler>> ALCHEMICAL_TABLE_MK2_SCREEN_HANDLER;

    /**
     * Forces class loading, which triggers the static field initialisers above
     * and therefore registers all screen handlers.
     */
    public static void registerScreenHandlers() {
        ALCHEMICAL_TABLE_MK2_SCREEN_HANDLER = registry.registerScreenHandlerType(_id("alchemical_table_mk2"),
                new SimpleScreenHandlerTypeBuilder<>(e -> new AlchemicalTableMk2ScreenHandler(e.syncId, e.playerInventory)));

    }
}
