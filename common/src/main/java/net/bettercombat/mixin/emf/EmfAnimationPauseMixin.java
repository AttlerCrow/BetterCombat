package net.bettercombat.mixin.emf;

import net.bettercombat.client.compat.EmfArmorModelBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps EMF animating the armor while an emote plays.
 *
 * <p>EMF pauses an entity's custom animations whenever it decides the player is emoting - its own
 * check, {@code isPlayerEmoting}, independent of the pause conditions the API lets a mod register.
 * That freezes every {@code .jem} animation on the entity, and the generated 3D armor's knee and
 * elbow are driven by exactly such an animation, reading {@code nf_bend_*}. Measured: during a
 * {@code sit_throne} the bend was written to the store ten times and EMF never once read it back.
 *
 * <p>Only lifted while the entity is being forced onto its vanilla model, which is the emote case and
 * nothing else. In that situation the body is vanilla and carries no EMF animation to disturb, so the
 * only custom models still standing are the layers {@code EmfVanillaModelLayerMixin} deliberately
 * kept - the armor. Outside it the pause behaves exactly as EMF intends.
 */
@Mixin(targets = "traben.entity_model_features.models.animation.EMFAnimationEntityContext", remap = false)
public abstract class EmfAnimationPauseMixin {

    @Inject(method = "isEntityAnimPaused", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void bettercombat$keepArmorAnimating(CallbackInfoReturnable<Boolean> cir) {
        if (EmfArmorModelBridge.entityForcedToVanilla()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isEntityAnimPausedWrapped", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void bettercombat$keepArmorAnimatingWrapped(CallbackInfoReturnable<Boolean> cir) {
        if (EmfArmorModelBridge.entityForcedToVanilla()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Drops the cached {@code nf_animating} answer whenever EMF moves to another entity.
     *
     * <p>{@code newEntity} is the single choke point both {@code setCurrentEntityIteration} and
     * {@code setCurrentEntityNoIteration} funnel through, so one hook covers every way the context
     * changes - and it is the exact boundary the cache is valid within.
     */
    @Inject(method = "newEntity", at = @At("HEAD"), remap = false, require = 0)
    private static void bettercombat$newEntity(@org.spongepowered.asm.mixin.injection.Coerce Object state,
                                               org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        EmfArmorModelBridge.invalidateAnimating();
    }
}
