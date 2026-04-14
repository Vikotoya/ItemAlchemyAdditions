package pl.viko.itemalchemyaddon.networking.packet;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pitan76.itemalchemy.EMCManager;
import net.pitan76.itemalchemy.data.ModState;
import net.pitan76.itemalchemy.data.TeamState;
import net.pitan76.mcpitanlib.api.entity.Player;
import net.pitan76.mcpitanlib.api.util.item.ItemUtil;
import pl.viko.itemalchemyaddon.screen.AlchemicalTableMk2ScreenHandler;
import pl.viko.itemalchemyaddon.screen.AlchemicalTableMk2ScreenHandler.GuiMode;

import java.util.Optional;

/**
 * Handles the client-to-server "request item" packet sent when the player
 * clicks an item in the Alchemical Table Mk2 transmutation list.
 *
 * <p>Buying is only allowed when the handler is in {@link GuiMode#BURNING}
 * and the requested item has been learned by the player's team.</p>
 */
public class RequestItemC2SPacket {

    public static void receive(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler,
                               PacketByteBuf buf, PacketSender responseSender) {

        ItemStack requestedStack = buf.readItemStack();
        int clickType = buf.readInt();

        server.execute(() -> {
            if (player.currentScreenHandler instanceof AlchemicalTableMk2ScreenHandler sh
                    && sh.getMode() != GuiMode.BURNING) {
                return;
            }

            Item requestedItem = requestedStack.getItem();
            long emcCost = EMCManager.get(requestedItem);
            if (emcCost <= 0) return;

            Optional<TeamState> teamState = ModState.getModState(player.getServer())
                    .getTeamByPlayer(player.getUuid());
            if (teamState.isEmpty()
                    || !teamState.get().registeredItems.contains(ItemUtil.toId(requestedItem).toString())) {
                return;
            }

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
                case 2 -> { // Shift + Left click — fill stack to cursor
                    ItemStack cursorStack = player.currentScreenHandler.getCursorStack();
                    if (cursorStack.isEmpty()) {
                        int maxAmount = Math.min(requestedItem.getMaxCount(), (int) (playerEmc / emcCost));
                        if (maxAmount > 0) {
                            EMCManager.decrementEmc(mcpPlayer, emcCost * maxAmount);
                            player.currentScreenHandler.setCursorStack(new ItemStack(requestedItem, maxAmount));
                        }
                    } else if (cursorStack.getItem() == requestedItem) {
                        int spaceLeft = requestedItem.getMaxCount() - cursorStack.getCount();
                        if (spaceLeft > 0) {
                            int buyAmount = Math.min(spaceLeft, (int) (playerEmc / emcCost));
                            if (buyAmount > 0) {
                                EMCManager.decrementEmc(mcpPlayer, emcCost * buyAmount);
                                cursorStack.increment(buyAmount);
                            }
                        }
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
