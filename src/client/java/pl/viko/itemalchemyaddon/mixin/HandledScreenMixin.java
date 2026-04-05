package pl.viko.itemalchemyaddon.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.viko.itemalchemyaddon.screen.AlchemicalTableMk2ScreenHandler;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Final
    @Shadow
    protected ScreenHandler handler;

}