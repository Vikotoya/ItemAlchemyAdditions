package pl.viko.itemalchemyaddon.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.pitan76.itemalchemy.EMCManager;
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
public class EmcEditScreen extends Screen {

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
    protected void init() {
        int fieldWidth = 90;
        int fieldHeight = 20;
        int fieldX = (this.width - fieldWidth) / 2;
        int fieldY = (this.height - fieldHeight) / 2;

        this.emcEditField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldWidth, fieldHeight, Text.literal(""));
        this.emcEditField.setText(String.valueOf(EMCManager.get(stackToEdit.getItem())));
        this.addDrawableChild(this.emcEditField);
        setFocused(this.emcEditField);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Consume the first press of the "edit EMC" key to prevent the
        // screen from immediately reopening when the key is released.
        if (!keyHandled && ModKeyBindings.editEmcKey.matchesKey(keyCode, scanCode)) {
            keyHandled = true;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            try {
                long newEmcValue = Long.parseLong(this.emcEditField.getText());
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

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer,
                "Set EMC for: " + stackToEdit.getName().getString(),
                this.width / 2, this.height / 2 - 20, 0xFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
