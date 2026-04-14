package pl.viko.itemalchemyaddon.util;

import net.pitan76.mcpitanlib.api.client.option.CompatKeyBinding;
import net.pitan76.mcpitanlib.api.client.registry.v3.KeybindingRegistry;
import net.pitan76.mcpitanlib.api.util.CompatIdentifier;
import org.lwjgl.glfw.GLFW;

import static pl.viko.itemalchemyaddon.ItemAlchemyAddon._id;

/**
 * Declares and registers all custom key bindings for this mod.
 */
public class ModKeyBindings {

    /** Translation key for the mod's key-binding category. */
    public static final CompatIdentifier KEY_CATEGORY_ALCHEMY = _id("alchemy");

    /** Translation key for the "Edit EMC" key binding. */
    public static final String KEY_EDIT_EMC = "key.itemalchemyaddon.edit_emc";

    /** The "Edit EMC" key binding (default: grave accent / tilde key). */
    public static CompatKeyBinding editEmcKey;

    /**
     * Registers all key bindings with the Fabric key-binding API.
     */
    public static void registerKeyInputs() {
        editEmcKey = new CompatKeyBinding(
                KEY_EDIT_EMC,
                GLFW.GLFW_KEY_GRAVE_ACCENT,
                KEY_CATEGORY_ALCHEMY
        );

        KeybindingRegistry.register(editEmcKey);
    }
}
