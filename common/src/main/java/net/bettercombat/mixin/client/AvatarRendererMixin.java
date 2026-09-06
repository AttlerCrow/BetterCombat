package net.bettercombat.mixin.client;

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
            holder.bettercombat$setHidingCape(animatable.isHidingCape());
        }
    }
}
