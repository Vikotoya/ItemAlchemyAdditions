package pl.viko.itemalchemyaddon.mixin;

import net.pitan76.itemalchemy.EMCManager;
import net.pitan76.mcpitanlib.midohra.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that cancels the original {@code EMCManager.setEmcFromRecipes} method.
 *
 * <p>This addon replaces that logic with its own multi-pass recipe scanner
 * exposed via the {@code /itemalchemyaddon reloademc} command, giving the
 * server operator explicit control over when EMC values are recalculated.</p>
 */
@Mixin(EMCManager.class)
public class EMCManagerMixin {

    @Inject(method = "setEmcFromRecipes", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onSetEmcFromRecipes(ServerWorld world, CallbackInfo ci) {
        System.out.println("ItemAlchemyAddon: Blocked the original setEmcFromRecipes method.");
        ci.cancel();
    }
}
