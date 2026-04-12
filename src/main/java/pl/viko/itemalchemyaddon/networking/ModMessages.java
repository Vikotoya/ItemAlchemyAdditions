package pl.viko.itemalchemyaddon.networking;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;
import pl.viko.itemalchemyaddon.ItemAlchemyAddon;
import pl.viko.itemalchemyaddon.networking.packet.RequestItemC2SPacket;
import pl.viko.itemalchemyaddon.networking.packet.UnlearnItemsC2SPacket;

/**
 * Declares and registers all custom networking channel identifiers
 * used by this mod.
 */
public class ModMessages {

    /** Channel ID for the client-to-server item request (buy) packet. */
    public static final Identifier REQUEST_ITEM_ID = new Identifier(ItemAlchemyAddon.MOD_ID, "request_item");

    /** Channel ID for the client-to-server unlearn-items packet. */
    public static final Identifier UNLEARN_ITEMS_ID = new Identifier(ItemAlchemyAddon.MOD_ID, "unlearn_items");

    /**
     * Registers all client-to-server (C2S) packet receivers.
     */
    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST_ITEM_ID, RequestItemC2SPacket::receive);
        ServerPlayNetworking.registerGlobalReceiver(UNLEARN_ITEMS_ID, UnlearnItemsC2SPacket::receive);
    }
}
