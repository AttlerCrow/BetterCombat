package net.bettercombat.mixin.client;

import com.zigythebird.playeranim.animation.AvatarAnimManager;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.bettercombat.client.compat.EmfBendVariables;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes the bend Player Animation Library has already computed, so the 3D armor can fold with it.
 *
 * <p>This is the same callback BendableCuboids hooks to draw the bend on the player itself, chosen
 * for that reason: it is proven to run every frame with a populated bone. An earlier attempt on
 * {@code AvatarAnimManager.updatePart} never fired at all.
 *
 * <p>Reading only. Asking the animation stack for a bone instead - {@code get3DTransform} - walks
 * every layer, and doing that per frame is what stops Fresh Animations animating while a weapon pose
 * loops. Here the value is already computed by the time this runs.
 */
@Mixin(value = PlayerModel.class, priority = 2003)
public abstract class PlayerModelBendObserverMixin {

    @Inject(
            method = {
                    "pal$updatePart(Lcom/zigythebird/playeranim/animation/AvatarAnimManager;Lnet/minecraft/client/model/geom/ModelPart;Lcom/zigythebird/playeranimcore/bones/PlayerAnimBone;)V",
                    "pal$updatePart(Lcom/zigythebird/playeranim/animation/AvatarAnimManager;Lnet/minecraft/class_630;Lcom/zigythebird/playeranimcore/bones/PlayerAnimBone;)V"
            },
            at = @At("RETURN")
    )
    @SuppressWarnings({"MixinAnnotationTarget", "UnresolvedMixinReference"})
    private void bettercombat$observeBend(AvatarAnimManager manager, ModelPart part, PlayerAnimBone bone,
                                          CallbackInfo ci) {
        EmfBendVariables.observe(bone);
    }
}
