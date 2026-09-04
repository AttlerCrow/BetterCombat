package net.bettercombat.mixin.client;

import net.bettercombat.client.compat.EmfPoseVariables;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the wearer's finished pose to the 3D armor, so its model can copy the body instead of
 * deriving the same pose a second time.
 *
 * <p>The obvious place for this - the model passed to {@code EquipmentLayerRenderer.renderLayers} -
 * is the wrong one, and quietly so: that model comes from {@code ArmorModelSet.get(slot)}, a separate
 * humanoid that nothing has posed yet at that point. Reading it captured a rest pose, and the armor
 * rendered rigid while the player moved underneath. The parent model is the entity's own, already set
 * up and animated by the time its layers submit.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin extends RenderLayer<net.minecraft.client.renderer.entity.state.HumanoidRenderState,
        HumanoidModel<net.minecraft.client.renderer.entity.state.HumanoidRenderState>> {

    private HumanoidArmorLayerMixin() {
        super(null);
    }

    @Inject(method = "renderArmorPiece", at = @At("HEAD"), require = 0)
    private void bettercombat$capturePose(CallbackInfo ci) {
        HumanoidModel<?> parent = this.getParentModel();
        if (parent != null) {
            EmfPoseVariables.capture(parent);
        }
    }
}
