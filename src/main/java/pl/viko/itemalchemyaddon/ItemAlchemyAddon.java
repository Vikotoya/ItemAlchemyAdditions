package pl.viko.itemalchemyaddon;

import net.pitan76.mcpitanlib.api.registry.v2.CompatRegistryV2;
import net.pitan76.mcpitanlib.api.util.CompatIdentifier;
import net.pitan76.mcpitanlib.fabric.ExtendModInitializer;
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
public class ItemAlchemyAddon extends ExtendModInitializer {

    /** The mod identifier used for all registry keys and resource paths. */
    public static final String MOD_ID = "itemalchemyaddon";

    public static CompatRegistryV2 registry;

    @Override
    public void init() {
        registry = super.registry;

        ModItems.registerModItems();
        ModScreenHandlers.registerScreenHandlers();
        ModMessages.registerC2SPackets();

        ReloadEmcCommand.register();
    }

    @Override
    public String getId() {
        return MOD_ID;
    }

    public static CompatIdentifier _id(String path) {
        return CompatIdentifier.of(MOD_ID, path);
    }
}
