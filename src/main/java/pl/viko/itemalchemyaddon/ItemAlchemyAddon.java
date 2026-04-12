package pl.viko.itemalchemyaddon;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import pl.viko.itemalchemyaddon.command.ReloadEmcCommand;
import pl.viko.itemalchemyaddon.item.ModItems;
import pl.viko.itemalchemyaddon.networking.ModMessages;
import pl.viko.itemalchemyaddon.screen.ModScreenHandlers;

/**
 * Main server-side entry point for the ItemAlchemyAddon mod.
 *
 * <p>Registers items, screen handlers, networking packets, and the
 * {@code /itemalchemyaddon} command tree.  No per-tick processing is
 * required — the Alchemical Table Mk2 burn zone is virtual and burns
 * items immediately on interaction.</p>
 */
public class ItemAlchemyAddon implements ModInitializer {

    /** The mod identifier used for all registry keys and resource paths. */
    public static final String MOD_ID = "itemalchemyaddon";

    @Override
    public void onInitialize() {
        ModItems.registerModItems();
        ModScreenHandlers.registerScreenHandlers();
        ModMessages.registerC2SPackets();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ReloadEmcCommand.register(dispatcher)
        );
    }
}
