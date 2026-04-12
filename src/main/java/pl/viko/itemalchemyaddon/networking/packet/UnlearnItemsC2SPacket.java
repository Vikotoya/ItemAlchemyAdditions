package pl.viko.itemalchemyaddon.networking.packet;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pitan76.itemalchemy.data.ModState;
import net.pitan76.itemalchemy.data.ServerState;
import net.pitan76.itemalchemy.data.TeamState;
import pl.viko.itemalchemyaddon.screen.AlchemicalTableMk2ScreenHandler;
import pl.viko.itemalchemyaddon.screen.AlchemicalTableMk2ScreenHandler.GuiMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles the client-to-server "unlearn items" packet sent when the player
 * confirms their unlearning selection in the Alchemical Table Mk2.
 *
 * <p>The packet payload is an {@code int} count followed by that many
 * {@link String} item IDs (e.g. {@code "minecraft:diamond"}).  Each ID is
 * removed from the player's team learned-item pool.</p>
 *
 * <p>After processing, the GUI mode is switched back to
 * {@link GuiMode#BURNING}.</p>
 */
public class UnlearnItemsC2SPacket {

    /**
     * Server-side receiver for the unlearn-items packet.
     */
    public static void receive(MinecraftServer server, ServerPlayerEntity player,
                               ServerPlayNetworkHandler handler, PacketByteBuf buf,
                               PacketSender responseSender) {

        int count = buf.readInt();
        List<String> itemIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            itemIds.add(buf.readString());
        }

        server.execute(() -> {
            if (!(player.currentScreenHandler instanceof AlchemicalTableMk2ScreenHandler screenHandler)) {
                return;
            }
            if (screenHandler.getMode() != GuiMode.UNLEARNING) {
                return;
            }

            Optional<TeamState> teamState = ModState.getModState(player.getServer())
                    .getTeamByPlayer(player.getUuid());
            if (teamState.isPresent()) {
                teamState.get().registeredItems.removeAll(itemIds);
                ServerState.of(player.getServer()).callMarkDirty();
            }

            screenHandler.setMode(GuiMode.BURNING);
        });
    }
}
