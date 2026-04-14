package pl.viko.itemalchemyaddon.networking.packet;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.pitan76.itemalchemy.EMCManager;
import net.pitan76.itemalchemy.data.ModState;
import net.pitan76.itemalchemy.data.TeamState;
import net.pitan76.mcpitanlib.api.entity.Player;
import net.pitan76.mcpitanlib.api.network.PacketByteUtil;
import net.pitan76.mcpitanlib.api.network.v2.args.ServerReceiveEvent;
import net.pitan76.mcpitanlib.api.util.ItemStackUtil;
import net.pitan76.mcpitanlib.api.util.ScreenHandlerUtil;
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

    public static void receive(ServerReceiveEvent e) {

        ItemStack requestedStack = PacketByteUtil.readItemStack(e.buf);
        int clickType = PacketByteUtil.readInt(e.buf);

        e.server.execute(() -> {
            Player player = e.player;
            if (player.getCurrentScreenHandler() instanceof AlchemicalTableMk2ScreenHandler sh
                    && sh.getMode() != GuiMode.BURNING) {
                return;
            }

            Item requestedItem = requestedStack.getItem();
            long emcCost = EMCManager.get(requestedItem);
            if (emcCost <= 0) return;

            Optional<TeamState> teamState = ModState.getModState(e.server)
                    .getTeamByPlayer(player.getUUID());
            if (teamState.isEmpty()
                    || !teamState.get().registeredItems.contains(ItemUtil.toId(requestedItem).toString())) {
                return;
            }

            long playerEmc = EMCManager.getEmcFromPlayer(player);

            switch (clickType) {
                case 0 -> { // Left click — single item to cursor
                    ItemStack cursorStack = player.getCursorStack();
                    if (playerEmc >= emcCost) {
                        if (cursorStack.isEmpty()) {
                            EMCManager.decrementEmc(player, emcCost);
                            ScreenHandlerUtil.setCursorStack(player.getCurrentScreenHandler(), ItemStackUtil.create(requestedItem, 1));
                        } else if (cursorStack.getItem() == requestedItem
                                && cursorStack.getCount() < requestedItem.getMaxCount()) {
                            EMCManager.decrementEmc(player, emcCost);
                            cursorStack.increment(1);
                        }
                    }
                }
                case 1 -> { // Right click — single item to inventory
                    if (playerEmc >= emcCost) {
                        EMCManager.decrementEmc(player, emcCost);
                        player.offerOrDrop(ItemStackUtil.create(requestedItem, 1));
                    }
                }
                case 2 -> { // Shift + Left click — fill stack to cursor
                    ItemStack cursorStack = player.getCursorStack();
                    if (cursorStack.isEmpty()) {
                        int maxAmount = Math.min(requestedItem.getMaxCount(), (int) (playerEmc / emcCost));
                        if (maxAmount > 0) {
                            EMCManager.decrementEmc(player, emcCost * maxAmount);
                            player.getCurrentScreenHandler().setCursorStack(ItemStackUtil.create(requestedItem, maxAmount));
                        }
                    } else if (cursorStack.getItem() == requestedItem) {
                        int spaceLeft = requestedItem.getMaxCount() - cursorStack.getCount();
                        if (spaceLeft > 0) {
                            int buyAmount = Math.min(spaceLeft, (int) (playerEmc / emcCost));
                            if (buyAmount > 0) {
                                EMCManager.decrementEmc(player, emcCost * buyAmount);
                                cursorStack.increment(buyAmount);
                            }
                        }
                    }
                }
                case 3 -> { // Shift + Right click — full stack to inventory
                    int maxAmount = Math.min(requestedItem.getMaxCount(), (int) (playerEmc / emcCost));
                    if (maxAmount > 0) {
                        EMCManager.decrementEmc(player, emcCost * maxAmount);
                        player.offerOrDrop(ItemStackUtil.create(requestedItem, maxAmount));
                    }
                }
                default -> { }
            }

            EMCManager.syncS2C(player);
        });
    }
}
