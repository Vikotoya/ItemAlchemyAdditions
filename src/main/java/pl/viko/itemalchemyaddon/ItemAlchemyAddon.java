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

public class ItemAlchemyAddon implements ModInitializer {

    public static final String MOD_ID = "itemalchemyaddon";

    @Override
    public void onInitialize() {
        ModItems.registerModItems();
        ModScreenHandlers.registerScreenHandlers();
        ModMessages.registerC2SPackets();


        // --- PRZYWRACAMY LOGIKĘ ZEGARA SERWERA ---
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Dla każdego gracza na serwerze...
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                // ...jeśli ma otwarty nasz ScreenHandler...
                if (player.currentScreenHandler instanceof AlchemicalTableMk2ScreenHandler) {
                    // ...wywołujemy jego metodę tick().
                    ((AlchemicalTableMk2ScreenHandler) player.currentScreenHandler).tick();
                }
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ReloadEmcCommand.register(dispatcher);
        });

    }
}