package net.bettercombat.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bettercombat.client.animation.EmoteItemAnchorHolder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves the held items undrawn while an emote asks for it.
 *
 * <p>Distinct from an anchor, which moves a weapon somewhere better: some poses want it gone. Both
 * hands of a conga are holding on to the waist ahead, and a katana floating through that reads as a
 * bug however well it is placed.
 *
 * <p>A real cancel rather than a zero scale or an offset off screen, so nothing is submitted for
 * these items at all.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void bettercombat$hideItemsDuringEmote(ArmedEntityRenderState state,
                                                   ItemStackRenderState itemState, ItemStack stack,
                                                   HumanoidArm arm, PoseStack poseStack,
                                                   SubmitNodeCollector collector, int light,
                                                   CallbackInfo ci) {
        if (state instanceof EmoteItemAnchorHolder holder && holder.bettercombat$isHidingEmoteItems()) {
            ci.cancel();
        }
    }
}
