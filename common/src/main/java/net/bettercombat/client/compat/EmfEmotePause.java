package net.bettercombat.client.compat;

import net.bettercombat.BetterCombatMod;
import net.bettercombat.client.animation.PlayerAttackAnimatable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Function;

/**
 * Hands the player model back to vanilla while an emote is playing, so Entity Model Features and any
 * pack built on it stand aside.
 *
 * <p>Packs like Fresh Animations' player add-on do two separate things: they <em>replace</em> the
 * player model with a custom one, and they animate it, driving all six channels on every part every
 * frame. Player animations write to the vanilla model's parts, so both halves have to be undone -
 * pausing the animation alone leaves a custom model the animation library never reaches, which is
 * why suppressing only the animation changed nothing.
 *
 * <p>Both conditions come from EMF's own API, so this is the supported integration rather than a
 * workaround. It is wired reflectively because EMF is optional; without it, registration is a no-op.
 * The outcome is logged either way - a silent failure here looks exactly like the bug it fixes.
 */
public final class EmfEmotePause {

    private static final String EMF_API = "traben.entity_model_features.EMFAnimationApi";
    private static final String ETF_ENTITY = "traben.entity_texture_features.utils.ETFEntity";

    private EmfEmotePause() {
    }

    public static void register() {
        Class<?> api;
        Method getUuid;
        try {
            api = Class.forName(EMF_API);
            getUuid = Class.forName(ETF_ENTITY).getMethod("etf$getUuid");
        } catch (ReflectiveOperationException | LinkageError absent) {
            BetterCombatMod.LOGGER.info("[bc] EMF not present, no emote compatibility needed.");
            return;
        }

        Function<Object, Boolean> whileEmoting = entity -> {
            try {
                return ownsWholeBody((UUID) getUuid.invoke(entity));
            } catch (ReflectiveOperationException | ClassCastException failure) {
                // Never answer yes to a question we could not evaluate: a stuck condition would
                // freeze the pack's own model for good, which is worse than the conflict it fixes.
                return false;
            }
        };

        // Order matters conceptually, not mechanically: the vanilla-model condition is the one that
        // actually matters, since an emote animates vanilla model parts. Pausing the custom
        // animation as well keeps the pack from fighting on the way in and out of the pose.
        apply(api, "registerVanillaModelCondition", whileEmoting);

        // The pause condition is deliberately not registered. It stopped an EMF pack animating on the
        // way in and out of a pose, but it pauses *every* EMF animation for the entity - including the
        // generated armor .jem, whose expressions are what rotate the knee and elbow from the emote's
        // own bend. Measured: EMF read those variables thousands of times a second right up to the
        // moment an emote started, then stopped entirely while it played.
        //
        // The vanilla-model condition above is the half that matters, and it stays: an emote animates
        // vanilla model parts, so the pack still has to stand aside from the body.
    }

    private static void apply(Class<?> api, String methodName, Function<Object, Boolean> condition) {
        try {
            Object result = api.getMethod(methodName, Function.class).invoke(null, condition);
            BetterCombatMod.LOGGER.info("[bc] EMF {} registered for emotes -> {}", methodName, result);
        } catch (ReflectiveOperationException failure) {
            BetterCombatMod.LOGGER.warn(
                    "[bc] EMF {} unavailable; emotes may conflict with EMF packs: {}",
                    methodName, failure.toString());
        }
    }

    /**
     * Whether an animation owns the player's whole body right now - an emote, or a skill or dodge the
     * server drove - as opposed to a weapon grip or a swing, which only move the arms.
     * {@code EmfPalCompatMixin} needs the same distinction.
     *
     * <p>Skill animations count because they are performances like an emote: a crouch, a leap, a spin.
     * Left to EMF, a pack such as Fresh Animations keeps its own model and its own legs and torso, and
     * of the skill only the arms arrive - a samurai stance drawn as a man standing with his sword up.
     */
    public static boolean ownsWholeBody(UUID playerId) {
        Minecraft client = Minecraft.getInstance();
        // The player being drawn is almost always the one holding the camera, and getPlayerByUUID
        // walks the player list. On a busy server that turned this into an O(players) scan run for
        // every bone of every armor piece each frame, so the common case answers without it.
        Player self = client.player;
        Player player = self != null && self.getUUID().equals(playerId)
                ? self
                : client.level == null ? null : client.level.getPlayerByUUID(playerId);
        return player instanceof PlayerAttackAnimatable animatable
                && (animatable.isEmoteActive() || animatable.isSkillAnimationActive());
    }
}
