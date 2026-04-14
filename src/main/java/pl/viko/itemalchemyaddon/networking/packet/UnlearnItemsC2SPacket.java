package pl.viko.itemalchemyaddon.networking.packet;

import net.pitan76.itemalchemy.EMCManager;
import net.pitan76.itemalchemy.data.ModState;
import net.pitan76.itemalchemy.data.ServerState;
import net.pitan76.itemalchemy.data.TeamState;
import net.pitan76.mcpitanlib.api.entity.Player;
import net.pitan76.mcpitanlib.api.network.PacketByteUtil;
import net.pitan76.mcpitanlib.api.network.v2.args.ServerReceiveEvent;
import pl.viko.itemalchemyaddon.screen.AlchemicalTableMk2ScreenHandler;
import pl.viko.itemalchemyaddon.screen.AlchemicalTableMk2ScreenHandler.GuiMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles the client-to-server "unlearn items" packet sent when the player
 * confirms their unlearning selection in the Alchemical Table Mk2.
 *
 * <p>After removing the selected items from the team's learned pool, a full
 * state sync is sent to the client so the GUI reflects the change
 * immediately.</p>
 */
public class UnlearnItemsC2SPacket {

    public static void receive(ServerReceiveEvent e) {

        int count = PacketByteUtil.readInt(e.buf);
        List<String> itemIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            itemIds.add(PacketByteUtil.readString(e.buf));
        }

        e.server.execute(() -> {
            Player player = e.player;
            if (!(player.getCurrentScreenHandler() instanceof AlchemicalTableMk2ScreenHandler screenHandler)) {
                return;
            }
            if (screenHandler.getMode() != GuiMode.UNLEARNING) {
                return;
            }

            Optional<TeamState> teamState = ModState.getModState(e.server)
                    .getTeamByPlayer(player.getUUID());
            if (teamState.isPresent()) {
                teamState.get().registeredItems.removeAll(itemIds);
                ServerState.of(e.server).callMarkDirty();
            }

            screenHandler.setMode(GuiMode.BURNING);

            EMCManager.syncS2C(player);
        });
    }
}
