package pl.viko.itemalchemyaddon;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import pl.viko.itemalchemyaddon.command.ReloadEmcCommand;
import pl.viko.itemalchemyaddon.item.ModItems;
import pl.viko.itemalchemyaddon.networking.ModMessages;
import pl.viko.itemalchemyaddon.screen.AlchemicalTableMk2ScreenHandler;
import pl.viko.itemalchemyaddon.screen.ModScreenHandlers;

/**
 * Main server-side entry point for the ItemAlchemyAddon mod.
 *
 * <p>Registers items, screen handlers, networking packets, server-tick logic
 * for the Alchemical Table Mk2 burning process, and the {@code /itemalchemyaddon}
 * command tree.</p>
 */
public class ItemAlchemyAddon implements ModInitializer {

    /** The mod identifier used for all registry keys and resource paths. */
    public static final String MOD_ID = "itemalchemyaddon";

    @Override
    public void onInitialize() {
        ModItems.registerModItems();
        ModScreenHandlers.registerScreenHandlers();
        ModMessages.registerC2SPackets();

        // Every server tick, advance the burning logic for every player
        // who currently has the Alchemical Table Mk2 screen open.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.currentScreenHandler instanceof AlchemicalTableMk2ScreenHandler handler) {
                    handler.tick();
                }
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ReloadEmcCommand.register(dispatcher)
        );
    }
}
