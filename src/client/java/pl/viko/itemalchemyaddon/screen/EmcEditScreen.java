package pl.viko.itemalchemyaddon.screen;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.pitan76.itemalchemy.EMCManager;
import net.pitan76.mcpitanlib.api.client.SimpleScreen;
import net.pitan76.mcpitanlib.api.client.render.handledscreen.KeyEventArgs;
import net.pitan76.mcpitanlib.api.client.render.handledscreen.RenderArgs;
import net.pitan76.mcpitanlib.api.util.TextUtil;
import net.pitan76.mcpitanlib.api.util.client.ClientUtil;
import net.pitan76.mcpitanlib.api.util.client.ScreenUtil;
import net.pitan76.mcpitanlib.api.util.client.widget.TextFieldUtil;
import net.pitan76.mcpitanlib.api.util.item.ItemUtil;
import org.lwjgl.glfw.GLFW;
import pl.viko.itemalchemyaddon.util.ModKeyBindings;

/**
 * A lightweight overlay screen that allows the player to set the EMC value
 * of a specific item via a text field.
 *
 * <p>Pressing <b>Enter</b> sends an {@code /itemalchemy setemc} command on
 * behalf of the player (requires appropriate permissions).  Pressing
 * <b>Escape</b> returns to the parent screen without changes.</p>
 */
public class EmcEditScreen extends SimpleScreen {

    private final Screen parent;
    private final ItemStack stackToEdit;
    private TextFieldWidget emcEditField;
    private boolean keyHandled = false;

    /**
     * @param parent      the screen to return to when this overlay is closed
     * @param stackToEdit the item whose EMC value will be edited
     */
    public EmcEditScreen(Screen parent, ItemStack stackToEdit) {
        super(Text.literal("Edit EMC"));
        this.parent = parent;
        this.stackToEdit = stackToEdit;
    }

    @Override
    public void initOverride() {
        int fieldWidth = 90;
        int fieldHeight = 20;
        int fieldX = (this.width - fieldWidth) / 2;
        int fieldY = (this.height - fieldHeight) / 2;

        this.emcEditField = TextFieldUtil.create(this.textRenderer, fieldX, fieldY, fieldWidth, fieldHeight, TextUtil.literal(""));
        TextFieldUtil.setText(this.emcEditField, String.valueOf(EMCManager.get(stackToEdit.getItem())));
        this.addDrawableChild_compatibility(this.emcEditField);
        TextFieldUtil.setFocused(this.emcEditField, true);
    }

    @Override
    public boolean keyPressed(KeyEventArgs args) {
        int keyCode = args.keyCode;
        int scanCode = args.scanCode;

        // Consume the first press of the "edit EMC" key to prevent the
        // screen from immediately reopening when the key is released.
        if (!keyHandled && ModKeyBindings.editEmcKey.toMinecraft().matchesKey(keyCode, scanCode)) {
            keyHandled = true;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            try {
                long newEmcValue = Long.parseLong(TextFieldUtil.getText(this.emcEditField));
                if (newEmcValue >= 0) {
                    String itemId = ItemUtil.toId(stackToEdit.getItem()).toString();
                    String command = "itemalchemy setemc " + itemId + " " + newEmcValue;
                    this.client.player.networkHandler.sendChatCommand(command);
                }
            } catch (NumberFormatException ignored) { }
            close();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        return super.keyPressed(args);
    }

    @Override
    public void render(RenderArgs args) {
        this.renderBackground(args);
        super.render(args);
        ScreenUtil.RendererUtil.drawText(this.textRenderer, args.getDrawObjectDM(),
            TextUtil.literal("Set EMC for: " + stackToEdit.getName().getString()),
            this.width / 2, this.height / 2 - 20, 0xFFFFFF);
    }

    @Override
    public void closeOverride() {
        ClientUtil.setScreen(this.parent);
    }
}
