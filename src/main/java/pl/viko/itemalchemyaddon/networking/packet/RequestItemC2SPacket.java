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
import pl.viko.itemalchemyaddon.screen.AlchemicalTableMk2ScreenHandler;
import pl.viko.itemalchemyaddon.screen.AlchemicalTableMk2ScreenHandler.GuiMode;

/**
 * Handles the client-to-server "request item" packet sent when the player
 * clicks an item in the Alchemical Table Mk2 transmutation list.
 *
 * <p>The packet payload consists of the requested {@link ItemStack} followed
 * by an {@code int} click-type code that determines the quantity and
 * destination:</p>
 * <ul>
 *   <li>{@code 0} — Left click (no shift): single item to cursor</li>
 *   <li>{@code 1} — Right click (no shift): single item to inventory</li>
 *   <li>{@code 2} — Shift + Left click: full stack to cursor</li>
 *   <li>{@code 3} — Shift + Right click: full stack to inventory</li>
 * </ul>
 *
 * <p>Buying is only allowed when the handler is in {@link GuiMode#BURNING}.</p>
 */
public class RequestItemC2SPacket {

    /**
     * Server-side receiver for the item-request packet.
     */
    public static void receive(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler,
                               PacketByteBuf buf, PacketSender responseSender) {

        ItemStack requestedStack = buf.readItemStack();
        int clickType = buf.readInt();

        server.execute(() -> {
            // Block buying when not in BURNING mode
            if (player.currentScreenHandler instanceof AlchemicalTableMk2ScreenHandler sh
                    && sh.getMode() != GuiMode.BURNING) {
                return;
            }

            Item requestedItem = requestedStack.getItem();
            long emcCost = EMCManager.get(requestedItem);
            if (emcCost <= 0) return;

            Player mcpPlayer = new Player(player);
            long playerEmc = EMCManager.getEmcFromPlayer(mcpPlayer);

            switch (clickType) {
                case 0 -> { // Left click — single item to cursor
                    ItemStack cursorStack = player.currentScreenHandler.getCursorStack();
                    if (playerEmc >= emcCost) {
                        if (cursorStack.isEmpty()) {
                            EMCManager.decrementEmc(mcpPlayer, emcCost);
                            player.currentScreenHandler.setCursorStack(new ItemStack(requestedItem, 1));
                        } else if (cursorStack.getItem() == requestedItem
                                && cursorStack.getCount() < requestedItem.getMaxCount()) {
                            EMCManager.decrementEmc(mcpPlayer, emcCost);
                            cursorStack.increment(1);
                        }
                    }
                }
                case 1 -> { // Right click — single item to inventory
                    if (playerEmc >= emcCost) {
                        EMCManager.decrementEmc(mcpPlayer, emcCost);
                        player.getInventory().offerOrDrop(new ItemStack(requestedItem, 1));
                    }
                }
                case 2 -> { // Shift + Left click — full stack to cursor
                    int maxAmount = Math.min(requestedItem.getMaxCount(), (int) (playerEmc / emcCost));
                    if (maxAmount > 0 && player.currentScreenHandler.getCursorStack().isEmpty()) {
                        EMCManager.decrementEmc(mcpPlayer, emcCost * maxAmount);
                        player.currentScreenHandler.setCursorStack(new ItemStack(requestedItem, maxAmount));
                    }
                }
                case 3 -> { // Shift + Right click — full stack to inventory
                    int maxAmount = Math.min(requestedItem.getMaxCount(), (int) (playerEmc / emcCost));
                    if (maxAmount > 0) {
                        EMCManager.decrementEmc(mcpPlayer, emcCost * maxAmount);
                        player.getInventory().offerOrDrop(new ItemStack(requestedItem, maxAmount));
                    }
                }
                default -> { }
            }

            EMCManager.syncS2C(mcpPlayer);
        });
    }
}
