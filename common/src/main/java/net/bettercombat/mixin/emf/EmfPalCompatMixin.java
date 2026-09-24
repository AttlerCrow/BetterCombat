package net.bettercombat.mixin.emf;

import net.bettercombat.client.compat.EmfArmorModelBridge;
import net.bettercombat.client.compat.EmfEmotePause;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops a weapon grip from switching Entity Model Features off.
 *
 * <p>EMF ships its own PlayerAnimationLib integration, and it is deliberately blunt:
 *
 * <pre>{@code
 * // traben.entity_model_features.mod_compat.PALCompat
 * return avatar.playerAnimLib$getAnimManager() != null && manager.isActive();
 * }</pre>
 *
 * <p>{@code EMF} registers that one method twice - as a pause condition when
 * {@code player_animation_library} is installed, and as a <em>vanilla model</em> condition when
 * {@code bendable_cuboids} is. So on this modpack any active player animation both freezes EMF's
 * animations and hands the model back to vanilla.
 *
 * <p>That is the right call for an emote, which owns the whole body for a few seconds. It is the
 * wrong call for Better Combat, whose weapon grips are player animations that <em>never end</em>:
 * hold a katana and EMF stands aside for as long as you hold it, which reads in game as Fresh
 * Animations being switched off by that one weapon. Measured, frame-exact: the grip starting and
 * {@code player_slim} dropping to {@code state=0 forced=true} are the same frame, and it comes back
 * the moment the grip stops. An attack does the same thing for the length of the swing, which is
 * short enough that nobody notices - which is exactly why the bug looked like it belonged to the
 * grip rather than to animations in general.
 *
 * <p>So the condition is narrowed to what it was meant to catch: an animation that owns the whole
 * body - an emote, or a skill or dodge the server drove (see {@link EmfEmotePause#ownsWholeBody}).
 * Grips and swings leave EMF in charge of the body, which is the arrangement {@code emf_compat_better_combat} already
 * assumes - it exists to re-apply Better Combat's arm pose <em>after</em> EMF has animated.
 *
 * <p>{@code @Coerce} carries the entity as a bare {@link Object} because EMF is not on this mod's
 * compile path; {@code require = 0} keeps the mod loading when it is absent.
 */
@Mixin(targets = "traben.entity_model_features.mod_compat.PALCompat", remap = false)
public abstract class EmfPalCompatMixin {

    @Inject(method = "shouldPauseEntityAnim", at = @At("HEAD"), cancellable = true, remap = false,
            require = 0)
    private static void bettercombat$onlyEmotesTakeOverTheModel(@Coerce Object entity,
                                                                CallbackInfoReturnable<Boolean> cir) {
        java.util.UUID id = EmfArmorModelBridge.uuidOf(entity);
        if (id != null && !EmfEmotePause.ownsWholeBody(id)) {
            cir.setReturnValue(false);
        }
    }
}
