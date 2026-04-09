package pl.viko.itemalchemyaddon.screen;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import pl.viko.itemalchemyaddon.ItemAlchemyAddon;

/**
 * Registers all custom {@link ScreenHandlerType}s for this mod.
 */
public class ModScreenHandlers {

    /** Screen handler type for the Alchemical Table Mk2. */
    public static final ScreenHandlerType<AlchemicalTableMk2ScreenHandler> ALCHEMICAL_TABLE_MK2_SCREEN_HANDLER =
            Registry.register(Registries.SCREEN_HANDLER, new Identifier(ItemAlchemyAddon.MOD_ID, "alchemical_table_mk2"),
                    new ScreenHandlerType<>((syncId, playerInventory) ->
                            new AlchemicalTableMk2ScreenHandler(syncId, playerInventory),
                            FeatureFlags.VANILLA_FEATURES));

    /**
     * Forces class loading, which triggers the static field initialisers above
     * and therefore registers all screen handlers.
     */
    public static void registerScreenHandlers() {
        // Intentionally empty — class loading performs the registration.
    }
}
