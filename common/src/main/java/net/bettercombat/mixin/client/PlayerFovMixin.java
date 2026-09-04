package net.bettercombat.mixin.client;

import net.bettercombat.client.animation.PlayerAttackAnimatable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the view at its normal width during an emote.
 *
 * <p>Minecraft derives the field of view from the movement speed attribute, and a group emote takes
 * that attribute to zero so nobody can walk or sprint the line along. The side effect is a noticeably
 * narrowed, zoomed-in view for as long as the dance lasts - the speed lock is wanted, the zoom is not.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class PlayerFovMixin {

    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void bettercombat$keepFovDuringEmote(boolean isFirstPerson, float partialTick,
                                                 CallbackInfoReturnable<Float> info) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        if (this instanceof PlayerAttackAnimatable animatable && animatable.isEmoteActive()) {
            info.setReturnValue(1.0F);
        }
    }
}
