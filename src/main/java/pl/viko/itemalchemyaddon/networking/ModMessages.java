package pl.viko.itemalchemyaddon.networking;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;
import pl.viko.itemalchemyaddon.ItemAlchemyAddon;
import pl.viko.itemalchemyaddon.networking.packet.RequestItemC2SPacket;

/**
 * Declares and registers all custom networking channel identifiers
 * used by this mod.
 */
public class ModMessages {

    /** Channel ID for the client-to-server item request packet. */
    public static final Identifier REQUEST_ITEM_ID = new Identifier(ItemAlchemyAddon.MOD_ID, "request_item");

    /**
     * Registers all client-to-server (C2S) packet receivers.
     */
    public static void registerC2SPackets() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST_ITEM_ID, RequestItemC2SPacket::receive);
    }
}
