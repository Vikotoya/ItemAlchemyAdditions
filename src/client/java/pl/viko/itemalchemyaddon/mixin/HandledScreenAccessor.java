package pl.viko.itemalchemyaddon.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Mixin accessor that exposes the private
 * {@link HandledScreen#getSlotAt(double, double)} method for use by this mod's
 * key-event handler to determine which slot the mouse is hovering over.
 */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {

    /**
     * Invokes the private {@code getSlotAt} method on {@link HandledScreen}.
     *
     * @param x the mouse X coordinate (scaled)
     * @param y the mouse Y coordinate (scaled)
     * @return the slot at the given position, or {@code null} if none
     */
    @Invoker("getSlotAt")
    Slot invokeGetSlotAt(double x, double y);
}
