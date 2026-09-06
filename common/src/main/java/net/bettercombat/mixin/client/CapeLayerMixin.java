package net.bettercombat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bettercombat.client.animation.EmoteItemAnchorHolder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves the cape undrawn while an emote bends the torso.
 *
 * <p>PlayerAnimationLib attaches the cape to the animated body with {@code translateAndRotate} -
 * position and rotation, nothing else. A bend is not either of those: it is a deformation applied
 * inside the cuboid renderer, so the torso curves while the cape stays flat against the straight
 * anchor it was attached to, and hangs in the air behind the player.
 *
 * <p>Hiding it is the honest fix rather than the lazy one. Making the cape follow would mean knowing
 * where the top of a bent torso ended up, and nothing exposes that - the deformation never leaves the
 * renderer. Every emote here bends the torso except {@code sit}, which is also the only one whose
 * cape looked right, and that is what pointed at the cause.
 */
@Mixin(CapeLayer.class)
public class CapeLayerMixin {

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void bettercombat$hideCapeDuringBentEmote(PoseStack poseStack, SubmitNodeCollector collector,
                                                      int packedLight, AvatarRenderState state,
                                                      float yRot, float xRot, CallbackInfo ci) {
        if (state instanceof EmoteItemAnchorHolder holder && holder.bettercombat$isHidingCape()) {
            ci.cancel();
        }
    }
}
