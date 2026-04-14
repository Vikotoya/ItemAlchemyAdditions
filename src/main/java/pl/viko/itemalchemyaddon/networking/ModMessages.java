package pl.viko.itemalchemyaddon.networking;

import net.pitan76.mcpitanlib.api.network.v2.ServerNetworking;
import net.pitan76.mcpitanlib.api.util.CompatIdentifier;
import pl.viko.itemalchemyaddon.networking.packet.RequestItemC2SPacket;
import pl.viko.itemalchemyaddon.networking.packet.UnlearnItemsC2SPacket;

import static pl.viko.itemalchemyaddon.ItemAlchemyAddon._id;

/**
 * Declares and registers all custom networking channel identifiers
 * used by this mod.
 */
public class ModMessages {

    /** Channel ID for the client-to-server item request (buy) packet. */
    public static final CompatIdentifier REQUEST_ITEM_ID = _id("request_item");

    /** Channel ID for the client-to-server unlearn-items packet. */
    public static final CompatIdentifier UNLEARN_ITEMS_ID = _id("unlearn_items");

    /**
     * Registers all client-to-server (C2S) packet receivers.
     */
    public static void registerC2SPackets() {
        ServerNetworking.registerReceiver(REQUEST_ITEM_ID, RequestItemC2SPacket::receive);
        ServerNetworking.registerReceiver(UNLEARN_ITEMS_ID, UnlearnItemsC2SPacket::receive);
    }
}
