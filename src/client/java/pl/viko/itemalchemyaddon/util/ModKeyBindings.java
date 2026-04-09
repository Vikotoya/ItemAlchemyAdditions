package pl.viko.itemalchemyaddon.util;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Declares and registers all custom key bindings for this mod.
 */
public class ModKeyBindings {

    /** Translation key for the mod's key-binding category. */
    public static final String KEY_CATEGORY_ALCHEMY = "key.category.itemalchemyaddon.alchemy";

    /** Translation key for the "Edit EMC" key binding. */
    public static final String KEY_EDIT_EMC = "key.itemalchemyaddon.edit_emc";

    /** The "Edit EMC" key binding (default: grave accent / tilde key). */
    public static KeyBinding editEmcKey;

    /**
     * Registers all key bindings with the Fabric key-binding API.
     */
    public static void registerKeyInputs() {
        editEmcKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                KEY_EDIT_EMC,
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_GRAVE_ACCENT,
                KEY_CATEGORY_ALCHEMY
        ));
    }
}
