package net.bettercombat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bettercombat.client.animation.EmoteItemAnchor;
import net.bettercombat.client.animation.EmoteItemAnchorHolder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the held item somewhere other than the hand while an emote asks for it.
 *
 * <p>Two facts about this spot, both established by reading the render path rather than assuming it.
 * The pose stack here is in <strong>block</strong> units, because {@code ModelPart#translateAndRotate}
 * divides by 16 - feeding it raw model units puts the item metres away. And <strong>+Z is behind</strong>
 * the player: the cape part hangs off the body at {@code z = +2}.
 *
 * <p>Vanilla applies its own {@code -90 deg X} and {@code 180 deg Y} after this method returns, so the
 * anchor's rotation composes with the item's normal held orientation rather than replacing it.
 *
 * <p>Replaces the hand transform rather than adding to it: the item is positioned against the torso,
 * so it holds still on the back or on the ground while the arms do whatever the emote wants. The item
 * still renders through vanilla code - only where it lands changes.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelItemAnchorMixin {

    @Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD"), cancellable = true)
    private void bettercombat$anchorEmoteItem(AvatarRenderState state, HumanoidArm arm, PoseStack poseStack,
                                              CallbackInfo ci) {
        EmoteItemAnchorHolder holder = (EmoteItemAnchorHolder) state;
        // Each hand gets its own anchor. With one shared anchor two weapons land on the same spot,
        // which is why dual wielding stacked both daggers instead of crossing them.
        EmoteItemAnchor anchor = arm == state.mainArm
                ? holder.bettercombat$getEmoteItemAnchor()
                : holder.bettercombat$getEmoteOffHandAnchor();
        if (anchor == null) {
            return;
        }

        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        // Into torso space first, so the anchor is expressed against the body and inherits none of
        // the arm's motion - which is exactly what the animation route could not achieve.
        model.body.translateAndRotate(poseStack);
        // ModelPart#translateAndRotate divides by 16, so the stack is in block units from here on.
        // The anchor is authored in model units - the same numbers you read off a model or a rig -
        // so it has to be scaled to match. Feeding raw model units in put the item metres away.
        poseStack.translate(anchor.x() / 16.0F, anchor.y() / 16.0F, anchor.z() / 16.0F);
        poseStack.mulPose(new Quaternionf().rotationXYZ(anchor.pitch(), anchor.yaw(), anchor.roll()));
        ci.cancel();
    }

}
