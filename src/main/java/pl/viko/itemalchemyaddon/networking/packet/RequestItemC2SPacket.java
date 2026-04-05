package pl.viko.itemalchemyaddon.networking.packet;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pitan76.itemalchemy.EMCManager;
import net.pitan76.mcpitanlib.api.entity.Player;

public class RequestItemC2SPacket {
    public static void receive(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler,
                               PacketByteBuf buf, PacketSender responseSender) {

        // Odczytujemy, jaki przedmiot i jaki typ kliknięcia wysłał klient
        ItemStack requestedStack = buf.readItemStack();
        int clickType = buf.readInt();

        // Uruchamiamy logikę na głównym wątku serwera, aby uniknąć problemów
        server.execute(() -> {
            Item requestedItem = requestedStack.getItem();
            long emcCost = EMCManager.get(requestedItem);
            if (emcCost <= 0) return; // Przerywamy, jeśli przedmiot nie ma wartości EMC

            Player mcpPlayer = new Player(player);
            long playerEmc = EMCManager.getEmcFromPlayer(mcpPlayer);

            switch (clickType) {
                case 2: // Lewy Przycisk Myszy (LPM) - Cały stack na kursor
                    int maxAmountLMB = Math.min(requestedItem.getMaxCount(), (int) (playerEmc / emcCost));
                    if (maxAmountLMB > 0 && player.currentScreenHandler.getCursorStack().isEmpty()) {
                        EMCManager.decrementEmc(mcpPlayer, emcCost * maxAmountLMB);
                        player.currentScreenHandler.setCursorStack(new ItemStack(requestedItem, maxAmountLMB));
                    }
                    break;

                case 0: // Prawy Przycisk Myszy (PPM) - Jeden przedmiot na kursor
                    ItemStack cursorStackRMB = player.currentScreenHandler.getCursorStack();
                    if (playerEmc >= emcCost) {
                        if (cursorStackRMB.isEmpty()) {
                            EMCManager.decrementEmc(mcpPlayer, emcCost);
                            player.currentScreenHandler.setCursorStack(new ItemStack(requestedItem, 1));
                        } else if (cursorStackRMB.getItem() == requestedItem && cursorStackRMB.getCount() < requestedItem.getMaxCount()) {
                            EMCManager.decrementEmc(mcpPlayer, emcCost);
                            cursorStackRMB.increment(1);
                        }
                    }
                    break;

                case 3: // Shift + LPM - Cały stack do ekwipunku
                    int maxAmountShiftLMB = Math.min(requestedItem.getMaxCount(), (int) (playerEmc / emcCost));
                    if (maxAmountShiftLMB > 0) {
                        EMCManager.decrementEmc(mcpPlayer, emcCost * maxAmountShiftLMB);
                        player.getInventory().offerOrDrop(new ItemStack(requestedItem, maxAmountShiftLMB));
                    }
                    break;

                case 1: // Shift + PPM - Jeden przedmiot do ekwipunku
                    if (playerEmc >= emcCost) {
                        EMCManager.decrementEmc(mcpPlayer, emcCost);
                        player.getInventory().offerOrDrop(new ItemStack(requestedItem, 1));
                    }
                    break;
            }

            // Synchronizujemy EMC z klientem, aby zobaczył zmianę
            EMCManager.syncS2C(mcpPlayer);
        });
    }
}
