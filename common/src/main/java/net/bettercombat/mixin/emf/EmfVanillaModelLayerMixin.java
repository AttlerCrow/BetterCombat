package net.bettercombat.mixin.emf;

import net.bettercombat.client.compat.EmfArmorModelBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets an emote or a weapon pose drop the player model back to vanilla without taking the 3D armor
 * with it.
 *
 * <p>EMF decides vanilla-or-custom once per entity, in
 * {@code EMFAnimationEntityContext.setCurrentEntityIteration}, and applies it in bulk:
 * {@code rootParts.forEach(root -> root.setVariantStateTo(0))}. Those roots are the player model
 * <em>and</em> every armor model together, which is why there is no per-layer question to answer -
 * measured directly: the layer-phase flag was false on all 40 sampled calls. {@code isMainModel} is
 * true only for the entity's own model, so the others can be treated separately.
 *
 * <p>Crucially this re-runs the model's own variant check rather than simply cancelling. State 0 is
 * not only "go vanilla" - it is the same switch EMF uses to <em>change</em> variant, so refusing it
 * outright freezes the root on whatever it last wore: swap chestplates and the previous one keeps
 * rendering. Asking the root to re-pick means a change of armor is still noticed, and the piece that
 * comes back is the one actually equipped.
 *
 * <p>Only the forced case is touched, so a pack that legitimately has no custom model still resolves
 * to vanilla as usual.
 */
@Mixin(targets = "traben.entity_model_features.models.parts.EMFModelPartRoot", remap = false)
public abstract class EmfVanillaModelLayerMixin {

    @Shadow
    public boolean isMainModel;

    @Shadow
    public abstract void doVariantCheck();

    /**
     * Guards the re-entry from {@link #doVariantCheck()}, which sets the variant state itself and
     * would otherwise land straight back in this handler.
     */
    private static boolean bettercombat$rechecking;


    /**
     * Model names this exemption is allowed to touch.
     *
     * <p>Scoped deliberately. Every root that is not the entity's own model matches
     * {@code isMainModel == false}, which on a Fresh Animations client also covers
     * {@code player_inner_armor}, {@code player_outer_armor}, {@code player_cape} and {@code elytra}.
     * Holding a Better Combat weapon forces the vanilla model for as long as the idle pose loops, so
     * an unscoped exemption re-checks FA's own roots every single frame and its animations stop.
     * NightFantasy's armor arrives through EMF's generic fallback names, none of which appear in FA's.
     */
    private static final String[] ARMOR_MODEL_NAMES = {"chestplate", "leggings", "boots", "helmet"};

    @Inject(method = "setVariantStateTo", at = @At("TAIL"), remap = false)
    private void bettercombat$keepArmorOnCustomModel(int state, CallbackInfo ci) {
        // Only the moment EMF hands this entity the vanilla model. An earlier attempt also re-picked
        // when the forcing *ended*, to stop the armor staying stale after an emote - but that branch
        // could fire on a non-zero state, and re-picking there resolved the root to vanilla, which on
        // a client where the shader path draws nothing means the armor simply vanished. Staying stale
        // is a far smaller problem than being invisible.
        if (state != 0 || isMainModel || bettercombat$rechecking) {
            return;
        }
        if (!bettercombat$isNightFantasyArmor() || !EmfArmorModelBridge.entityForcedToVanilla()) {
            return;
        }

        // Restore afterwards rather than refusing the switch: state 0 is also how EMF *changes*
        // variant, so blocking it froze the root on whatever armor it last wore.
        bettercombat$rechecking = true;
        try {
            doVariantCheck();
        } finally {
            bettercombat$rechecking = false;
        }
    }

    private boolean bettercombat$isNightFantasyArmor() {
        // modelName is an EMF type, so it is read reflectively - EMF is not on this mod's compile path.
        String name = EmfArmorModelBridge.modelNameOf(this);
        if (name == null) {
            return false;
        }
        for (String armor : ARMOR_MODEL_NAMES) {
            if (name.contains(armor)) {
                return true;
            }
        }
        return false;
    }
}
