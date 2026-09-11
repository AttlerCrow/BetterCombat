package net.bettercombat.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.bettercombat.client.CombatInputReporter;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catches a key press where vanilla itself counts one.
 *
 * <p>{@code KeyMapping.click} is called once per physical press by the mouse and keyboard handlers,
 * and only when no screen has taken the click - which makes it the one place that sees exactly the
 * presses a player aimed at the world, however fast they come and whatever they are bound to. The
 * press is only recorded here; see {@link CombatInputReporter} for why it is sent a moment later.
 */
@Mixin(KeyMapping.class)
public class KeyMappingClickMixin {

    @Inject(method = "click", at = @At("HEAD"))
    private static void bettercombat$reportCombatInput(InputConstants.Key key, CallbackInfo ci) {
        CombatInputReporter.onKeyClicked(key);
    }
}
