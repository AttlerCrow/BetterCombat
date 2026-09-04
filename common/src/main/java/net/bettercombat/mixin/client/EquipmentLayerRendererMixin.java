package net.bettercombat.mixin.client;

import net.bettercombat.client.compat.EmfArmorModelBridge;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Draws NightFantasy's 3D armor once instead of once per packed layer, when Entity Model Features is
 * there to draw it properly.
 *
 * <p>The armor's geometry is packed across many vanilla equipment layers - 27 for a chestplate - so a
 * client with only the resource pack can rebuild it in {@code entity.vsh}. A client running EMF never
 * uses those: it draws the CEM model, and a latch in the generated {@code .jem} hides the piece until
 * the final layer. The other layers still cost a full render pass each, and the model carries Fresh
 * Animations' rig, so each pass walks 69 animation channels.
 *
 * <p>Measured on a dev client: with the armor equipped, {@code EMFModelPartRoot.animate} ran 222
 * times per frame for 5.4 ms of a 7.7 ms frame - against 1.8 ms with no armor. The same call on an
 * ordinary entity costs 18 ns; ours cost 22 us. Gating every Fresh Animations expression in the model
 * bought 12% of that, which is what proved the cost is the walking, not the maths. The only thing
 * left worth removing is the repetition itself.
 *
 * <p>So on an EMF client the layer list is cut to one. Nothing is lost: the other 26 were drawing a
 * model the latch was hiding. Without EMF the list is untouched, every layer renders, and the shader
 * path keeps working exactly as before - which is the whole reason the packed layers exist.
 */
@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {

    /** Namespace the armor converter writes its equipment assets under. */
    private static final String ARMOR_NAMESPACE = "nfarmor";

    @Redirect(
            method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;"
                    + "Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;"
                    + "Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
                    + "ILnet/minecraft/resources/Identifier;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/model/EquipmentClientInfo;getLayers"
                            + "(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;)Ljava/util/List;"
            ),
            require = 0
    )
    private List<EquipmentClientInfo.Layer> bettercombat$collapsePackedArmorLayers(
            EquipmentClientInfo info, EquipmentClientInfo.LayerType type) {
        List<EquipmentClientInfo.Layer> layers = info.getLayers(type);
        if (layers.size() < 2 || !EmfArmorModelBridge.collapsesArmorLayers()) {
            return layers;
        }
        // The piece is identified by the texture its layers point at rather than by the equipment
        // asset key, which would mean capturing all eleven arguments of the target method to reach.
        // Every layer of a piece shares a namespace, so the first one answers for the set.
        return ARMOR_NAMESPACE.equals(layers.get(0).textureId().getNamespace())
                ? layers.subList(0, 1)
                : layers;
    }
}
