package pl.viko.itemalchemyaddon.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.pitan76.itemalchemy.EMCManager;
import net.pitan76.itemalchemy.data.ModState;
import net.pitan76.itemalchemy.data.TeamState;
import net.pitan76.mcpitanlib.api.entity.Player;
import net.pitan76.mcpitanlib.api.util.item.ItemUtil;
import org.jetbrains.annotations.Nullable;
import pl.viko.itemalchemyaddon.screen.AlchemicalTableMk2ScreenHandler;

import java.util.Optional;

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
        // Tworzymy ScreenHandler, przekazując mu nasz trwały ekwipunek z przedmiotu
        return new AlchemicalTableMk2ScreenHandler(syncId, playerInventory, new ItemInventory(playerEntity.getMainHandStack(), 21));
    }
}
