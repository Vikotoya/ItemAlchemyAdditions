package pl.viko.itemalchemyaddon.util;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {
    public static final String KEY_CATEGORY_ALCHEMY = "key.category.itemalchemyaddon.alchemy";
    public static final String KEY_EDIT_EMC = "key.itemalchemyaddon.edit_emc";

    public static KeyBinding editEmcKey;

    public static void registerKeyInputs() {
        editEmcKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                KEY_EDIT_EMC,
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_GRAVE_ACCENT, // Domyślnie klawisz tyldy ( ` )
                KEY_CATEGORY_ALCHEMY
        ));
    }
}
