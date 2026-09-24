package net.bettercombat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.bettercombat.client.animation.DashAimHolder;
import net.bettercombat.client.animation.EmoteItemAnchorHolder;
import net.bettercombat.client.animation.PlayerAttackAnimatable;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Copies the emote item anchor from the player onto the render state each frame.
 *
 * <p>The render layer that draws the held item is handed a state, not an entity, so this is the only
 * point where the two are both in scope.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin<T extends Avatar & ClientAvatarEntity> {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void bettercombat$extractEmoteItemAnchor(T entity, AvatarRenderState state, float partialTick,
                                                     CallbackInfo ci) {
        if (entity instanceof PlayerAttackAnimatable animatable) {
            EmoteItemAnchorHolder holder = (EmoteItemAnchorHolder) state;
            holder.bettercombat$setEmoteItemAnchor(animatable.getEmoteItemAnchor());
            holder.bettercombat$setEmoteOffHandAnchor(animatable.getEmoteOffHandAnchor());
            holder.bettercombat$setHidingEmoteItems(animatable.isHidingEmoteItems());
            ((DashAimHolder) state).bettercombat$setDashAim(animatable.getDashAimPitch(partialTick));
        }
    }

    /** Hip height, in blocks: a flat dart lies along it, so tipping about it keeps the body in place. */
    private static final float DASH_AIM_PIVOT = 0.75F;

    /**
     * Tips the whole model along its flight path, after vanilla has turned it to its heading - the
     * same place vanilla tips an elytra flyer, and with its sign: negative about X points the head
     * down. Outside the animation, so a spin inside it stays about the body.
     */
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL"))
    private void bettercombat$tipAlongDash(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale,
                                           CallbackInfo ci) {
        float aim = ((DashAimHolder) state).bettercombat$getDashAim();
        if (Math.abs(aim) < 0.05F) {
            return;
        }
        poseStack.translate(0.0F, DASH_AIM_PIVOT * scale, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-aim));
        poseStack.translate(0.0F, -DASH_AIM_PIVOT * scale, 0.0F);
    }
}
