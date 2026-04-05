package pl.viko.itemalchemyaddon.screen;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import pl.viko.itemalchemyaddon.ItemAlchemyAddon;

public class ModScreenHandlers {

    public static final ScreenHandlerType<AlchemicalTableMk2ScreenHandler> ALCHEMICAL_TABLE_MK2_SCREEN_HANDLER =
            Registry.register(Registries.SCREEN_HANDLER, new Identifier(ItemAlchemyAddon.MOD_ID, "alchemical_table_mk2"),
                    new ScreenHandlerType<>((syncId, playerInventory) ->
                            new AlchemicalTableMk2ScreenHandler(syncId, playerInventory),
                            FeatureFlags.VANILLA_FEATURES));

    public static void registerScreenHandlers() {
        // Pusta metoda do uruchomienia rejestracji
    }
}