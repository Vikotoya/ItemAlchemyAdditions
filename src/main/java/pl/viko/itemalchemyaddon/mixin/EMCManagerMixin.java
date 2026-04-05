package pl.viko.itemalchemyaddon.mixin;

import net.pitan76.itemalchemy.EMCManager;
import net.pitan76.mcpitanlib.midohra.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EMCManager.class)
public class EMCManagerMixin {

    @Inject(method = "setEmcFromRecipes", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onSetEmcFromRecipes(ServerWorld world, CallbackInfo ci) {
        System.out.println("ItemAlchemyAddon: Zablokowano oryginalną funkcję setEmcFromRecipes.");
        ci.cancel();
    }
}