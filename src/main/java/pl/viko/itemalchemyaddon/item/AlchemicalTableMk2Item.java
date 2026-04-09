package pl.viko.itemalchemyaddon.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import pl.viko.itemalchemyaddon.screen.AlchemicalTableMk2ScreenHandler;

/**
 * A portable alchemical table item that opens the Mk2 transmutation UI
 * when used (right-clicked).
 *
 * <p>The item itself acts as a {@link NamedScreenHandlerFactory}, storing a
 * 21-slot {@link ItemInventory} inside its NBT so the buffer contents persist
 * between sessions.</p>
 */
public class AlchemicalTableMk2Item extends Item implements NamedScreenHandlerFactory {

    public AlchemicalTableMk2Item(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient()) {
            user.openHandledScreen(this);
        }
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable(this.getTranslationKey());
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity playerEntity) {
        return new AlchemicalTableMk2ScreenHandler(
                syncId, playerInventory,
                new ItemInventory(playerEntity.getMainHandStack(), 21));
    }
}
