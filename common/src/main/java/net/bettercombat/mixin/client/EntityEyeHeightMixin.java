package net.bettercombat.mixin.client;

import net.bettercombat.client.animation.PlayerAttackAnimatable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drops the eye with the pose.
 *
 * <p>A seated model whose camera stays at standing height reads as hovering above your own body, and
 * the effect is strongest in the very poses worth looking at.
 *
 * <p>Scoped hard: the local player only, and only while an emote that asks for it is playing. Eye
 * height feeds more than the camera - reach and line of sight among them - so this must not leak into
 * anything the player is not currently sitting still through.
 */
@Mixin(Entity.class)
public abstract class EntityEyeHeightMixin {

    @Inject(method = "getEyeY", at = @At("RETURN"), cancellable = true)
    private void bettercombat$lowerEyeDuringEmote(CallbackInfoReturnable<Double> info) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        float offset = ((PlayerAttackAnimatable) this).getEmoteCameraHeightOffset();
        if (offset != 0F) {
            info.setReturnValue(info.getReturnValue() + offset);
        }
    }
}
